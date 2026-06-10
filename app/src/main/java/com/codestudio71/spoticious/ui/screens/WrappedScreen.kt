package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.data.wrapped.WrappedPeriod
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.wrapped.WrappedViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WrappedViewModel = viewModel(),
) {
    val period by viewModel.period.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topTracksByTime by viewModel.topTracksByTime.collectAsState()
    val topArtistsByTime by viewModel.topArtistsByTime.collectAsState()
    val totalPlays by viewModel.totalPlays.collectAsState()
    val totalTimeMs by viewModel.totalTimeMs.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val isEmpty = !loading && totalPlays == 0

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.wrapped),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WrappedPeriodChip(
                    label = stringResource(R.string.wrapped_period_week),
                    selected = period == WrappedPeriod.LAST_WEEK,
                    onClick = { viewModel.setPeriod(WrappedPeriod.LAST_WEEK) },
                    modifier = Modifier.weight(1f),
                )
                WrappedPeriodChip(
                    label = stringResource(R.string.wrapped_period_month),
                    selected = period == WrappedPeriod.THIS_MONTH,
                    onClick = { viewModel.setPeriod(WrappedPeriod.THIS_MONTH) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WrappedPeriodChip(
                    label = stringResource(R.string.wrapped_period_year),
                    selected = period == WrappedPeriod.THIS_YEAR,
                    onClick = { viewModel.setPeriod(WrappedPeriod.THIS_YEAR) },
                    modifier = Modifier.weight(1f),
                )
                WrappedPeriodChip(
                    label = stringResource(R.string.wrapped_period_all),
                    selected = period == WrappedPeriod.ALL_TIME,
                    onClick = { viewModel.setPeriod(WrappedPeriod.ALL_TIME) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            if (loading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MiamiCyan)
                }
            } else if (isEmpty) {
                Text(
                    text = stringResource(R.string.wrapped_empty),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = stringResource(R.string.wrapped_total_time),
                        value = formatWrappedDuration(totalTimeMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = stringResource(R.string.wrapped_total_plays),
                        value = totalPlays.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(24.dp))

                WrappedRankingSection(
                    title = stringResource(R.string.wrapped_top_tracks),
                    rows =
                        topTracks.map { row ->
                            row.title to stringResource(R.string.wrapped_play_count, row.playCount)
                        },
                )

                Spacer(Modifier.height(20.dp))

                WrappedRankingSection(
                    title = stringResource(R.string.wrapped_top_tracks_time),
                    rows =
                        topTracksByTime.map { row ->
                            row.title to
                                stringResource(
                                    R.string.wrapped_listen_time,
                                    formatWrappedDuration(row.listenedMs),
                                )
                        },
                )

                Spacer(Modifier.height(20.dp))

                WrappedRankingSection(
                    title = stringResource(R.string.wrapped_top_artists),
                    rows =
                        topArtists.map { row ->
                            (row.artist?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.wrapped_unknown_artist)) to
                                stringResource(R.string.wrapped_play_count, row.playCount)
                        },
                )

                Spacer(Modifier.height(20.dp))

                WrappedRankingSection(
                    title = stringResource(R.string.wrapped_top_artists_time),
                    rows =
                        topArtistsByTime.map { row ->
                            (row.artist?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.wrapped_unknown_artist)) to
                                stringResource(
                                    R.string.wrapped_listen_time,
                                    formatWrappedDuration(row.listenedMs),
                                )
                        },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WrappedPeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = Color.White,
                selectedContainerColor = MiamiCyan,
                selectedLabelColor = Color(0xFF0D0D1A),
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = Color.White.copy(alpha = 0.35f),
                selectedBorderColor = Color.Transparent,
                borderWidth = 1.dp,
                selectedBorderWidth = 0.dp,
            ),
    )
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    MiamiFrame(modifier = modifier) {
        Text(title, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WrappedRankingSection(
    title: String,
    rows: List<Pair<String, String>>,
) {
    if (rows.isEmpty()) return
    Text(
        title,
        color = MiamiCyan,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    rows.forEachIndexed { index, (primary, secondary) ->
        RankedRow(
            rank = index + 1,
            primary = primary,
            secondary = secondary,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    primary: String,
    secondary: String,
) {
    MiamiFrame(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank.",
                color = MiamiCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    primary,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(secondary, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

private fun formatWrappedDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
