package com.codestudio71.spoticious.player

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.tan

/**
 * EQ zgodny z Audacious `libaudcore/equalizer.cc` (Anders Johansson / John Lindgren).
 *
 * - Band-pass 2. rzędu ([bp2]), Q = √(3/2) ≈ 1.2247449
 * - CF: 31.25, 62.5, 125…16000 (UI może pokazywać 31 / 62)
 * - Gain pasma: `10^((preamp + band)/20) − 1`, potem suma składowych BP
 * - Liczba aktywnych pasm K: CF < rate / (2.005·Q)
 *
 * Wspólne dla [EqualizerAudioProcessor] (odsłuch) i [AudioRenderPipeline] (export).
 */
object AudaciousEqMath {
    /** Jak w Audacious: „4 dB suppression at Fc×2 and Fc/2”. */
    const val Q = 1.2247449f

    val CF_HZ = floatArrayOf(
        31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f,
    )

    /** Etykiety UI (zaokrąglenie jak dotychczas w Spoticious / typowe UI Audacious). */
    val BAND_LABELS_HZ = intArrayOf(
        31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000,
    )

    const val BAND_COUNT = 10
    const val MIN_DB = -15f
    const val MAX_DB = 15f

    data class Bp2Coeffs(
        val a0: Float,
        val a1: Float,
        val b0: Float,
        val b1: Float,
    )

    fun activeBandCount(sampleRate: Int): Int {
        if (sampleRate <= 0) return 0
        var k = BAND_COUNT
        val limit = sampleRate / (2.005f * Q)
        while (k > 0 && CF_HZ[k - 1] > limit) k--
        return k
    }

    /** [fcNormalized] = CF_Hz / sampleRate (jak `CF[k] / rate` w Audacious). */
    fun bp2(fcNormalized: Float): Bp2Coeffs {
        val th = (2.0 * Math.PI * fcNormalized).toFloat()
        val t = tan((th * Q / 2f).toDouble()).toFloat()
        val c = (1f - t) / (1f + t)
        return Bp2Coeffs(
            a0 = (1f + c) * cos(th.toDouble()).toFloat(),
            a1 = -c,
            b0 = (1f - c) / 2f,
            b1 = -1.005f,
        )
    }

    fun designAll(sampleRate: Int): Array<Bp2Coeffs> {
        val rate = sampleRate.toFloat().coerceAtLeast(1f)
        return Array(BAND_COUNT) { i -> bp2(CF_HZ[i] / rate) }
    }

    /** Audacious: `powf(10, (preamp + band) / 20) - 1`. */
    fun bandGainFactor(preampDb: Float, bandDb: Float): Float {
        val adj = (preampDb + bandDb).coerceIn(MIN_DB * 2f, MAX_DB * 2f)
        return 10f.pow(adj / 20f) - 1f
    }
}

/**
 * Stan + przetwarzanie EQ Audacious dla N kanałów.
 * Współczynniki zależą tylko od sample rate; gainy od preamp + pasm.
 */
class AudaciousEqEngine {
    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var activeBands: Int = 0
    private var coeffs: Array<AudaciousEqMath.Bp2Coeffs> = emptyArray()
    private var gains = FloatArray(AudaciousEqMath.BAND_COUNT)
    /** wq[ch][band][0|1] */
    private var wq: Array<Array<FloatArray>> = emptyArray()

    val configuredSampleRate: Int get() = sampleRate
    val configuredChannels: Int get() = channelCount

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount.coerceAtLeast(1)
        activeBands = AudaciousEqMath.activeBandCount(sampleRate)
        coeffs = AudaciousEqMath.designAll(sampleRate)
        wq = Array(this.channelCount) {
            Array(AudaciousEqMath.BAND_COUNT) { FloatArray(2) }
        }
        // gains zostają — caller woła [setGains] po configure jeśli trzeba
    }

    fun setGains(preampDb: Float, bandGainsDb: FloatArray) {
        val pre = preampDb.coerceIn(AudaciousEqMath.MIN_DB, AudaciousEqMath.MAX_DB)
        for (i in 0 until AudaciousEqMath.BAND_COUNT) {
            val b = bandGainsDb.getOrElse(i) { 0f }
                .coerceIn(AudaciousEqMath.MIN_DB, AudaciousEqMath.MAX_DB)
            gains[i] = AudaciousEqMath.bandGainFactor(pre, b)
        }
    }

    fun resetState() {
        for (ch in wq.indices) {
            for (band in wq[ch].indices) {
                wq[ch][band][0] = 0f
                wq[ch][band][1] = 0f
            }
        }
    }

    fun release() {
        sampleRate = 0
        channelCount = 0
        activeBands = 0
        coeffs = emptyArray()
        wq = emptyArray()
        gains.fill(0f)
    }

    /** Jedna próbka float ∈ ℝ (typowo ~[-1, 1]); kanał 0..channelCount-1. */
    fun processSample(input: Float, channel: Int): Float {
        if (activeBands <= 0 || coeffs.isEmpty() || wq.isEmpty()) return input
        val ch = channel.coerceIn(0, channelCount - 1)
        var yt = input
        val g = gains
        val cfs = coeffs
        val wqCh = wq[ch]
        for (k in 0 until activeBands) {
            val cf = cfs[k]
            val wqBand = wqCh[k]
            val w = yt * cf.b0 + wqBand[0] * cf.a0 + wqBand[1] * cf.a1
            yt += (w + wqBand[1] * cf.b1) * g[k]
            wqBand[1] = wqBand[0]
            wqBand[0] = w
        }
        return yt
    }

    /** Interleaved float PCM in-place. */
    fun processInterleavedInPlace(samples: FloatArray) {
        if (samples.isEmpty() || channelCount <= 0) return
        var i = 0
        while (i < samples.size) {
            val ch = i % channelCount
            samples[i] = processSample(samples[i], ch)
            i++
        }
    }
}
