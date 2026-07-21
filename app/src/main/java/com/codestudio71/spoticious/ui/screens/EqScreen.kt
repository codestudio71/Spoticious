package com.codestudio71.spoticious.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.EqPreset
import com.codestudio71.spoticious.player.EqPresetImportConflictMode
import com.codestudio71.spoticious.player.EqPresetImportOutcome
import com.codestudio71.spoticious.player.EqPresetSaveOutcome
import com.codestudio71.spoticious.player.EqualizerAudioProcessor
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.theme.EqBackground
import com.codestudio71.spoticious.ui.theme.EqMiamiCyan
import com.codestudio71.spoticious.ui.theme.EqMiamiPink
import com.codestudio71.spoticious.ui.theme.MiamiDialogFill
import com.codestudio71.spoticious.ui.theme.MiamiMenuShape
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import kotlin.OptIn
import kotlinx.coroutines.launch

/** Presety nad suwakami EQ — prawie pełne tło, żeby suwaki nie przeświecały. */
private val EqPresetsMenuFill = Color(0xFF1A0A2E)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EqScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val bandGains by viewModel.eqBandGains.collectAsState()
    val preampDb by viewModel.eqPreampDb.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bandResetInteractionSources = remember {
        List(EqualizerAudioProcessor.BAND_FREQUENCIES_HZ.size) { MutableInteractionSource() }
    }
    var presetsMenuExpanded by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var overwriteName by remember { mutableStateOf<String?>(null) }
    var importPending by remember { mutableStateOf<List<EqPreset>?>(null) }
    var importConflictCount by remember { mutableStateOf(0) }

    fun handleImportResult(r: EqPresetImportOutcome) {
        when (r) {
            is EqPresetImportOutcome.Imported -> {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.eq_preset_import_done,
                        r.added,
                        r.overwritten,
                        r.skipped,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
            is EqPresetImportOutcome.NeedsConflictResolution -> {
                importPending = r.pending
                importConflictCount = r.conflictNames.size
            }
            is EqPresetImportOutcome.Error ->
                Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                handleImportResult(viewModel.importAudaciousPresetsFromUri(uri))
            }
        }

    deleteCandidate?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.eq_preset_delete_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.eq_preset_delete_message, name),
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        scope.launch {
                            viewModel.deletePreset(name)
                        }
                    }
                ) {
                    Text(stringResource(R.string.eq_preset_delete_confirm), color = EqMiamiPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel), color = EqMiamiCyan)
                }
            },
            containerColor = MiamiDialogFill
        )
    }

    overwriteName?.let { pending ->
        AlertDialog(
            onDismissRequest = { overwriteName = null },
            title = { Text(stringResource(R.string.eq_preset_overwrite_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.eq_preset_overwrite_message),
                    color = Color.White.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overwriteName = null
                        scope.launch {
                            when (val r = viewModel.savePreset(pending, overwrite = true)) {
                                is EqPresetSaveOutcome.Saved -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.eq_preset_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    presetNameInput = ""
                                }
                                is EqPresetSaveOutcome.Error ->
                                    Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                else -> {}
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save), color = EqMiamiCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { overwriteName = null }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = MiamiDialogFill
        )
    }

    importPending?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                importPending = null
                importConflictCount = 0
            },
            title = {
                Text(stringResource(R.string.eq_preset_import_conflict_title), color = Color.White)
            },
            text = {
                Text(
                    stringResource(R.string.eq_preset_import_conflict_message, importConflictCount),
                    color = Color.White.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val list = pending
                        importPending = null
                        importConflictCount = 0
                        scope.launch {
                            handleImportResult(
                                viewModel.commitAudaciousPresetImport(
                                    pending = list,
                                    mode = EqPresetImportConflictMode.Overwrite,
                                ),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.eq_preset_import_overwrite), color = EqMiamiCyan)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val list = pending
                            importPending = null
                            importConflictCount = 0
                            scope.launch {
                                handleImportResult(
                                    viewModel.commitAudaciousPresetImport(
                                        pending = list,
                                        mode = EqPresetImportConflictMode.SkipExisting,
                                    ),
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.eq_preset_import_skip), color = EqMiamiCyan)
                    }
                    TextButton(
                        onClick = {
                            importPending = null
                            importConflictCount = 0
                        },
                    ) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }
                }
            },
            containerColor = MiamiDialogFill,
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.eq),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Box {
                    OutlinedButton(
                        onClick = { presetsMenuExpanded = true },
                        border = BorderStroke(1.dp, EqMiamiCyan),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EqMiamiCyan,
                            containerColor = Color.Transparent,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(R.string.eq_presets_content_description),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.eq_presets),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = presetsMenuExpanded,
                        onDismissRequest = { presetsMenuExpanded = false },
                        modifier =
                            Modifier
                                .widthIn(min = 280.dp)
                                .heightIn(max = 480.dp)
                                .clip(MiamiMenuShape)
                                .background(EqPresetsMenuFill, MiamiMenuShape)
                                .border(1.dp, MiamiCyan.copy(alpha = 0.85f), MiamiMenuShape),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .widthIn(min = 264.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = presetNameInput,
                                        onValueChange = { presetNameInput = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        label = {
                                            Text(
                                                stringResource(R.string.eq_preset_name_hint),
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = EqMiamiCyan,
                                            unfocusedBorderColor = Color.Gray,
                                            cursorColor = EqMiamiCyan
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                when (val r = viewModel.savePreset(presetNameInput.trim())) {
                                                    is EqPresetSaveOutcome.Saved -> {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.eq_preset_saved),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        presetNameInput = ""
                                                    }
                                                    EqPresetSaveOutcome.DuplicateRequiresConfirmation -> {
                                                        overwriteName = presetNameInput.trim()
                                                    }
                                                    is EqPresetSaveOutcome.Error ->
                                                        Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            stringResource(R.string.eq_preset_save),
                                            color = EqMiamiCyan,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        presetsMenuExpanded = false
                                        importLauncher.launch(
                                            arrayOf(
                                                "text/*",
                                                "application/octet-stream",
                                                "*/*",
                                            ),
                                        )
                                    },
                                ) {
                                    Text(
                                        stringResource(R.string.eq_preset_import),
                                        color = EqMiamiCyan,
                                        fontSize = 12.sp,
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = Color.White.copy(alpha = 0.15f)
                                )
                                if (presets.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.eq_preset_empty_hint),
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        presets.forEach { preset ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = {
                                                            presetsMenuExpanded = false
                                                            scope.launch {
                                                                viewModel.loadPreset(preset.name)
                                                                Toast.makeText(
                                                                    context,
                                                                    context.getString(
                                                                        R.string.eq_preset_loaded,
                                                                        preset.name
                                                                    ),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        },
                                                        onLongClick = { deleteCandidate = preset.name }
                                                    )
                                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "● ${preset.name}",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1
                                                )
                                                IconButton(onClick = { deleteCandidate = preset.name }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.eq_preset_delete_title),
                                                        tint = EqMiamiPink.copy(alpha = 0.85f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (eqEnabled) stringResource(R.string.eq_on) else stringResource(R.string.eq_off),
                        color = if (eqEnabled) EqMiamiCyan else Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { viewModel.setEqEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EqBackground,
                            checkedTrackColor = EqMiamiCyan,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.eq_pre),
                    color = EqMiamiPink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = preampDb,
                    onValueChange = { viewModel.setEqPreamp(it) },
                    onValueChangeFinished = { viewModel.flushEqPersist() },
                    valueRange = -15f..15f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF4DB8),
                        activeTrackColor = Color(0xFFFF4444),
                        inactiveTrackColor = Color(0xFF662222)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0xFFFF4DB8),
                                    spotColor = Color(0xFFFF4DB8)
                                )
                                .background(
                                    color = Color(0xFFFF4DB8),
                                    shape = CircleShape
                                )
                        )
                    }
                )
                Text(
                    text = formatDb(preampDb),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.width(52.dp)
                )
            }

            EqualizerAudioProcessor.BAND_FREQUENCIES_HZ.forEachIndexed { index, freqHz ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatFreq(freqHz),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = (bandGains.getOrElse(index) { 0f } + 15f) / 30f,
                        onValueChange = { viewModel.setEqBandGain(index, it * 30f - 15f) },
                        onValueChangeFinished = { viewModel.flushEqPersist() },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF4DB8),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF333333)
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        ambientColor = Color(0xFFFF4DB8),
                                        spotColor = Color(0xFFFF4DB8)
                                    )
                                    .background(
                                        color = Color(0xFFFF4DB8),
                                        shape = CircleShape
                                    )
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .combinedClickable(
                                interactionSource = bandResetInteractionSources[index],
                                indication = null,
                                onClick = { },
                                onClickLabel = stringResource(R.string.eq_band_reset),
                                onDoubleClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.resetEqBand(index)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatDb(bandGains.getOrElse(index) { 0f }),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.widthIn(min = 48.dp)
                        )
                    }
                }
            }
        }

            TextButton(
                onClick = { viewModel.resetEq() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.eq_reset),
                    color = EqMiamiCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDb(db: Float): String {
    return when {
        db > 0 -> "+${String.format("%.1f", db)}"
        db < 0 -> String.format("%.1f", db)
        else -> "0.0"
    }
}

private fun formatFreq(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }
}
