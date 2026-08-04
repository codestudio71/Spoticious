package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.data.UiPrefKeys
import com.codestudio71.spoticious.data.uiPreferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.codestudio71.spoticious.folders.FoldersViewModel
import com.codestudio71.spoticious.folders.TrackSortOrder
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.player.RenderViewModel
import com.codestudio71.spoticious.ui.components.MiniPlayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.LookExtraAtmosphere
import com.codestudio71.spoticious.ui.theme.MiamiMenuShape
import com.codestudio71.spoticious.ui.theme.miamiVerticalGradient
import com.codestudio71.spoticious.ui.theme.usesPremiumExtraChrome

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
    val look = LocalSpoticiousLook.current
    var selectedTab by remember { mutableStateOf(Tab.UTWORY) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showExtraScreen by remember { mutableStateOf(false) }
    var showPlaylistScreen by remember { mutableStateOf(false) }
    var showRecordPreview by remember { mutableStateOf(false) }
    var showWrapped by remember { mutableStateOf(false) }
    var showAudioCut by remember { mutableStateOf(false) }
    var showNewLook by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(TrackSortOrder.NAME_ASC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showTrackNumbers by context.uiPreferencesDataStore.data
        .map { prefs -> prefs[UiPrefKeys.SHOW_TRACK_NUMBERS] ?: false }
        .collectAsState(initial = false)
    val lookId by context.uiPreferencesDataStore.data
        .map { prefs ->
            com.codestudio71.spoticious.ui.theme.SpoticiousLookId.fromStorageKey(prefs[UiPrefKeys.APP_LOOK])
        }
        .collectAsState(initial = com.codestudio71.spoticious.ui.theme.SpoticiousLookId.MIAMI)
    val playerViewModel: PlayerViewModel = viewModel()
    val foldersViewModel: FoldersViewModel = viewModel()

    val openFullPlayer: () -> Unit = {
        showFullPlayer = true
    }

    LaunchedEffect(sortMode) {
        foldersViewModel.setSortOrder(sortMode)
    }

    LaunchedEffect(Unit) {
        foldersViewModel.loadAudioFilesIfPermitted()
    }

    val immersiveExtraScreen = showRecordPreview || showAudioCut || showNewLook

    fun clearExtraSubScreens() {
        showRecordPreview = false
        showPlaylistScreen = false
        showWrapped = false
        showAudioCut = false
        showNewLook = false
    }

    fun leaveExtraToTabs() {
        clearExtraSubScreens()
        showExtraScreen = false
    }

    BackHandler(enabled = showNewLook && showExtraScreen) {
        showNewLook = false
    }
    BackHandler(enabled = showRecordPreview && showExtraScreen) {
        showRecordPreview = false
    }
    BackHandler(enabled = showAudioCut && showExtraScreen) {
        showAudioCut = false
    }
    BackHandler(enabled = showWrapped && showExtraScreen) {
        showWrapped = false
    }
    BackHandler(
        enabled =
            showExtraScreen &&
                !showRecordPreview &&
                !showWrapped &&
                !showAudioCut &&
                !showNewLook,
    ) {
        showExtraScreen = false
    }
    BackHandler(enabled = searchExpanded && !showExtraScreen) {
        searchExpanded = false
        searchQuery = ""
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(miamiVerticalGradient()),
    ) {
        // Extra + Extra submenus only — classic Miami gets no overlay
        if (showExtraScreen && look.usesPremiumExtraChrome) {
            LookExtraAtmosphere(modifier = Modifier.fillMaxSize())
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (!(showExtraScreen && immersiveExtraScreen)) {
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
                            clearExtraSubScreens()
                            showExtraScreen = true
                        },
                        sortMode = sortMode,
                        onSortModeChange = { sortMode = it },
                        sortMenuExpanded = sortMenuExpanded,
                        onSortMenuExpandedChange = { sortMenuExpanded = it },
                        showTrackNumbers = showTrackNumbers,
                        onShowTrackNumbersChange = { enabled ->
                            scope.launch {
                                context.uiPreferencesDataStore.edit { prefs ->
                                    prefs[UiPrefKeys.SHOW_TRACK_NUMBERS] = enabled
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (!(showExtraScreen && immersiveExtraScreen)) {
                    Column {
                        MiniPlayer(
                            viewModel = playerViewModel,
                            onTapWhenEmpty = {
                                leaveExtraToTabs()
                                selectedTab = Tab.UTWORY
                            },
                            onTapWhenPlaying = openFullPlayer,
                        )
                        NavigationBar(
                            containerColor = Color.Transparent,
                            contentColor = LocalSpoticiousLook.current.textPrimary,
                        ) {
                            Tab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                val lookNav = LocalSpoticiousLook.current
                                val selectedColor =
                                    when (tab) {
                                        Tab.UTWORY -> look.accent
                                        Tab.FOLDERY -> lookNav.textPrimary
                                        Tab.EQ -> lookNav.textPrimary
                                        Tab.RENDER -> lookNav.accentAlt
                                    }
                                val unselectedColor = selectedColor.copy(alpha = 0.4f)
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        leaveExtraToTabs()
                                        selectedTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.titleResId),
                                            tint =
                                                if (isSelected) {
                                                    selectedColor
                                                } else {
                                                    unselectedColor
                                                },
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.titleResId),
                                            color =
                                                if (isSelected) {
                                                    selectedColor
                                                } else {
                                                    unselectedColor
                                                },
                                            fontSize = 12.sp,
                                            fontWeight =
                                                if (isSelected) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                        )
                                    },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent,
                                            selectedIconColor = selectedColor,
                                            selectedTextColor = selectedColor,
                                            unselectedIconColor = unselectedColor,
                                            unselectedTextColor = unselectedColor,
                                        ),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (showExtraScreen) {
                when {
                    showNewLook ->
                        NewLookScreen(
                            selectedLookId = lookId,
                            onLookSelected = { id ->
                                scope.launch {
                                    context.uiPreferencesDataStore.edit { prefs ->
                                        prefs[UiPrefKeys.APP_LOOK] = id.storageKey
                                    }
                                }
                            },
                            onBack = { showNewLook = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                    showWrapped ->
                        WrappedScreen(
                            onBack = { showWrapped = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                    showRecordPreview ->
                        RecordPreviewScreen(
                            onBack = { showRecordPreview = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                    showAudioCut ->
                        AudioCutScreen(
                            onBack = { showAudioCut = false },
                            modifier = Modifier.fillMaxSize(),
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
                                showWrapped = false
                                showAudioCut = false
                                showNewLook = false
                                showPlaylistScreen = true
                            },
                            onRecordPreviewClick = {
                                showPlaylistScreen = false
                                showWrapped = false
                                showAudioCut = false
                                showNewLook = false
                                showRecordPreview = true
                            },
                            onWrappedClick = {
                                showRecordPreview = false
                                showPlaylistScreen = false
                                showAudioCut = false
                                showNewLook = false
                                showWrapped = true
                            },
                            onAudioCutClick = {
                                showRecordPreview = false
                                showPlaylistScreen = false
                                showWrapped = false
                                showNewLook = false
                                showAudioCut = true
                            },
                            onNewLookClick = {
                                showRecordPreview = false
                                showPlaylistScreen = false
                                showWrapped = false
                                showAudioCut = false
                                showNewLook = true
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
                        showTrackNumbers = showTrackNumbers,
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
    onSortMenuExpandedChange: (Boolean) -> Unit,
    showTrackNumbers: Boolean,
    onShowTrackNumbersChange: (Boolean) -> Unit,
) {
    val look = LocalSpoticiousLook.current
    val searchActive = selectedTab == Tab.UTWORY || selectedTab == Tab.FOLDERY

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.extra),
            color = look.accentAlt,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onExtraClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        if (searchActive) {
            if (searchExpanded) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(end = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 44.dp)
                                .background(
                                    look.accent.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = look.accent.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = look.textPrimary),
                            cursorBrush = SolidColor(look.accent),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_placeholder),
                                            color = look.textMuted,
                                            fontSize = 16.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        IconButton(onClick = onSearchClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = look.textMuted,
                            )
                        }
                    }
                }
                // 3 kreski zawsze na środku belki (nie przyklejone do prawej przy search).
                SortShelvesButton(
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChange = onSortMenuExpandedChange,
                    showTrackNumbers = showTrackNumbers,
                    onShowTrackNumbersChange = onShowTrackNumbersChange,
                    showTrackNumberingToggle = selectedTab == Tab.UTWORY,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Text(
                    text = stringResource(R.string.search),
                    color = look.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .clickable(onClick = onSearchClick),
                )
                SortShelvesButton(
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChange = onSortMenuExpandedChange,
                    showTrackNumbers = showTrackNumbers,
                    onShowTrackNumbersChange = onShowTrackNumbersChange,
                    showTrackNumberingToggle = selectedTab == Tab.UTWORY,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun SortShelvesButton(
    sortMode: TrackSortOrder,
    onSortModeChange: (TrackSortOrder) -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    showTrackNumbers: Boolean,
    onShowTrackNumbersChange: (Boolean) -> Unit,
    showTrackNumberingToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    val look = LocalSpoticiousLook.current
    val shelfColors =
        listOf(
            look.accent.copy(alpha = 0.55f),
            look.accent.copy(alpha = 0.8f),
            look.accent,
        )
    val shelfShape = RoundedCornerShape(2.dp)

    val density = LocalDensity.current
    val popupY = with(density) { 40.dp.roundToPx() }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .clickable { onSortMenuExpandedChange(true) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            shelfColors.forEach { color ->
                Box(
                    modifier =
                        Modifier
                            .width(22.dp)
                            .height(3.dp)
                            .background(color, shelfShape),
                )
            }
        }
        if (sortMenuExpanded) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupY),
                onDismissRequest = { onSortMenuExpandedChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(280.dp)
                            .clip(MiamiMenuShape)
                            .background(LocalSpoticiousLook.current.menuFill)
                            .border(1.dp, look.accent.copy(alpha = 0.85f), MiamiMenuShape)
                            .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sort_by),
                        color = look.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    TrackSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text =
                                        when (order) {
                                            TrackSortOrder.NAME_ASC -> stringResource(R.string.sort_title_asc)
                                            TrackSortOrder.NAME_DESC -> stringResource(R.string.sort_title_desc)
                                            TrackSortOrder.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
                                            TrackSortOrder.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
                                            TrackSortOrder.DATE_ADDED_ASC -> stringResource(R.string.sort_date_asc)
                                            TrackSortOrder.DATE_ADDED_DESC -> stringResource(R.string.sort_date_desc)
                                        },
                                    color = if (sortMode == order) look.accent else look.textPrimary,
                                )
                            },
                            onClick = {
                                onSortModeChange(order)
                                onSortMenuExpandedChange(false)
                            },
                        )
                    }
                    if (showTrackNumberingToggle) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = look.inactiveTrack,
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.track_numbering),
                                color = look.textPrimary,
                                fontSize = 14.sp,
                            )
                            Switch(
                                checked = showTrackNumbers,
                                onCheckedChange = onShowTrackNumbersChange,
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = look.onAccent,
                                        checkedTrackColor = look.accent,
                                        uncheckedThumbColor = look.textMuted,
                                        uncheckedTrackColor = look.inactiveTrack,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

