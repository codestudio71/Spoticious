package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.OutputFormat
import com.codestudio71.spoticious.player.RenderState
import com.codestudio71.spoticious.player.RenderViewModel
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook

@Composable
fun RenderScreen(
    modifier: Modifier = Modifier,
    viewModel: RenderViewModel,
    playerViewModel: com.codestudio71.spoticious.player.PlayerViewModel
) {
    val look = LocalSpoticiousLook.current
    val state by viewModel.state.collectAsState()
    val selectedUri by playerViewModel.selectedUri.collectAsState()
    val eqBandGains by playerViewModel.eqBandGains.collectAsState()
    val eqPreampDb by playerViewModel.eqPreampDb.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is RenderState.Idle -> IdleContent(
                selectedUri = selectedUri,
                onExportAac = {
                    selectedUri?.let { viewModel.onExportAacClicked(it, eqBandGains, eqPreampDb) }
                },
                onExportWav = {
                    selectedUri?.let { viewModel.onExportWavClicked(it, eqBandGains, eqPreampDb) }
                },
                buttonsEnabled = true
            )
            is RenderState.ShowAacDialog -> {
                AacDialog(
                    defaultName = s.defaultName,
                    aacSampleRate = s.aacSampleRate,
                    onAacSampleRateChange = { viewModel.onAacSampleRateSelected(it) },
                    onDismiss = { viewModel.resetToIdle() },
                    onRender = { name, aacSr -> viewModel.startRender(name, OutputFormat.AAC, aacSampleRate = aacSr) },
                )
            }
            is RenderState.ShowWavDialog -> {
                WavDialog(
                    defaultName = s.defaultName,
                    sampleRate = s.sampleRate,
                    onSampleRateChange = { viewModel.onWavSampleRateSelected(it) },
                    onDismiss = { viewModel.resetToIdle() },
                    onRender = { name, sr -> viewModel.startRender(name, OutputFormat.WAV, sr) },
                )
            }
            is RenderState.Rendering -> RenderingContent(
                progress = s.progress,
                outputName = s.outputName,
                phase = s.phase
            )
            is RenderState.Done -> DoneContent(
                outputName = s.outputName,
                outputFormat = s.outputFormat,
                onExportAnother = { viewModel.resetToIdle() }
            )
            is RenderState.Error -> ErrorContent(
                message = s.message,
                onRetry = { viewModel.resetToIdle() }
            )
        }
    }

}

@Composable
private fun IdleContent(
    selectedUri: android.net.Uri?,
    onExportAac: () -> Unit,
    onExportWav: () -> Unit,
    buttonsEnabled: Boolean = true
) {
    val look = LocalSpoticiousLook.current
    Text(
        text = stringResource(R.string.export_with_eq),
        color = look.textPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (selectedUri != null) stringResource(R.string.current_track_with_eq) else stringResource(R.string.select_track_in_tracks),
        color = look.textMuted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onExportAac,
        enabled = selectedUri != null && buttonsEnabled,
        colors = ButtonDefaults.buttonColors(containerColor = look.accentAlt, contentColor = look.onAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.export_as_aac))
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onExportWav,
        enabled = selectedUri != null && buttonsEnabled,
        colors = ButtonDefaults.buttonColors(containerColor = look.accent, contentColor = look.onAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.export_as_wav))
    }
}

@Composable
private fun AacDialog(
    defaultName: String,
    aacSampleRate: Int,
    onAacSampleRateChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRender: (String, Int) -> Unit
) {
    val look = LocalSpoticiousLook.current
    var customName by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = look.dialogFill,
        title = {
            Text(stringResource(R.string.file_name), color = look.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(stringResource(R.string.name_label), color = look.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = look.textPrimary,
                            unfocusedTextColor = look.textPrimary,
                            focusedBorderColor = look.accent,
                            unfocusedBorderColor = look.textMuted,
                            cursorColor = look.accent,
                            focusedLabelColor = look.accent,
                            unfocusedLabelColor = look.textMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(".m4a", color = look.textMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.sample_rate), color = look.textPrimary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = aacSampleRate == 0,
                        onClick = { onAacSampleRateChange(0) },
                        colors = RadioButtonDefaults.colors(selectedColor = look.accent)
                    )
                    Text(stringResource(R.string.from_original), color = look.textPrimary, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = aacSampleRate == 44100,
                        onClick = { onAacSampleRateChange(44100) },
                        colors = RadioButtonDefaults.colors(selectedColor = look.accent)
                    )
                    Text(stringResource(R.string.hz_44_1), color = look.textPrimary, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = aacSampleRate == 48000,
                        onClick = { onAacSampleRateChange(48000) },
                        colors = RadioButtonDefaults.colors(selectedColor = look.accent)
                    )
                    Text(stringResource(R.string.hz_48), color = look.textPrimary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.format_aac),
                    style = MaterialTheme.typography.bodySmall,
                    color = look.textMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRender(customName.ifBlank { defaultName }, aacSampleRate) },
                colors = ButtonDefaults.buttonColors(containerColor = look.accentAlt, contentColor = look.onAccent)
            ) {
                Text(stringResource(R.string.render_button))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = look.textMuted),
                border = BorderStroke(1.dp, look.textMuted)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun WavDialog(
    defaultName: String,
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRender: (String, Int) -> Unit
) {
    val look = LocalSpoticiousLook.current
    var customName by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = look.dialogFill,
        title = {
            Text(stringResource(R.string.file_name), color = look.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(stringResource(R.string.name_label), color = look.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = look.textPrimary,
                            unfocusedTextColor = look.textPrimary,
                            focusedBorderColor = look.accent,
                            unfocusedBorderColor = look.textMuted,
                            cursorColor = look.accent,
                            focusedLabelColor = look.accent,
                            unfocusedLabelColor = look.textMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(".wav", color = look.textMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.sample_rate), color = look.textPrimary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = sampleRate == 44100,
                        onClick = { onSampleRateChange(44100) },
                        colors = RadioButtonDefaults.colors(selectedColor = look.accent)
                    )
                    Text(stringResource(R.string.hz_44100), color = look.textPrimary, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = sampleRate == 48000,
                        onClick = { onSampleRateChange(48000) },
                        colors = RadioButtonDefaults.colors(selectedColor = look.accent)
                    )
                    Text(stringResource(R.string.hz_48000), color = look.textPrimary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.format_wav),
                    style = MaterialTheme.typography.bodySmall,
                    color = look.textMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRender(customName.ifBlank { defaultName }, sampleRate) },
                colors = ButtonDefaults.buttonColors(containerColor = look.accentAlt, contentColor = look.onAccent)
            ) {
                Text(stringResource(R.string.render_button))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = look.textMuted),
                border = BorderStroke(1.dp, look.textMuted)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RenderingContent(
    progress: Float,
    outputName: String,
    phase: String
) {
    val look = LocalSpoticiousLook.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.rendering), color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = look.accent,
            trackColor = look.inactiveTrack
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${(progress * 100).toInt()}% · $phase",
            color = look.accent,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = outputName,
            color = look.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.render_wait_large_files),
            color = look.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DoneContent(
    outputName: String,
    outputFormat: OutputFormat,
    onExportAnother: () -> Unit
) {
    val look = LocalSpoticiousLook.current
    val ext = when (outputFormat) {
        OutputFormat.AAC -> ".m4a"
        OutputFormat.WAV -> ".wav"
    }
    Text(
        text = "✓",
        color = look.accent,
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.saved), color = look.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("$outputName$ext", color = look.accent, fontSize = 14.sp)
    Text(stringResource(R.string.music_rendered), color = look.textMuted, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onExportAnother,
        colors = ButtonDefaults.buttonColors(containerColor = look.accentAlt, contentColor = look.onAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.export_another))
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    val look = LocalSpoticiousLook.current
    Text(
        text = "✗",
        color = Color.Red,
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.export_error), color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(message, color = look.textMuted, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(containerColor = look.accentAlt, contentColor = look.onAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.try_again))
    }
}
