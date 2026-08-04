package com.codestudio71.spoticious.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.folders.AudioFile
import com.codestudio71.spoticious.folders.FolderWithFiles
import com.codestudio71.spoticious.folders.FoldersViewModel
import com.codestudio71.spoticious.folders.TrackSortOrder
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.appListSelectedRow

/** Zaokrąglenie jak MiamiFrame (16dp), bez ramki — tylko delikatne podświetlenie wiersza. */
private val FolderFileRowShape = RoundedCornerShape(12.dp)

@Composable
fun FoldersScreen(
    modifier: Modifier = Modifier,
    foldersViewModel: FoldersViewModel,
    playerViewModel: PlayerViewModel,
    searchQuery: String = "",
    sortMode: TrackSortOrder = TrackSortOrder.NAME_ASC
) {
    val look = LocalSpoticiousLook.current
    val context = LocalContext.current
    val folders by foldersViewModel.folders.collectAsState()
    val filteredFolders = remember(folders, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) folders
        else folders.map { folder ->
            val matchingFiles = folder.files.filter { it.displayName.lowercase().contains(q) }
            if (matchingFiles.isEmpty()) null
            else folder.copy(files = matchingFiles)
        }.filterNotNull()
    }
    val isLoading by foldersViewModel.isLoading.collectAsState()
    val error by foldersViewModel.error.collectAsState()

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            foldersViewModel.loadAudioFilesIfPermitted()
        }
    }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.music_access_folders),
                color = look.textSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = { permissionLauncher.launch(audioPermission) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = look.accent,
                    contentColor = look.onAccent,
                )
            ) {
                Text(stringResource(R.string.share_access))
            }
        }
        return
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            foldersViewModel.loadAudioFilesIfPermitted()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading && folders.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = look.accent)
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { foldersViewModel.loadAudioFilesIfPermitted() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = look.accentAlt,
                            contentColor = look.onAccent,
                        )
                    ) {
                        Text(stringResource(R.string.try_again))
                    }
                }
            }
            folders.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_audio_files),
                        color = look.textMuted,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { foldersViewModel.loadAudioFilesIfPermitted() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = look.accent,
                            contentColor = look.onAccent,
                        )
                    ) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            }
            else -> {
                var expandedFolders by remember { mutableStateOf(setOf<String>()) }

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredFolders, key = { it.folderPath }) { folder ->
                        val isExpanded = folder.folderPath in expandedFolders
                        val folderHighlighted =
                            folder.files.any { playerViewModel.isCurrentTrackUri(it.uri) }
                        FolderSection(
                            folder = folder,
                            isExpanded = isExpanded,
                            highlighted = folderHighlighted,
                            onToggle = {
                                expandedFolders =
                                    if (isExpanded) {
                                        expandedFolders - folder.folderPath
                                    } else {
                                        expandedFolders + folder.folderPath
                                    }
                            },
                            onFileClick = { file ->
                                if (!playerViewModel.isCurrentTrackUri(file.uri)) {
                                    val folderTracks = folder.files
                                    val playlist = folderTracks.map { f -> f.uri to f.displayName }
                                    val index = folderTracks.indexOf(file)
                                    playerViewModel.selectFileWithPlaylist(
                                        file.uri,
                                        file.displayName,
                                        playlist,
                                        index.coerceAtLeast(0),
                                    )
                                }
                            },
                            isFileSelected = { uri -> playerViewModel.isCurrentTrackUri(uri) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSection(
    folder: FolderWithFiles,
    isExpanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
    isFileSelected: (Uri) -> Boolean,
) {
    val look = LocalSpoticiousLook.current
    MiamiFrame(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        highlighted = highlighted,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = look.accent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    color = look.textPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(R.string.tracks_in_folder, folder.files.size),
                    color = look.textMuted,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = look.accentAlt
            )
        }

        if (isExpanded) {
            folder.files.forEach { file ->
                val filePlaying = isFileSelected(file.uri)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .appListSelectedRow(selected = filePlaying, shape = FolderFileRowShape)
                            .clickable { onFileClick(file) }
                            .padding(vertical = 11.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = file.displayName,
                        color = look.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (filePlaying) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (file.durationMs > 0L) {
                        Text(
                            text = formatFolderDuration(file.durationMs),
                            color =
                                if (filePlaying) look.textSecondary
                                else look.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun formatFolderDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
