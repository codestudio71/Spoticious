package com.codestudio71.spoticious.audio.record

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Lokalny beat pod nagrywanie — bez audio focus, niezależny od [PlaybackService]. */
class BeatPreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var visualizer: Visualizer? = null
    private var preferredOutputDevice: AudioDeviceInfo? = null
    private var loadedBeatUri: Uri? = null

    private val player: ExoPlayer =
        ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            repeatMode = Player.REPEAT_MODE_ONE
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
                        if (d > 0L && d != com.google.android.exoplayer2.C.TIME_UNSET) {
                            _durationMs.value = d
                        }
                        startProgressUpdates()
                    }
                }
            },
        )
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob =
            scope.launch {
                while (isActive) {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    val d = player.duration
                    if (d > 0L && d != com.google.android.exoplayer2.C.TIME_UNSET) {
                        _durationMs.value = d
                    }
                    delay(100)
                }
            }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    @SuppressLint("MissingPermission")
    fun attachVisualizer(listener: Visualizer.OnDataCaptureListener) {
        releaseVisualizer()
        val id = resolveAudioSessionId()
        if (id <= 0) return

        try {
            val span = Visualizer.getCaptureSizeRange()
            val v =
                Visualizer(id).apply {
                    captureSize = span[1]
                    setDataCaptureListener(
                        listener,
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        false,
                    )
                    enabled = true
                }
            visualizer = v
        } catch (_: Throwable) {
            releaseVisualizer()
        }
    }

    fun releaseVisualizer() {
        val v =
            synchronized(this) {
                val holder = visualizer
                visualizer = null
                holder
            } ?: return
        runCatching { v.enabled = false }
            .onFailure { }
        runCatching { v.release() }
    }

    fun isVisualizerAttached(): Boolean =
        synchronized(this) {
            visualizer?.enabled == true
        }

    /** ExoPlayer 2 exposes session id via [ExoPlayer.getAudioSessionId] on implementations. */
    private fun resolveAudioSessionId(): Int {
        val id =
            try {
                val getter =
                    player.javaClass.methods
                        .filter { it.parameterCount == 0 && it.name == "getAudioSessionId" }
                        .firstOrNull()
                val raw = getter?.invoke(player) ?: return 0
                (raw as? Number)?.toInt() ?: 0
            } catch (_: Throwable) {
                0
            }
        return if (id <= 0) 0 else id
    }

    fun setPreferredOutputDevice(device: AudioDeviceInfo?) {
        preferredOutputDevice = device
        applyPreferredOutputDevice()
    }

    /** Głośność podglądu beatu (0..1) — żeby preview brzmiał jak mix. */
    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    private fun applyPreferredOutputDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            player.setPreferredAudioDevice(preferredOutputDevice)
        } catch (_: Exception) {
        }
    }

    fun isLoaded(uri: Uri): Boolean =
        loadedBeatUri == uri && player.mediaItemCount > 0

    fun loadBeat(uri: Uri) {
        if (isLoaded(uri) && player.playbackState != Player.STATE_IDLE) {
            applyPreferredOutputDevice()
            return
        }
        loadedBeatUri = uri
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        applyPreferredOutputDevice()
        startProgressUpdates()
    }

    fun play() {
        applyPreferredOutputDevice()
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.playWhenReady = false
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
    }

    fun stop() {
        pause()
        seekToStart()
    }

    fun seekToStart() {
        player.seekTo(0)
    }

    /**
     * Seek na początek i czekaj aż ExoPlayer realnie będzie na ~0 w STATE_READY.
     * Bez tego [play] zaraz po [seekToStart] potrafi wystartować ze starej pozycji
     * (seek jest asynchroniczny) — mix zawsze bierze beat od próbki 0.
     */
    suspend fun seekToStartAndAwaitReady(timeoutMs: Long = 4_000L): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (player.mediaItemCount <= 0) return@withContext false
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.seekTo(0)
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                val state = player.playbackState
                val pos = player.currentPosition
                if (state == Player.STATE_READY && pos <= SEEK_READY_POSITION_TOLERANCE_MS) {
                    _positionMs.value = pos.coerceAtLeast(0L)
                    return@withContext true
                }
                if (state == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                delay(16)
            }
            player.playbackState == Player.STATE_READY
        }

    /** Czyści źródło (np. po usunięciu bitu z listy). */
    fun clear() {
        releaseVisualizer()
        loadedBeatUri = null
        stopProgressUpdates()
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {
        }
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
    }

    fun release() {
        releaseVisualizer()
        stopProgressUpdates()
        try {
            player.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val SEEK_READY_POSITION_TOLERANCE_MS = 80L
    }
}
