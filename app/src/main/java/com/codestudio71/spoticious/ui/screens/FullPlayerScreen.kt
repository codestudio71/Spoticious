package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink

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
    val selectedUri by viewModel.selectedUri.collectAsState()
    var extraExpanded by remember { mutableStateOf(false) }
    var masterDataEnabled by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerRemaining by viewModel.sleepTimerRemainingMinutes.collectAsState()

    LaunchedEffect(selectedUri) { masterDataEnabled = false }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF003344),
                        Color(0xFF2D0050)
                    )
                )
            )
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
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MiamiCyan.copy(alpha = 0.3f), MiamiPink.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MiamiCyan.copy(alpha = 0.8f),
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = fileName ?: stringResource(R.string.track_default),
                color = Color.White,
                fontSize = 22.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1000,
                        initialDelayMillis = 2000,
                        velocity = 50.dp
                    )
            )

            Spacer(modifier = Modifier.height(28.dp))

            val durationMs = duration.coerceAtLeast(1L)
            val progress = (currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)

            CustomSeekBar(
                progress = progress,
                onSeek = { viewModel.seekTo((it * durationMs).toLong()) },
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.7f),
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
                        tint = if (shuffleEnabled) MiamiPink else Color.White.copy(alpha = 0.6f),
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
                        tint = Color.White,
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
                        tint = MiamiPink,
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
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (repeatMode != com.google.android.exoplayer2.Player.REPEAT_MODE_OFF) MiamiPink else Color.White.copy(alpha = 0.6f),
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
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (sleepTimerRemaining != null) MiamiCyan else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        if (sleepTimerRemaining != null) {
                            Text(
                                text = stringResource(R.string.min_compact, sleepTimerRemaining!!),
                                color = MiamiCyan,
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1A1F26),
                        RoundedCornerShape(12.dp)
                    )
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
                            color = MiamiCyan,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (extraExpanded) "▲" else "▼",
                            color = Color.White.copy(alpha = 0.6f),
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
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1A1F26),
                        RoundedCornerShape(12.dp)
                    )
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
                        Text(
                            text = stringResource(R.string.master_data_title),
                            color = MiamiCyan,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (masterDataEnabled) stringResource(R.string.eq_on) else stringResource(R.string.eq_off),
                                color = if (masterDataEnabled) MiamiCyan else Color.Gray,
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
                                    checkedThumbColor = Color(0xFF1A1F26),
                                    checkedTrackColor = MiamiCyan,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
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
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MiamiCyan,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        } else {
                            masterData?.let { md ->
                                MasterDataTable(md = md)
                            } ?: run {
                                Text(
                                    text = masterDataError ?: stringResource(R.string.master_data_no_data),
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
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
    val gradientBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF003344), Color(0xFF2D0050))
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                color = MiamiCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                TextButton(
                    onClick = { onSetTimer(mins) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.min_remaining, mins),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.custom_minutes), color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MiamiCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = MiamiCyan,
                        focusedLabelColor = MiamiCyan,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val m = customMinutes.toIntOrNull()?.coerceIn(1, 600)
                    if (m != null) onSetTimer(m)
                }) {
                    Text(stringResource(R.string.save), color = MiamiCyan)
                }
            }

            if (currentRemaining != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.disable),
                        color = MiamiPink,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun MasterDataTable(md: com.codestudio71.spoticious.player.MasterData) {
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
        else -> Color.White
    }

    fun clipsColor(clips: Int) = if (clips > 0) Color(0xFFE53935) else Color.White

    fun lufsIColor(lufsI: Double) = if (lufsI in -16.0..-9.0) Color(0xFF4CAF50) else Color.White

    fun lraColor(lra: Double) = if (lra < 3.0) Color(0xFFFF9800) else Color.White

    val valueColors = listOf(
        peakColor(md.peakDb),
        clipsColor(md.clips),
        Color.White,
        Color.White,
        lufsIColor(md.lufsI),
        lraColor(md.lra)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1F26))
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
                    color = MiamiCyan,
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
                .background(Color(0xFF2A2F36))
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
    }
}

@Composable
private fun CustomSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val progressCoerced = progress.coerceIn(0f, 1f)
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = dragProgress ?: progressCoerced
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    dragProgress = fraction
                    onSeek(fraction)
                    scope.launch {
                        delay(200)
                        dragProgress = null
                    }
                }
            }
            .pointerInput(progressCoerced) {
                detectHorizontalDragGestures(
                    onDragStart = { dragProgress = progressCoerced },
                    onDragEnd = {
                        dragProgress?.let { onSeek(it) }
                        dragProgress = null
                    },
                    onDragCancel = { dragProgress = null },
                    onHorizontalDrag = { _, dragAmount ->
                        val newProgress = ((dragProgress ?: progressCoerced) + dragAmount / size.width.toFloat())
                            .coerceIn(0f, 1f)
                        dragProgress = newProgress
                        onSeek(newProgress)
                    }
                )
            }
    ) {
        val width = maxWidth
        val trackHeight = 10.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.CenterStart)
                .background(MiamiCyan.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayedProgress)
                    .height(trackHeight)
                    .align(Alignment.CenterStart)
                    .background(MiamiCyan, RoundedCornerShape(2.dp))
            )
        }
        val thumbSize = 52.dp
        val thumbRadius = thumbSize / 2
        val thumbCenter = (width * displayedProgress).coerceIn(thumbRadius, width - thumbRadius)
        Icon(
            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = null,
            tint = MiamiPink,
            modifier = Modifier
                .size(52.dp)
                .align(Alignment.CenterStart)
                .offset(x = thumbCenter - thumbRadius)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val glowColor = Color(0xFFFF4DB8)
                    // neon glow — warstwy symulują blur
                    drawCircle(glowColor.copy(alpha = 0.15f), radius = size.minDimension / 2.2f + 12.dp.toPx(), center = center)
                    drawCircle(glowColor.copy(alpha = 0.25f), radius = size.minDimension / 2.2f + 6.dp.toPx(), center = center)
                    drawCircle(glowColor.copy(alpha = 0.4f), radius = size.minDimension / 2.2f, center = center)
                }
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
