package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onTapWhenEmpty: () -> Unit = {},
    onTapWhenPlaying: () -> Unit = {}
) {
    val selectedUri by viewModel.selectedUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val hasTrack = selectedUri != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (hasTrack) onTapWhenPlaying() else onTapWhenEmpty()
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1F26),
                        Color(0xFF151A22)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MiamiCyan,
                    modifier = Modifier.size(28.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fileName ?: stringResource(R.string.mini_player_empty),
                        color = if (hasTrack) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (hasTrack) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = MiamiPink
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MiamiPink.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            if (hasTrack && duration > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                ProgressBar(
                    currentPositionMs = currentPosition,
                    durationMs = duration,
                    onSeek = { viewModel.seekTo(it) }
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)

    Slider(
        value = progress,
        onValueChange = { onSeek((it * duration).toLong()) },
        modifier = modifier
            .height(20.dp)
            .padding(horizontal = 0.dp),
        colors = SliderDefaults.colors(
            thumbColor = MiamiCyan,
            activeTrackColor = MiamiCyan,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        )
    )
}
