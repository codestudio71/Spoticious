@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.codestudio71.spoticious.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import android.widget.Toast
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.miamiMenuSurface
import com.codestudio71.spoticious.ui.theme.miamiVerticalGradient
import org.json.JSONArray
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import org.json.JSONObject
import java.util.UUID

// --- 1. MODELE DANYCH I ZAPIS ---
data class CustomPlaylist(val id: String, val name: String, val tracks: List<Pair<String, String>>) // Pair<UriString, Name>

class PlaylistManager(context: Context) {
    private val prefs = context.getSharedPreferences("MyCustomPlaylists", Context.MODE_PRIVATE)

    fun getPlaylists(): List<CustomPlaylist> {
        val jsonString = prefs.getString("playlists_data", "[]") ?: "[]"
        val list = mutableListOf<CustomPlaylist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val tracksArray = obj.getJSONArray("tracks")
                val tracks = mutableListOf<Pair<String, String>>()
                for (j in 0 until tracksArray.length()) {
                    val trackObj = tracksArray.getJSONObject(j)
                    tracks.add(Pair(trackObj.getString("path"), trackObj.getString("name")))
                }
                list.add(CustomPlaylist(obj.getString("id"), obj.getString("name"), tracks))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun savePlaylists(playlists: List<CustomPlaylist>) {
        val jsonArray = JSONArray()
        playlists.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val tracksArray = JSONArray()
            p.tracks.forEach { t ->
                val tObj = JSONObject()
                tObj.put("path", t.first)
                tObj.put("name", t.second)
                tracksArray.put(tObj)
            }
            obj.put("tracks", tracksArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString("playlists_data", jsonArray.toString()).apply()
    }
}

// --- 2. LOGIKA POBIERANIA WSZYSTKICH UTWORÓW (DO DODAWANIA) ---
fun getAllAudioFiles(context: Context): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val collection = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = android.content.ContentUris.withAppendedId(collection, id)
            val name = cursor.getString(nameCol) ?: context.getString(R.string.track_default)
            list.add(uri.toString() to name)
        }
    }
    return list
}

private data class AudioFolderGroup(
    val folderName: String,
    val folderPath: String,
    val tracks: List<Pair<String, String>>,
)

private enum class PlaylistAddMode {
    Tracks,
    Folders,
}

/** Foldery z MediaStore — po kliknięciu dodajemy wszystkie utwory z folderu do playlisty. */
private fun getAudioFolderGroups(context: Context): List<AudioFolderGroup> {
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    val projection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
            )
        }
    val filesByFolder = linkedMapOf<String, MutableList<Pair<String, String>>>()
    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val pathCol =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            }
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: context.getString(R.string.track_default)
            val path = cursor.getString(pathCol).orEmpty()
            val folderPath =
                if (path.isNotEmpty()) {
                    path.substringBeforeLast('/').ifEmpty { path }
                } else {
                    "Other"
                }
            val uri = android.content.ContentUris.withAppendedId(collection, id)
            filesByFolder.getOrPut(folderPath) { mutableListOf() }.add(uri.toString() to name)
        }
    }
    return filesByFolder.map { (path, tracks) ->
        val folderName = path.substringAfterLast('/').ifEmpty { path }.ifEmpty { "Other" }
        AudioFolderGroup(
            folderName = folderName,
            folderPath = path,
            tracks = tracks,
        )
    }.sortedBy { it.folderName.lowercase() }
}

// --- 3. GŁÓWNY EKRAN UI ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalSpoticiousLook.current
    val context = LocalContext.current
    val playlistManager = remember { PlaylistManager(context) }

    var playlists by remember { mutableStateOf(playlistManager.getPlaylists()) }
    var selectedPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showTrackSelector by remember { mutableStateOf(false) }

    /** Lista główna: long press na playliście */
    var playlistRowMenuTarget by remember { mutableStateOf<CustomPlaylist?>(null) }
    var renamePlaylistTarget by remember { mutableStateOf<CustomPlaylist?>(null) }
    var renamePlaylistField by remember { mutableStateOf("") }
    var confirmDeletePlaylistTarget by remember { mutableStateOf<CustomPlaylist?>(null) }

    // Funkcja odświeżająca zapis
    val saveAndRefresh: (List<CustomPlaylist>) -> Unit = { newList ->
        playlistManager.savePlaylists(newList)
        playlists = newList
    }

    // --- DIALOG NOWEJ PLAYLISTY ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.new_playlist), color = look.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = look.textPrimary, unfocusedTextColor = look.textPrimary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val newList = playlists.toMutableList()
                        newList.add(CustomPlaylist(UUID.randomUUID().toString(), newPlaylistName.trim(), emptyList()))
                        saveAndRefresh(newList)
                        newPlaylistName = ""
                        showAddDialog = false
                    }
                }) { Text(stringResource(R.string.save), color = look.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel), color = look.accentAlt) }
            },
            containerColor = look.dialogFill
        )
    }

    // --- DIALOGI: OPCJE PLAYLISTY (lista główna, long press) ---
    playlistRowMenuTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistRowMenuTarget = null },
            title = { Text(stringResource(R.string.playlist_row_options_title), color = look.textPrimary) },
            text = {
                Column {
                    Text(
                        pl.name,
                        color = look.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            playlistRowMenuTarget = null
                            renamePlaylistField = pl.name
                            renamePlaylistTarget = pl
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.rename_playlist), color = look.accent)
                    }
                    TextButton(
                        onClick = {
                            playlistRowMenuTarget = null
                            confirmDeletePlaylistTarget = pl
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete_playlist), color = look.accentAlt)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistRowMenuTarget = null }) {
                    Text(stringResource(R.string.cancel), color = look.textSecondary)
                }
            },
            containerColor = look.dialogFill
        )
    }

    renamePlaylistTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { renamePlaylistTarget = null },
            title = { Text(stringResource(R.string.rename_playlist), color = look.textPrimary) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistField,
                    onValueChange = { renamePlaylistField = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = look.textPrimary,
                        unfocusedTextColor = look.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renamePlaylistField.trim()
                        if (name.isNotEmpty()) {
                            val updated = pl.copy(name = name)
                            val newList = playlists.map { if (it.id == updated.id) updated else it }
                            saveAndRefresh(newList)
                            if (selectedPlaylist?.id == pl.id) {
                                selectedPlaylist = updated
                            }
                            renamePlaylistTarget = null
                        }
                    }
                ) { Text(stringResource(R.string.save), color = look.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renamePlaylistTarget = null }) {
                    Text(stringResource(R.string.cancel), color = look.accentAlt)
                }
            },
            containerColor = look.dialogFill
        )
    }

    confirmDeletePlaylistTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { confirmDeletePlaylistTarget = null },
            title = { Text(stringResource(R.string.delete_playlist), color = look.textPrimary) },
            text = { Text(stringResource(R.string.delete_playlist_confirm), color = look.textMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newList = playlists.filterNot { it.id == pl.id }
                        saveAndRefresh(newList)
                        if (selectedPlaylist?.id == pl.id) {
                            selectedPlaylist = null
                        }
                        confirmDeletePlaylistTarget = null
                    }
                ) { Text(stringResource(R.string.delete_playlist), color = look.accentAlt) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePlaylistTarget = null }) {
                    Text(stringResource(R.string.cancel), color = look.textSecondary)
                }
            },
            containerColor = look.dialogFill
        )
    }

    // --- SELECTOR UTWORÓW / FOLDERÓW ---
    if (showTrackSelector && selectedPlaylist != null) {
        val allTracks = remember { getAllAudioFiles(context) }
        val folderGroups = remember { getAudioFolderGroups(context) }
        var addMode by remember { mutableStateOf(PlaylistAddMode.Tracks) }

        fun mergeTracksIntoPlaylist(incoming: List<Pair<String, String>>): Int {
            val current = selectedPlaylist ?: return 0
            val merged = current.tracks.toMutableList()
            var added = 0
            for (track in incoming) {
                if (merged.none { it.first == track.first }) {
                    merged.add(track)
                    added++
                }
            }
            if (added > 0) {
                val updated = current.copy(tracks = merged)
                val newList = playlists.map { if (it.id == updated.id) updated else it }
                saveAndRefresh(newList)
                selectedPlaylist = updated
            }
            return added
        }

        AlertDialog(
            onDismissRequest = { showTrackSelector = false },
            title = {
                Text(
                    text =
                        if (addMode == PlaylistAddMode.Tracks) {
                            stringResource(R.string.select_track)
                        } else {
                            stringResource(R.string.select_folder)
                        },
                    color = look.textPrimary,
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .clickable { addMode = PlaylistAddMode.Tracks }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = addMode == PlaylistAddMode.Tracks,
                                onClick = { addMode = PlaylistAddMode.Tracks },
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = look.accent,
                                        unselectedColor = look.textMuted,
                                    ),
                            )
                            Text(
                                stringResource(R.string.playlist_add_mode_tracks),
                                color = look.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .clickable { addMode = PlaylistAddMode.Folders }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = addMode == PlaylistAddMode.Folders,
                                onClick = { addMode = PlaylistAddMode.Folders },
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = look.accent,
                                        unselectedColor = look.textMuted,
                                    ),
                            )
                            Text(
                                stringResource(R.string.playlist_add_mode_folders),
                                color = look.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (addMode == PlaylistAddMode.Tracks) {
                            items(allTracks.size) { i ->
                                val track = allTracks[i]
                                Text(
                                    text = track.second,
                                    color = look.textPrimary,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                mergeTracksIntoPlaylist(listOf(track))
                                                showTrackSelector = false
                                            }
                                            .padding(16.dp),
                                )
                            }
                        } else {
                            items(folderGroups, key = { it.folderPath }) { folder ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val added = mergeTracksIntoPlaylist(folder.tracks)
                                                if (added > 0) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.playlist_added_tracks_from_folder,
                                                            added,
                                                            folder.folderName,
                                                        ),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.playlist_all_tracks_already_in_list,
                                                        ),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                            .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = look.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.playlist_folder_tracks_count,
                                                folder.folderName,
                                                folder.tracks.size,
                                            ),
                                        color = look.textPrimary,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelector = false }) {
                    Text(stringResource(R.string.close), color = look.accentAlt)
                }
            },
            containerColor = look.dialogFill,
        )
        return
    }

    // --- GŁÓWNE TŁO ---
    Column(
        modifier = modifier.fillMaxSize().background(miamiVerticalGradient()),
    ) {

        // --- 3A. WIDOK SZCZEGÓŁÓW PLAYLISTY ---
        if (selectedPlaylist != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedPlaylist = null }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = look.textPrimary) }
                Text(selectedPlaylist!!.name, color = look.accent, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showTrackSelector = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_tracks), tint = look.textPrimary) }
            }

            if (selectedPlaylist!!.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.playlist_empty_hint), color = look.textMuted)
                }
            } else {
                val playlist = selectedPlaylist!!
                key(playlist.id) {
                    val lazyListState = rememberLazyListState()
                    val haptic = LocalHapticFeedback.current
                    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        val pl = selectedPlaylist ?: return@rememberReorderableLazyListState
                        val newTracks = pl.tracks.toMutableList()
                        newTracks.add(to.index, newTracks.removeAt(from.index))
                        val updatedPlaylist = pl.copy(tracks = newTracks)
                        val newList = playlists.map { if (it.id == updatedPlaylist.id) updatedPlaylist else it }
                        saveAndRefresh(newList)
                        selectedPlaylist = updatedPlaylist
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                    ) {
                        itemsIndexed(
                            playlist.tracks,
                            key = { _, t -> t.first }
                        ) { idx, track ->
                            var trackMenuExpanded by remember(track.first) { mutableStateOf(false) }

                            fun applyNewTrackOrder(newTracks: List<Pair<String, String>>) {
                                val updatedPlaylist = playlist.copy(tracks = newTracks)
                                val newList = playlists.map { if (it.id == updatedPlaylist.id) updatedPlaylist else it }
                                saveAndRefresh(newList)
                                selectedPlaylist = updatedPlaylist
                            }

                            ReorderableItem(
                                state = reorderableLazyListState,
                                key = track.first,
                            ) { _ ->
                                val dragScope = this
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {},
                                        modifier = with(dragScope) {
                                            Modifier
                                                .size(40.dp)
                                                .draggableHandle(
                                                    onDragStarted = { _ ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = stringResource(R.string.playlist_reorder_drag),
                                            tint = look.textMuted
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                onClick = {
                                                    val pl = selectedPlaylist ?: return@combinedClickable
                                                    val trackUri = Uri.parse(track.first)
                                                    if (!viewModel.isCurrentTrackUri(trackUri)) {
                                                        val uris = pl.tracks.map { Uri.parse(it.first) to it.second }
                                                        viewModel.selectFileWithPlaylist(
                                                            trackUri,
                                                            track.second,
                                                            uris,
                                                            idx
                                                        )
                                                    }
                                                },
                                                onLongClick = { trackMenuExpanded = true }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}.", color = look.accent, fontSize = 14.sp, modifier = Modifier.width(32.dp))
                                        Text(
                                            track.second,
                                            color = look.textPrimary,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { trackMenuExpanded = true },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.playlist_track_options),
                                                tint = look.textSecondary
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = trackMenuExpanded,
                                            onDismissRequest = { trackMenuExpanded = false },
                                            modifier = Modifier.miamiMenuSurface(),
                                            containerColor = Color.Transparent,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 8.dp,
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.remove_from_playlist), color = look.accentAlt) },
                                                onClick = {
                                                    trackMenuExpanded = false
                                                    val newTracks = playlist.tracks.toMutableList()
                                                    newTracks.removeAt(idx)
                                                    applyNewTrackOrder(newTracks)
                                                }
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
        // --- 3B. WIDOK GŁÓWNY (LISTA PLAYLIST) ---
        else {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = look.textPrimary) }
                Icon(Icons.Default.PlaylistPlay, null, tint = look.accentAlt, modifier = Modifier.padding(start = 8.dp))
                Text(stringResource(R.string.your_playlists), color = look.accent, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_playlist), tint = look.textPrimary) }
            }

            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PlaylistPlay, null, tint = look.textMuted, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.no_playlists), color = look.textPrimary, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = look.accentAlt,
                            contentColor = look.onAccent,
                        ),
                    ) {
                        Text(stringResource(R.string.add_new_list))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        MiamiFrame(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            contentPadding = 0.dp,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { selectedPlaylist = playlist },
                                            onLongClick = { playlistRowMenuTarget = playlist },
                                        )
                                        .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.MusicNote, null, tint = look.accent)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(playlist.name, color = look.textPrimary, fontSize = 18.sp)
                                    Text(
                                        stringResource(R.string.tracks_count, playlist.tracks.size),
                                        color = look.textMuted,
                                        fontSize = 12.sp,
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
