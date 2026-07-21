package com.codestudio71.spoticious.player

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * 10-band EQ — silnik Audacious (`bp2`), wspólny z [AudioRenderPipeline].
 * Etykiety UI: 31, 62, 125…; DSP CF: 31.25, 62.5… (jak Audacious).
 * Preamp + pasma: `10^((preamp+band)/20)−1` na każdej wstędze BP.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        /** Etykiety suwaków (Hz). */
        val BAND_FREQUENCIES_HZ = AudaciousEqMath.BAND_LABELS_HZ

        private const val MIN_DB = AudaciousEqMath.MIN_DB
        private const val MAX_DB = AudaciousEqMath.MAX_DB
    }

    @Volatile
    var eqEnabled: Boolean = false

    @Volatile
    var preampDb: Float = 0f
        set(value) {
            field = value.coerceIn(MIN_DB, MAX_DB)
            syncGains()
        }

    private val bands = Array(AudaciousEqMath.BAND_COUNT) { BandState() }
    private val engine = AudaciousEqEngine()

    inner class BandState {
        @Volatile
        var gainDb: Float = 0f
            set(value) {
                field = value.coerceIn(MIN_DB, MAX_DB)
            }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0 until AudaciousEqMath.BAND_COUNT) {
            bands[bandIndex].gainDb = gainDb.coerceIn(MIN_DB, MAX_DB)
            syncGains()
        }
    }

    fun setPreamp(gainDb: Float) {
        preampDb = gainDb.coerceIn(MIN_DB, MAX_DB)
    }

    private fun syncGains() {
        if (engine.configuredSampleRate <= 0) return
        val bandDb = FloatArray(AudaciousEqMath.BAND_COUNT) { i -> bands[i].gainDb }
        engine.setGains(preampDb, bandDb)
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

        engine.configure(sr, inputAudioFormat.channelCount)
        syncGains()
        engine.resetState()

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

        val channels = engine.configuredChannels.coerceAtLeast(1)
        var sampleIndex = 0

        while (inputBuffer.hasRemaining()) {
            val raw = inputBuffer.short.toFloat() / 32768f
            val ch = sampleIndex % channels
            val out = engine.processSample(raw, ch)
            outputBuffer.putShort((out * 32768f).toInt().coerceIn(-32768, 32767).toShort())
            sampleIndex++
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        engine.resetState()
    }

    override fun onReset() {
        engine.release()
    }
}
