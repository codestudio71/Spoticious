package com.codestudio71.spoticious.audio.cut

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlin.math.roundToInt

/** MediaExtractor + MediaCodec — strumieniowo (peaki, okno czasowe), bez całego pliku w RAM. */
object PcmStreamDecoder {

    const val EXPORT_BITS_PER_SAMPLE = 24

    fun decodeUriToMonoFloat(
        context: Context,
        uri: Uri,
        targetSampleRate: Int,
    ): FloatArray? {
        var session: DecodeSession? = null
        return try {
            session = openSession(context, uri) ?: return null
            val decoded = session.decodeAllToInterleavedFloat()
            if (decoded.samples.isEmpty()) return null
            val mono = interleavedToMono(decoded.samples, decoded.channelCount)
            resampleMono(mono, decoded.sampleRate, targetSampleRate)
        } catch (_: Exception) {
            null
        } finally {
            session?.release()
        }
    }

    /**
     * Jak [decodeUriToMonoFloat], ale kończy dekod po zebraniu tyle próbek źródłowych,
     * ile wystarczy do [maxSamples] po resamplingu — mniejszy peak RAM przy długich beatach.
     */
    fun decodeUriToMonoFloatLimited(
        context: Context,
        uri: Uri,
        targetSampleRate: Int,
        maxSamples: Int,
    ): FloatArray? {
        if (maxSamples <= 0) return FloatArray(0)
        var session: DecodeSession? = null
        return try {
            session = openSession(context, uri) ?: return null
            val dstRate = targetSampleRate.coerceAtLeast(1)
            var srcRate = session.sampleRate
            var channelCount = session.channelCount
            val srcSamplesNeeded =
                if (maxSamples <= 1) {
                    1
                } else {
                    ((maxSamples - 1L) * srcRate / dstRate).toInt() + 2
                }
            val monoSrc = FloatArray(srcSamplesNeeded)
            var monoCount = 0

            session.decodeStreaming(
                shouldStop = { monoCount >= srcSamplesNeeded },
                onChunk = { chunk, _, channels, rate, pcmEncoding ->
                    if (monoCount >= srcSamplesNeeded) return@decodeStreaming
                    srcRate = rate
                    channelCount = channels.coerceAtLeast(1)
                    val bps = bytesPerSample(pcmEncoding)
                    val frames = chunk.size / bps / channelCount
                    if (frames <= 0) return@decodeStreaming

                    var byteIndex = 0
                    var frameIndex = 0
                    while (frameIndex < frames && monoCount < srcSamplesNeeded) {
                        var mono = 0f
                        for (ch in 0 until channelCount) {
                            mono += readSampleFloat(chunk, byteIndex, pcmEncoding)
                            byteIndex += bps
                        }
                        monoSrc[monoCount++] = mono / channelCount
                        frameIndex++
                    }
                },
            )

            if (monoCount <= 0) return null
            val resampled = resampleMono(monoSrc.copyOf(monoCount), srcRate, dstRate)
            if (resampled.size <= maxSamples) resampled else resampled.copyOf(maxSamples)
        } catch (_: Exception) {
            null
        } finally {
            session?.release()
        }
    }

    /**
     * Szacunek peak dBFS pliku (pierwsze [probeSeconds] albo cały krótki plik).
     * Do UI metra BEAT: wyświetlane ≈ peak + 20·log10(gain).
     */
    fun estimatePeakDbfs(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = 48_000,
        probeSeconds: Int = 45,
    ): Float {
        val maxSamples = (targetSampleRate.toLong() * probeSeconds.coerceAtLeast(1)).toInt()
        val samples =
            decodeUriToMonoFloatLimited(context, uri, targetSampleRate, maxSamples)
                ?: return -12f
        var peak = 1e-6f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
        }
        return (20.0 * kotlin.math.log10(peak.toDouble())).toFloat().coerceIn(-60f, 0f)
    }

    fun extractPeaks(
        context: Context,
        uri: Uri,
        bucketCount: Int = 3000,
        onProgress: (Float) -> Unit = {},
    ): WaveformPeaks? {
        var session: DecodeSession? = null
        return try {
            session = openSession(context, uri) ?: return null
            val buckets = bucketCount.coerceIn(256, 8000)
            val min = FloatArray(buckets) { 0f }
            val max = FloatArray(buckets) { 0f }
            val touched = BooleanArray(buckets)

            var durationUs = session.durationUs.coerceAtLeast(1L)
            var maxSeenUs = 0L

            session.decodeStreaming(onChunk = { chunk, presentationTimeUs, channelCount, sampleRate, pcmEncoding ->
                if (presentationTimeUs > maxSeenUs) maxSeenUs = presentationTimeUs
                if (durationUs <= 1L && maxSeenUs > 0L) durationUs = maxSeenUs.coerceAtLeast(1L)

                val frames = chunk.size / bytesPerSample(pcmEncoding) / channelCount.coerceAtLeast(1)
                if (frames <= 0) return@decodeStreaming

                val samplesPerFrame = channelCount.coerceAtLeast(1)
                val frameDurUs = 1_000_000L / sampleRate.coerceAtLeast(1)

                var frameIndex = 0
                var byteIndex = 0
                val bps = bytesPerSample(pcmEncoding)
                while (frameIndex < frames) {
                    var mono = 0f
                    for (ch in 0 until samplesPerFrame) {
                        mono += readSampleFloat(chunk, byteIndex, pcmEncoding)
                        byteIndex += bps
                    }
                    mono /= samplesPerFrame

                    val timeUs = presentationTimeUs + frameIndex * frameDurUs
                    val bucket =
                        ((timeUs * buckets) / durationUs)
                            .toInt()
                            .coerceIn(0, buckets - 1)
                    if (!touched[bucket]) {
                        min[bucket] = mono
                        max[bucket] = mono
                        touched[bucket] = true
                    } else {
                        if (mono < min[bucket]) min[bucket] = mono
                        if (mono > max[bucket]) max[bucket] = mono
                    }
                    frameIndex++
                }

                val progress = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            })

            val finalDurationUs = maxOf(durationUs, maxSeenUs).coerceAtLeast(1L)
            val durationMs = (finalDurationUs / 1000L).coerceAtLeast(1L)

            for (i in min.indices) {
                if (!touched[i]) {
                    min[i] = 0f
                    max[i] = 0f
                }
            }

            WaveformPeaks(
                min = min,
                max = max,
                durationMs = durationMs,
                sampleRate = session.sampleRate,
                channelCount = session.channelCount,
                bitDepth = EXPORT_BITS_PER_SAMPLE,
            )
        } catch (_: Exception) {
            null
        } finally {
            session?.release()
        }
    }

    fun exportWindow(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        writer: WavStreamWriter,
    ): Boolean {
        if (endMs <= startMs) return false
        var session: DecodeSession? = null
        return try {
            session = openSession(context, uri = sourceUri) ?: return false
            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val endUs = endMs.coerceAtLeast(0L) * 1000L
            session.seekToUs(startUs)

            var wroteAny = false
            var stop = false
            session.decodeStreaming(
                shouldStop = { stop },
                onChunk = { chunk, presentationTimeUs, channelCount, _, pcmEncoding ->

                val frames = chunk.size / bytesPerSample(pcmEncoding) / channelCount.coerceAtLeast(1)
                if (frames <= 0) return@decodeStreaming

                val bps = bytesPerSample(pcmEncoding)
                val samplesPerFrame = channelCount.coerceAtLeast(1)
                val frameDurUs = 1_000_000L / session.sampleRate.coerceAtLeast(1)

                var frameIndex = 0
                var byteIndex = 0
                val outChunk = java.io.ByteArrayOutputStream()

                while (frameIndex < frames) {
                    val timeUs = presentationTimeUs + frameIndex * frameDurUs
                    if (timeUs >= endUs) {
                        stop = true
                        break
                    }
                    if (timeUs >= startUs) {
                        for (ch in 0 until samplesPerFrame) {
                            val sample = readSampleFloat(chunk, byteIndex, pcmEncoding)
                            writeSample24(outChunk, sample)
                            byteIndex += bps
                        }
                        wroteAny = true
                    } else {
                        byteIndex += bps * samplesPerFrame
                    }
                    frameIndex++
                }

                val bytes = outChunk.toByteArray()
                if (bytes.isNotEmpty()) writer.write(bytes)
                },
            )
            wroteAny
        } catch (_: Exception) {
            false
        } finally {
            session?.release()
        }
    }

    private class DecodeSession(
        private val extractor: MediaExtractor,
        private val decoder: MediaCodec,
        val sampleRate: Int,
        val channelCount: Int,
        val durationUs: Long,
    ) {
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        private var liveSampleRate = sampleRate
        private var liveChannelCount = channelCount

        fun seekToUs(timeUs: Long) {
            // seekTo(timeUs, mode) od API 26; starsze API — dekod od początku + skip w pętli.
            if (Build.VERSION.SDK_INT >= 26) {
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            decoder.flush()
        }

        fun decodeAllToInterleavedFloat(): DecodedPcm {
            val chunks = mutableListOf<ByteArray>()
            var encoding = pcmEncoding
            decodeStreaming(onChunk = { chunk, _, channels, rate, enc ->
                encoding = enc
                liveChannelCount = channels
                liveSampleRate = rate
                chunks.add(chunk)
            })
            val samples = pcmBytesToFloat(chunks, encoding)
            return DecodedPcm(samples, liveChannelCount, liveSampleRate)
        }

        fun decodeStreaming(
            shouldStop: () -> Boolean = { false },
            onChunk: (
                chunk: ByteArray,
                presentationTimeUs: Long,
                channelCount: Int,
                sampleRate: Int,
                pcmEncoding: Int,
            ) -> Unit,
        ) {
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && !shouldStop()) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0 && !inputDone) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            liveChannelCount =
                                out.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            liveSampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                        }
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }

                    outputIndex >= 0 -> {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(chunk, 0, bufferInfo.size)
                            onChunk(
                                chunk,
                                bufferInfo.presentationTimeUs,
                                liveChannelCount,
                                liveSampleRate,
                                pcmEncoding,
                            )
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        }

        fun release() {
            try {
                decoder.stop()
            } catch (_: Exception) {
            }
            try {
                decoder.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private data class DecodedPcm(
        val samples: FloatArray,
        val channelCount: Int,
        val sampleRate: Int,
    )

    private fun openSession(context: Context, uri: Uri): DecodeSession? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex =
                (0 until extractor.trackCount).firstOrNull { i ->
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L)
                } else {
                    0L
                }

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            DecodeSession(extractor, decoder, sampleRate, channelCount, durationUs)
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesPerSample(pcmEncoding: Int): Int =
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> 2
        }

    private fun readSampleFloat(chunk: ByteArray, offset: Int, pcmEncoding: Int): Float {
        if (offset < 0 || offset >= chunk.size) return 0f
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> {
                ((chunk[offset].toInt() and 0xff) - 128) / 128f
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                if (offset + 2 >= chunk.size) return 0f
                val b0 = chunk[offset].toInt() and 0xff
                val b1 = chunk[offset + 1].toInt() and 0xff
                val b2 = chunk[offset + 2].toInt()
                val packed = b0 or (b1 shl 8) or (b2 shl 16)
                ((packed shl 8) shr 8) / 8_388_608f
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                if (offset + 3 >= chunk.size) return 0f
                val bits =
                    (chunk[offset].toInt() and 0xff) or
                        ((chunk[offset + 1].toInt() and 0xff) shl 8) or
                        ((chunk[offset + 2].toInt() and 0xff) shl 16) or
                        ((chunk[offset + 3].toInt() and 0xff) shl 24)
                kotlin.math.min(1f, kotlin.math.max(-1f, Float.fromBits(bits)))
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                if (offset + 3 >= chunk.size) return 0f
                val v =
                    (chunk[offset].toInt() and 0xff).toLong() or
                        ((chunk[offset + 1].toInt() and 0xff).toLong() shl 8) or
                        ((chunk[offset + 2].toInt() and 0xff).toLong() shl 16) or
                        ((chunk[offset + 3].toInt() and 0xff).toLong() shl 24)
                v.toInt() / 2_147_483_648f
            }
            else -> {
                if (offset + 1 >= chunk.size) return 0f
                val lo = chunk[offset].toInt() and 0xff
                val hi = chunk[offset + 1].toInt()
                ((hi shl 8) or lo) / 32768f
            }
        }
    }

    private fun writeSample24(out: java.io.ByteArrayOutputStream, sample: Float) {
        val v =
            (sample.coerceIn(-1f, 1f) * 8_388_607f)
                .roundToInt()
                .coerceIn(-8_388_608, 8_388_607)
        out.write(v and 0xff)
        out.write((v shr 8) and 0xff)
        out.write((v shr 16) and 0xff)
    }

    private fun pcmBytesToFloat(chunks: List<ByteArray>, pcmEncoding: Int): FloatArray {
        val bps = bytesPerSample(pcmEncoding)
        var total = 0
        for (chunk in chunks) total += chunk.size / bps
        val out = FloatArray(total)
        var o = 0
        for (chunk in chunks) {
            var i = 0
            val limit = chunk.size - bps
            while (i <= limit && o < out.size) {
                out[o++] = readSampleFloat(chunk, i, pcmEncoding)
                i += bps
            }
        }
        return out.copyOf(o)
    }

    private fun interleavedToMono(interleaved: FloatArray, channelCount: Int): FloatArray {
        if (channelCount <= 1) return interleaved
        val frames = interleaved.size / channelCount
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channelCount) {
                sum += interleaved[f * channelCount + ch]
            }
            mono[f] = sum / channelCount
        }
        return mono
    }

    private fun resampleMono(
        input: FloatArray,
        srcRate: Int,
        dstRate: Int,
    ): FloatArray {
        if (input.isEmpty()) return input
        if (srcRate == dstRate) return input
        val outLen =
            (input.size.toLong() * dstRate / srcRate)
                .toInt()
                .coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i.toDouble() * srcRate / dstRate
            val idx = srcPos.toInt().coerceIn(0, input.size - 1)
            val frac = (srcPos - idx).toFloat()
            val next = input.getOrElse(idx + 1) { input[idx] }
            out[i] = input[idx] * (1f - frac) + next * frac
        }
        return out
    }
}
