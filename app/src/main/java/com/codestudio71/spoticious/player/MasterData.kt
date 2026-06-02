package com.codestudio71.spoticious.player

data class MasterData(
    val peakDb: Double,
    val clips: Int,
    val maxLufsM: Double,
    val maxLufsS: Double,
    val lufsI: Double,
    val lra: Double,
) {
    companion object {
        /** WebM/Opus — kolumna Clips pokazuje „—”, nie licznik. */
        const val CLIPS_UNSUPPORTED = -1

        fun unsupportedClipsContainer(): MasterData =
            MasterData(
                peakDb = Double.NaN,
                clips = CLIPS_UNSUPPORTED,
                maxLufsM = Double.NaN,
                maxLufsS = Double.NaN,
                lufsI = Double.NaN,
                lra = Double.NaN,
            )
    }

    private fun fmtDb(v: Double): String = if (v.isNaN()) "—" else "%+.1f".format(v)

    private fun fmtLufs(v: Double): String = if (v.isNaN()) "—" else "%.1f".format(v)

    fun formatPeak(): String = fmtDb(peakDb)

    fun formatClips(): String =
        when (clips) {
            CLIPS_UNSUPPORTED -> "—"
            else -> clips.toString()
        }
    fun formatLufsM(): String = fmtLufs(maxLufsM)

    fun formatLufsS(): String = fmtLufs(maxLufsS)

    fun formatLufsI(): String = fmtLufs(lufsI)

    fun formatLra(): String = if (lra.isNaN()) "—" else "%.1f LU".format(lra)
}
