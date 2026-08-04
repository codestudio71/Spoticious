package com.codestudio71.spoticious.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.audio.cut.AudioCutViewModel
import com.codestudio71.spoticious.audio.cut.CutUiState
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.components.StaticWaveform
import com.codestudio71.spoticious.ui.components.TimeStepper
import com.codestudio71.spoticious.ui.components.formatStepperTime
import com.codestudio71.spoticious.ui.components.formatStepperTimeHms
import com.codestudio71.spoticious.ui.components.replaceTimeUnit
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import java.io.File


/** Export/merge name dialog: null = closed; segmentId null = merge. */
private data class PendingExportName(
    val segmentId: Long?,
    val draft: String,
)

@Composable
fun AudioCutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioCutViewModel = viewModel(),
) {
    val look = LocalSpoticiousLook.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val fromMs by viewModel.fromMs.collectAsState()
    val toMs by viewModel.toMs.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val exportingId by viewModel.exportingId.collectAsState()
    val selectedSegmentIds by viewModel.selectedSegmentIds.collectAsState()
    val merging by viewModel.merging.collectAsState()
    val previewPlaying by viewModel.previewPlaying.collectAsState()
    val previewPositionMs by viewModel.previewPositionMs.collectAsState()
    var pendingExportName by remember { mutableStateOf<PendingExportName?>(null) }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val name = uri.lastPathSegment ?: "audio"
                viewModel.loadFile(context, uri, name)
            }
        }

    pendingExportName?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingExportName = null },
            title = {
                Text(
                    stringResource(R.string.audio_cut_export_name_title),
                    color = look.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value = pending.draft,
                    onValueChange = { pendingExportName = pending.copy(draft = it) },
                    singleLine = true,
                    label = {
                        Text(stringResource(R.string.audio_cut_export_name_hint), color = look.textMuted)
                    },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = look.textPrimary,
                            unfocusedTextColor = look.textPrimary,
                            focusedBorderColor = look.accent,
                            unfocusedBorderColor = look.accent.copy(alpha = 0.5f),
                            cursorColor = look.accent,
                            focusedLabelColor = look.accent,
                            unfocusedLabelColor = look.textMuted,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = pending.draft.trim().ifBlank { pending.draft }
                        val id = pending.segmentId
                        pendingExportName = null
                        if (id == null) {
                            viewModel.exportMerged(
                                context = context,
                                label = label.ifBlank { context.getString(R.string.audio_cut_merge_default) },
                                onSaved = { name ->
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.record_saved_named, name),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                                onError = {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.audio_cut_export_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            )
                        } else {
                            viewModel.export(
                                context = context,
                                id = id,
                                label = label.takeIf { it.isNotBlank() },
                                onSaved = { name ->
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.record_saved_named, name),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                                onError = {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.audio_cut_export_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.save), color = look.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportName = null }) {
                    Text(stringResource(R.string.cancel), color = look.textMuted)
                }
            },
            containerColor = look.dialogFill,
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = look.textPrimary)
            }
            Text(
                stringResource(R.string.audio_cut_title),
                color = look.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { filePicker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
        ) {
            Text(stringResource(R.string.audio_cut_pick_file))
        }

        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            CutUiState.Idle -> {
                Text(
                    stringResource(R.string.audio_cut_idle_hint),
                    color = look.textMuted,
                    fontSize = 14.sp,
                )
            }

            is CutUiState.Loading -> {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.audio_cut_loading),
                        color = look.accent,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = look.accent,
                    )
                }
            }

            is CutUiState.Error -> {
                Text(
                    stringResource(R.string.audio_cut_error),
                    color = look.accentAlt,
                    fontSize = 14.sp,
                )
            }

            is CutUiState.Ready -> {
                Text(
                    state.displayName,
                    color = look.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))

                StaticWaveform(
                    peaks = state.peaks,
                    fromMs = fromMs,
                    toMs = toMs,
                    onFromChange = viewModel::setFromMs,
                    onToChange = viewModel::setToMs,
                    playheadMs = if (previewPlaying) previewPositionMs else null,
                )

                Spacer(modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = viewModel::togglePreview,
                        enabled = fromMs < toMs,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (previewPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                            contentDescription = stringResource(R.string.audio_cut_preview),
                            tint = if (fromMs < toMs) look.accent else look.inactiveTrack,
                        )
                    }
                    Text(
                        text =
                            if (previewPlaying) {
                                stringResource(
                                    R.string.audio_cut_preview_playing,
                                    formatStepperTimeHms(previewPositionMs),
                                    formatStepperTimeHms(toMs),
                                )
                            } else {
                                stringResource(R.string.audio_cut_preview_hint)
                            },
                        color = look.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier.height(16.dp))

                MiamiFrame(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                    solidFill = false,
                ) {
                    val showHours = state.peaks.durationMs >= 3_600_000L
                    TimeStepper(
                        label = stringResource(R.string.audio_cut_from),
                        timeMs = fromMs,
                        onNudge = viewModel::nudgeFrom,
                        onSetUnit = { unit, value ->
                            viewModel.setFromMs(replaceTimeUnit(fromMs, unit, value))
                        },
                        accent = look.accent,
                        showHours = showHours,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = look.accent.copy(alpha = 0.25f),
                    )
                    TimeStepper(
                        label = stringResource(R.string.audio_cut_to),
                        timeMs = toMs,
                        onNudge = viewModel::nudgeTo,
                        onSetUnit = { unit, value ->
                            viewModel.setToMs(replaceTimeUnit(toMs, unit, value))
                        },
                        accent = look.accentAlt,
                        showHours = showHours,
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = viewModel::addSegment,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fromMs < toMs,
                ) {
                    Text(stringResource(R.string.audio_cut_add_segment), color = look.accent)
                }

                if (segments.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.audio_cut_segments),
                        color = look.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    if (segments.size >= 2) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.audio_cut_merge_hint),
                            color = look.textMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                pendingExportName =
                                    PendingExportName(
                                        segmentId = null,
                                        draft = context.getString(R.string.audio_cut_merge_default),
                                    )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedSegmentIds.size >= 2 && !merging && exportingId == null,
                        ) {
                            if (merging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = look.accent,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.audio_cut_merge_segments),
                                    color = look.accent,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    segments.forEach { segment ->
                        SegmentRow(
                            segmentId = segment.id,
                            label = segment.label,
                            rangeLabel =
                                "${formatStepperTimeHms(segment.startMs)} – ${formatStepperTimeHms(segment.endMs)}",
                            durationLabel =
                                formatStepperTimeHms((segment.endMs - segment.startMs).coerceAtLeast(0L)),
                            isExporting = exportingId == segment.id,
                            exportCacheFile = segment.exportCacheFile,
                            selectedForMerge = segment.id in selectedSegmentIds,
                            mergeOrder = selectedSegmentIds.indexOf(segment.id).let { if (it >= 0) it + 1 else null },
                            showMergeCheckbox = segments.size >= 2,
                            onToggleMergeSelect = { viewModel.toggleSegmentSelected(segment.id) },
                            onRename = { viewModel.renameSegment(segment.id, it) },
                            onExport = {
                                pendingExportName =
                                    PendingExportName(
                                        segmentId = segment.id,
                                        draft = segment.label,
                                    )
                            },
                            onShare = { file -> shareWav(context, file) },
                            onDelete = { viewModel.removeSegment(segment.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SegmentRow(
    segmentId: Long,
    label: String,
    rangeLabel: String,
    durationLabel: String,
    isExporting: Boolean,
    exportCacheFile: java.io.File?,
    selectedForMerge: Boolean,
    mergeOrder: Int?,
    showMergeCheckbox: Boolean,
    onToggleMergeSelect: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onShare: (java.io.File) -> Unit,
    onDelete: () -> Unit,
) {
    val look = LocalSpoticiousLook.current
    var editName by remember(segmentId, label) { mutableStateOf(label) }

    MiamiFrame(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        if (showMergeCheckbox) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = selectedForMerge,
                    onCheckedChange = { onToggleMergeSelect() },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = look.accent,
                            uncheckedColor = look.textMuted,
                            checkmarkColor = look.onAccent,
                        ),
                )
                if (mergeOrder != null) {
                    Text(
                        "$mergeOrder.",
                        color = look.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    label,
                    color = look.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            value = editName,
            onValueChange = {
                editName = it
                onRename(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.audio_cut_segment_name)) },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = look.textPrimary,
                    unfocusedTextColor = look.textPrimary,
                    focusedBorderColor = look.accent,
                    unfocusedBorderColor = look.inactiveTrack,
                    cursorColor = look.accent,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$rangeLabel ($durationLabel)",
            color = look.textMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onExport,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = look.onAccent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.record_save))
                }
            }
            IconButton(
                onClick = { exportCacheFile?.let(onShare) },
                enabled = exportCacheFile != null && exportCacheFile.exists(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = look.accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = look.accentAlt)
            }
        }
    }
}

private fun shareWav(context: android.content.Context, file: File) {
    val authority = "${context.packageName}.recording_fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.record_share)))
}
