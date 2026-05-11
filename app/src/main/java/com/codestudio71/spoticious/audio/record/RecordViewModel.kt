package com.codestudio71.spoticious.audio.record

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
    private val deviceRepo = RecordingDeviceRepository(application)
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

    val recordingState: StateFlow<RecordingState> = vocalRecorder.recordingState

    val beatPositionMs: StateFlow<Long> = beatPreviewPlayer.positionMs
    val beatDurationMs: StateFlow<Long> = beatPreviewPlayer.durationMs
    val beatIsPlaying: StateFlow<Boolean> = beatPreviewPlayer.isPlaying

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

    val pickedInputLabel: StateFlow<String?> =
        combine(deviceRepo.devices, _selectedDeviceId) { devices, id ->
            val match = id?.let { pick -> devices.firstOrNull { it.info.id == pick } }
            match?.label ?: devices.firstOrNull()?.label
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        deviceRepo.start()

        vocalRecorder.onWaveformFrame = { pcm16, sampleCount ->
            val frame = waveformAnalyzer.analyze(pcm16, 0, sampleCount)
            pushMicFrame(frame)
        }
        vocalRecorder.onWaveformFrame24 = { bytes, samples24 ->
            val frame = waveformAnalyzer.analyze24bit(bytes, 0, samples24)
            pushMicFrame(frame)
        }

        viewModelScope.launch {
            deviceRepo.devices.collect { list ->
                if (list.isEmpty()) return@collect
                val id = _selectedDeviceId.value
                if (id == null || list.none { it.info.id == id }) {
                    _selectedDeviceId.value = list.first().info.id
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        beatPreviewPlayer.release()
        vocalRecorder.release()
        deviceRepo.stop()
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
        _selectedDeviceId.value = deviceId
    }

    fun shouldAdviceHeadphonesForFreestyle(): Boolean {
        if (_mode.value != RecordMode.Freestyle) return false
        val curId = _selectedDeviceId.value ?: return false
        val dev = deviceRepo.devices.value.firstOrNull { it.info.id == curId } ?: return false
        return dev.info.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
    }

    private fun resolveRecordingDevice(): AudioDeviceInfo? {
        val list = deviceRepo.devices.value
        val id = _selectedDeviceId.value
        return id?.let { lid -> list.firstOrNull { it.info.id == lid }?.info }
            ?: list.firstOrNull()?.info
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

    fun saveToMediaStoreAndToast(): Uri? {
        val st = vocalRecorder.recordingState.value
        val file = (st as? RecordingState.Saved)?.file
        if (file == null || !file.exists()) {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.record_save_nothing),
                Toast.LENGTH_LONG,
            ).show()
            return null
        }
        return trySaveToMusic(file).also {
            Toast.makeText(
                getApplication(),
                if (it != null) {
                    getApplication<Application>().getString(R.string.record_saved_ok)
                } else {
                    getApplication<Application>().getString(R.string.record_save_failed)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun trySaveToMusic(src: File): Uri? {
        return try {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val display = src.name.ifBlank { "Spoticious_rec.wav" }
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

    fun shareIntent(): Intent? {
        val st = vocalRecorder.recordingState.value
        val file = (st as? RecordingState.Saved)?.file ?: return null
        val uri =
            FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.recording_fileprovider",
                file,
            )
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val PERMISSION_RECORD_AUDIO: String = Manifest.permission.RECORD_AUDIO
    }
}
