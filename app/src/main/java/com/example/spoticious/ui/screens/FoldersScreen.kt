package com.example.spoticious.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spoticious.R
import com.example.spoticious.folders.AudioFile
import com.example.spoticious.folders.FolderWithFiles
import com.example.spoticious.folders.FoldersViewModel
import com.example.spoticious.folders.TrackSortOrder
import com.example.spoticious.player.PlayerViewModel
import com.example.spoticious.ui.theme.MiamiCyan
import com.example.spoticious.ui.theme.MiamiPink

@Composable
fun FoldersScreen(
    modifier: Modifier = Modifier,
    foldersViewModel: FoldersViewModel,
    playerViewModel: PlayerViewModel,
    searchQuery: String = "",
    sortMode: TrackSortOrder = TrackSortOrder.NAME_ASC
) {
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
                text = "Dostęp do muzyki jest potrzebny,\naby przeglądać pliki na urządzeniu",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = { permissionLauncher.launch(audioPermission) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MiamiCyan)
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
        when {
            isLoading && folders.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MiamiCyan)
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
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MiamiPink)
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
                        text = "Brak plików audio na urządzeniu",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { foldersViewModel.loadAudioFilesIfPermitted() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MiamiCyan)
                    ) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            }
            else -> {
                var expandedFolders by remember { mutableStateOf(setOf<String>()) }
                val allFilteredFiles = remember(filteredFolders) {
                    filteredFolders.flatMap { it.files }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredFolders) { folder ->
                        val isExpanded = folder.folderPath in expandedFolders
                        FolderSection(
                            folder = folder,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedFolders = if (isExpanded) {
                                    expandedFolders - folder.folderPath
                                } else {
                                    expandedFolders + folder.folderPath
                                }
                            },
                            onFileClick = { file ->
                                val playlist = allFilteredFiles.map { f -> f.uri to f.displayName }
                                val index = allFilteredFiles.indexOf(file)
                                playerViewModel.selectFileWithPlaylist(
                                    file.uri,
                                    file.displayName,
                                    playlist,
                                    index.coerceAtLeast(0)
                                )
                            }
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
    onToggle: () -> Unit,
    onFileClick: (AudioFile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1F26), RoundedCornerShape(16.dp))
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
                tint = MiamiCyan,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(R.string.tracks_in_folder, folder.files.size),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MiamiPink
            )
        }

        if (isExpanded) {
            folder.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileClick(file) }
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MiamiPink.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = file.displayName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
