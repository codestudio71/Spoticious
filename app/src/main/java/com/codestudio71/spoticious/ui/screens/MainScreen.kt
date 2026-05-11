package com.codestudio71.spoticious.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.folders.FoldersViewModel
import com.codestudio71.spoticious.folders.TrackSortOrder
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.player.RenderViewModel
import com.codestudio71.spoticious.ui.components.MiniPlayer
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink

enum class Tab(
    val titleResId: Int,
    val icon: ImageVector
) {
    UTWORY(R.string.tracks, Icons.Default.PlayArrow),
    FOLDERY(R.string.folders, Icons.Default.Folder),
    EQ(R.string.eq, Icons.Default.GraphicEq),
    RENDER(R.string.render, Icons.Default.Save)
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(Tab.UTWORY) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showExtraScreen by remember { mutableStateOf(false) }
    var showPlaylistScreen by remember { mutableStateOf(false) }
    var showRecordPreview by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(TrackSortOrder.NAME_ASC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val playerViewModel: PlayerViewModel = viewModel()
    val foldersViewModel: FoldersViewModel = viewModel()

    val openFullPlayer: () -> Unit = {
        Log.d("R3Trace", "MainScreen setShowFullPlayer(true): selectedUri=${playerViewModel.selectedUri.value}")
        showFullPlayer = true
    }

    LaunchedEffect(sortMode) {
        foldersViewModel.setSortOrder(sortMode)
    }

    LaunchedEffect(Unit) {
        foldersViewModel.loadAudioFilesIfPermitted()
    }

    BackHandler(enabled = showRecordPreview && showExtraScreen) {
        showRecordPreview = false
    }
    BackHandler(enabled = showExtraScreen && !showRecordPreview) { showExtraScreen = false }
    BackHandler(enabled = searchExpanded && !showExtraScreen) {
        searchExpanded = false
        searchQuery = ""
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MainTopBar(
                    selectedTab = selectedTab,
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchClick = {
                        if (selectedTab == Tab.UTWORY || selectedTab == Tab.FOLDERY) {
                            searchExpanded = true
                        }
                    },
                    onSearchClose = {
                        searchExpanded = false
                        searchQuery = ""
                    },
                    onExtraClick = {
                        showRecordPreview = false
                        showPlaylistScreen = false
                        showExtraScreen = true
                    },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChange = { sortMenuExpanded = it }
                )
            },
            bottomBar = {
            Column {
                MiniPlayer(
                    viewModel = playerViewModel,
                    onTapWhenEmpty = { selectedTab = Tab.UTWORY },
                    onTapWhenPlaying = openFullPlayer
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Tab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val selectedColor = when (tab) {
                            Tab.UTWORY -> Color(0xFF00E5FF)
                            Tab.FOLDERY -> Color(0xFFFFFFFF)
                            Tab.EQ -> Color(0xFFFFFFFF)
                            Tab.RENDER -> Color(0xFFFF4DB8)
                        }
                        val unselectedColor = when (tab) {
                            Tab.UTWORY -> Color(0x4D00E5FF)
                            Tab.FOLDERY -> Color(0xFF666666)
                            Tab.EQ -> Color(0xFF666666)
                            Tab.RENDER -> Color(0x4DFF4DB8)
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                showExtraScreen = false
                                showRecordPreview = false
                                selectedTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.titleResId),
                                    tint = if (isSelected) selectedColor else unselectedColor
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.titleResId),
                                    color = if (isSelected) selectedColor else unselectedColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surface,
                                selectedIconColor = selectedColor,
                                selectedTextColor = selectedColor,
                                unselectedIconColor = unselectedColor,
                                unselectedTextColor = unselectedColor
                            )
                        )
                    }
                }
            }
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showExtraScreen) {
                when {
                    showRecordPreview ->
                        RecordPreviewScreen(
                            onBack = { showRecordPreview = false },
                        )

                    showPlaylistScreen ->
                        PlaylistScreen(
                            viewModel = playerViewModel,
                            onBack = { showPlaylistScreen = false },
                        )

                    else ->
                        ExtraScreen(
                            onBack = { showExtraScreen = false },
                            onPlaylistClick = {
                                showRecordPreview = false
                                showPlaylistScreen = true
                            },
                            onRecordPreviewClick = {
                                showPlaylistScreen = false
                                showRecordPreview = true
                            },
                        )
                }
            } else {
                when (selectedTab) {
                    Tab.UTWORY -> TracksScreen(
                        modifier = Modifier.fillMaxSize(),
                        playerViewModel = playerViewModel,
                        foldersViewModel = foldersViewModel,
                        searchQuery = searchQuery,
                        sortMode = sortMode,
                        onOpenFullPlayer = openFullPlayer
                    )
                    Tab.FOLDERY -> FoldersScreen(
                        modifier = Modifier.fillMaxSize(),
                        foldersViewModel = foldersViewModel,
                        playerViewModel = playerViewModel,
                        searchQuery = searchQuery,
                        sortMode = sortMode
                    )
                    Tab.EQ -> EqScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = playerViewModel
                    )
                    Tab.RENDER -> RenderScreen(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel<RenderViewModel>(),
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }

        if (showFullPlayer) {
            BackHandler { showFullPlayer = false }
            FullPlayerScreen(
                viewModel = playerViewModel,
                onBack = { showFullPlayer = false }
            )
        }
    }
}

@Composable
private fun MainTopBar(
    selectedTab: Tab,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onExtraClick: () -> Unit,
    sortMode: TrackSortOrder,
    onSortModeChange: (TrackSortOrder) -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit
) {
    val searchActive = selectedTab == Tab.UTWORY || selectedTab == Tab.FOLDERY

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (!searchExpanded && searchActive) Modifier.clickable(onClick = onSearchClick)
                        else Modifier
                    )
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = !searchExpanded && searchActive) {
                    Text(
                        text = stringResource(R.string.search),
                        color = Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                androidx.compose.animation.AnimatedVisibility(visible = searchExpanded && searchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .background(
                                Color(0xFF00E5FF).copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            cursorBrush = SolidColor(MiamiCyan),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_placeholder),
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = onSearchClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            if (searchActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    IconButton(
                        onClick = { onSortMenuExpandedChange(true) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFF0066FF), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFF00AACC), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFF00E5FF), CircleShape)
                            )
                        }
                    }
                    DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuExpandedChange(false) },
                    modifier = Modifier.background(Color(0xFF1A1F26))
                ) {
                    Text(
                        text = stringResource(R.string.sort_by),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    TrackSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = when (order) {
                                        TrackSortOrder.NAME_ASC -> stringResource(R.string.sort_title_asc)
                                        TrackSortOrder.NAME_DESC -> stringResource(R.string.sort_title_desc)
                                        TrackSortOrder.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
                                        TrackSortOrder.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
                                        TrackSortOrder.DATE_ADDED_ASC -> stringResource(R.string.sort_date_asc)
                                        TrackSortOrder.DATE_ADDED_DESC -> stringResource(R.string.sort_date_desc)
                                    },
                                    color = if (sortMode == order) MiamiCyan else Color.White
                                )
                            },
                            onClick = {
                                onSortModeChange(order)
                                onSortMenuExpandedChange(false)
                            }
                        )
                    }
                }
                }
            }
        }

        Box(
            modifier = Modifier
                .clickable(onClick = onExtraClick)
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFFF4DB8),
                    shape = RoundedCornerShape(4.dp)
                )
                .drawBehind {
                    val glowColor = Color(0xFFFF4DB8)
                    val cornerRadius = 4.dp.toPx()
                    drawRoundRect(
                        color = glowColor.copy(alpha = 180 / 255f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cornerRadius),
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringResource(R.string.extra),
                color = Color(0xFFFF4DB8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

