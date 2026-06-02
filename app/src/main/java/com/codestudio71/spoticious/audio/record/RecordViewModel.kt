package com.codestudio71.spoticious.audio.record

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.media.AudioDeviceInfo
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codestudio71.spoticious.R
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class RecordMode {
    Freestyle,
    VoiceOnly,
}

private const val SAMPLE_RATE_DEFAULT = 48_000
private const val BIT_DEPTH_DEFAULT = 24

private const val VU_ATTACK = 0.4f

private const val VU_RELEASE = 0.05f

private const val PEAK_FOLLOW_MS = 0.02f

private const val WAVE_SNAPSHOT_INTERVAL_MS = 33L

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val vocalRecorder = VocalRecorder()
    private val beatPreviewPlayer = BeatPreviewPlayer(application)
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

    private val beatVisualizerListener =
        object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(
                visualizer: Visualizer?,
                waveform: ByteArray?,
                samplingRate: Int,
            ) {
                if (waveform == null) return
                val frame = waveformAnalyzer.analyzeUnsigned8bit(waveform, 0, waveform.size)
                pushBeatFrame(frame)
            }

            override fun onFftDataCapture(
                visualizer: Visualizer?,
                fft: ByteArray?,
                samplingRate: Int,
            ) {
            }
        }

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

    val beatPositionMs: StateFlow<Long> = beatPreviewPlayer.positionMs
    val beatDurationMs: StateFlow<Long> = beatPreviewPlayer.durationMs
    val beatIsPlaying: StateFlow<Boolean> = beatPreviewPlayer.isPlaying

    val savedTakePositionMs: StateFlow<Long> = savedTakePreviewPlayer.positionMs
    val savedTakeDurationMs: StateFlow<Long> = savedTakePreviewPlayer.durationMs
    val savedTakeIsPlaying: StateFlow<Boolean> = savedTakePreviewPlayer.isPlaying

    private val _mode = MutableStateFlow(RecordMode.Freestyle)
    val mode: StateFlow<RecordMode> = _mode.asStateFlow()

    private val _selectedBeatUri = MutableStateFlow<Uri?>(null)
    val selectedBeatUri: StateFlow<Uri?> = _selectedBeatUri.asStateFlow()

    val beatLabel: StateFlow<String?> =
        _selectedBeatUri
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

        viewModelScope.launch {
            deviceRepo.devices.collect { list -> onInputDevicesChanged(list) }
        }

        viewModelScope.launch {
            outputDeviceRepo.devices.collect { list -> onOutputDevicesChanged(list) }
        }

        viewModelScope.launch {
            vocalRecorder.recordingState.collect { st ->
                when (st) {
                    is RecordingState.Saved -> savedTakePreviewPlayer.loadFile(st.file)
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

    override fun onCleared() {
        super.onCleared()
        beatPreviewPlayer.release()
        savedTakePreviewPlayer.destroy()
        vocalRecorder.release()
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
        when (_mode.value) {
            RecordMode.Freestyle -> _mode.value = RecordMode.VoiceOnly
            RecordMode.VoiceOnly -> _mode.value = RecordMode.Freestyle
        }
        if (_mode.value == RecordMode.VoiceOnly) {
            _selectedBeatUri.value = null
            beatPreviewPlayer.clear()
        }
    }

    fun pickBeat(uri: Uri) {
        _selectedBeatUri.value = uri
        beatPreviewPlayer.setPreferredOutputDevice(_selectedOutputDevice.value)
        beatPreviewPlayer.loadBeat(uri)
        beatPreviewPlayer.seekToStart()
        beatPreviewPlayer.pause()
    }

    fun toggleBeatPreviewPlayback() {
        beatPreviewPlayer.togglePlayPause()
    }

    fun seekBeatTo(ms: Long) {
        beatPreviewPlayer.seekTo(ms)
    }

    fun clearBeat() {
        _selectedBeatUri.value = null
        beatPreviewPlayer.clear()
    }

    fun setSelectedDevice(deviceId: Int) {
        userOverrodeInputSelection = true
        _selectedDeviceId.value = deviceId
    }

    fun shouldAdviceHeadphonesForFreestyle(): Boolean {
        if (_mode.value != RecordMode.Freestyle) return false
        val curId = _selectedDeviceId.value ?: return false
        val dev = deviceRepo.devices.value.firstOrNull { it.info.id == curId } ?: return false
        return dev.info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
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

    private fun pushBeatFrame(frame: FrameResult) {
        processBeatVuAndPeak(frame.dbfs)
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(waveformBufferLock) {
            beatWaveformBuffer.addLast(frame)
            while (beatWaveformBuffer.size > 200) {
                beatWaveformBuffer.removeFirst()
            }
            if (lastBeatWaveListEmitMs < 0 || nowMs - lastBeatWaveListEmitMs >= WAVE_SNAPSHOT_INTERVAL_MS) {
                _beatWaveform.value = beatWaveformBuffer.toList()
                lastBeatWaveListEmitMs = nowMs
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

    private fun processBeatVuAndPeak(rawDbFs: Float) {
        val prev = smoothedBeatDb
        val coeff = if (rawDbFs > prev) VU_ATTACK else VU_RELEASE
        smoothedBeatDb = prev + coeff * (rawDbFs - prev)
        _beatDbfs.value = smoothedBeatDb

        val nowMs = SystemClock.elapsedRealtime()
        if (rawDbFs >= beatPeakDisplayDb) {
            beatPeakDisplayDb = rawDbFs.coerceAtMost(0f)
            beatPeakLastRaiseMs = nowMs
        } else if (nowMs - beatPeakLastRaiseMs > 2000L) {
            beatPeakDisplayDb += PEAK_FOLLOW_MS * (smoothedBeatDb - beatPeakDisplayDb)
        }
        _beatPeakDbfs.value = beatPeakDisplayDb
    }

    private suspend fun attachBeatVisualizerWhenRecording() {
        repeat(42) {
            beatPreviewPlayer.attachVisualizer(beatVisualizerListener)
            if (beatPreviewPlayer.isVisualizerAttached()) return
            delay(48)
        }
    }

    fun confirmStartRecording() {
        viewModelScope.launch {
            val prev = vocalRecorder.recordingState.value
            if (prev is RecordingState.Saved) {
                withContext(Dispatchers.IO) {
                    runCatching { prev.file.delete() }
                }
                vocalRecorder.discardSavedToIdle()
                savedTakePreviewPlayer.clear()
            }

            val outFile =
                File(
                    getApplication<Application>().filesDir,
                    "Spoticious_rec_${System.currentTimeMillis()}.wav",
                )

            resetWaveformVisualization()

            val mic = resolveRecordingDevice()

            val started =
                withContext(Dispatchers.IO) {
                    vocalRecorder.start(
                        outputFile = outFile,
                        sampleRate = SAMPLE_RATE_DEFAULT,
                        bitDepth = BIT_DEPTH_DEFAULT,
                        preferredDevice = mic,
                    )
                }

            if (!started) {
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.record_error_start),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            if (_mode.value == RecordMode.Freestyle) {
                val beatUri = _selectedBeatUri.value
                if (beatUri != null) {
                    withContext(Dispatchers.Main) {
                        beatPreviewPlayer.setPreferredOutputDevice(_selectedOutputDevice.value)
                        beatPreviewPlayer.loadBeat(beatUri)
                        beatPreviewPlayer.seekToStart()
                        beatPreviewPlayer.play()
                    }
                    attachBeatVisualizerWhenRecording()
                } else {
                    withContext(Dispatchers.Main) {
                        beatPreviewPlayer.releaseVisualizer()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    beatPreviewPlayer.releaseVisualizer()
                }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                beatPreviewPlayer.stop()
                beatPreviewPlayer.releaseVisualizer()
            }
            withContext(Dispatchers.IO) {
                vocalRecorder.stop(null)
            }
            resetWaveformVisualization()
        }
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
        viewModelScope.launch {
            val st = vocalRecorder.recordingState.value as? RecordingState.Saved ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching { st.file.delete() }
            }
            vocalRecorder.discardSavedToIdle()
            savedTakePreviewPlayer.clear()
        }
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
