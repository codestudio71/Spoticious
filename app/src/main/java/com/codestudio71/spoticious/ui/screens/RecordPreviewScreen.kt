package com.codestudio71.spoticious.ui.screens

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
private val PlaceholderWaveBlue = Color(0xFF2196F3)
private val RecRed = Color(0xFFFF1744)
private val DarkOnCyan = Color(0xFF0D0D1A)
private val AccentDelete = Color(0xFFFF5277)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecordViewModel = viewModel()
    val context = LocalContext.current

    val mode by viewModel.mode.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val beatUri by viewModel.selectedBeatUri.collectAsState()
    val beatName by viewModel.beatLabel.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()
    val pickedLabel by viewModel.pickedInputLabel.collectAsState()

    val micWaveform by viewModel.micWaveform.collectAsState()
    val beatWaveform by viewModel.beatWaveform.collectAsState()
    val micDbfs by viewModel.micDbfs.collectAsState()
    val beatDbfs by viewModel.beatDbfs.collectAsState()
    val micPeakDbfs by viewModel.micPeakDbfs.collectAsState()
    val beatPeakDbfs by viewModel.beatPeakDbfs.collectAsState()

    var deviceMenu by remember { mutableStateOf(false) }
    var headsetDialog by remember { mutableStateOf(false) }
    var skipHeadsetAdvice by remember { mutableStateOf(false) }

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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(BgGrad))
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
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

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (isRecording) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
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
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PlaceholderWaveBlue,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = PlaceholderWaveBlue,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = formattedDuration,
            color = CyanUi,
            fontSize = 22.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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

        if (recState is RecordingState.Saved) {
            val name = (recState as RecordingState.Saved).file.name
            Text(
                name,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveToMediaStoreAndToast() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = CyanUi,
                            contentColor = Color.Black,
                        ),
                    border = null,
                ) {
                    Text(stringResource(R.string.record_save), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        val send = viewModel.shareIntent()
                        if (send != null) {
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = AccentDelete,
                        ),
                    border = BorderStroke(1.dp, AccentDelete),
                ) {
                    Text(stringResource(R.string.record_share), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
