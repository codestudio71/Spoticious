package com.codestudio71.spoticious.player

import android.content.Context
import android.util.Log
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * LUFS loudness analysis (ITU-R BS.1770-4).
 * STREAMING: processes chunks on-the-fly, stores only block loudness (~1KB/min).
 * No full FloatArray in RAM - works for files of any length without OOM.
 */
object MasterDataAnalyzer {

    private const val TAG = "MasterDataAnalyzer"

    private const val TARGET_SAMPLE_RATE = 48000
    private const val BLOCK_MS = 100
    private const val MOMENTARY_MS = 400
    private const val SHORT_TERM_MS = 3000
    private const val ABSOLUTE_GATE_LUFS = -70.0
    private const val RELATIVE_GATE_LU = 10.0

    // K-weighting coefficients for 48kHz (BS.1770)
    private const val K_PREFILTER_B0 = 1.53512485958697
    private const val K_PREFILTER_B1 = -2.69169618940638
    private const val K_PREFILTER_B2 = 1.19839281085285
    private const val K_PREFILTER_A1 = -1.69065929318241
    private const val K_PREFILTER_A2 = 0.73248077421585

    private const val K_HIGHPASS_B0 = 1.0
    private const val K_HIGHPASS_B1 = -2.0
    private const val K_HIGHPASS_B2 = 1.0
    private const val K_HIGHPASS_A1 = -1.99004745483398
    private const val K_HIGHPASS_A2 = 0.99007225036621

    fun analyze(context: Context, uri: Uri): MasterData? {
        return try {
            Log.d(TAG, "analyze: uri=$uri")
            try {
                val wavResult = decodeWavStreaming(context, uri)
                if (wavResult != null) {
                    Log.d(TAG, "analyze: WAV streaming succeeded")
                    return wavResult
                }
                Log.d(TAG, "analyze: WAV failed, trying MediaCodec streaming")
                val codecResult = decodeWithMediaCodecStreaming(context, uri)
                if (codecResult != null) Log.d(TAG, "analyze: MediaCodec streaming succeeded")
                codecResult
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM - file too large")
                null
            } catch (e: Exception) {
                Log.e(TAG, "analyze: exception", e)
                null
            }
        } catch (t: Throwable) {
            Log.e("MDDiag", "analyze() THREW", t)
            throw t
        }
    }

    /** Compute MasterData from block loudness list (~1KB per minute, not 46MB) */
    private fun computeMasterDataFromBlocks(
        blockLoudnessList: List<Double>,
        peakSample: Float,
        clipCount: Int
    ): MasterData {
        val peakDb = if (peakSample > 0f) 20.0 * log10(peakSample.toDouble()) else -100.0

        if (blockLoudnessList.isEmpty()) return MasterData(
            peakDb = peakDb, clips = clipCount,
            maxLufsM = -100.0, maxLufsS = -100.0, lufsI = -100.0, lra = 0.0
        )

        val gated1 = blockLoudnessList.filter { it > ABSOLUTE_GATE_LUFS }
        if (gated1.isEmpty()) return MasterData(
            peakDb = peakDb, clips = clipCount,
            maxLufsM = -100.0, maxLufsS = -100.0, lufsI = -100.0, lra = 0.0
        )

        val energyAvg1 = gated1.map { 10.0.pow((it + 0.691) / 10.0) }.average()
        val relThreshold = -0.691 + 10 * log10(energyAvg1) - RELATIVE_GATE_LU
        val gated2 = gated1.filter { it > relThreshold }

        val lufsI = if (gated2.isEmpty()) -100.0 else {
            val energyAvg2 = gated2.map { 10.0.pow((it + 0.691) / 10.0) }.average()
            -0.691 + 10 * log10(energyAvg2)
        }

        val blocksPerMomentary = MOMENTARY_MS / BLOCK_MS
        val maxLufsM = if (blockLoudnessList.size >= blocksPerMomentary) {
            (0..blockLoudnessList.size - blocksPerMomentary).maxOfOrNull { start ->
                val energyAvg = blockLoudnessList.subList(start, start + blocksPerMomentary)
                    .map { 10.0.pow((it + 0.691) / 10.0) }.average()
                -0.691 + 10 * log10(energyAvg)
            } ?: -100.0
        } else blockLoudnessList.maxOrNull() ?: -100.0

        val blocksPerShortTerm = SHORT_TERM_MS / BLOCK_MS
        val maxLufsS = if (blockLoudnessList.size >= blocksPerShortTerm) {
            (0..blockLoudnessList.size - blocksPerShortTerm).maxOfOrNull { start ->
                val energyAvg = blockLoudnessList.subList(start, start + blocksPerShortTerm)
                    .map { 10.0.pow((it + 0.691) / 10.0) }.average()
                -0.691 + 10 * log10(energyAvg)
            } ?: -100.0
        } else blockLoudnessList.maxOrNull() ?: -100.0

        // LRA: BS.1770 requires 3s short-term blocks, not 100ms. Aggregate 30 blocks → 3s windows.
        val shortTermBlocks = if (blockLoudnessList.size >= blocksPerShortTerm) {
            (0..blockLoudnessList.size - blocksPerShortTerm).map { start ->
                val window = blockLoudnessList.subList(start, start + blocksPerShortTerm)
                val energyAvg = window.map { 10.0.pow((it + 0.691) / 10.0) }.average()
                -0.691 + 10 * log10(energyAvg)
            }
        } else emptyList()

        val gated1Lra = shortTermBlocks.filter { it > ABSOLUTE_GATE_LUFS }
        val relThresholdLra = if (gated1Lra.isNotEmpty()) {
            val energyAvgLra = gated1Lra.map { 10.0.pow((it + 0.691) / 10.0) }.average()
            -0.691 + 10 * log10(energyAvgLra) - RELATIVE_GATE_LU
        } else -100.0
        val gatedForLra = gated1Lra.filter { it > relThresholdLra }
        val lra = if (gatedForLra.size >= 2) {
            val sorted = gatedForLra.sorted()
            val idx10 = ((sorted.size - 1) * 0.10).roundToInt().coerceIn(0, sorted.size - 1)
            val idx95 = ((sorted.size - 1) * 0.95).roundToInt().coerceIn(0, sorted.size - 1)
            (sorted[idx95] - sorted[idx10]).coerceAtLeast(0.0)
        } else 0.0

        return MasterData(
            peakDb = peakDb, clips = clipCount,
            maxLufsM = maxLufsM, maxLufsS = maxLufsS, lufsI = lufsI, lra = lra
        )
    }

    private fun formatGetIntOrNull(format: MediaFormat, key: String): Int? = try {
        format.getInteger(key)
    } catch (_: Exception) {
        null
    }

    /** encoder-delay / encoder-padding w próbkach na kanał (typowe dla AAC); fallback ~AAC+MP3. */
    private fun readEncoderSkipSamples(format: MediaFormat): Pair<Int, Int> {
        val d = formatGetIntOrNull(format, MediaFormat.KEY_ENCODER_DELAY) ?: 0
        val p = formatGetIntOrNull(format, MediaFormat.KEY_ENCODER_PADDING) ?: 0
        val effD = if (d > 0) d else 2200
        val effP = if (p > 0) p else 1500
        Log.d(TAG, "Encoder delay skipped: $effD, padding skipped: $effP")
        return effD to effP
    }

    private fun blockLufs(samples: FloatArray, channels: Int): Double {
        if (samples.isEmpty()) return -100.0
        val samplesPerChannel = samples.size / channels
        if (samplesPerChannel == 0) return -100.0
        var sumSq = 0.0
        for (ch in 0 until channels) {
            var chSumSq = 0.0
            var i = ch
            while (i < samples.size) {
                val s = samples[i].toDouble()
                chSumSq += s * s
                i += channels
            }
            sumSq += chSumSq / samplesPerChannel  // G_ch=1 per BS.1770 L/R
        }
        if (sumSq <= 0) return -100.0
        return -0.691 + 10 * log10(sumSq)
    }

    private class BiquadFilter(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x0: Double): Double {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0
        }

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }
    }

    /**
     * Streaming processor: only 100ms buffer + List<Double>. ~40KB total instead of 46MB per minute.
     */
    private class StreamingProcessor(private val sampleRate: Int, private val channels: Int) {
        val blockLoudnessList = mutableListOf<Double>()
        var peakSample = 0f
        var clipCount = 0

        private val blockSize = (sampleRate * BLOCK_MS / 1000.0 * channels).toInt()
        private val blockSamples = FloatArray(blockSize)
        private var blockOffset = 0

        private val kFilters = Array(channels) {
            Pair(
                BiquadFilter(K_PREFILTER_B0, K_PREFILTER_B1, K_PREFILTER_B2, K_PREFILTER_A1, K_PREFILTER_A2),
                BiquadFilter(K_HIGHPASS_B0, K_HIGHPASS_B1, K_HIGHPASS_B2, K_HIGHPASS_A1, K_HIGHPASS_A2)
            )
        }

        fun addClips(n: Int) { clipCount += n }

        fun processChunk(rawSamples: FloatArray) {
            var i = 0
            while (i < rawSamples.size) {
                val rawAbs = abs(rawSamples[i])
                if (rawAbs > peakSample) peakSample = rawAbs

                val ch = i % channels
                var s = rawSamples[i].toDouble()
                s = kFilters[ch].first.process(s)
                s = kFilters[ch].second.process(s)
                val f = s.toFloat().coerceIn(-1f, 1f)

                blockSamples[blockOffset++] = f
                if (blockOffset >= blockSize) {
                    blockLoudnessList.add(blockLufs(blockSamples, channels))
                    blockOffset = 0
                }
                i++
            }
        }

        fun finish(): Triple<List<Double>, Float, Int> {
            if (blockOffset > 0) {
                blockLoudnessList.add(blockLufs(blockSamples.copyOf(blockOffset), channels))
            }
            return Triple(blockLoudnessList, peakSample, clipCount)
        }
    }

    private fun decodeWavStreaming(context: Context, uri: Uri): MasterData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                if (input.read(header) < 12) return null
                if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return null
                if (header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte() ||
                    header[10] != 'V'.code.toByte() || header[11] != 'E'.code.toByte()) return null

                var sampleRate = 0
                var channels = 1
                var bitsPerSample = 16
                var audioFormat = 1

                val chunkBuffer = ByteArray(8)
                var dataSize = 0L

                while (input.read(chunkBuffer) == 8) {
                    val chunkId = String(chunkBuffer, 0, 4)
                    val chunkSize = (chunkBuffer[4].toInt() and 0xFF) or
                        ((chunkBuffer[5].toInt() and 0xFF) shl 8) or
                        ((chunkBuffer[6].toInt() and 0xFF) shl 16) or
                        ((chunkBuffer[7].toInt() and 0xFF) shl 24)

                    when (chunkId) {
                        "fmt " -> {
                            val fmtData = ByteArray(chunkSize.coerceAtLeast(16))
                            var read = 0
                            while (read < fmtData.size) {
                                val n = input.read(fmtData, read, fmtData.size - read)
                                if (n <= 0) break
                                read += n
                            }
                            if (read < 16) return null
                            audioFormat = (fmtData[0].toInt() and 0xFF) or ((fmtData[1].toInt() and 0xFF) shl 8)
                            channels = ((fmtData[2].toInt() and 0xFF) or ((fmtData[3].toInt() and 0xFF) shl 8)).coerceAtLeast(1)
                            sampleRate = (fmtData[4].toInt() and 0xFF) or
                                ((fmtData[5].toInt() and 0xFF) shl 8) or
                                ((fmtData[6].toInt() and 0xFF) shl 16) or
                                ((fmtData[7].toInt() and 0xFF) shl 24)
                            bitsPerSample = ((fmtData[14].toInt() and 0xFF) or ((fmtData[15].toInt() and 0xFF) shl 8)).coerceIn(8, 32)
                        }
                        "data" -> {
                            if (sampleRate > 0) { dataSize = chunkSize.toLong(); break }
                            input.skip(chunkSize.toLong())
                        }
                        else -> input.skip((chunkSize + chunkSize % 2).toLong())
                    }
                }

                if (dataSize <= 0 || sampleRate <= 0) return null
                if (bitsPerSample !in listOf(16, 24, 32)) return null

                val bytesPerSample = when (bitsPerSample) { 16 -> 2; 24 -> 3; 32 -> 4; else -> return null }
                val processor = StreamingProcessor(sampleRate, channels)
                val readBuffer = ByteArray(8192)
                var remaining = dataSize
                /** Reszta bajtów między readami — wymagane dla 24-bit (np. 8192 mod 3 = 2). */
                var carry24 = ByteArray(0)
                /** Stan cluster clipów (≥2 kolejne próbki na szynie) — między chunkami WAV. */
                var prevWasRail = false

                while (remaining > 0) {
                    val toRead = minOf(readBuffer.size.toLong(), remaining).toInt()
                    val n = input.read(readBuffer, 0, toRead)
                    if (n <= 0) break
                    remaining -= n

                    val chunkFloats = when {
                        bitsPerSample == 16 && audioFormat == 1 -> {
                            val count = n / 2
                            if (count <= 0) {
                                FloatArray(0)
                            } else {
                                var chunkClips = 0
                                val out = FloatArray(count)
                                var i = 0
                                while (i < count) {
                                    val j = i * 2
                                    val lo = readBuffer[j].toInt() and 0xFF
                                    val hi = readBuffer[j + 1].toInt() and 0xFF
                                    val shortVal = (lo or (hi shl 8)).toShort()
                                    val floatVal = shortVal.toInt() / 32768f
                                    val isClip = abs(floatVal) >= 0.99f
                                    if (isClip && prevWasRail) chunkClips++
                                    prevWasRail = isClip
                                    out[i] = floatVal
                                    i++
                                }
                                processor.addClips(chunkClips)
                                out
                            }
                        }
                        bitsPerSample == 24 && audioFormat == 1 -> {
                            val bytesPerFrame = channels * 3
                            if (bytesPerFrame <= 0) return null
                            val combined = ByteArray(carry24.size + n)
                            if (carry24.isNotEmpty()) {
                                System.arraycopy(carry24, 0, combined, 0, carry24.size)
                            }
                            System.arraycopy(readBuffer, 0, combined, carry24.size, n)
                            val totalComplete = (combined.size / bytesPerFrame) * bytesPerFrame
                            carry24 = if (totalComplete < combined.size) {
                                combined.copyOfRange(totalComplete, combined.size)
                            } else {
                                ByteArray(0)
                            }
                            if (totalComplete == 0) {
                                FloatArray(0)
                            } else {
                                val decodeBytes = combined.copyOfRange(0, totalComplete)
                                val sampleCount = decodeBytes.size / 3
                                var chunkClips = 0
                                val out = FloatArray(sampleCount)
                                var i = 0
                                while (i < sampleCount) {
                                    val j = i * 3
                                    var v = (decodeBytes[j].toInt() and 0xFF) or
                                        ((decodeBytes[j + 1].toInt() and 0xFF) shl 8) or
                                        ((decodeBytes[j + 2].toInt() and 0xFF) shl 16)
                                    if (v >= 0x800000) v -= 0x1000000
                                    val floatVal = v / 8388608f
                                    val isClip = abs(floatVal) >= 0.99f
                                    if (isClip && prevWasRail) chunkClips++
                                    prevWasRail = isClip
                                    out[i] = floatVal
                                    i++
                                }
                                processor.addClips(chunkClips)
                                out
                            }
                        }
                        bitsPerSample == 32 && audioFormat == 3 -> {
                            val bb = ByteBuffer.wrap(readBuffer, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                            val count = n / 4
                            if (count <= 0) FloatArray(0)
                            else {
                                var chunkClips = 0
                                val out = FloatArray(count)
                                var i = 0
                                while (i < count) {
                                    val raw = bb.float
                                    val isClip = abs(raw) >= 0.99f
                                    if (isClip && prevWasRail) chunkClips++
                                    prevWasRail = isClip
                                    out[i++] = raw.coerceIn(-1f, 1f)
                                }
                                processor.addClips(chunkClips)
                                out
                            }
                        }
                        bitsPerSample == 32 && audioFormat == 1 -> {
                            val bb = ByteBuffer.wrap(readBuffer, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                            val count = n / 4
                            if (count <= 0) FloatArray(0)
                            else {
                                var chunkClips = 0
                                val out = FloatArray(count)
                                var i = 0
                                while (i < count) {
                                    val iv = bb.int
                                    val floatVal = (iv / 2147483648f).coerceIn(-1f, 1f)
                                    val isClip = abs(floatVal) >= 0.99f
                                    if (isClip && prevWasRail) chunkClips++
                                    prevWasRail = isClip
                                    out[i++] = floatVal
                                }
                                processor.addClips(chunkClips)
                                out
                            }
                        }
                        else -> return null
                    }
                    processor.processChunk(chunkFloats)
                }

                val (blocks, peak, clips) = processor.finish()
                computeMasterDataFromBlocks(blocks, peak, clips)
            }
        } catch (e: Exception) {
            Log.e(TAG, "decodeWavStreaming: exception", e)
            null
        }
    }

    private fun decodeWithMediaCodecStreaming(context: Context, uri: Uri): MasterData? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                try {
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } catch (_: Exception) { false }
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val trackDurationUs = try { format.getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { 0L }

            val sourceSampleRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 48000 }
            val sourceChannels = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 2 }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val outputFormat = decoder.outputFormat
            val outputSampleRate = try { outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { sourceSampleRate }
            val outputChannels = try { outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { sourceChannels }

            var encoderDelaySamples = 2200
            var encoderPaddingSamples = 1500
            readEncoderSkipSamples(outputFormat).let { pair ->
                encoderDelaySamples = pair.first
                encoderPaddingSamples = pair.second
            }

            val samplesPerChannelEst = if (trackDurationUs > 0L && outputSampleRate > 0) {
                trackDurationUs * outputSampleRate.toLong() / 1_000_000L
            } else {
                0L
            }
            val totalEstInterleaved: Long = if (samplesPerChannelEst > 0L && outputChannels > 0) {
                val cap = minOf(samplesPerChannelEst, Long.MAX_VALUE / outputChannels.coerceAtLeast(1))
                cap * outputChannels
            } else {
                Long.MAX_VALUE
            }

            var globalOutputSampleIndex = 0L
            var prevWasRailMc = false

            // STREAMING: only block buffer + List<Double>, no full FloatArray
            val processor = StreamingProcessor(outputSampleRate, outputChannels)
            val bufferInfo = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false
            var emptyCount = 0

            while (!outputDone && emptyCount < 100) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(5000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 5000)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> emptyCount++
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        readEncoderSkipSamples(decoder.outputFormat).let { pair ->
                            encoderDelaySamples = pair.first
                            encoderPaddingSamples = pair.second
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        emptyCount = 0
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (bufferInfo.size > 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                // Nie kopiuj całego bufora do byte[] (duże chunki → OOM ~50MB+).
                                // PCM 16-bit LE: przetwarzaj krótkimi seriami.
                                val dup = outputBuffer.duplicate()
                                dup.order(ByteOrder.nativeOrder())
                                dup.position(bufferInfo.offset)
                                dup.limit(bufferInfo.offset + bufferInfo.size)
                                val shortBuf = dup.asShortBuffer()
                                val totalShorts = shortBuf.remaining()
                                val maxShortsPerChunk = 8192
                                var offset = 0
                                while (offset < totalShorts) {
                                    val chunkShorts = minOf(maxShortsPerChunk, totalShorts - offset)
                                    shortBuf.position(offset)
                                    shortBuf.limit(offset + chunkShorts)
                                    var chunkClips = 0
                                    val chunk = FloatArray(chunkShorts)
                                    var w = 0
                                    while (shortBuf.hasRemaining()) {
                                        val shortVal = shortBuf.get()
                                        val skipStart =
                                            globalOutputSampleIndex < encoderDelaySamples.toLong() * outputChannels
                                        val skipEnd = totalEstInterleaved != Long.MAX_VALUE &&
                                            globalOutputSampleIndex >=
                                            totalEstInterleaved - encoderPaddingSamples.toLong() * outputChannels
                                        globalOutputSampleIndex++

                                        if (skipStart || skipEnd) {
                                            prevWasRailMc = false
                                            continue
                                        }

                                        val floatVal = shortVal.toInt() / 32768f
                                        val isClip = abs(floatVal) >= 0.99f
                                        if (isClip && prevWasRailMc) chunkClips++
                                        prevWasRailMc = isClip
                                        chunk[w++] = floatVal
                                    }
                                    if (w > 0) {
                                        processor.addClips(chunkClips)
                                        processor.processChunk(if (w == chunk.size) chunk else chunk.copyOf(w))
                                    }
                                    offset += chunkShorts
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            }

            val (blocks, peak, clips) = processor.finish()
            Log.d(TAG, "decodeWithMediaCodecStreaming: ${blocks.size} blocks")
            computeMasterDataFromBlocks(blocks, peak, clips)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "decodeWithMediaCodecStreaming: OOM")
            null
        } catch (e: Exception) {
            Log.e(TAG, "decodeWithMediaCodecStreaming: exception", e)
            null
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }
}
