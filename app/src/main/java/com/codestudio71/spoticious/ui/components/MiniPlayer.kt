package com.codestudio71.spoticious.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onTapWhenEmpty: () -> Unit = {},
    onTapWhenPlaying: () -> Unit = {},
) {
    val look = LocalSpoticiousLook.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val hasTrack = selectedUri != null

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable {
                    if (hasTrack) onTapWhenPlaying() else onTapWhenEmpty()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = look.accent,
                    modifier = Modifier.size(28.dp),
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = fileName ?: stringResource(R.string.mini_player_empty),
                        color =
                            if (hasTrack) {
                                look.textPrimary
                            } else {
                                look.textMuted
                            },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasTrack) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription =
                                if (isPlaying) {
                                    stringResource(R.string.pause)
                                } else {
                                    stringResource(R.string.play)
                                },
                            tint = look.accentAlt,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = look.accentAlt.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            if (hasTrack && duration > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                GradientSeekBarMs(
                    positionMs = currentPosition,
                    durationMs = duration,
                    onSeekMs = { viewModel.seekTo(it) },
                )
            }
        }
    }
}
