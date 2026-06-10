package com.codestudio71.spoticious.ui.screens

import android.app.Activity
import android.app.Application
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.folders.AudioFile
import com.codestudio71.spoticious.folders.FoldersViewModel
import com.codestudio71.spoticious.folders.TrackSortOrder
import com.codestudio71.spoticious.folders.applyDisplayNameUpdate
import com.codestudio71.spoticious.folders.buildSafeDisplayName
import com.codestudio71.spoticious.folders.requestDeleteTrack
import com.codestudio71.spoticious.folders.requestWriteAccessForRename
import com.codestudio71.spoticious.folders.TrackMediaOpResult
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiGradientColors
import com.codestudio71.spoticious.ui.theme.MiamiPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TrackRowShape = RoundedCornerShape(12.dp)

private val TrackRowSelectedBrush =
    Brush.verticalGradient(
        colors =
            listOf(
                MiamiGradientColors[1].copy(alpha = 0.72f),
                MiamiGradientColors[2].copy(alpha = 0.58f),
            ),
    )

private sealed class PendingMediaAction {
    data class DeleteApi30(val uri: android.net.Uri) : PendingMediaAction()
    data class DeleteRetry(val uri: android.net.Uri) : PendingMediaAction()
    data class RenameAfterWrite(val uri: android.net.Uri, val newName: String) : PendingMediaAction()
}

@Composable
fun TracksScreen(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel,
    foldersViewModel: FoldersViewModel,
    searchQuery: String = "",
    sortMode: TrackSortOrder = TrackSortOrder.NAME_ASC,
    onOpenFullPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val allFiles by foldersViewModel.allAudioFiles.collectAsState()
    val filteredFiles = remember(allFiles, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allFiles
        else allFiles.filter { it.displayName.lowercase().contains(q) }
    }
    val isLoading by foldersViewModel.isLoading.collectAsState()
    val selectedUri by playerViewModel.selectedUri.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    var optionsTarget by remember { mutableStateOf<AudioFile?>(null) }
    var confirmDeleteTarget by remember { mutableStateOf<AudioFile?>(null) }
    var renameTarget by remember { mutableStateOf<AudioFile?>(null) }
    var renameFieldText by remember { mutableStateOf("") }
    var pendingMediaAction by remember { mutableStateOf<PendingMediaAction?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        val pending = pendingMediaAction
        pendingMediaAction = null
        when (pending) {
            is PendingMediaAction.DeleteApi30 -> {
                if (ok) {
                    foldersViewModel.loadAudioFilesIfPermitted()
                    playerViewModel.clearPlaybackIfUriRemoved(pending.uri)
                    Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                }
            }
            is PendingMediaAction.DeleteRetry -> {
                if (ok) {
                    scope.launch(Dispatchers.IO) {
                        when (val r = requestDeleteTrack(app, pending.uri)) {
                            is TrackMediaOpResult.Success -> {
                                withContext(Dispatchers.Main) {
                                    foldersViewModel.loadAudioFilesIfPermitted()
                                    playerViewModel.clearPlaybackIfUriRemoved(pending.uri)
                                    Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                                }
                            }
                            is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.track_action_failed), Toast.LENGTH_SHORT).show()
                            }
                            is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                                Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            is PendingMediaAction.RenameAfterWrite -> {
                if (!ok) return@rememberLauncherForActivityResult
                scope.launch(Dispatchers.IO) {
                    when (val r = applyDisplayNameUpdate(app.contentResolver, pending.uri, pending.newName)) {
                        is TrackMediaOpResult.Success -> withContext(Dispatchers.Main) {
                            foldersViewModel.loadAudioFilesIfPermitted()
                            playerViewModel.updateDisplayedFileNameIfCurrent(pending.uri, pending.newName)
                            Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                        }
                        is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.track_action_failed), Toast.LENGTH_SHORT).show()
                        }
                        is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                            Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            null -> {}
        }
    }

    fun launchDelete(file: AudioFile) {
        scope.launch(Dispatchers.IO) {
            when (val r = requestDeleteTrack(app, file.uri)) {
                is TrackMediaOpResult.Success -> withContext(Dispatchers.Main) {
                    foldersViewModel.loadAudioFilesIfPermitted()
                    playerViewModel.clearPlaybackIfUriRemoved(file.uri)
                    Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                }
                is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                    pendingMediaAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        PendingMediaAction.DeleteApi30(file.uri)
                    } else {
                        PendingMediaAction.DeleteRetry(file.uri)
                    }
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(r.intentSender).build()
                    )
                }
                is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                    Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startRenameFlow(file: AudioFile, rawName: String) {
        val finalName = buildSafeDisplayName(file.displayName, rawName)
        renameTarget = null
        scope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (val w = requestWriteAccessForRename(app, file.uri)) {
                    is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                        pendingMediaAction = PendingMediaAction.RenameAfterWrite(file.uri, finalName)
                        intentSenderLauncher.launch(
                            IntentSenderRequest.Builder(w.intentSender).build()
                        )
                    }
                    is TrackMediaOpResult.Success -> {
                        when (val u = applyDisplayNameUpdate(app.contentResolver, file.uri, finalName)) {
                            is TrackMediaOpResult.Success -> withContext(Dispatchers.Main) {
                                foldersViewModel.loadAudioFilesIfPermitted()
                                playerViewModel.updateDisplayedFileNameIfCurrent(file.uri, finalName)
                                Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                            }
                            is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                                pendingMediaAction = PendingMediaAction.RenameAfterWrite(file.uri, finalName)
                                intentSenderLauncher.launch(
                                    IntentSenderRequest.Builder(u.intentSender).build()
                                )
                            }
                            is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                                Toast.makeText(context, u.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                        Toast.makeText(context, w.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                when (val u = applyDisplayNameUpdate(app.contentResolver, file.uri, finalName)) {
                    is TrackMediaOpResult.Success -> withContext(Dispatchers.Main) {
                        foldersViewModel.loadAudioFilesIfPermitted()
                        playerViewModel.updateDisplayedFileNameIfCurrent(file.uri, finalName)
                        Toast.makeText(context, context.getString(R.string.track_action_done), Toast.LENGTH_SHORT).show()
                    }
                    is TrackMediaOpResult.NeedIntentSender -> withContext(Dispatchers.Main) {
                        pendingMediaAction = PendingMediaAction.RenameAfterWrite(file.uri, finalName)
                        intentSenderLauncher.launch(
                            IntentSenderRequest.Builder(u.intentSender).build()
                        )
                    }
                    is TrackMediaOpResult.Failed -> withContext(Dispatchers.Main) {
                        Toast.makeText(context, u.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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
                text = stringResource(R.string.music_access_tracks),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
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

    // --- Dialogi: opcje / potwierdzenie usuniÄ™cia / zmiana nazwy ---
    optionsTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { optionsTarget = null },
            title = { Text(stringResource(R.string.track_options_title), color = Color.White) },
            text = {
                Column {
                    Text(
                        text = file.displayName,
                        color = Color.LightGray,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            optionsTarget = null
                            renameFieldText = file.displayName
                            renameTarget = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.rename_track), color = MiamiCyan)
                    }
                    TextButton(
                        onClick = {
                            optionsTarget = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                launchDelete(file)
                            } else {
                                confirmDeleteTarget = file
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete_track_from_device), color = MiamiPink)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { optionsTarget = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.85f))
                }
            },
            containerColor = Color(0xFF1A1F26)
        )
    }

    confirmDeleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTarget = null },
            title = { Text(stringResource(R.string.delete_track_from_device), color = Color.White) },
            text = {
                Text(stringResource(R.string.delete_track_confirm), color = Color.LightGray)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteTarget = null
                        launchDelete(file)
                    }
                ) {
                    Text(stringResource(R.string.delete_track_from_device), color = MiamiPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTarget = null }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.85f))
                }
            },
            containerColor = Color(0xFF1A1F26)
        )
    }

    renameTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_track_dialog_title), color = Color.White) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.rename_track_hint),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameFieldText,
                        onValueChange = { renameFieldText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MiamiCyan,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { startRenameFlow(file, renameFieldText) }
                ) {
                    Text(stringResource(R.string.save), color = MiamiCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel), color = MiamiPink)
                }
            },
            containerColor = Color(0xFF1A1F26)
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLoading && allFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MiamiCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val index = filteredFiles.indexOf(file)
                        TrackRow(
                            file = file,
                            isSelected = playerViewModel.isCurrentTrackUri(file.uri),
                            onClick = {
                                if (!playerViewModel.isCurrentTrackUri(file.uri)) {
                                    val playlist = filteredFiles.map { f -> f.uri to f.displayName }
                                    playerViewModel.selectFileWithPlaylist(
                                        file.uri,
                                        file.displayName,
                                        playlist,
                                        index
                                    )
                                }
                                onOpenFullPlayer()
                            },
                            onLongClick = {
                                optionsTarget = file
                            }
                        )
                    }
                }
            }

            if (selectedUri != null) {
                androidx.compose.material3.Button(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MiamiPink)
                ) {
                    Text(
                        text = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    file: AudioFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(TrackRowShape)
                .then(
                    if (isSelected) {
                        Modifier.background(TrackRowSelectedBrush, TrackRowShape)
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 11.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = file.displayName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(file.durationMs),
            color =
                if (isSelected) Color.White.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
