package com.codestudio71.spoticious.audio.cut

data class WaveformPeaks(
    val min: FloatArray,
    val max: FloatArray,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    /** Głębokość eksportu WAV (24-bit). */
    val bitDepth: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformPeaks
        return min.contentEquals(other.min) &&
            max.contentEquals(other.max) &&
            durationMs == other.durationMs &&
            sampleRate == other.sampleRate &&
            channelCount == other.channelCount &&
            bitDepth == other.bitDepth
    }

    override fun hashCode(): Int {
        var result = min.contentHashCode()
        result = 31 * result + max.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + bitDepth
        return result
    }
}
