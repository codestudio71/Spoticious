package com.codestudio71.spoticious.audio.cut

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lokalny podgląd zakresu Od–Do w Audio Cut.
 * Osobny ExoPlayer — zero wspólnego stanu z [com.codestudio71.spoticious.player.PlaybackService]
 * ani Record Preview.
 */
class CutRangePreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    private var loadedUri: Uri? = null
    private var rangeStartMs = 0L
    private var rangeEndMs = 1L

    private val player: ExoPlayer =
        ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            repeatMode = Player.REPEAT_MODE_OFF
        }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        pauseAtRangeStart()
                    }
                }
            },
        )
    }

    fun load(uri: Uri) {
        if (loadedUri == uri && player.mediaItemCount > 0) return
        pause()
        stopProgressUpdates()
        loadedUri = uri
        runCatching {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
        _positionMs.value = 0L
    }

    fun toggle(fromMs: Long, toMs: Long) {
        if (player.isPlaying) {
            pause()
            return
        }
        playRange(fromMs, toMs)
    }

    fun playRange(fromMs: Long, toMs: Long) {
        if (player.mediaItemCount <= 0) return
        val start = fromMs.coerceAtLeast(0L)
        val end = toMs.coerceAtLeast(start + 10L)
        rangeStartMs = start
        rangeEndMs = end
        player.seekTo(start)
        player.playWhenReady = true
        player.play()
        _positionMs.value = start
        startProgressUpdates()
    }

    fun pause() {
        runCatching {
            player.playWhenReady = false
            player.pause()
        }
        _isPlaying.value = false
    }

    private fun pauseAtRangeStart() {
        pause()
        runCatching { player.seekTo(rangeStartMs) }
        _positionMs.value = rangeStartMs
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob =
            scope.launch {
                while (isActive) {
                    if (player.playbackState != Player.STATE_IDLE) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        _positionMs.value = pos
                        if (player.isPlaying && pos >= rangeEndMs) {
                            pauseAtRangeStart()
                            break
                        }
                    }
                    delay(50)
                }
            }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun clear() {
        pause()
        stopProgressUpdates()
        loadedUri = null
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        _positionMs.value = 0L
        _isPlaying.value = false
    }

    fun destroy() {
        clear()
        runCatching { player.release() }
    }
}
