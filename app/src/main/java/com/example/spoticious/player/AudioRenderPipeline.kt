package com.example.spoticious.player

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class OutputFormat { AAC, WAV }

object AudioRenderPipeline {

    private const val Q = 1.41
    private val BAND_FREQUENCIES_HZ = intArrayOf(
        31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    )

    suspend fun render(
        context: Context,
        inputUri: Uri,
        outputFormat: OutputFormat,
        outputFileName: String,
        sampleRate: Int = 44100,
        aacSampleRate: Int = 0,
        eqBands: FloatArray,
        preampDb: Float,
        onProgress: (Float, String) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, inputUri, null)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext Result.failure(Exception("No audio track found"))

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext Result.failure(Exception("Invalid format"))
            val durationUs = format.getLong(MediaFormat.KEY_DURATION, 0L).takeIf { it > 0 } ?: 1L
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 44100

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmFloat = decodeToFloat(extractor, decoder, channelCount, sourceSampleRate, durationUs, { isActive }) { progress ->
                if (isActive) onProgress(progress * 0.6f, "Dekodowanie audio...")
            }
            if (isActive) onProgress(0.6f, "Dekodowanie zakończone")
            decoder.stop()
            decoder.release()
            extractor.release()

            if (!isActive) return@withContext Result.failure(Exception("Cancelled"))
            if (pcmFloat.isEmpty()) return@withContext Result.failure(Exception("No audio data decoded"))

            val preampMult = Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat()
            val eqFilters = buildEqFilters(sourceSampleRate, channelCount, eqBands)
            applyEq(pcmFloat, channelCount, preampMult, eqFilters)
            if (isActive) onProgress(0.75f, "Korekcja EQ zastosowana")

            if (isActive) onProgress(0.76f, "Kodowanie i zapis pliku...")
            val outputUri = when (outputFormat) {
                OutputFormat.AAC -> {
                    val targetRate = if (aacSampleRate > 0) aacSampleRate else sourceSampleRate
                    val pcmForAac = if (targetRate != sourceSampleRate) {
                        resample(pcmFloat, channelCount, sourceSampleRate, targetRate)
                    } else pcmFloat
                    encodeAac(context, pcmForAac, channelCount, targetRate, outputFileName)
                }
                OutputFormat.WAV -> {
                    val pcmForWav = if (sampleRate != sourceSampleRate) {
                        resample(pcmFloat, channelCount, sourceSampleRate, sampleRate)
                    } else pcmFloat
                    writeWav(context, pcmForWav, channelCount, sampleRate, outputFileName)
                }
            }
            onProgress(1.0f, "Gotowe!")
            Result.success(outputUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class BiquadFilter {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
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

    private fun buildEqFilters(
        sampleRate: Int,
        channelCount: Int,
        eqBands: FloatArray
    ): Array<Array<BiquadFilter>> {
        val filters = Array(10) { bandIdx ->
            Array(channelCount) { BiquadFilter() }.also { chFilters ->
                val gainDb = eqBands.getOrElse(bandIdx) { 0f }.toDouble().coerceIn(-15.0, 15.0)
                val freq = BAND_FREQUENCIES_HZ[bandIdx].toDouble()
                val a = sqrt(Math.pow(10.0, gainDb / 20.0))
                val w0 = 2.0 * Math.PI * freq / sampleRate
                val alpha = sin(w0) / (2.0 * Q)
                var b0 = 1.0 + alpha * a
                val b1 = -2.0 * cos(w0)
                var b2 = 1.0 - alpha * a
                var a0 = 1.0 + alpha / a
                val a1 = -2.0 * cos(w0)
                var a2 = 1.0 - alpha / a
                b0 /= a0
                val b1n = b1 / a0
                b2 /= a0
                val a1n = a1 / a0
                val a2n = a2 / a0
                for (ch in 0 until channelCount) {
                    chFilters[ch].b0 = b0
                    chFilters[ch].b1 = b1n
                    chFilters[ch].b2 = b2
                    chFilters[ch].a1 = a1n
                    chFilters[ch].a2 = a2n
                }
            }
        }
        return filters
    }

    private fun applyEq(
        samples: FloatArray,
        channelCount: Int,
        preampMult: Float,
        filters: Array<Array<BiquadFilter>>
    ) {
        for (bandIdx in 0 until 10) {
            for (ch in 0 until channelCount) {
                filters[bandIdx][ch].reset()
            }
        }
        var i = 0
        while (i < samples.size) {
            var sample = samples[i] * preampMult
            val ch = i % channelCount
            for (bandIdx in 0 until 10) {
                sample = filters[bandIdx][ch].process(sample.toDouble()).toFloat()
            }
            samples[i] = sample.coerceIn(-1f, 1f)
            i++
        }
    }

    private fun decodeToFloat(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        channelCount: Int,
        sampleRate: Int,
        durationUs: Long,
        isActive: () -> Boolean,
        onProgress: (Float) -> Unit
    ): FloatArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val outputBuffers = mutableListOf<ByteArray>()
        var totalSamples = 0
        var inputDone = false
        var outputDone = false
        var presentationTimeUs = 0L

        while (!outputDone && isActive()) {
            val inputIndex = decoder.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                if (!inputDone) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex) ?: break
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val outputBuffer = decoder.getOutputBuffer(outputIndex) ?: run {
                    decoder.releaseOutputBuffer(outputIndex, false)
                    continue
                }
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                outputBuffers.add(chunk)
                totalSamples += bufferInfo.size / (channelCount * 2)
                presentationTimeUs = bufferInfo.presentationTimeUs
                if (durationUs > 0 && !outputDone) onProgress((presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        }

        val bytesPerSample = channelCount * 2
        val floatSamples = FloatArray(totalSamples * channelCount)
        var offset = 0
        val byteBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        for (chunk in outputBuffers) {
            var i = 0
            while (i < chunk.size && offset < floatSamples.size) {
                byteBuffer.clear()
                byteBuffer.put(chunk[i])
                if (i + 1 < chunk.size) byteBuffer.put(chunk[i + 1])
                i += 2
                byteBuffer.flip()
                val shortVal = byteBuffer.getShort()
                floatSamples[offset++] = (shortVal.toInt() / 32768f)
            }
        }
        return floatSamples.copyOf(offset)
    }

    private fun encodeAac(
        context: Context,
        pcmFloat: FloatArray,
        channelCount: Int,
        sampleRate: Int,
        outputFileName: String
    ): Uri {
        val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 320000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val tempFile = File(context.cacheDir, "render_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmShort = FloatArray(pcmFloat.size)
        for (i in pcmFloat.indices) {
            pcmShort[i] = (pcmFloat[i] * 32767f).coerceIn(-32768f, 32767f)
        }
        val byteBuffer = ByteBuffer.allocate(pcmShort.size * 2).order(ByteOrder.nativeOrder())
        for (s in pcmShort) {
            byteBuffer.putShort((s.toInt() and 0xFFFF).toShort())
        }
        byteBuffer.flip()

        var muxerTrackIndex = -1
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            val inputIndex = encoder.dequeueInputBuffer(10000)
            if (inputIndex >= 0 && !inputDone) {
                val inputBuffer = encoder.getInputBuffer(inputIndex) ?: break
                inputBuffer.clear()
                if (byteBuffer.hasRemaining()) {
                    val toWrite = minOf(byteBuffer.remaining(), inputBuffer.remaining())
                    val chunk = ByteArray(toWrite)
                    byteBuffer.get(chunk)
                    inputBuffer.put(chunk)
                    encoder.queueInputBuffer(inputIndex, 0, toWrite, System.nanoTime() / 1000, 0)
                } else {
                    encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                }
            }

            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    encoder.releaseOutputBuffer(outputIndex, false)
                    continue
                }
                if (muxerTrackIndex < 0) {
                    muxerTrackIndex = muxer.addTrack(encoder.getOutputFormat(outputIndex))
                    muxer.start()
                }
                val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: run {
                    encoder.releaseOutputBuffer(outputIndex, false)
                    continue
                }
                muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                encoder.releaseOutputBuffer(outputIndex, false)
            }
        }

        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()

        return saveToMediaStore(context, tempFile, "audio/mp4", "m4a", outputFileName)
    }

    private fun resample(
        input: FloatArray,
        channelCount: Int,
        inputSampleRate: Int,
        outputSampleRate: Int
    ): FloatArray {
        if (inputSampleRate == outputSampleRate) return input
        val inputFrames = input.size / channelCount
        val outputFrames = (inputFrames.toLong() * outputSampleRate / inputSampleRate).toInt()
        val output = FloatArray(outputFrames * channelCount)
        val ratio = inputSampleRate.toDouble() / outputSampleRate
        for (i in 0 until outputFrames) {
            val srcIdx = i * ratio
            val idx0 = (srcIdx.toInt()).coerceIn(0, inputFrames - 1)
            val idx1 = (idx0 + 1).coerceAtMost(inputFrames - 1)
            val frac = srcIdx - idx0
            for (ch in 0 until channelCount) {
                val s0 = input[idx0 * channelCount + ch]
                val s1 = input[idx1 * channelCount + ch]
                output[i * channelCount + ch] = (s0 + (s1 - s0) * frac).toFloat()
            }
        }
        return output
    }

    private fun writeWav(
        context: Context,
        pcmFloat: FloatArray,
        channelCount: Int,
        sampleRate: Int,
        outputFileName: String
    ): Uri {
        val tempFile = File(context.cacheDir, "render_${System.currentTimeMillis()}.wav")
        val numChannels = channelCount
        val bitsPerSample = 24
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()
        val dataSize = pcmFloat.size * 3

        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.write("RIFF".toByteArray())
            raf.write(intToLe(dataSize + 36))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intToLe(16))
            raf.write(shortToLe(1))
            raf.write(shortToLe(numChannels.toShort()))
            raf.write(intToLe(sampleRate))
            raf.write(intToLe(byteRate))
            raf.write(shortToLe(blockAlign))
            raf.write(shortToLe(bitsPerSample.toShort()))
            raf.write("data".toByteArray())
            raf.write(intToLe(dataSize))

            for (sample in pcmFloat) {
                val clamped = sample.coerceIn(-1f, 1f)
                val int24 = (clamped * 8388607f).toInt().coerceIn(-8388608, 8388607)
                val b0 = (int24 and 0xFF).toByte()
                val b1 = ((int24 shr 8) and 0xFF).toByte()
                val b2 = ((int24 shr 16) and 0xFF).toByte()
                raf.write(byteArrayOf(b0, b1, b2))
            }
        }
        return saveToMediaStore(context, tempFile, "audio/wav", "wav", outputFileName)
    }

    private fun intToLe(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    private fun shortToLe(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        ((v.toInt() shr 8) and 0xFF).toByte()
    )

    private fun saveToMediaStore(context: Context, file: File, mimeType: String, extension: String, outputFileName: String): Uri {
        val displayName = "$outputFileName.$extension"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Rendered")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Failed to create MediaStore entry")
            context.contentResolver.openOutputStream(uri).use { outs ->
                file.inputStream().use { ins -> ins.copyTo(outs!!) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            file.delete()
            uri
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val renderedDir = File(musicDir, "Rendered")
            renderedDir.mkdirs()
            val destFile = File(renderedDir, displayName)
            file.copyTo(destFile, overwrite = true)
            file.delete()
            Uri.fromFile(destFile)
        }
    }
}
