package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.ContentUris
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.OptIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.PlayerViewModel
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.appVerticalGradient
import com.google.android.exoplayer2.Player

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName by viewModel.fileName.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val masterData by viewModel.masterData.collectAsState()
    val masterDataLoading by viewModel.masterDataLoading.collectAsState()
    val masterDataError by viewModel.masterDataError.collectAsState()
    val masterDataProgress by viewModel.masterDataProgress.collectAsState()
    val selectedUri by viewModel.selectedUri.collectAsState()
    var extraExpanded by remember { mutableStateOf(false) }
    val masterDataSaveKey = remember(selectedUri) {
        selectedUri?.let { u ->
            runCatching { "id:${ContentUris.parseId(u)}" }.getOrElse { u.toString() }
        }.orEmpty()
    }
    var masterDataEnabled by rememberSaveable(masterDataSaveKey) { mutableStateOf(false) }
    var glossaryDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerRemaining by viewModel.sleepTimerRemainingMinutes.collectAsState()
    val look = LocalSpoticiousLook.current

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentRemaining = sleepTimerRemaining,
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onDisable = {
                viewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            }
        )
    }

    if (glossaryDialog) {
        AlertDialog(
            onDismissRequest = { glossaryDialog = false },
            containerColor = look.dialogFill,
            title = {
                Text(
                    text = stringResource(R.string.master_data_title),
                    color = look.accent,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_peak))
                    Spacer(modifier = Modifier.height(10.dp))
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_clips))
                    Spacer(modifier = Modifier.height(10.dp))
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_lufs_m))
                    Spacer(modifier = Modifier.height(10.dp))
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_lufs_s))
                    Spacer(modifier = Modifier.height(10.dp))
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_lufs_i))
                    Spacer(modifier = Modifier.height(10.dp))
                    MasterDataGlossaryEntry(stringResource(R.string.master_data_glossary_lra))
                }
            },
            confirmButton = {
                TextButton(onClick = { glossaryDialog = false }) {
                    Text("OK", color = look.accent)
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appVerticalGradient())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.close),
                    tint = look.textPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(look.accent.copy(alpha = 0.3f), look.accentAlt.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = look.accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = fileName ?: stringResource(R.string.track_default),
                color = look.textPrimary,
                fontSize = 22.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 6000,
                        initialDelayMillis = 2000,
                        velocity = 50.dp
                    )
            )

            Spacer(modifier = Modifier.height(28.dp))

            val durationMs = duration.coerceAtLeast(1L)
            val progress = if (duration > 0 && currentPosition >= duration - 300L) {
                1f
            } else {
                (currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)
            }

            CustomSeekBar(
                progress = progress,
                onSeek = { viewModel.seekTo((it * durationMs).toLong()) },
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = look.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = formatTime(duration),
                    color = look.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
            val repeatMode by viewModel.repeatMode.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (shuffleEnabled) look.accentAlt else look.textMuted,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        tint = look.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = look.accentAlt,
                        modifier = Modifier.size(64.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        tint = look.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(48.dp)
                ) {
                    val repeatCd = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> R.string.repeat_off
                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                        else -> R.string.repeat_off
                    }
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = stringResource(repeatCd),
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> look.textMuted
                            Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE -> look.accent
                            else -> look.textMuted
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .widthIn(min = 40.dp, max = 48.dp)
                        .clickable { showSleepTimerDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (sleepTimerRemaining != null) look.accent else look.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        if (sleepTimerRemaining != null) {
                            Text(
                                text = stringResource(R.string.min_compact, sleepTimerRemaining!!),
                                color = look.accent,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiamiFrame(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { extraExpanded = !extraExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.full_player_section_extra),
                            color = look.accent,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (extraExpanded) "▲" else "▼",
                            color = look.textMuted,
                            fontSize = 10.sp
                        )
                    }

                    if (extraExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = metadata?.let { buildMetadataText(it) } ?: "—",
                                color = look.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            MiamiFrame(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.master_data_title),
                                color = look.accent,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = { glossaryDialog = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.master_data_glossary_title),
                                    tint = look.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (masterDataEnabled) stringResource(R.string.eq_on) else stringResource(R.string.eq_off),
                                color = if (masterDataEnabled) look.accent else look.textMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = masterDataEnabled,
                                onCheckedChange = {
                                    masterDataEnabled = it
                                    if (it && masterData == null && !masterDataLoading) {
                                        viewModel.loadMasterData()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = look.onAccent,
                                    checkedTrackColor = look.accent,
                                    uncheckedThumbColor = look.textMuted,
                                    uncheckedTrackColor = look.inactiveTrack
                                )
                            )
                        }
                    }

                    if (masterDataEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (masterDataLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.master_data_analyzing),
                                    color = look.textMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { masterDataProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = look.accent,
                                    trackColor = look.inactiveTrack
                                )
                            }
                        } else {
                            masterData?.let { md ->
                                MasterDataTable(md = md)
                            } ?: run {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = masterDataError
                                            ?: stringResource(R.string.master_data_no_data),
                                        color = look.textMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (masterDataError != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(onClick = { viewModel.loadMasterData() }) {
                                            Text(
                                                text = stringResource(R.string.try_again),
                                                color = look.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentRemaining: Int?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onDisable: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }
    val look = LocalSpoticiousLook.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(look.verticalGradient(), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                color = look.accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                TextButton(
                    onClick = { onSetTimer(mins) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.min_remaining, mins),
                        color = look.textPrimary,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = customMinutes,
                onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(4) },
                label = {
                    Text(
                        text = stringResource(R.string.custom_minutes),
                        color = look.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = look.textPrimary,
                    textAlign = TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = look.textPrimary,
                    unfocusedTextColor = look.textPrimary,
                    focusedBorderColor = look.accent,
                    unfocusedBorderColor = look.textMuted,
                    cursorColor = look.accent,
                    focusedLabelColor = look.accent,
                    unfocusedLabelColor = look.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (currentRemaining != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.disable),
                        color = look.accentAlt,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = look.textSecondary)
                }
                TextButton(onClick = {
                    val m = customMinutes.toIntOrNull()?.coerceIn(1, 600)
                    if (m != null) onSetTimer(m)
                }) {
                    Text(stringResource(R.string.save), color = look.accent)
                }
            }
        }
    }
}

@Composable
private fun MasterDataTable(md: com.codestudio71.spoticious.player.MasterData) {
    val look = LocalSpoticiousLook.current
    val headers = listOf(
        stringResource(R.string.master_data_header_peak),
        stringResource(R.string.master_data_header_clips),
        stringResource(R.string.master_data_header_lufs_m),
        stringResource(R.string.master_data_header_lufs_s),
        stringResource(R.string.master_data_header_lufs_i),
        stringResource(R.string.master_data_header_lra)
    )
    val values = listOf(
        md.formatPeak(),
        md.formatClips(),
        md.formatLufsM(),
        md.formatLufsS(),
        md.formatLufsI(),
        md.formatLra()
    )

    fun peakColor(peakDb: Double) = when {
        peakDb >= 0.0 -> Color(0xFFE53935)
        peakDb >= -3.0 -> Color(0xFFFF9800)
        else -> look.textPrimary
    }

    fun clipsColor(clips: Int, reliable: Boolean) =
        if (!reliable) look.textPrimary else if (clips > 0) Color(0xFFE53935) else look.textPrimary

    fun lufsIColor(lufsI: Double) = if (lufsI in -16.0..-9.0) Color(0xFF4CAF50) else look.textPrimary

    fun lraColor(lra: Double) = if (lra < 3.0) Color(0xFFFF9800) else look.textPrimary

    val valueColors = listOf(
        peakColor(md.peakDb),
        clipsColor(md.clips, md.clipsReliable),
        look.textPrimary,
        look.textPrimary,
        lufsIColor(md.lufsI),
        lraColor(md.lra)
    )

    MiamiFrame(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
    ) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            headers.forEach { h ->
                Text(
                    text = h,
                    color = look.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(look.accent.copy(alpha = 0.25f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            values.forEachIndexed { i, v ->
                Text(
                    text = v,
                    color = valueColors[i],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (!md.clipsReliable) {
            Text(
                text = stringResource(R.string.master_data_clips_unavailable),
                color = look.textMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    }
}

@Composable
private fun CustomSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val look = LocalSpoticiousLook.current
    val progressCoerced = progress.coerceIn(0f, 1f)
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = dragProgress ?: progressCoerced
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width.toFloat().coerceAtLeast(1f)
                fun fraction(px: Float) = (px / w).coerceIn(0f, 1f)

                val downPos = down.position
                val startFrac = fraction(downPos.x)
                dragProgress = startFrac
                var dragging = false
                var lastFrac = startFrac
                val slop = 4.dp.toPx()

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                    if (!change.pressed) {
                        onSeek(lastFrac)
                        scope.launch {
                            delay(200)
                            dragProgress = null
                        }
                        change.consume()
                        break
                    }

                    val dist = hypot(
                        change.position.x - downPos.x,
                        change.position.y - downPos.y
                    )
                    if (!dragging && dist > slop) {
                        dragging = true
                    }

                    val newFrac = fraction(change.position.x)
                    lastFrac = newFrac
                    dragProgress = newFrac
                    if (dragging) {
                        onSeek(newFrac)
                    }
                    change.consume()
                }
            }
        }
    ) {
        val width = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val trackHeight = 10.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .align(Alignment.Center)
                    .background(look.inactiveTrack.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedProgress)
                        .height(trackHeight)
                        .align(Alignment.CenterStart)
                        .background(look.seekBrush(), RoundedCornerShape(2.dp))
                )
            }
            val thumbSize = 52.dp
            val thumbRadius = thumbSize / 2
            val thumbCenter = (width * displayedProgress).coerceIn(0.dp, width)
            val glowColor = look.accentAlt
            Icon(
                imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                tint = look.accentAlt,
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = thumbCenter - thumbRadius)
                    .drawBehind {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(glowColor.copy(alpha = 0.15f), radius = size.minDimension / 2.2f + 12.dp.toPx(), center = center)
                        drawCircle(glowColor.copy(alpha = 0.25f), radius = size.minDimension / 2.2f + 6.dp.toPx(), center = center)
                        drawCircle(glowColor.copy(alpha = 0.4f), radius = size.minDimension / 2.2f, center = center)
                    }
            )
        }
    }
}

@Composable
private fun MasterDataGlossaryEntry(text: String) {
    val look = LocalSpoticiousLook.current
    val separator = " — "
    val splitIndex = text.indexOf(separator)
    if (splitIndex >= 0) {
        Text(
            text = text.substring(0, splitIndex),
            color = look.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            text = text.substring(splitIndex + separator.length),
            color = look.textSecondary,
            fontSize = 13.sp,
        )
    } else {
        Text(
            text = text,
            color = look.textSecondary,
            fontSize = 13.sp,
        )
    }
}

private fun buildMetadataText(m: com.codestudio71.spoticious.player.AudioMetadata): String {
    val parts = mutableListOf<String>()
    parts.add(m.format)
    m.bitrateKbps?.let { parts.add("$it kbps") }
    m.sampleRateHz?.let { parts.add("$it Hz") }
    m.bitDepth?.let { parts.add("$it-bit") }
    return parts.joinToString("  •  ")
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
