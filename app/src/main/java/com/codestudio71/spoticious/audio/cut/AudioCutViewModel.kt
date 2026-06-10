package com.codestudio71.spoticious.audio.cut

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codestudio71.spoticious.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CutSegment(
    val id: Long,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    /** Ostatni plik w filesDir — do opcjonalnego udostępnienia. */
    val exportCacheFile: java.io.File? = null,
)

sealed interface CutUiState {
    data object Idle : CutUiState

    data class Loading(val progress: Float) : CutUiState

    data class Ready(
        val peaks: WaveformPeaks,
        val sourceUri: Uri,
        val displayName: String,
    ) : CutUiState

    data class Error(val message: String) : CutUiState
}

class AudioCutViewModel(application: Application) : AndroidViewModel(application) {

    private val peaksExtractor = AudioPeaks()
    private val cutter = AudioCutter()

    private val _uiState = MutableStateFlow<CutUiState>(CutUiState.Idle)
    val uiState: StateFlow<CutUiState> = _uiState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _fromMs = MutableStateFlow(0L)
    val fromMs: StateFlow<Long> = _fromMs.asStateFlow()

    private val _toMs = MutableStateFlow(0L)
    val toMs: StateFlow<Long> = _toMs.asStateFlow()

    private val _segments = MutableStateFlow<List<CutSegment>>(emptyList())
    val segments: StateFlow<List<CutSegment>> = _segments.asStateFlow()

    private val _exportingId = MutableStateFlow<Long?>(null)
    val exportingId: StateFlow<Long?> = _exportingId.asStateFlow()

    private var nextSegmentId = 1L

    fun loadFile(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.value = CutUiState.Loading(0f)
            _segments.value = emptyList()
            nextSegmentId = 1L
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }

            val peaks =
                peaksExtractor.extract(getApplication(), uri) { p ->
                    _uiState.value = CutUiState.Loading(p.coerceIn(0f, 1f))
                }

            if (peaks == null) {
                _uiState.value = CutUiState.Error("decode_failed")
                return@launch
            }

            _fromMs.value = 0L
            _toMs.value = peaks.durationMs.coerceAtLeast(1L)
            _positionMs.value = 0L
            _uiState.value = CutUiState.Ready(peaks, uri, displayName)
        }
    }

    fun setPosition(ms: Long) {
        val duration = durationMs() ?: return
        _positionMs.value = ms.coerceIn(0L, duration)
    }

    fun nudgeFrom(deltaMs: Long) {
        val duration = durationMs() ?: return
        _fromMs.value = (_fromMs.value + deltaMs).coerceIn(0L, duration)
        if (_fromMs.value >= _toMs.value) {
            _toMs.value = (_fromMs.value + 10L).coerceAtMost(duration)
        }
    }

    fun nudgeTo(deltaMs: Long) {
        val duration = durationMs() ?: return
        _toMs.value = (_toMs.value + deltaMs).coerceIn(0L, duration)
        if (_toMs.value <= _fromMs.value) {
            _fromMs.value = (_toMs.value - 10L).coerceAtLeast(0L)
        }
    }

    fun setFromMs(ms: Long) {
        val duration = durationMs() ?: return
        _fromMs.value = ms.coerceIn(0L, duration)
        if (_fromMs.value >= _toMs.value) {
            _toMs.value = (_fromMs.value + 10L).coerceAtMost(duration)
        }
    }

    fun setToMs(ms: Long) {
        val duration = durationMs() ?: return
        _toMs.value = ms.coerceIn(0L, duration)
        if (_toMs.value <= _fromMs.value) {
            _fromMs.value = (_toMs.value - 10L).coerceAtLeast(0L)
        }
    }

    fun addSegment() {
        if (_fromMs.value >= _toMs.value) return
        val index = _segments.value.size + 1
        val app = getApplication<Application>()
        val segment =
            CutSegment(
                id = nextSegmentId++,
                label = app.getString(R.string.audio_cut_segment_default, index),
                startMs = _fromMs.value,
                endMs = _toMs.value,
            )
        _segments.value = _segments.value + segment
    }

    fun renameSegment(id: Long, label: String) {
        _segments.value =
            _segments.value.map {
                if (it.id == id) it.copy(label = label.trim().ifEmpty { it.label }) else it
            }
    }

    fun removeSegment(id: Long) {
        _segments.value
            .firstOrNull { it.id == id }
            ?.exportCacheFile
            ?.let { runCatching { it.delete() } }
        _segments.value = _segments.value.filter { it.id != id }
    }

    fun export(
        context: Context,
        id: Long,
        onSaved: (String) -> Unit,
        onError: () -> Unit,
    ) {
        val state = _uiState.value as? CutUiState.Ready ?: return
        val segment = _segments.value.firstOrNull { it.id == id } ?: return
        if (_exportingId.value != null) return

        viewModelScope.launch {
            _exportingId.value = id
            val outFile =
                cutter.buildOutputFile(
                    context.filesDir,
                    segment.label,
                )
            val ok =
                cutter.exportSegment(
                    context = context,
                    sourceUri = state.sourceUri,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    outFile = outFile,
                    sampleRate = state.peaks.sampleRate,
                    channelCount = state.peaks.channelCount,
                )
            _exportingId.value = null
            if (!ok || !outFile.exists() || outFile.length() <= 44) {
                runCatching { outFile.delete() }
                onError()
                return@launch
            }
            val displayName = WavMusicExporter.sanitizeFileName(segment.label)
            val savedUri =
                WavMusicExporter.saveToMusic(
                    context.contentResolver,
                    outFile,
                    displayName,
                )
            if (savedUri == null) {
                runCatching { outFile.delete() }
                onError()
                return@launch
            }
            _segments.value =
                _segments.value.map {
                    if (it.id == id) {
                        it.exportCacheFile?.let { old -> runCatching { old.delete() } }
                        it.copy(exportCacheFile = outFile)
                    } else {
                        it
                    }
                }
            onSaved(displayName)
        }
    }

    private fun durationMs(): Long? =
        when (val s = _uiState.value) {
            is CutUiState.Ready -> s.peaks.durationMs
            else -> null
        }
}
