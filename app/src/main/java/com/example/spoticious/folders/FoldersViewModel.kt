package com.example.spoticious.folders

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val folderPath: String,
    val durationMs: Long = 0L,
    val dateAddedSec: Long = 0L
)

enum class TrackSortOrder {
    NAME_ASC,
    NAME_DESC,
    DURATION_ASC,
    DURATION_DESC,
    DATE_ADDED_ASC,
    DATE_ADDED_DESC
}

data class FolderWithFiles(
    val folderName: String,
    val folderPath: String,
    val files: List<AudioFile>
)

class FoldersViewModel(application: Application) : AndroidViewModel(application) {

    private val _folders = MutableStateFlow<List<FolderWithFiles>>(emptyList())
    val folders: StateFlow<List<FolderWithFiles>> = _folders.asStateFlow()

    private val _allAudioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val allAudioFiles: StateFlow<List<AudioFile>> = _allAudioFiles.asStateFlow()

    private val _sortOrder = MutableStateFlow(TrackSortOrder.NAME_ASC)
    val sortOrder: StateFlow<TrackSortOrder> = _sortOrder.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadAudioFilesIfPermitted() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(getApplication(), perm) == PackageManager.PERMISSION_GRANTED) {
            loadAudioFiles()
        }
    }

    fun setSortOrder(order: TrackSortOrder) {
        _sortOrder.value = order
        applySortToAllFiles()
    }

    private fun applySortToAllFiles() {
        val order = _sortOrder.value
        val comparator = when (order) {
            TrackSortOrder.NAME_ASC -> compareBy<AudioFile> { it.displayName.lowercase() }
            TrackSortOrder.NAME_DESC -> compareByDescending<AudioFile> { it.displayName.lowercase() }
            TrackSortOrder.DURATION_ASC -> compareBy<AudioFile> { it.durationMs }
            TrackSortOrder.DURATION_DESC -> compareByDescending<AudioFile> { it.durationMs }
            TrackSortOrder.DATE_ADDED_ASC -> compareBy<AudioFile> { it.dateAddedSec }
            TrackSortOrder.DATE_ADDED_DESC -> compareByDescending<AudioFile> { it.dateAddedSec }
        }
        _allAudioFiles.value = _allAudioFiles.value.sortedWith(comparator)
        _folders.value = _folders.value.map { folder ->
            folder.copy(files = folder.files.sortedWith(comparator))
        }
    }

    fun loadAudioFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    loadFromMediaStore()
                }
                _folders.value = result
                _allAudioFiles.value = result.flatMap { it.files }
                applySortToAllFiles()
            } catch (e: Exception) {
                _error.value = e.message ?: "Błąd ładowania plików"
                _folders.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    private fun loadFromMediaStore(): List<FolderWithFiles> {
        val context = getApplication<Application>()
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED
            )
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        val filesByFolder = mutableMapOf<String, MutableList<AudioFile>>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            }
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "Nieznany"
                val path = cursor.getString(pathIndex) ?: ""
                val durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L)
                val dateAddedSec = cursor.getLong(dateAddedIndex).coerceAtLeast(0L)

                val folderPath = if (path.isNotEmpty()) {
                    path.substringBeforeLast('/').ifEmpty { path }
                } else {
                    "Inne"
                }
                val folderName = folderPath.substringAfterLast('/').ifEmpty { folderPath }.ifEmpty { "Inne" }

                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                val file = AudioFile(id = id, uri = uri, displayName = name, folderPath = folderPath, durationMs = durationMs, dateAddedSec = dateAddedSec)
                filesByFolder.getOrPut(folderPath) { mutableListOf() }.add(file)
            }
        }

        return filesByFolder.map { (path, files) ->
            val folderName = path.substringAfterLast('/').ifEmpty { path }.ifEmpty { "Inne" }
            FolderWithFiles(
                folderName = folderName,
                folderPath = path,
                files = files.sortedBy { it.displayName.lowercase() }
            )
        }.sortedBy { it.folderName.lowercase() }
    }
}
