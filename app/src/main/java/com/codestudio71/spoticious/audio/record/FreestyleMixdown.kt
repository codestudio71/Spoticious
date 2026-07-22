package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.net.Uri
import com.codestudio71.spoticious.audio.cut.PcmStreamDecoder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Miks wokal + bit po STOP (freestyle) — w pełni strumieniowy:
 * - beat dekodowany tylko do długości nagrania (cap [MAX_BEAT_LOOP_MINUTES]) i trzymany
 *   w tymczasowym pliku PCM 16-bit na dysku, nie w RAM;
 * - wokal czytany chunkami wyrównanymi do pełnych ramek ([CHUNK_FRAMES] × bytesPerFrame) —
 *   rozmiar odczytu NIGDY nie tnie próbki w połowie (historyczny bug: chunk 1 MiB przy
 *   3 bajtach/ramkę rozjeżdżał alignment po ~7,3 s i zamieniał wokal w szum);
 * - gain beatu może być automatyką z suwaka ([GainEvent]) — miks odtwarza dokładnie to,
 *   co user słyszał podczas REC, z wygładzaniem anty-zipper.
 */
object FreestyleMixdown {

    const val VOCAL_GAIN = 0.9f

    /**
     * Domyślna pozycja suwaka BEAT (0..1). Realny gain = [sliderToLinearGain]
     * (krzywa pod freestyle — mic dyktafonu rzadko wychodzi powyżej ok. −18 dBFS).
     */
    const val BEAT_GAIN = 0.55f

    /**
     * Sufit liniowy przy 100% suwaka — bit nigdy nie idzie na full peak pliku
     * (to by zjadło wokal z telefonu).
     */
    private const val BEAT_GAIN_MAX_LINEAR = 0.4f

    /**
     * Suwak UI 0..1 → liniowy gain do ExoPlayer / miksu.
     * - 0% → 0 → meter −60
     * - 50% → 0.10 ≈ −20 dB względem peaku pliku
     * - 100% → 0.40 ≈ −8 dB względem peaku (nie 0 dBFS)
     */
    fun sliderToLinearGain(slider: Float): Float {
        val s = slider.coerceIn(0f, 1f)
        return s * s * BEAT_GAIN_MAX_LINEAR
    }

    /** Próg poniżej którego suma idzie 1:1; powyżej — miękkie ograniczanie (bez twardego clipu). */
    private const val SOFT_LIMIT_THRESHOLD = 0.92f

    private const val WAV_HEADER_SIZE = 44

    /**
     * Chunk w RAMKACH, nie w bajtach — odczyt zawsze wielokrotnością bytesPerFrame,
     * niezależnie od bit depth (16/24) i liczby kanałów.
     */
    private const val CHUNK_FRAMES = 65_536

    /** Stała czasowa wygładzania zmian gainu beatu w miksie (anty-zipper przy automatyce). */
    private const val GAIN_SMOOTH_MS = 12.0

    /** Cap długości zapętlanego beatu — plik tymczasowy na dysku, więc limit jest hojny. */
    private const val MAX_BEAT_LOOP_MINUTES = 15L

    /** Ruch suwaka BEAT podczas REC: czas od startu mikrofonu + docelowy gain liniowy. */
    data class GainEvent(val timeMs: Long, val linearGain: Float)

    /**
     * Nadpisuje [vocalWav] zmiksowanym mono PCM WAV (ten sam sample rate i bit depth co nagranie).
     * @param beatGain statyczny gain liniowy beatu — używany, gdy [beatGainAutomation] puste.
     * @param beatStartOffsetMs beat wystartował później niż mikrofon o tyle ms —
     *   w miksie beat jest przesunięty w przód, żeby wokal i beat się nie rozjechały.
     * @param beatGainAutomation zmiany suwaka podczas REC (czas względem startu mikrofonu);
     *   miks odtwarza je per-sample z wygładzaniem, jak automatyka głośności.
     * Beat krótszy niż nagranie jest **zapętlany** (jak [BeatPreviewPlayer] REPEAT_MODE_ONE).
     * @return false przy błędzie odczytu wokalu lub dekodowania bitu.
     */
    fun mixInPlace(
        context: Context,
        vocalWav: File,
        beatUri: Uri,
        beatGain: Float = sliderToLinearGain(BEAT_GAIN),
        beatStartOffsetMs: Long = 0L,
        beatGainAutomation: List<GainEvent> = emptyList(),
    ): Boolean {
        val info = readWavPcmInfo(vocalWav) ?: return false
        if (info.frameCount <= 0) return false

        val rate = info.sampleRate.coerceAtLeast(1)
        val beatOffsetSamples = beatStartOffsetMs.coerceAtLeast(0L) * rate / 1000L
        val beatSamplesNeeded = info.frameCount.toLong() - beatOffsetSamples
        // Beat zacząłby się za końcem nagrania — wokal zostaje nietknięty (bit-exact).
        if (beatSamplesNeeded <= 0L) return true

        val beatPcm = File(vocalWav.path + ".beatpcm")
        val temp = File(vocalWav.path + ".mix")
        val maxLoopSamples = rate.toLong() * 60L * MAX_BEAT_LOOP_MINUTES

        return try {
            val beatSampleCount =
                decodeBeatToPcm16File(
                    context = context,
                    uri = beatUri,
                    targetSampleRate = rate,
                    maxSamples = min(beatSamplesNeeded, maxLoopSamples),
                    outFile = beatPcm,
                )
            if (beatSampleCount <= 0L) return false

            RandomAccessFile(temp, "rw").use { outRaf ->
                outRaf.write(ByteArray(WAV_HEADER_SIZE))
                RandomAccessFile(beatPcm, "r").use { beatRaf ->
                    RandomAccessFile(vocalWav, "r").use { inRaf ->
                        mixVocalWithBeat(
                            inRaf = inRaf,
                            outRaf = outRaf,
                            beatRaf = beatRaf,
                            info = info,
                            beatSampleCount = beatSampleCount,
                            beatOffsetSamples = beatOffsetSamples,
                            staticBeatGain = beatGain.coerceIn(0f, 1f),
                            automation = beatGainAutomation,
                        )
                    }
                }
                val pcmLen = (outRaf.length() - WAV_HEADER_SIZE).coerceAtLeast(0L)
                if (pcmLen <= 0L) return false
                outRaf.seek(0)
                VocalRecorder.writeStdPcmWaveHeader(
                    outRaf,
                    rate,
                    if (info.bitsPerSample == 24) 24 else 16,
                    1,
                    pcmLen,
                )
            }

            // rename(2) na tym samym katalogu podmienia atomowo; fallback dla FS bez replace.
            if (temp.renameTo(vocalWav)) {
                true
            } else {
                vocalWav.delete() && temp.renameTo(vocalWav)
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { beatPcm.delete() }
            runCatching { if (temp.exists()) temp.delete() }
        }
    }

    private fun mixVocalWithBeat(
        inRaf: RandomAccessFile,
        outRaf: RandomAccessFile,
        beatRaf: RandomAccessFile,
        info: WavPcmInfo,
        beatSampleCount: Long,
        beatOffsetSamples: Long,
        staticBeatGain: Float,
        automation: List<GainEvent>,
    ) {
        val bps = if (info.bitsPerSample == 24) 24 else 16
        val chunkBytes = CHUNK_FRAMES * info.bytesPerFrame
        val readBuf = ByteArray(chunkBytes)
        val vocalMono = FloatArray(CHUNK_FRAMES)
        val beatBuf = FloatArray(CHUNK_FRAMES)
        val beatScratch = ByteArray(CHUNK_FRAMES * 2)
        val outBuf = ByteArray(CHUNK_FRAMES * 3)
        val gainCurve = GainCurve(automation, info.sampleRate, staticBeatGain)

        inRaf.seek(info.dataOffset.toLong())
        var bytesRemaining = info.dataSize.toLong()
        var globalFrame = 0L

        while (bytesRemaining >= info.bytesPerFrame) {
            val toRead =
                (min(chunkBytes.toLong(), bytesRemaining).toInt() / info.bytesPerFrame) *
                    info.bytesPerFrame
            inRaf.readFully(readBuf, 0, toRead)
            val frames = toRead / info.bytesPerFrame

            pcmBytesToMonoFloat(readBuf, frames, info.channels, info.bitsPerSample, vocalMono)
            readBeatLooped(
                raf = beatRaf,
                totalSamples = beatSampleCount,
                startSample = globalFrame - beatOffsetSamples,
                out = beatBuf,
                count = frames,
                scratch = beatScratch,
            )

            for (j in 0 until frames) {
                vocalMono[j] =
                    softLimit(vocalMono[j] * VOCAL_GAIN + beatBuf[j] * gainCurve.next())
            }

            val outLen =
                when (bps) {
                    24 -> encode24(vocalMono, frames, outBuf)
                    else -> encode16(vocalMono, frames, outBuf)
                }
            outRaf.write(outBuf, 0, outLen)

            globalFrame += frames
            bytesRemaining -= toRead
        }
    }

    /**
     * Dekoduje beat strumieniowo (mono, resampling do [targetSampleRate]) i zapisuje jako
     * surowy PCM 16-bit LE do [outFile]. Zwraca liczbę zapisanych próbek (≤ [maxSamples]).
     */
    private fun decodeBeatToPcm16File(
        context: Context,
        uri: Uri,
        targetSampleRate: Int,
        maxSamples: Long,
        outFile: File,
    ): Long {
        if (maxSamples <= 0L) return 0L
        return try {
            BufferedOutputStream(FileOutputStream(outFile), 1 shl 16).use { os ->
                var byteBuf = ByteArray(0)
                PcmStreamDecoder.decodeUriToMonoStreaming(
                    context = context,
                    uri = uri,
                    targetSampleRate = targetSampleRate,
                    maxSamples = maxSamples,
                ) { samples, count ->
                    if (byteBuf.size < count * 2) byteBuf = ByteArray(count * 2)
                    var o = 0
                    for (i in 0 until count) {
                        val v =
                            (samples[i].coerceIn(-1f, 1f) * 32767f)
                                .roundToInt()
                                .coerceIn(-32768, 32767)
                        byteBuf[o++] = (v and 0xff).toByte()
                        byteBuf[o++] = ((v shr 8) and 0xff).toByte()
                    }
                    os.write(byteBuf, 0, o)
                }
            }
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Czyta [count] próbek beatu (16-bit LE mono) od globalnego indeksu [startSample],
     * zapętlając plik jak REPEAT_MODE_ONE. Indeksy < 0 (beat jeszcze nie wystartował) → cisza.
     */
    private fun readBeatLooped(
        raf: RandomAccessFile,
        totalSamples: Long,
        startSample: Long,
        out: FloatArray,
        count: Int,
        scratch: ByteArray,
    ) {
        var j = 0
        while (j < count) {
            val idx = startSample + j
            if (idx < 0L) {
                val silent = min(-idx, (count - j).toLong()).toInt()
                java.util.Arrays.fill(out, j, j + silent, 0f)
                j += silent
                continue
            }
            val pos = idx % totalSamples
            val span = min(totalSamples - pos, (count - j).toLong()).toInt()
            raf.seek(pos * 2L)
            raf.readFully(scratch, 0, span * 2)
            var o = 0
            for (k in 0 until span) {
                val lo = scratch[o].toInt() and 0xff
                val hi = scratch[o + 1].toInt()
                out[j + k] = ((hi shl 8) or lo) / 32768f
                o += 2
            }
            j += span
        }
    }

    /**
     * Per-sample gain beatu z automatyki suwaka: step do najnowszego eventu + one-pole
     * smoothing ([GAIN_SMOOTH_MS]) — bez zipper noise w finalnym pliku.
     */
    private class GainCurve(
        events: List<GainEvent>,
        sampleRate: Int,
        fallbackLinearGain: Float,
    ) {
        private val eventFrames: LongArray
        private val eventGains: FloatArray
        private var idx = 1
        private var frame = 0L
        private var current: Float
        private var target: Float
        private val coeff: Float

        init {
            val sorted = events.sortedBy { it.timeMs }
            if (sorted.isEmpty()) {
                eventFrames = longArrayOf(0L)
                eventGains = floatArrayOf(fallbackLinearGain.coerceIn(0f, 1f))
            } else {
                eventFrames = LongArray(sorted.size) { sorted[it].timeMs * sampleRate / 1000L }
                eventGains = FloatArray(sorted.size) { sorted[it].linearGain.coerceIn(0f, 1f) }
            }
            current = eventGains[0]
            target = eventGains[0]
            coeff =
                (1.0 - exp(-1000.0 / (sampleRate.coerceAtLeast(1) * GAIN_SMOOTH_MS))).toFloat()
        }

        fun next(): Float {
            while (idx < eventFrames.size && frame >= eventFrames[idx]) {
                target = eventGains[idx]
                idx++
            }
            frame++
            if (current != target) {
                current += (target - current) * coeff
                if (abs(target - current) < 1e-4f) current = target
            }
            return current
        }
    }

    private data class WavPcmInfo(
        val sampleRate: Int,
        val bitsPerSample: Int,
        val channels: Int,
        val dataOffset: Int,
        val dataSize: Int,
        val frameCount: Int,
        val bytesPerFrame: Int,
    )

    private fun readWavPcmInfo(file: File): WavPcmInfo? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < WAV_HEADER_SIZE + 2) return null
                val header = ByteArray(WAV_HEADER_SIZE)
                raf.readFully(header)
                if (!header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())) return null
                if (!header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) return null

                val channels =
                    ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        .coerceAtLeast(1)
                val sampleRate =
                    ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val bitsPerSample =
                    ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val dataSize =
                    ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        .coerceAtLeast(0)
                if (bitsPerSample != 16 && bitsPerSample != 24) return null

                val bytesPerFrame = channels * bitsPerSample / 8
                if (bytesPerFrame <= 0) return null
                val frameCount = dataSize / bytesPerFrame
                if (frameCount <= 0) return null

                WavPcmInfo(
                    sampleRate = sampleRate,
                    bitsPerSample = bitsPerSample,
                    channels = channels,
                    dataOffset = WAV_HEADER_SIZE,
                    dataSize = dataSize.coerceAtMost((raf.length() - WAV_HEADER_SIZE).toInt()),
                    frameCount = frameCount,
                    bytesPerFrame = bytesPerFrame,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Dekoduje [frameCount] ramek PCM z [pcmBytes] do mono float w [out] (reużywany bufor). */
    private fun pcmBytesToMonoFloat(
        pcmBytes: ByteArray,
        frameCount: Int,
        channels: Int,
        bitsPerSample: Int,
        out: FloatArray,
    ) {
        when (bitsPerSample) {
            16 -> {
                var fi = 0
                for (f in 0 until frameCount) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val lo = pcmBytes[fi].toInt() and 0xff
                        val hi = pcmBytes[fi + 1].toInt()
                        sum += ((hi shl 8) or lo) / 32768f
                        fi += 2
                    }
                    out[f] = sum / channels
                }
            }

            24 -> {
                var fi = 0
                for (f in 0 until frameCount) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val b0 = pcmBytes[fi].toInt() and 0xff
                        val b1 = pcmBytes[fi + 1].toInt() and 0xff
                        val b2 = pcmBytes[fi + 2].toInt()
                        val s = b0 or (b1 shl 8) or (b2 shl 16)
                        val v = (s shl 8) shr 8
                        sum += v / 8_388_608f
                        fi += 3
                    }
                    out[f] = sum / channels
                }
            }
        }
    }

    /**
     * Miękkie ograniczanie zamiast twardego coerceIn(-1,1) — mniej cyfrowego spłaszczenia
     * przy głośnym wokalu + beacie.
     */
    private fun softLimit(x: Float): Float {
        val ax = abs(x)
        if (ax <= SOFT_LIMIT_THRESHOLD) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = ax - SOFT_LIMIT_THRESHOLD
        val room = 1f - SOFT_LIMIT_THRESHOLD
        val shaped = SOFT_LIMIT_THRESHOLD + room * (excess / (excess + room))
        return sign * shaped.coerceAtMost(1f)
    }

    private fun encode16(samples: FloatArray, count: Int, out: ByteArray): Int {
        var o = 0
        for (i in 0 until count) {
            val v =
                (samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
            out[o++] = (v and 0xff).toByte()
            out[o++] = ((v shr 8) and 0xff).toByte()
        }
        return o
    }

    private fun encode24(samples: FloatArray, count: Int, out: ByteArray): Int {
        var o = 0
        for (i in 0 until count) {
            val v =
                (samples[i].coerceIn(-1f, 1f) * 8_388_607f)
                    .roundToInt()
                    .coerceIn(-8_388_608, 8_388_607)
            out[o++] = (v and 0xff).toByte()
            out[o++] = ((v shr 8) and 0xff).toByte()
            out[o++] = ((v shr 16) and 0xff).toByte()
        }
        return o
    }
}
