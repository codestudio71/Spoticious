package com.codestudio71.spoticious.audio.record

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.audio.cut.PcmStreamDecoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10

enum class RecordMode {
    Freestyle,
    VoiceOnly,
}

private const val VU_ATTACK = 0.4f

private const val VU_RELEASE = 0.05f

private const val PEAK_FOLLOW_MS = 0.02f

private const val WAVE_SNAPSHOT_INTERVAL_MS = 33L

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    /** Nagrywanie + beat żyją w [RecordingSession] (proces), nie w ViewModelu — patrz [RecordingService]. */
    private val vocalRecorder = RecordingSession.vocalRecorder
    private val beatPreviewPlayer = RecordingSession.beatPreviewPlayer
    private val savedTakePreviewPlayer = SavedTakePreviewPlayer(application)
    private val deviceRepo = RecordingDeviceRepository(application)
    private val outputDeviceRepo = BeatOutputDeviceRepository(application)
    private val waveformAnalyzer = WaveformAnalyzer()

    private val waveformBufferLock = Any()
    private val micWaveformBuffer = ArrayDeque<FrameResult>(200)
    private val beatWaveformBuffer = ArrayDeque<FrameResult>(200)

    private var lastMicWaveListEmitMs = -1L
    private var lastBeatWaveListEmitMs = -1L

    private var smoothedMicDb = -60f
    private var smoothedBeatDb = -60f

    private var micPeakDisplayDb = -60f
    private var micPeakLastRaiseMs = 0L

    private var beatPeakDisplayDb = -60f
    private var beatPeakLastRaiseMs = 0L

    /**
     * Peak dBFS pliku beatu przy gain=1 (analiza po pick). Meter UI =
     * [beatRefPeakDbfs] + 20·log10(gain) — bez Visualizera i bez fałszywych delt.
     */
    private val _beatRefPeakDbfs = MutableStateFlow(-12f)

    private val _micWaveform = MutableStateFlow<List<FrameResult>>(emptyList())
    val micWaveform: StateFlow<List<FrameResult>> = _micWaveform.asStateFlow()

    private val _beatWaveform = MutableStateFlow<List<FrameResult>>(emptyList())
    val beatWaveform: StateFlow<List<FrameResult>> = _beatWaveform.asStateFlow()

    private val _micDbfs = MutableStateFlow(-60f)
    val micDbfs: StateFlow<Float> = _micDbfs.asStateFlow()

    private val _beatDbfs = MutableStateFlow(-60f)
    val beatDbfs: StateFlow<Float> = _beatDbfs.asStateFlow()

    private val _micPeakDbfs = MutableStateFlow(-60f)
    val micPeakDbfs: StateFlow<Float> = _micPeakDbfs.asStateFlow()

    private val _beatPeakDbfs = MutableStateFlow(-60f)
    val beatPeakDbfs: StateFlow<Float> = _beatPeakDbfs.asStateFlow()

    val inputDevices: StateFlow<List<InputDeviceOption>> = deviceRepo.devices

    val outputDevices: StateFlow<List<OutputDeviceOption>> = outputDeviceRepo.devices

    private val _selectedOutputDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val selectedOutputDevice: StateFlow<AudioDeviceInfo?> = _selectedOutputDevice.asStateFlow()

    val pickedOutputLabel: StateFlow<String?> =
        combine(outputDeviceRepo.devices, _selectedOutputDevice) { devices, selected ->
            selected?.let { sel ->
                devices.firstOrNull { it.info.id == sel.id }?.label
                    ?: run {
                        val prod =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                sel.productName?.toString()?.trim().orEmpty()
                            } else {
                                ""
                            }
                        prod.ifBlank { null }
                    }
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recordingState: StateFlow<RecordingState> = vocalRecorder.recordingState

    /** Ostatni czas nagrania (nie resetuje się w Stopping) + flaga miksu w tle. */
    val recordingElapsedMs: StateFlow<Long> = RecordingSession.recordingElapsedMs
    val mixInProgress: StateFlow<Boolean> = RecordingSession.mixInProgress

    val beatPositionMs: StateFlow<Long> = beatPreviewPlayer.positionMs
    val beatDurationMs: StateFlow<Long> = beatPreviewPlayer.durationMs
    val beatIsPlaying: StateFlow<Boolean> = beatPreviewPlayer.isPlaying

    val savedTakePositionMs: StateFlow<Long> = savedTakePreviewPlayer.positionMs
    val savedTakeDurationMs: StateFlow<Long> = savedTakePreviewPlayer.durationMs
    val savedTakeIsPlaying: StateFlow<Boolean> = savedTakePreviewPlayer.isPlaying

    val mode: StateFlow<RecordMode> = RecordingSession.mode

    val selectedBeatUri: StateFlow<Uri?> = RecordingSession.selectedBeatUri

    /** Głośność bitu (0..1) — wspólna dla podglądu i miksu freestyle. */
    val beatGain: StateFlow<Float> = RecordingSession.beatGain

    val beatLabel: StateFlow<String?> =
        selectedBeatUri
            .map { u ->
                u?.lastPathSegment?.substringAfter(':')
                    ?: u?.toString()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedDeviceId = MutableStateFlow<Int?>(null)

    /** Gdy true — użytkownik jest na Record Preview; logika auto-USB działa tylko wtedy. */
    private var recordPreviewScreenActive = false

    /** Ręczny wybór z listy — nie nadpisuj automatycznie (USB plug itd.) do czasu wyjścia z ekranu. */
    private var userOverrodeInputSelection = false

    private var hadUsbFamilyConnected = false

    val pickedInputLabel: StateFlow<String?> =
        combine(deviceRepo.devices, _selectedDeviceId) { devices, id ->
            val match = id?.let { pick -> devices.firstOrNull { it.info.id == pick } }
            match?.label ?: devices.firstOrNull()?.label
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        deviceRepo.start()
        outputDeviceRepo.start()

        vocalRecorder.onWaveformFrame = { pcm16, sampleCount ->
            val frame = waveformAnalyzer.analyze(pcm16, 0, sampleCount)
            pushMicFrame(frame)
        }
        vocalRecorder.onWaveformFrame24 = { bytes, samples24 ->
            val frame = waveformAnalyzer.analyze24bit(bytes, 0, samples24)
            pushMicFrame(frame)
        }
        vocalRecorder.onMicSignalLost = { toastMicSignalLost() }

        viewModelScope.launch {
            deviceRepo.devices.collect { list -> onInputDevicesChanged(list) }
        }

        viewModelScope.launch {
            outputDeviceRepo.devices.collect { list -> onOutputDevicesChanged(list) }
        }

        // Podgląd ładujemy dopiero, gdy plik jest finalny (Saved + miks zakończony) —
        // inaczej ExoPlayer trzymałby plik, który FreestyleMixdown nadpisuje.
        viewModelScope.launch {
            combine(
                vocalRecorder.recordingState,
                RecordingSession.mixInProgress,
            ) { st, mixing -> st to mixing }
                .collect { (st, mixing) ->
                    when {
                        st is RecordingState.Saved && !mixing ->
                            savedTakePreviewPlayer.loadFile(st.file)
                        st is RecordingState.Saved && mixing -> Unit
                        else -> savedTakePreviewPlayer.clear()
                    }
                }
        }
    }

    /** Wywołaj przy wejściu na Record Preview (np. [DisposableEffect]). */
    fun notifyRecordPreviewScreenOpened() {
        recordPreviewScreenActive = true
        userOverrodeInputSelection = false
        val list = deviceRepo.devices.value
        if (list.isEmpty()) {
            _selectedDeviceId.value = null
        } else {
            _selectedDeviceId.value = RecordingDeviceRepository.defaultInputDeviceId(list)
        }
        hadUsbFamilyConnected = RecordingDeviceRepository.listHasUsbFamily(list)
        refreshDefaultOutputDevice()
    }

    /** Wywołaj przy opuszczeniu Record Preview — znów można stosować pełną automatykę przy następnym wejściu. */
    fun notifyRecordPreviewScreenClosed() {
        recordPreviewScreenActive = false
        userOverrodeInputSelection = false
    }

    private fun onInputDevicesChanged(list: List<InputDeviceOption>) {
        if (!recordPreviewScreenActive) return

        if (list.isEmpty()) {
            _selectedDeviceId.value = null
            hadUsbFamilyConnected = false
            return
        }

        val nowUsb = RecordingDeviceRepository.listHasUsbFamily(list)
        val selectedId = _selectedDeviceId.value
        val selectedStillValid = selectedId != null && list.any { it.info.id == selectedId }

        if (userOverrodeInputSelection && selectedStillValid) {
            hadUsbFamilyConnected = nowUsb
            return
        }

        if (userOverrodeInputSelection && !selectedStillValid) {
            userOverrodeInputSelection = false
        }

        if (nowUsb && !hadUsbFamilyConnected && !userOverrodeInputSelection) {
            val usb =
                list.firstOrNull { RecordingDeviceRepository.isUsbInputFamily(it.info.type) }
            if (usb != null) {
                _selectedDeviceId.value = usb.info.id
                toastSwitchedToUsbMic()
            }
            hadUsbFamilyConnected = nowUsb
            return
        }

        if (!nowUsb && hadUsbFamilyConnected && !userOverrodeInputSelection) {
            val builtinId = RecordingDeviceRepository.firstBuiltinMicId(list)
            _selectedDeviceId.value =
                builtinId ?: RecordingDeviceRepository.defaultInputDeviceId(list)
            hadUsbFamilyConnected = false
            return
        }

        if (!selectedStillValid) {
            _selectedDeviceId.value = RecordingDeviceRepository.defaultInputDeviceId(list)
        }

        hadUsbFamilyConnected = nowUsb
    }

    private fun toastSwitchedToUsbMic() {
        viewModelScope.launch(Dispatchers.Main) {
            val app = getApplication<Application>()
            Toast.makeText(
                app,
                app.getString(R.string.record_switched_usb_mic),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun toastMicSignalLost() {
        viewModelScope.launch(Dispatchers.Main) {
            val app = getApplication<Application>()
            Toast.makeText(
                app,
                app.getString(R.string.record_mic_signal_lost),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Recorder i beat player należą do RecordingSession (przeżywają Activity) —
        // tu odpinamy tylko callbacki wizualizacji i lokalne zasoby.
        vocalRecorder.onWaveformFrame = null
        vocalRecorder.onWaveformFrame24 = null
        vocalRecorder.onMicSignalLost = null
        beatPreviewPlayer.releaseVisualizer()
        savedTakePreviewPlayer.destroy()
        deviceRepo.stop()
        outputDeviceRepo.stop()
    }

    fun setOutputDevice(device: AudioDeviceInfo?) {
        _selectedOutputDevice.value = device
        beatPreviewPlayer.setPreferredOutputDevice(device)
    }

    private fun refreshDefaultOutputDevice() {
        val list = outputDeviceRepo.devices.value
        setOutputDevice(BeatOutputDeviceRepository.defaultOutputDevice(list))
    }

    private fun onOutputDevicesChanged(list: List<OutputDeviceOption>) {
        if (!recordPreviewScreenActive) return
        val selected = _selectedOutputDevice.value
        if (selected != null && list.any { it.info.id == selected.id }) return
        setOutputDevice(BeatOutputDeviceRepository.defaultOutputDevice(list))
    }

    fun toggleSavedTakePreview() {
        savedTakePreviewPlayer.togglePlayPause()
    }

    fun seekSavedTakePreview(ms: Long) {
        savedTakePreviewPlayer.seekToMs(ms)
    }

    fun toggleMode() {
        val next =
            when (mode.value) {
                RecordMode.Freestyle -> RecordMode.VoiceOnly
                RecordMode.VoiceOnly -> RecordMode.Freestyle
            }
        RecordingSession.setMode(next)
    }

    fun pickBeat(uri: Uri) {
        RecordingSession.pickBeat(uri, _selectedOutputDevice.value)
        _beatRefPeakDbfs.value = -12f
        publishBeatLevelFromGain(beatGain.value)
        viewModelScope.launch(Dispatchers.IO) {
            val peak =
                PcmStreamDecoder.estimatePeakDbfs(
                    context = getApplication(),
                    uri = uri,
                )
            _beatRefPeakDbfs.value = peak
            publishBeatLevelFromGain(RecordingSession.beatGain.value)
        }
    }

    /**
     * Suwak: aktualizuje gain + meter od razu, **bez** ExoPlayer.volume.
     * Ciągłe setVolume = trzaski w wyjściu → brudny mic. Volume: [commitBeatGain].
     */
    fun setBeatGain(value: Float) {
        val g = value.coerceIn(0f, 1f)
        if (beatGain.value == g) return
        RecordingSession.setBeatGain(g, applyToPlayer = false)
        publishBeatLevelFromGain(g)
    }

    /** Puszczenie suwaka / play / przed REC — jednorazowe [ExoPlayer.setVolume]. */
    fun commitBeatGain() {
        RecordingSession.applyBeatGainToPlayer()
        publishBeatLevelFromGain(beatGain.value)
    }

    fun toggleBeatPreviewPlayback() {
        commitBeatGain()
        beatPreviewPlayer.togglePlayPause()
    }

    fun seekBeatTo(ms: Long) {
        beatPreviewPlayer.seekTo(ms)
    }

    fun clearBeat() {
        RecordingSession.clearBeat()
        _beatRefPeakDbfs.value = -12f
        publishBeatLevelFromGain(0f)
        synchronized(waveformBufferLock) {
            beatWaveformBuffer.clear()
            _beatWaveform.value = emptyList()
        }
    }

    fun setSelectedDevice(deviceId: Int) {
        userOverrodeInputSelection = true
        _selectedDeviceId.value = deviceId
    }

    /**
     * Radź słuchawki, gdy beat gra przez wbudowany głośnik (wycieka do mikrofonu).
     * Liczy się OUTPUT bitu, nie typ mikrofonu. Tylko freestyle z wczytanym bitem.
     */
    fun shouldAdviceHeadphonesForFreestyle(): Boolean {
        if (mode.value != RecordMode.Freestyle) return false
        if (selectedBeatUri.value == null) return false
        val outType =
            _selectedOutputDevice.value?.type
                ?: BeatOutputDeviceRepository
                    .defaultOutputDevice(outputDeviceRepo.devices.value)
                    ?.type
                ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        return outType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    /** Mono: jedno wejście z listy whitelist (patrz RecordingDeviceRepository). */
    private fun resolveRecordingDevice(): AudioDeviceInfo? {
        val list = deviceRepo.devices.value
        val id = _selectedDeviceId.value
        id?.let { lid ->
            list.firstOrNull { it.info.id == lid }?.info?.let { return it }
        }
        RecordingDeviceRepository.defaultInputDeviceId(list)?.let { def ->
            list.firstOrNull { it.info.id == def }?.info?.let { return it }
        }
        return list.firstOrNull()?.info
    }

    private fun resetWaveformVisualization() {
        synchronized(waveformBufferLock) {
            micWaveformBuffer.clear()
            beatWaveformBuffer.clear()
            lastMicWaveListEmitMs = -1L
            lastBeatWaveListEmitMs = -1L
        }
        smoothedMicDb = -60f
        smoothedBeatDb = -60f
        micPeakDisplayDb = -60f
        beatPeakDisplayDb = -60f
        micPeakLastRaiseMs = SystemClock.elapsedRealtime()
        beatPeakLastRaiseMs = micPeakLastRaiseMs
        _micWaveform.value = emptyList()
        _beatWaveform.value = emptyList()
        _micDbfs.value = -60f
        _beatDbfs.value = -60f
        _micPeakDbfs.value = -60f
        _beatPeakDbfs.value = -60f
        publishBeatLevelFromGain(beatGain.value)
    }

    private fun pushMicFrame(frame: FrameResult) {
        processMicVuAndPeak(frame.dbfs)
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(waveformBufferLock) {
            micWaveformBuffer.addLast(frame)
            while (micWaveformBuffer.size > 200) {
                micWaveformBuffer.removeFirst()
            }
            if (lastMicWaveListEmitMs < 0 || nowMs - lastMicWaveListEmitMs >= WAVE_SNAPSHOT_INTERVAL_MS) {
                _micWaveform.value = micWaveformBuffer.toList()
                lastMicWaveListEmitMs = nowMs
            }
        }
    }

    /** Skalowanie waveformu (gdy kiedyś wróci live capture) — dBFS BEAT z peak×gain. */
    private fun applyBeatGainToFrame(frame: FrameResult, gain: Float): FrameResult {
        val g = gain.coerceIn(0f, 1f)
        if (g <= 0f) return waveformAnalyzer.silentFrame()
        if (g == 1f) return frame
        val dbOffset = (20f * log10(g.toDouble())).toFloat()
        return FrameResult(
            minAmplitude = (frame.minAmplitude * g).coerceIn(-1f, 0f),
            maxAmplitude = (frame.maxAmplitude * g).coerceIn(0f, 1f),
            rms = (frame.rms * g).coerceIn(0f, 1f),
            dbfs = (frame.dbfs + dbOffset).coerceIn(-60f, 0f),
        )
    }

    private fun estimateBeatOutputDbfs(slider: Float): Float {
        val g = FreestyleMixdown.sliderToLinearGain(slider)
        if (g <= 0f) return -60f
        val gainDb = (20.0 * log10(g.toDouble())).toFloat()
        return (_beatRefPeakDbfs.value + gainDb).coerceIn(-60f, 0f)
    }

    private fun publishBeatLevelFromGain(slider: Float) {
        val linear = FreestyleMixdown.sliderToLinearGain(slider)
        val level = estimateBeatOutputDbfs(slider)
        smoothedBeatDb = level
        _beatDbfs.value = level
        if (level >= beatPeakDisplayDb) {
            beatPeakDisplayDb = level
            beatPeakLastRaiseMs = SystemClock.elapsedRealtime()
        } else {
            // Peak hold: przy ściszeniu bieżący spada, peak zostaje chwilę
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - beatPeakLastRaiseMs > 2000L) {
                beatPeakDisplayDb = level
            }
        }
        _beatPeakDbfs.value = beatPeakDisplayDb.coerceIn(-60f, 0f)
        synchronized(waveformBufferLock) {
            if (beatWaveformBuffer.isNotEmpty()) {
                _beatWaveform.value = beatWaveformBuffer.map { applyBeatGainToFrame(it, linear) }
            }
        }
    }

    private fun processMicVuAndPeak(rawDbFs: Float) {
        val prev = smoothedMicDb
        val coeff = if (rawDbFs > prev) VU_ATTACK else VU_RELEASE
        smoothedMicDb = prev + coeff * (rawDbFs - prev)
        _micDbfs.value = smoothedMicDb

        val nowMs = SystemClock.elapsedRealtime()
        if (rawDbFs >= micPeakDisplayDb) {
            micPeakDisplayDb = rawDbFs.coerceAtMost(0f)
            micPeakLastRaiseMs = nowMs
        } else if (nowMs - micPeakLastRaiseMs > 2000L) {
            micPeakDisplayDb += PEAK_FOLLOW_MS * (smoothedMicDb - micPeakDisplayDb)
        }
        _micPeakDbfs.value = micPeakDisplayDb
    }


    fun confirmStartRecording() {
        viewModelScope.launch {
            savedTakePreviewPlayer.clear()
            resetWaveformVisualization()
            commitBeatGain()

            val started =
                RecordingSession.startRecording(
                    micDevice = resolveRecordingDevice(),
                    beatOutputDevice = _selectedOutputDevice.value,
                )

            if (!started) {
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.record_error_start),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            beatPreviewPlayer.releaseVisualizer()
            publishBeatLevelFromGain(beatGain.value)
        }
    }

    fun stopRecording() {
        RecordingSession.stopRecording()
        resetWaveformVisualization()
    }

    fun saveRecordingToMusicWithBaseName(baseName: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val st = vocalRecorder.recordingState.value
            val file = (st as? RecordingState.Saved)?.file
            if (file == null || !file.exists()) {
                Toast.makeText(
                    app,
                    app.getString(R.string.record_save_nothing),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val safe = sanitizeRecordingBaseName(baseName)
            val displayFileName = "$safe.wav"
            val uri =
                withContext(Dispatchers.IO) {
                    trySaveToMusic(file, displayFileName)
                }
            if (uri != null) {
                withContext(Dispatchers.IO) {
                    runCatching { file.delete() }
                }
                vocalRecorder.discardSavedToIdle()
                savedTakePreviewPlayer.clear()
                Toast.makeText(
                    app,
                    app.getString(R.string.record_saved_named, displayFileName),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    app,
                    app.getString(R.string.record_save_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun deleteSavedRecording() {
        savedTakePreviewPlayer.clear()
        RecordingSession.deleteSavedRecording()
    }

    private fun sanitizeRecordingBaseName(raw: String): String {
        val trimmed =
            raw.trim()
                .removeSuffix(".wav")
                .removeSuffix(".WAV")
        val base =
            trimmed.ifBlank {
                "Spoticious_rec_${System.currentTimeMillis()}"
            }
        val noIllegal = base.replace(Regex("""[/\\:*?"<>|]"""), "_").take(180)
        return noIllegal.ifBlank { "Spoticious_rec_${System.currentTimeMillis()}" }
    }

    private fun trySaveToMusic(src: File, displayFileName: String): Uri? {
        return try {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val display = displayFileName.ifBlank { "Spoticious_rec.wav" }
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, display)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/Spoticious",
                )
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            @Suppress("DEPRECATION")
            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            val uri = resolver.insert(collection, values) ?: return null
            val stream = resolver.openOutputStream(uri)
            if (stream == null) {
                resolver.delete(uri, null, null)
                return null
            }
            stream.use { os -> src.inputStream().use { it.copyTo(os) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PERMISSION_RECORD_AUDIO: String = Manifest.permission.RECORD_AUDIO
    }
}
