package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.net.Uri
import com.codestudio71.spoticious.audio.cut.PcmStreamDecoder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Miks wokal + bit po STOP (freestyle); beat dekodowany z URI, nie z mikrofonu. */
object FreestyleMixdown {

    const val VOCAL_GAIN = 0.9f
    const val BEAT_GAIN = 0.7f

    private const val WAV_HEADER_SIZE = 44

    /**
     * Nadpisuje [vocalWav] zmiksowanym mono PCM WAV (ten sam sample rate co nagranie).
     * @return false przy błędzie odczytu wokalu lub dekodowania bitu.
     */
    fun mixInPlace(
        context: Context,
        vocalWav: File,
        beatUri: Uri,
        targetSampleRate: Int,
        beatGain: Float = BEAT_GAIN,
    ): Boolean {
        val vocal = readMonoPcmWav(vocalWav) ?: return false
        if (vocal.samples.isEmpty()) return false

        val rate = vocal.sampleRate.coerceAtLeast(1)
        val beatMono =
            PcmStreamDecoder.decodeUriToMonoFloat(context, beatUri, rate)
                ?: return false

        val mixed =
            mixMonoFloat(
                vocalSamples = vocal.samples,
                beatSamples = beatMono,
                vocalGain = VOCAL_GAIN,
                beatGain = beatGain.coerceIn(0f, 1f),
            )

        return writeMonoPcmWav(vocalWav, mixed, rate, vocal.bitsPerSample)
    }

    private data class MonoPcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val bitsPerSample: Int,
    )

    private fun mixMonoFloat(
        vocalSamples: FloatArray,
        beatSamples: FloatArray,
        vocalGain: Float,
        beatGain: Float,
    ): FloatArray {
        val out = FloatArray(vocalSamples.size)
        for (i in vocalSamples.indices) {
            val v = vocalSamples[i] * vocalGain
            val b = if (i < beatSamples.size) beatSamples[i] * beatGain else 0f
            out[i] = (v + b).coerceIn(-1f, 1f)
        }
        return out
    }

    private fun readMonoPcmWav(file: File): MonoPcm? {
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

                val pcmBytes = ByteArray(dataSize.coerceAtMost(raf.length().toInt() - WAV_HEADER_SIZE))
                raf.readFully(pcmBytes)

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

                    else -> return null
                }
                MonoPcm(mono, sampleRate, bitsPerSample)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMonoPcmWav(
        file: File,
        samples: FloatArray,
        sampleRate: Int,
        bitsPerSample: Int,
    ): Boolean {
        val bps = if (bitsPerSample == 24) 24 else 16
        return try {
            val pcmBytes =
                when (bps) {
                    24 -> encode24(samples)
                    else -> encode16(samples)
                }
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                raf.seek(0)
                raf.write(ByteArray(WAV_HEADER_SIZE))
                raf.write(pcmBytes)
                raf.seek(0)
                VocalRecorder.writeStdPcmWaveHeader(raf, sampleRate, bps, 1, pcmBytes.size.toLong())
            }
            true
        } catch (_: Exception) {
            false
        }
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
