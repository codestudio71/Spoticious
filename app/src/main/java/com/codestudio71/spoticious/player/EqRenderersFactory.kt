package com.codestudio71.spoticious.player

import android.content.Context
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink

/**
 * Custom RenderersFactory that injects EqualizerAudioProcessor into the audio pipeline.
 */
class EqRenderersFactory(
    context: Context,
    private val eqProcessor: EqualizerAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
        enableOffload: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setOffloadMode(
                if (enableOffload) DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                else DefaultAudioSink.OFFLOAD_MODE_DISABLED
            )
            .setAudioProcessors(arrayOf(eqProcessor))
            .build()
    }
}
