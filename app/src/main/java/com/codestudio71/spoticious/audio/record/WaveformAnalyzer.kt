package com.codestudio71.spoticious.audio.record

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FrameResult(
    /** Normalized amplitude in roughly -1.0 .. 0.0 (typically negative crest / lower envelope). */
    val minAmplitude: Float,
    /** Normalized amplitude in roughly 0.0 .. 1.0 (upper envelope). */
    val maxAmplitude: Float,
    /** RMS magnitude 0.0 .. 1.0 (relative to FS). */
    val rms: Float,
    /** dBFS, clamped approximately to -60.0 .. 0.0. */
    val dbfs: Float,
)

class WaveformAnalyzer {

    private companion object {
        const val WINDOW = 1024
        const val MIN_DB_FS = -60f
        const val RMS_FLOOR = 1e-8f
    }

    /** [rms] is already RMS of samples normalized by full scale (~0..1). */
    private fun dbfsFromNormalizedRms(rms: Float): Float {
        val lin = rms.coerceAtLeast(RMS_FLOOR)
        val db = (20 * kotlin.math.log10(lin.toDouble())).toFloat()
        return db.coerceIn(MIN_DB_FS, 0f)
    }

    /** 16‑bit PCM in host byte order samples. */
    fun analyze(pcmSamples: ShortArray, offset: Int, length: Int): FrameResult =
        analyze16Internal(pcmSamples, offset, length, 32767f)

    private fun analyze16Internal(
        pcmSamples: ShortArray,
        offset: Int,
        length: Int,
        fullScaleRef: Float,
    ): FrameResult {
        if (length <= 0) return silentFrame()
        val start = offset + (length - min(length, WINDOW)).coerceAtLeast(0)
        val endExclusive = offset + length
        var minN = Float.POSITIVE_INFINITY
        var maxN = Float.NEGATIVE_INFINITY
        var sumSq = 0.0
        var count = 0
        var i = start
        while (i < endExclusive) {
            val s = pcmSamples[i].toInt().toFloat() / fullScaleRef
            if (s < minN) minN = s
            if (s > maxN) maxN = s
            val si = pcmSamples[i].toInt().toDouble() / fullScaleRef.toDouble()
            sumSq += si * si
            count++
            i++
        }
        if (count == 0) return silentFrame()
        val rms = sqrt(sumSq / count).toFloat().coerceIn(0f, 1f)
        return FrameResult(
            minAmplitude = min(minN, 0f).coerceAtLeast(-1f),
            maxAmplitude = max(maxN, 0f).coerceAtMost(1f),
            rms = rms,
            dbfs = dbfsFromNormalizedRms(rms),
        )
    }

    /** Packed 24‑bit PCM little‑endian mono; [sampleCount] is number of samples (bytes used = sampleCount * 3). */
    fun analyze24bit(pcmBytes: ByteArray, offset: Int, sampleCount: Int): FrameResult {
        val fullScaleRef = 8388607f
        if (sampleCount <= 0) return silentFrame()
        val window = min(sampleCount, WINDOW)
        val firstSampleIdx = sampleCount - window
        val firstByte = offset + firstSampleIdx * 3
        val lastByteExclusive = offset + sampleCount * 3
        var minN = Float.POSITIVE_INFINITY
        var maxN = Float.NEGATIVE_INFINITY
        var sumSq = 0.0
        var n = 0
        var b = firstByte
        while (b + 2 < lastByteExclusive) {
            val b0 = pcmBytes[b].toInt() and 0xff
            val b1 = pcmBytes[b + 1].toInt() and 0xff
            val b2 = pcmBytes[b + 2].toInt()
            val packed = b0 or (b1 shl 8) or (b2 shl 16)
            val samp = ((packed shl 8) shr 8)
            val s = samp.toFloat() / fullScaleRef
            if (s < minN) minN = s
            if (s > maxN) maxN = s
            val sd = samp.toDouble() / fullScaleRef.toDouble()
            sumSq += sd * sd
            n++
            b += 3
        }
        if (n == 0) return silentFrame()
        val rms = sqrt(sumSq / n).toFloat().coerceIn(0f, 1f)
        return FrameResult(
            minAmplitude = min(minN, 0f).coerceAtLeast(-1f),
            maxAmplitude = max(maxN, 0f).coerceAtMost(1f),
            rms = rms,
            dbfs = dbfsFromNormalizedRms(rms),
        )
    }

    /** Android [Visualizer] unsigned 8‑bit waveform blob (typically ~128 bytes). Center at 128. */
    fun analyzeUnsigned8bit(data: ByteArray, offset: Int, length: Int): FrameResult {
        val fullScaleRef = 128f
        if (length <= 0 || offset >= data.size) return silentFrame()
        val safeLen = length.coerceAtMost(data.size - offset)
        val window = min(safeLen, WINDOW)
        val start = offset + (safeLen - window).coerceAtLeast(0)
        val end = offset + safeLen
        var minN = Float.POSITIVE_INFINITY
        var maxN = Float.NEGATIVE_INFINITY
        var sumSq = 0.0
        var count = 0
        var i = start
        while (i < end) {
            val u = data[i].toInt() and 0xff
            val samp = u - 128
            val s = samp.toFloat() / fullScaleRef
            if (s < minN) minN = s
            if (s > maxN) maxN = s
            val sd = samp.toDouble() / fullScaleRef.toDouble()
            sumSq += sd * sd
            count++
            i++
        }
        if (count == 0) return silentFrame()
        val rms = sqrt(sumSq / count).toFloat().coerceIn(0f, 1f)
        return FrameResult(
            minAmplitude = min(minN, 0f).coerceAtLeast(-1f),
            maxAmplitude = max(maxN, 0f).coerceAtMost(1f),
            rms = rms,
            dbfs = dbfsFromNormalizedRms(rms),
        )
    }

    fun silentFrame(): FrameResult =
        FrameResult(
            minAmplitude = -0f,
            maxAmplitude = 0f,
            rms = 0f,
            dbfs = MIN_DB_FS,
        )
}
