package com.codestudio71.spoticious.player

import android.app.Application
import android.net.Uri
import com.codestudio71.spoticious.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RenderState {
    data object Idle : RenderState()

    data class ShowAacDialog(
        val defaultName: String,
        val aacSampleRate: Int = 0
    ) : RenderState()

    data class ShowWavDialog(
        val defaultName: String,
        val sampleRate: Int = 44100
    ) : RenderState()

    data class Rendering(
        val progress: Float,
        val outputName: String,
        val phase: String
    ) : RenderState()

    data class Done(
        val outputUri: Uri,
        val outputName: String,
        val outputFormat: OutputFormat
    ) : RenderState()

    data class Error(val message: String) : RenderState()
}

class RenderViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<RenderState>(RenderState.Idle)
    val state: StateFlow<RenderState> = _state.asStateFlow()

    private var renderJob: Job? = null
    private var pendingInputUri: Uri? = null
    private var pendingEqBands: FloatArray? = null
    private var pendingPreampDb: Float = 0f

    private fun defaultNameFromUri(uri: Uri): String {
        return uri.lastPathSegment
            ?.substringBeforeLast(".")
            ?.plus("_EQ")
            ?: "export_EQ"
    }

    fun onExportAacClicked(inputUri: Uri, eqBands: List<Float>, preampDb: Float) {
        pendingInputUri = inputUri
        pendingEqBands = eqBands.map { it.coerceIn(-15f, 15f) }.toFloatArray()
        pendingPreampDb = preampDb.coerceIn(-15f, 15f)
        _state.value = RenderState.ShowAacDialog(defaultName = defaultNameFromUri(inputUri))
    }

    fun onExportWavClicked(inputUri: Uri, eqBands: List<Float>, preampDb: Float) {
        pendingInputUri = inputUri
        pendingEqBands = eqBands.map { it.coerceIn(-15f, 15f) }.toFloatArray()
        pendingPreampDb = preampDb.coerceIn(-15f, 15f)
        _state.value = RenderState.ShowWavDialog(
            defaultName = defaultNameFromUri(inputUri),
            sampleRate = 44100
        )
    }

    fun onWavSampleRateSelected(sampleRate: Int) {
        val current = _state.value
        if (current is RenderState.ShowWavDialog) {
            _state.value = current.copy(sampleRate = sampleRate)
        }
    }

    fun onAacSampleRateSelected(sampleRate: Int) {
        val current = _state.value
        if (current is RenderState.ShowAacDialog) {
            _state.value = current.copy(aacSampleRate = sampleRate)
        }
    }

    fun startRender(customName: String, format: OutputFormat, sampleRate: Int = 44100, aacSampleRate: Int = 0) {
        val inputUri = pendingInputUri ?: return
        val eqBands = pendingEqBands ?: return
        val preampDb = pendingPreampDb
        val outputName = customName.ifBlank { defaultNameFromUri(inputUri) }

        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _state.value = RenderState.Rendering(
                progress = 0f,
                outputName = outputName,
                phase = app.getString(R.string.render_phase_preparing)
            )
            val result = AudioRenderPipeline.render(
                context = getApplication(),
                inputUri = inputUri,
                outputFormat = format,
                outputFileName = outputName,
                sampleRate = sampleRate,
                aacSampleRate = aacSampleRate,
                eqBands = eqBands,
                preampDb = preampDb,
                onProgress = { progress, phase ->
                    _state.value = RenderState.Rendering(
                        progress = progress,
                        outputName = outputName,
                        phase = phase
                    )
                }
            )
            _state.value = result.fold(
                onSuccess = { RenderState.Done(it, outputName, format) },
                onFailure = {
                    RenderState.Error(
                        it.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
            )
        }
    }

    fun resetToIdle() {
        renderJob?.cancel()
        pendingInputUri = null
        pendingEqBands = null
        _state.value = RenderState.Idle
    }
}
