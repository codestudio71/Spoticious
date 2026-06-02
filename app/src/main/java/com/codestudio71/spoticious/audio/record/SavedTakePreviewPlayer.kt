package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import java.io.File
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

/** Odtwarzanie pojedynczego pliku WAV z nagrania (nie bitu sceny freestyle). */
class SavedTakePreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    private val player: ExoPlayer =
        ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            repeatMode = Player.REPEAT_MODE_OFF
        }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val d = player.duration
                        if (d > 0L && d != C.TIME_UNSET) {
                            _durationMs.value = d
                        }
                        startProgressUpdates()
                    } else if (playbackState == Player.STATE_ENDED) {
                        player.seekTo(0)
                        player.pause()
                        _positionMs.value = 0L
                        _isPlaying.value = false
                    }
                }
            },
        )
    }

    fun loadFile(file: File) {
        if (!file.exists()) return
        stopProgressUpdates()
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false

        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        startProgressUpdates()
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob =
            scope.launch {
                while (isActive) {
                    val st = player.playbackState
                    if (st != Player.STATE_IDLE && st != Player.STATE_ENDED) {
                        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    }
                    val d = player.duration
                    if (d > 0L && d != C.TIME_UNSET) {
                        _durationMs.value = d
                    }
                    delay(100)
                }
            }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.playWhenReady = true
            player.play()
        }
    }

    fun seekToMs(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0))
        _positionMs.value = player.currentPosition.coerceAtLeast(0)
    }

    /** Zatrzymuje i czyści media; można potem znów [loadFile]. */
    fun clear() {
        stopProgressUpdates()
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    fun destroy() {
        clear()
        runCatching { player.release() }
    }
}
