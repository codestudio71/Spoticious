package com.codestudio71.spoticious.audio.record

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.SpoticiousApplication
import com.codestudio71.spoticious.player.PlaybackService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Procesowy właściciel sesji nagrywania — [VocalRecorder] i [BeatPreviewPlayer] żyją tu,
 * a nie w ViewModelu, więc nagrywanie trwa po wyjściu z ekranu / apki (razem z
 * [RecordingService] jako foreground service typu microphone|mediaPlayback).
 */
object RecordingSession {

    const val SAMPLE_RATE_DEFAULT = 48_000
    const val BIT_DEPTH_DEFAULT = 24

    private val appContext: Context
        get() = requireNotNull(SpoticiousApplication.instance) { "Application not ready" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val vocalRecorder = VocalRecorder()

    /** Tworzony leniwie na main (ExoPlayer wymaga Loopera). */
    val beatPreviewPlayer: BeatPreviewPlayer by lazy { BeatPreviewPlayer(appContext) }

    val recordingState: StateFlow<RecordingState> = vocalRecorder.recordingState

    private val _mode = MutableStateFlow(RecordMode.Freestyle)
    val mode: StateFlow<RecordMode> = _mode.asStateFlow()

    private val _selectedBeatUri = MutableStateFlow<Uri?>(null)
    val selectedBeatUri: StateFlow<Uri?> = _selectedBeatUri.asStateFlow()

    /** Głośność bitu (0..1) — wspólna dla podglądu i miksu freestyle. */
    private val _beatGain = MutableStateFlow(FreestyleMixdown.BEAT_GAIN)
    val beatGain: StateFlow<Float> = _beatGain.asStateFlow()

    /** true od STOP (freestyle) do zakończenia miksu — UI pokazuje "przetwarzanie". */
    private val _mixInProgress = MutableStateFlow(false)
    val mixInProgress: StateFlow<Boolean> = _mixInProgress.asStateFlow()

    /** Ostatni znany czas nagrania — nie resetuje się w stanie Stopping (timer w UI/notyfikacji). */
    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    /** O ile ms beat wystartował później niż mikrofon — kompensowane w miksie. */
    @Volatile
    private var beatStartOffsetMs = 0L

    /** Główny player był w trakcie odtwarzania — wznów po STOP nagrania. */
    private var resumeMainPlaybackAfterRecording = false

    init {
        scope.launch {
            vocalRecorder.recordingState.collect { st ->
                when (st) {
                    is RecordingState.Recording -> _recordingElapsedMs.value = st.durationMs
                    is RecordingState.Idle -> _recordingElapsedMs.value = 0L
                    else -> Unit // Stopping / Saved: zachowaj ostatnią wartość
                }
            }
        }
    }

    fun setMode(mode: RecordMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        if (mode == RecordMode.VoiceOnly) {
            _selectedBeatUri.value = null
            beatPreviewPlayer.clear()
        }
    }

    fun pickBeat(uri: Uri, outputDevice: AudioDeviceInfo?) {
        _selectedBeatUri.value = uri
        beatPreviewPlayer.setPreferredOutputDevice(outputDevice)
        beatPreviewPlayer.loadBeat(uri)
        beatPreviewPlayer.setVolume(FreestyleMixdown.sliderToLinearGain(_beatGain.value))
        beatPreviewPlayer.seekToStart()
        beatPreviewPlayer.pause()
    }

    fun clearBeat() {
        _selectedBeatUri.value = null
        beatPreviewPlayer.clear()
    }

    /** Suwak 0..1 w stanie (UI %). Na player/mix: [FreestyleMixdown.sliderToLinearGain]. */
    fun setBeatGain(value: Float, applyToPlayer: Boolean = true) {
        val g = value.coerceIn(0f, 1f)
        _beatGain.value = g
        if (applyToPlayer) {
            beatPreviewPlayer.setVolume(FreestyleMixdown.sliderToLinearGain(g))
        }
    }

    /** Po puszczeniu suwaka — jednorazowe [ExoPlayer.setVolume]. */
    fun applyBeatGainToPlayer() {
        beatPreviewPlayer.setVolume(FreestyleMixdown.sliderToLinearGain(_beatGain.value))
    }

    /**
     * Startuje FGS + mikrofon (+ beat we freestyle). Zwraca false, gdy start się nie powiódł.
     * Wołać z main; mikrofon wybiera warstwa UI (ViewModel zna listę urządzeń).
     */
    suspend fun startRecording(
        micDevice: AudioDeviceInfo?,
        beatOutputDevice: AudioDeviceInfo?,
    ): Boolean {
        val prev = vocalRecorder.recordingState.value
        if (prev is RecordingState.Recording || prev is RecordingState.Stopping) return false
        if (prev is RecordingState.Saved) {
            withContext(Dispatchers.IO) { runCatching { prev.file.delete() } }
            vocalRecorder.discardSavedToIdle()
        }

        val freestyleWithBeat =
            _mode.value == RecordMode.Freestyle && _selectedBeatUri.value != null
        val outFile =
            File(
                appContext.filesDir,
                if (freestyleWithBeat) {
                    "Spoticious_freestyle_${System.currentTimeMillis()}.wav"
                } else {
                    "Spoticious_rec_${System.currentTimeMillis()}.wav"
                },
            )

        // FGS musi wystartować zanim system zacznie oceniać dostęp do mikrofonu w tle.
        RecordingService.start(appContext)

        val started =
            withContext(Dispatchers.IO) {
                vocalRecorder.start(
                    outputFile = outFile,
                    sampleRate = SAMPLE_RATE_DEFAULT,
                    bitDepth = BIT_DEPTH_DEFAULT,
                    preferredDevice = micDevice,
                )
            }
        if (!started) {
            RecordingService.stop(appContext)
            return false
        }

        pauseMainPlaybackForRecording()

        beatStartOffsetMs = 0L
        if (freestyleWithBeat) {
            val beatUri = _selectedBeatUri.value
            if (beatUri != null) {
                beatPreviewPlayer.releaseVisualizer()
                beatPreviewPlayer.setPreferredOutputDevice(beatOutputDevice)
                if (!beatPreviewPlayer.isLoaded(beatUri)) {
                    beatPreviewPlayer.loadBeat(beatUri)
                }
                beatPreviewPlayer.setVolume(FreestyleMixdown.sliderToLinearGain(_beatGain.value))
                // Mik: czekaj aż pętla AudioRecord ustawi captureStart (race bez tego → offset=0).
                withTimeoutOrNull(2_000L) {
                    while (vocalRecorder.captureStartElapsedMs <= 0L) {
                        delay(5)
                    }
                }
                // Seek jest async — bez await mix (od 0) rozjeżdża się z tym co słychać.
                beatPreviewPlayer.seekToStartAndAwaitReady()
                beatPreviewPlayer.play()
                captureBeatStartOffset()
            }
        }
        return true
    }

    /** Beat rusza później niż mikrofon — zapamiętaj różnicę, żeby mix się nie rozjechał. */
    private suspend fun captureBeatStartOffset() {
        val captureStart =
            withTimeoutOrNull(2_000L) {
                while (vocalRecorder.captureStartElapsedMs <= 0L) {
                    delay(5)
                }
                vocalRecorder.captureStartElapsedMs
            } ?: return

        val startedPlaying =
            withTimeoutOrNull(5_000L) {
                beatPreviewPlayer.isPlaying.first { it }
            }
        if (startedPlaying == true && captureStart > 0L) {
            beatStartOffsetMs =
                (SystemClock.elapsedRealtime() - captureStart).coerceAtLeast(0L)
        }
    }

    /**
     * STOP: finalizacja WAV, potem (freestyle) asynchroniczny mixdown do pliku tymczasowego.
     * Stan Saved pojawia się od razu; [mixInProgress] mówi UI, że plik jeszcze się przetwarza.
     */
    fun stopRecording() {
        scope.launch {
            val needsMix =
                _mode.value == RecordMode.Freestyle && _selectedBeatUri.value != null
            val beatUri = _selectedBeatUri.value
            if (needsMix) _mixInProgress.value = true

            beatPreviewPlayer.stop()
            beatPreviewPlayer.releaseVisualizer()

            try {
                val vocalFile =
                    withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine { cont ->
                            vocalRecorder.stop { file -> cont.resume(file) }
                        }
                    }
                if (vocalFile != null && needsMix && beatUri != null) {
                    val mixed =
                        withContext(Dispatchers.IO) {
                            FreestyleMixdown.mixInPlace(
                                context = appContext,
                                vocalWav = vocalFile,
                                beatUri = beatUri,
                                targetSampleRate = SAMPLE_RATE_DEFAULT,
                                beatGain = FreestyleMixdown.sliderToLinearGain(_beatGain.value),
                                beatStartOffsetMs = beatStartOffsetMs,
                            )
                        }
                    if (!mixed) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.record_freestyle_mix_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } finally {
                _mixInProgress.value = false
                resumeMainPlaybackIfNeeded()
            }
        }
    }

    fun deleteSavedRecording() {
        scope.launch {
            val st = vocalRecorder.recordingState.value as? RecordingState.Saved ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching { st.file.delete() }
            }
            vocalRecorder.discardSavedToIdle()
        }
    }

    private fun pauseMainPlaybackForRecording() {
        val p = PlaybackService.player
        if (p != null && p.isPlaying) {
            p.pause()
            resumeMainPlaybackAfterRecording = true
        } else {
            resumeMainPlaybackAfterRecording = false
        }
    }

    private fun resumeMainPlaybackIfNeeded() {
        if (!resumeMainPlaybackAfterRecording) return
        resumeMainPlaybackAfterRecording = false
        PlaybackService.player?.play()
    }
}
