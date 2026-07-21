package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.net.Uri
import com.codestudio71.spoticious.audio.cut.PcmStreamDecoder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/** Miks wokal + bit po STOP (freestyle); beat dekodowany z URI, nie z mikrofonu. */
object FreestyleMixdown {

    const val VOCAL_GAIN = 0.9f
    const val BEAT_GAIN = 0.55f

    /** Próg poniżej którego suma idzie 1:1; powyżej — miękkie ograniczanie (bez twardego clipu). */
    private const val SOFT_LIMIT_THRESHOLD = 0.92f

    private const val WAV_HEADER_SIZE = 44
    private const val CHUNK_PCM_BYTES = 1_048_576

    /**
     * Nadpisuje [vocalWav] zmiksowanym mono PCM WAV (ten sam sample rate co nagranie).
     * @param beatStartOffsetMs beat wystartował później niż mikrofon o tyle ms —
     *   w miksie beat jest przesunięty w przód, żeby wokal i beat się nie rozjechały.
     * Beat krótszy niż nagranie jest **zapętlany** (jak [BeatPreviewPlayer] REPEAT_MODE_ONE).
     * @return false przy błędzie odczytu wokalu lub dekodowania bitu.
     */
    fun mixInPlace(
        context: Context,
        vocalWav: File,
        beatUri: Uri,
        targetSampleRate: Int,
        beatGain: Float = BEAT_GAIN,
        beatStartOffsetMs: Long = 0L,
    ): Boolean {
        val info = readWavPcmInfo(vocalWav) ?: return false
        if (info.frameCount <= 0) return false

        val rate = info.sampleRate.coerceAtLeast(1)
        val beatOffsetSamples =
            (beatStartOffsetMs.coerceAtLeast(0L) * rate / 1000L)
                .coerceAtMost(info.frameCount.toLong())
                .toInt()

        // Pełny cykl beatu (cap ~12 min) — zapętlamy jak REPEAT_MODE_ONE w preview.
        val maxBeatLoopSamples =
            (rate.toLong() * 60L * 12L).coerceAtMost(Int.MAX_VALUE.toLong() / 4).toInt()
        val beatMono =
            PcmStreamDecoder.decodeUriToMonoFloatLimited(
                context = context,
                uri = beatUri,
                targetSampleRate = rate,
                maxSamples = maxBeatLoopSamples,
            ) ?: return false
        if (beatMono.isEmpty()) return false

        val temp = File(vocalWav.path + ".mix")
        val gain = beatGain.coerceIn(0f, 1f)
        val bps = if (info.bitsPerSample == 24) 24 else 16

        return try {
            RandomAccessFile(temp, "rw").use { outRaf ->
                outRaf.write(ByteArray(WAV_HEADER_SIZE))
                var globalFrame = 0

                RandomAccessFile(vocalWav, "r").use { inRaf ->
                    inRaf.seek(info.dataOffset.toLong())
                    var bytesRemaining = info.dataSize
                    val readBuf = ByteArray(min(CHUNK_PCM_BYTES, info.dataSize.coerceAtLeast(1)))

                    while (bytesRemaining > 0) {
                        val toRead = min(readBuf.size, bytesRemaining)
                        inRaf.readFully(readBuf, 0, toRead)
                        val framesInChunk = toRead / info.bytesPerFrame
                        if (framesInChunk <= 0) break

                        val vocalMono =
                            pcmBytesToMonoFloat(
                                pcmBytes = readBuf,
                                frameCount = framesInChunk,
                                channels = info.channels,
                                bitsPerSample = info.bitsPerSample,
                            )
                        val mixedBytes =
                            mixAndEncodeChunk(
                                vocalMono = vocalMono,
                                globalFrameStart = globalFrame,
                                beatMono = beatMono,
                                beatOffsetSamples = beatOffsetSamples,
                                beatGain = gain,
                                bitsPerSample = bps,
                            )
                        outRaf.write(mixedBytes)
                        globalFrame += framesInChunk
                        bytesRemaining -= toRead
                    }
                }

                val pcmLen = (outRaf.length() - WAV_HEADER_SIZE).coerceAtLeast(0L)
                if (pcmLen <= 0L) {
                    temp.delete()
                    return false
                }
                outRaf.seek(0)
                VocalRecorder.writeStdPcmWaveHeader(outRaf, rate, bps, 1, pcmLen)
            }

            if (!vocalWav.delete()) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(vocalWav)) {
                temp.delete()
                return false
            }
            true
        } catch (_: Exception) {
            temp.delete()
            false
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

    private fun pcmBytesToMonoFloat(
        pcmBytes: ByteArray,
        frameCount: Int,
        channels: Int,
        bitsPerSample: Int,
    ): FloatArray {
        val mono = FloatArray(frameCount)
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
                    mono[f] = sum / channels
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
                    mono[f] = sum / channels
                }
            }
        }
        return mono
    }

    private fun mixAndEncodeChunk(
        vocalMono: FloatArray,
        globalFrameStart: Int,
        beatMono: FloatArray,
        beatOffsetSamples: Int,
        beatGain: Float,
        bitsPerSample: Int,
    ): ByteArray {
        val beatLen = beatMono.size
        val mixed = FloatArray(vocalMono.size)
        for (j in vocalMono.indices) {
            val i = globalFrameStart + j
            val v = vocalMono[j] * VOCAL_GAIN
            val bi = i - beatOffsetSamples
            val b =
                when {
                    beatLen <= 0 || bi < 0 -> 0f
                    // Zapętlenie jak REPEAT_MODE_ONE w BeatPreviewPlayer
                    else -> beatMono[bi % beatLen] * beatGain
                }
            mixed[j] = softLimit(v + b)
        }
        return when (bitsPerSample) {
            24 -> encode24(mixed)
            else -> encode16(mixed)
        }
    }

    /**
     * Miękkie ograniczanie zamiast twardego coerceIn(-1,1) — mniej cyfrowego spłaszczenia
     * przy głośnym wokalu + beacie.
     */
    private fun softLimit(x: Float): Float {
        val ax = kotlin.math.abs(x)
        if (ax <= SOFT_LIMIT_THRESHOLD) return x
        val sign = if (x >= 0f) 1f else -1f
        val excess = ax - SOFT_LIMIT_THRESHOLD
        val room = 1f - SOFT_LIMIT_THRESHOLD
        val shaped = SOFT_LIMIT_THRESHOLD + room * (excess / (excess + room))
        return sign * shaped.coerceAtMost(1f)
    }

    private fun encode16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        var o = 0
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
            out[o++] = (v and 0xff).toByte()
            out[o++] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }

    private fun encode24(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 3)
        var o = 0
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 8_388_607f).roundToInt().coerceIn(-8_388_608, 8_388_607)
            out[o++] = (v and 0xff).toByte()
            out[o++] = ((v shr 8) and 0xff).toByte()
            out[o++] = ((v shr 16) and 0xff).toByte()
        }
        return out
    }
}
