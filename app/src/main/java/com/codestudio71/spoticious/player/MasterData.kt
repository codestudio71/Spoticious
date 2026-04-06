package com.codestudio71.spoticious.player

data class MasterData(
    val peakDb: Double,
    val clips: Int,
    val maxLufsM: Double,
    val maxLufsS: Double,
    val lufsI: Double,
    val lra: Double
) {
    fun formatPeak(): String = "%+.1f".format(peakDb)
    fun formatClips(): String = clips.toString()
    fun formatLufsM(): String = "%.1f".format(maxLufsM)
    fun formatLufsS(): String = "%.1f".format(maxLufsS)
    fun formatLufsI(): String = "%.1f".format(lufsI)
    fun formatLra(): String = "%.1f LU".format(lra)
}
