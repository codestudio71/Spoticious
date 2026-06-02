package com.codestudio71.spoticious.ui.screens

import android.Manifest
import android.media.AudioDeviceInfo
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.audio.record.RecordMode
import com.codestudio71.spoticious.audio.record.RecordViewModel
import com.codestudio71.spoticious.audio.record.RecordingState
import com.codestudio71.spoticious.ui.components.WaveformWithMeter
import java.util.Locale

private val CyanUi = Color(0xFF00BCD4)
private val BgGrad =
    listOf(
        Color(0xFF0D0D1A),
        Color(0xFF1A0A2E),
        Color(0xFF2D1B4E),
    )
private val RecRed = Color(0xFFFF1744)
private val DarkOnCyan = Color(0xFF0D0D1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecordViewModel = viewModel()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.notifyRecordPreviewScreenOpened()
        onDispose {
            viewModel.notifyRecordPreviewScreenClosed()
        }
    }

    val mode by viewModel.mode.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val beatUri by viewModel.selectedBeatUri.collectAsState()
    val beatName by viewModel.beatLabel.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()
    val pickedLabel by viewModel.pickedInputLabel.collectAsState()
    val outputDevices by viewModel.outputDevices.collectAsState()
    val pickedOutputLabel by viewModel.pickedOutputLabel.collectAsState()
    val selectedOutputDevice by viewModel.selectedOutputDevice.collectAsState()

    val micWaveform by viewModel.micWaveform.collectAsState()
    val beatWaveform by viewModel.beatWaveform.collectAsState()
    val micDbfs by viewModel.micDbfs.collectAsState()
    val beatDbfs by viewModel.beatDbfs.collectAsState()
    val micPeakDbfs by viewModel.micPeakDbfs.collectAsState()
    val beatPeakDbfs by viewModel.beatPeakDbfs.collectAsState()

    val savedTakePosMs by viewModel.savedTakePositionMs.collectAsState()
    val savedTakeDurMs by viewModel.savedTakeDurationMs.collectAsState()
    val savedTakePlaying by viewModel.savedTakeIsPlaying.collectAsState()

    var deviceMenu by remember { mutableStateOf(false) }
    var outputDeviceMenu by remember { mutableStateOf(false) }
    var headsetDialog by remember { mutableStateOf(false) }
    var skipHeadsetAdvice by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    val savedRecording = recState as? RecordingState.Saved
    val savedFilePath = savedRecording?.file?.absolutePath
    var recordingName by remember { mutableStateOf("") }
    LaunchedEffect(savedFilePath) {
        savedRecording?.file?.let { recordingName = it.nameWithoutExtension }
    }

    var sliderDragging by remember { mutableStateOf(false) }
    var sliderDraft by remember { mutableFloatStateOf(0f) }

    val permLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) {
                Toast.makeText(context, context.getString(R.string.record_error_start), Toast.LENGTH_SHORT).show()
            } else {
                tryStartRecording(skipHeadsetAdvice, viewModel) { headsetDialog = true }
            }
        }

    val beatPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.pickBeat(it) }
        }

    val beatLoaded = beatUri != null

    val isRecording = recState is RecordingState.Recording
    val isBusy = recState is RecordingState.Stopping
    val beatUiLocked = isRecording || isBusy
    val isFreestyle = mode == RecordMode.Freestyle
    val isSaved = recState is RecordingState.Saved

    val durMs =
        when (val st = recState) {
            is RecordingState.Recording -> st.durationMs
            else -> 0L
        }
    val formattedDuration = formatRecordTime(durMs)

    val selectedDeviceName =
        pickedLabel
            ?.substringBefore(" · ")
            ?.takeIf { it.isNotBlank() }
            ?: pickedLabel
            ?: "—"

    val selectedOutputName =
        pickedOutputLabel
            ?.substringBefore(" · ")
            ?.takeIf { it.isNotBlank() }
            ?: pickedOutputLabel
            ?: "—"

    val outputIcon =
        beatOutputIcon(
            selectedOutputDevice?.type
                ?: outputDevices.firstOrNull()?.info?.type
                ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )

    val toggleRecording: () -> Unit = {
        when (recState) {
            is RecordingState.Recording -> viewModel.stopRecording()
            is RecordingState.Stopping -> {}
            else -> {
                if (!hasAudioPermission(context)) {
                    permLauncher.launch(RecordViewModel.PERMISSION_RECORD_AUDIO)
                } else {
                    tryStartRecording(skipHeadsetAdvice, viewModel) {
                        headsetDialog = true
                    }
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(BgGrad))
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                stringResource(R.string.record_preview_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = isFreestyle,
                onClick = {
                    if (!isFreestyle) viewModel.toggleMode()
                },
                label = { Text(stringResource(R.string.record_mode_freestyle)) },
                modifier = Modifier.weight(1f),
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = CyanUi,
                        selectedLabelColor = DarkOnCyan,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isFreestyle,
                        borderColor = Color.Gray,
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
            )
            FilterChip(
                selected = !isFreestyle,
                onClick = {
                    if (isFreestyle) viewModel.toggleMode()
                },
                label = { Text(stringResource(R.string.record_mode_voice_only)) },
                modifier = Modifier.weight(1f),
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = CyanUi,
                        selectedLabelColor = DarkOnCyan,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = !isFreestyle,
                        borderColor = Color.Gray,
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
            )
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            border = BorderStroke(1.dp, CyanUi.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = CyanUi,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        stringResource(R.string.record_input_mic),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    selectedDeviceName,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { deviceMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CyanUi),
                        colors =
                            ButtonDefaults.outlinedButtonColors(contentColor = CyanUi),
                    ) {
                        Text(stringResource(R.string.record_choose_input))
                    }
                    DropdownMenu(
                        expanded = deviceMenu,
                        onDismissRequest = { deviceMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1F26)),
                    ) {
                        inputDevices.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label, color = Color.White) },
                                onClick = {
                                    viewModel.setSelectedDevice(opt.info.id)
                                    deviceMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isFreestyle) {
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                border = BorderStroke(1.dp, CyanUi.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            outputIcon,
                            contentDescription = null,
                            tint = CyanUi,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            stringResource(R.string.record_output_beat),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        selectedOutputName,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { outputDeviceMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, CyanUi),
                            colors =
                                ButtonDefaults.outlinedButtonColors(contentColor = CyanUi),
                        ) {
                            Text(stringResource(R.string.record_choose_output))
                        }
                        DropdownMenu(
                            expanded = outputDeviceMenu,
                            onDismissRequest = { outputDeviceMenu = false },
                            modifier = Modifier.background(Color(0xFF1A1F26)),
                        ) {
                            outputDevices.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                beatOutputIcon(opt.info.type),
                                                contentDescription = null,
                                                tint = CyanUi,
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                            Text(opt.label, color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setOutputDevice(opt.info)
                                        outputDeviceMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isFreestyle) {
            if (beatUri != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        beatName ?: "—",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                    )
                    IconButton(
                        onClick = { viewModel.clearBeat() },
                        enabled = !beatUiLocked,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = CyanUi,
                        )
                    }
                }
            } else {
                Button(
                    onClick = { beatPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = CyanUi,
                            contentColor = Color.Black,
                        ),
                ) {
                    Text(stringResource(R.string.record_add_beat), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isRecording) {
            WaveformWithMeter(
                label = "MIC",
                frames = micWaveform,
                dbfs = micDbfs,
                peakDbfs = micPeakDbfs,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isFreestyle && beatLoaded) {
                Spacer(Modifier.height(12.dp))
                WaveformWithMeter(
                    label = "BEAT",
                    frames = beatWaveform,
                    dbfs = beatDbfs,
                    peakDbfs = beatPeakDbfs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
        } else if (isSaved) {
            val durMax = savedTakeDurMs.coerceAtLeast(1L).toFloat()
            val clampedPos =
                if (savedTakeDurMs > 0L) savedTakePosMs.coerceIn(0L, savedTakeDurMs) else savedTakePosMs
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = recordingName,
                    onValueChange = { recordingName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.record_name_placeholder),
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CyanUi,
                            focusedBorderColor = CyanUi,
                            unfocusedBorderColor = CyanUi,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = CyanUi,
                            unfocusedLabelColor = CyanUi,
                        ),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleSavedTakePreview() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (savedTakePlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                contentDescription = null,
                                tint = CyanUi,
                            )
                        }
                        Slider(
                            value =
                                if (sliderDragging) {
                                    sliderDraft
                                } else {
                                    clampedPos.toFloat()
                                }.coerceIn(0f, durMax),
                            onValueChange = { v ->
                                sliderDragging = true
                                sliderDraft = v.coerceIn(0f, durMax)
                            },
                            onValueChangeFinished = {
                                sliderDragging = false
                                viewModel.seekSavedTakePreview(sliderDraft.toLong())
                            },
                            modifier = Modifier.weight(1f),
                            valueRange = 0f..durMax,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = CyanUi,
                                    activeTrackColor = CyanUi,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                        )
                        Text(
                            text =
                                "${formatRecordTime(if (sliderDragging) sliderDraft.toLong() else savedTakePosMs)} / " +
                                    formatRecordTime(savedTakeDurMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    Button(
                        onClick = { viewModel.saveRecordingToMusicWithBaseName(recordingName) },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = CyanUi,
                                contentColor = DarkOnCyan,
                            ),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.record_save))
                    }
                    OutlinedButton(
                        onClick = { deleteDialog = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, RecRed),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = RecRed,
                            ),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.record_delete))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!isSaved) {
            Text(
                text = formattedDuration,
                color = CyanUi,
                fontSize = 22.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = toggleRecording,
                modifier = Modifier.size(72.dp),
                enabled = !isBusy,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecRed),
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (headsetDialog) {
        AlertDialog(
            onDismissRequest = { headsetDialog = false },
            title = { Text(stringResource(R.string.record_headset_title), color = Color.White) },
            text = {
                Text(stringResource(R.string.record_headset_body), color = Color.White.copy(alpha = 0.9f))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        headsetDialog = false
                        skipHeadsetAdvice = true
                        viewModel.confirmStartRecording()
                    },
                ) {
                    Text(stringResource(R.string.record_headset_continue), color = CyanUi)
                }
            },
            dismissButton = {
                TextButton(onClick = { headsetDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1F26),
        )
    }

    if (deleteDialog) {
        val displayName =
            recordingName.trim().ifBlank {
                savedRecording?.file?.nameWithoutExtension ?: ""
            }
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            text = {
                Text(
                    stringResource(R.string.record_delete_confirm, displayName),
                    color = Color.White,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialog = false
                        viewModel.deleteSavedRecording()
                    },
                ) {
                    Text(stringResource(R.string.record_delete_delete), color = RecRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1F26),
        )
    }
}

private fun beatOutputIcon(type: Int): ImageVector =
    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
        Icons.Default.VolumeUp
    } else {
        Icons.Default.Headset
    }

private fun hasAudioPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun tryStartRecording(
    skipHeadsetAdvice: Boolean,
    viewModel: RecordViewModel,
    showHeadsetDialog: () -> Unit,
) {
    if (viewModel.shouldAdviceHeadphonesForFreestyle() && !skipHeadsetAdvice) {
        showHeadsetDialog()
    } else {
        viewModel.confirmStartRecording()
    }
}

private fun formatRecordTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
}
