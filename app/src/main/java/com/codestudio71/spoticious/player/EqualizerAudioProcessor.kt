package com.codestudio71.spoticious.player

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 10-band peaking EQ identical to Audacious media player.
 * Bands: 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz
 * Q = 1.0 for all bands. Range ±15 dB per band. Preamp ±15 dB.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        val BAND_FREQUENCIES_HZ = intArrayOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )

        private const val Q = 1.41
        private const val MIN_DB = -15f
        private const val MAX_DB = 15f
    }

    @Volatile
    var eqEnabled: Boolean = false

    @Volatile
    var preampDb: Float = 0f
        set(value) {
            field = value.coerceIn(MIN_DB, MAX_DB)
        }

    private val bands = Array(10) { BandState() }

    private var filters: Array<Array<BiquadFilter>> = emptyArray()
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    inner class BandState {
        @Volatile
        var gainDb: Float = 0f
            set(value) {
                field = value.coerceIn(MIN_DB, MAX_DB)
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

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0..9) {
            bands[bandIndex].gainDb = gainDb.coerceIn(MIN_DB, MAX_DB)
            if (sampleRate > 0) recalculateCoefficients()
        }
    }

    fun setPreamp(gainDb: Float) {
        preampDb = gainDb.coerceIn(MIN_DB, MAX_DB)
    }

    private fun recalculateCoefficients() {
        if (sampleRate <= 0 || channelCount <= 0) return

        for (bandIdx in 0 until 10) {
            val freq = BAND_FREQUENCIES_HZ[bandIdx].toDouble()
            val gainDb = bands[bandIdx].gainDb.toDouble()

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
                val f = filters[bandIdx][ch]
                f.b0 = b0
                f.b1 = b1n
                f.b2 = b2
                f.a1 = a1n
                f.a2 = a2n
            }
        }
    }

    @Throws(AudioProcessor.UnhandledAudioFormatException::class)
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sr = inputAudioFormat.sampleRate
        if (sr != 44100 && sr != 48000) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        sampleRate = sr
        channelCount = inputAudioFormat.channelCount

        filters = Array(10) { bandIdx ->
            Array(channelCount) { BiquadFilter() }
        }
        recalculateCoefficients()

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val outputBuffer = replaceOutputBuffer(size)

        if (!eqEnabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val preampMult = Math.pow(10.0, (preampDb / 20.0).toDouble()).toFloat()
        var sampleIndex = 0

        while (inputBuffer.hasRemaining()) {
            var sample = inputBuffer.short.toFloat() / 32768f * preampMult
            val ch = sampleIndex % channelCount
            for (bandIdx in 0 until 10) {
                sample = filters[bandIdx][ch].process(sample.toDouble()).toFloat()
            }
            outputBuffer.putShort((sample * 32768f).toInt().coerceIn(-32768, 32767).toShort())
            sampleIndex++
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        for (bandIdx in 0 until 10) {
            for (ch in filters[bandIdx].indices) {
                filters[bandIdx][ch].reset()
            }
        }
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        filters = emptyArray()
    }
}
