package com.codestudio71.spoticious.player

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import com.codestudio71.spoticious.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioMetadata(
    val format: String,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackRepo = PlaybackStateRepository(application)
    private val eqPrefs = application.getSharedPreferences("eq_settings", Context.MODE_PRIVATE)

    private val player: ExoPlayer
        get() = PlaybackService.player ?: error("PlaybackService not ready")

    val eqProcessor: EqualizerAudioProcessor
        get() = PlaybackService.eqProcessor ?: error("PlaybackService not ready")

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _metadata = MutableStateFlow<AudioMetadata?>(null)
    val metadata: StateFlow<AudioMetadata?> = _metadata.asStateFlow()

    private val _masterData = MutableStateFlow<MasterData?>(null)
    val masterData: StateFlow<MasterData?> = _masterData.asStateFlow()

    private val _masterDataLoading = MutableStateFlow(false)
    val masterDataLoading: StateFlow<Boolean> = _masterDataLoading.asStateFlow()

    private val _masterDataError = MutableStateFlow<String?>(null)
    val masterDataError: StateFlow<String?> = _masterDataError.asStateFlow()

    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqBandGains = MutableStateFlow(List(10) { 0f })
    val eqBandGains: StateFlow<List<Float>> = _eqBandGains.asStateFlow()

    private val _eqPreampDb = MutableStateFlow(0f)
    val eqPreampDb: StateFlow<Float> = _eqPreampDb.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val currentPlaylist: StateFlow<List<Pair<Uri, String>>> = _currentPlaylist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _savedPlaylists = MutableStateFlow<List<String>>(emptyList())
    val savedPlaylists: StateFlow<List<String>> = _savedPlaylists.asStateFlow()

    private val _sleepTimerRemainingMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMinutes: StateFlow<Int?> = _sleepTimerRemainingMinutes.asStateFlow()

    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSeekAtMs: Long = 0L

    private fun defaultTrackName(): String = getApplication<Application>().getString(R.string.track_default)

    init {
        application.startService(Intent(application, PlaybackService::class.java))
        viewModelScope.launch {
            while (PlaybackService.player == null) delay(50)
            PlaybackService.onSkipToNextCallback = { skipToNext() }
            PlaybackService.onSkipToPreviousCallback = { skipToPrevious() }
            player.addListener(object : com.google.android.exoplayer2.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                        _duration.value = player.duration.coerceAtLeast(0L)
                    }
                    if (playbackState == Player.STATE_ENDED && _currentPlaylist.value.isNotEmpty()) {
                        when (_repeatMode.value) {
                            Player.REPEAT_MODE_ONE -> {
                                player.seekTo(0)
                                player.play()
                                _currentPosition.value = 0
                            }
                            Player.REPEAT_MODE_ALL -> skipToNext()
                            else -> {
                                if (_shuffleEnabled.value) {
                                    skipToNext()
                                } else {
                                    val lastIndex = _currentPlaylist.value.size - 1
                                    if (_currentIndex.value < lastIndex) {
                                        skipToNext()
                                    }
                                }
                            }
                        }
                    }
                }
            })
            loadEqState()
            eqProcessor.eqEnabled = _eqEnabled.value
            val externalUri = PendingExternalAudio.poll()
            if (externalUri != null) {
                val name = displayNameForUri(externalUri)
                selectFileWithPlaylist(externalUri, name, listOf(externalUri to name), 0)
            } else {
                restoreLastPlayback()
            }
        }
    }

    fun displayNameForUri(uri: Uri): String {
        return try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                getApplication<Application>().contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                }
            } else null
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: getApplication<Application>().getString(R.string.track_default)
    }

    /** When the activity already exists ([Activity.onNewIntent]); cold start uses [PendingExternalAudio] + [init]. */
    fun playExternalUri(uri: Uri) {
        viewModelScope.launch {
            while (PlaybackService.player == null) delay(50)
            val name = displayNameForUri(uri)
            selectFileWithPlaylist(uri, name, listOf(uri to name), 0)
        }
    }

    private fun saveEqState() {
        val json = JSONObject()
            .put("preamp", _eqPreampDb.value.toDouble())
            .put("bands", JSONArray(_eqBandGains.value.map { it.toDouble() }))
        eqPrefs.edit().putString("eq_json", json.toString()).apply()
    }

    private fun loadEqState() {
        val jsonStr = eqPrefs.getString("eq_json", null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val preamp = json.optDouble("preamp", 0.0).toFloat().coerceIn(-15f, 15f)
            val bandsArray = json.optJSONArray("bands")
            val bands = if (bandsArray != null && bandsArray.length() == 10) {
                (0 until 10).map { bandsArray.optDouble(it, 0.0).toFloat().coerceIn(-15f, 15f) }
            } else {
                List(10) { 0f }
            }
            _eqPreampDb.value = preamp
            _eqBandGains.value = bands
            eqProcessor.setPreamp(preamp)
            bands.forEachIndexed { i, g -> eqProcessor.setBandGain(i, g) }
        } catch (_: Exception) {}
    }

    private fun restoreLastPlayback() {
        viewModelScope.launch {
            val state = playbackRepo.getLastPlaybackState() ?: return@launch
            try {
                val uri = Uri.parse(state.uri)
                _selectedUri.value = uri
                _fileName.value = state.fileName
                val audioList = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Pair<Uri, String>>()
                    val contentResolver = getApplication<Application>().contentResolver
                    val collection = if (android.os.Build.VERSION.SDK_INT >= 29)
                        android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
                    else
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(
                        android.provider.MediaStore.Audio.Media._ID,
                        android.provider.MediaStore.Audio.Media.DISPLAY_NAME
                    )
                    contentResolver.query(collection, projection, null, null,
                        "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} ASC")?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol)
                            val fileUri = android.content.ContentUris.withAppendedId(collection, id)
                            list.add(fileUri to name)
                        }
                    }
                    list
                }
                _currentPlaylist.value = audioList
                val idx = audioList.indexOfFirst { it.first.toString() == state.uri }
                _currentIndex.value = if (idx >= 0) idx else 0
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.seekTo(state.position.coerceAtLeast(0L))
                _currentPosition.value = state.position
                _duration.value = player.duration.coerceAtLeast(0L)
                viewModelScope.launch {
                    _metadata.value = withContext(Dispatchers.IO) { extractMetadata(uri) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                val pos = player.currentPosition
                val now = System.currentTimeMillis()
                if (now - lastSeekAtMs >= 150) {
                    _currentPosition.value = pos
                }
                _duration.value = player.duration.coerceAtLeast(0L)
                saveCounter++
                if (saveCounter >= 30) {
                    saveCounter = 0
                    _selectedUri.value?.toString()?.let { uri ->
                        playbackRepo.savePlaybackState(uri, pos, _fileName.value ?: defaultTrackName())
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        _selectedUri.value?.toString()?.let { uri ->
            playbackRepo.savePlaybackState(uri, player.currentPosition, _fileName.value ?: defaultTrackName())
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(clamped)
        _currentPosition.value = clamped
        lastSeekAtMs = System.currentTimeMillis()
    }

    fun selectFile(uri: Uri, displayName: String?, autoplay: Boolean = true) {
        selectFileInternal(uri, displayName ?: defaultTrackName(), autoplay)
    }

    fun selectFileWithPlaylist(uri: Uri, displayName: String?, playlist: List<Pair<Uri, String>>, index: Int) {
        _currentPlaylist.value = playlist
        _currentIndex.value = index.coerceIn(0, playlist.size - 1)
        selectFileInternal(uri, displayName ?: defaultTrackName(), autoplay = true)
    }

    private fun selectFileInternal(uri: Uri, name: String, autoplay: Boolean) {
        player.stop()
        _selectedUri.value = uri
        _fileName.value = name
        _metadata.value = null
        _masterData.value = null
        _masterDataError.value = null
        playbackRepo.savePlaybackState(uri.toString(), 0L, name)
        viewModelScope.launch {
            _metadata.value = withContext(Dispatchers.IO) { extractMetadata(uri) }
        }
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (autoplay) {
            player.play()
            _isPlaying.value = true
        } else {
            player.seekTo(0)
            _currentPosition.value = 0
            _isPlaying.value = false
        }
    }

    private fun extractMetadata(uri: Uri): AudioMetadata? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(getApplication(), uri)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            retriever.release()

            val format = when {
                mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3"
                mimeType.contains("mp4") || mimeType.contains("aac") -> "M4A / AAC"
                mimeType.contains("wav") || mimeType.contains("wave") -> "WAV"
                mimeType.contains("flac") -> "FLAC"
                mimeType.contains("ogg") -> "OGG"
                mimeType.isNotEmpty() -> mimeType.substringAfterLast("/").uppercase()
                else -> "—"
            }

            val bitrateKbps = bitrateStr?.toIntOrNull()?.let { (it / 1000).coerceAtLeast(1) }
            val sampleRateHz = sampleRateStr?.toIntOrNull()

            AudioMetadata(
                format = format,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                bitDepth = null
            )
        } catch (e: Exception) {
            null
        }
    }

    fun loadMasterData() {
        val uri = _selectedUri.value ?: run {
            Log.w("MasterDataAnalyzer", "loadMasterData: no uri selected")
            return
        }
        Log.d("MasterDataAnalyzer", "loadMasterData: uri=$uri")
        _masterDataLoading.value = true
        _masterData.value = null
        _masterDataError.value = null

        val durationMs = player.duration.coerceAtLeast(0L)
        if (durationMs > 10 * 60 * 1000L) {
            _masterDataLoading.value = false
            _masterDataError.value = getApplication<Application>().getString(R.string.master_data_track_too_long)
            return
        }

        viewModelScope.launch {
            _masterData.value = withContext(Dispatchers.IO) {
                MasterDataAnalyzer.analyze(getApplication(), uri)
            }
            _masterDataLoading.value = false
        }
    }

    /** Gdy plik został usunięty z urządzenia (np. z listy Utwory). */
    fun clearPlaybackIfUriRemoved(uri: Uri) {
        if (_selectedUri.value != uri) return
        try {
            player.stop()
        } catch (_: Exception) {
        }
        _selectedUri.value = null
        _fileName.value = null
        _metadata.value = null
        _masterData.value = null
        _masterDataError.value = null
        _currentPlaylist.value = emptyList()
        _currentIndex.value = 0
        _currentPosition.value = 0
        _duration.value = 0
        playbackRepo.clearPlaybackState()
    }

    fun updateDisplayedFileNameIfCurrent(uri: Uri, newName: String) {
        if (_selectedUri.value != uri) return
        _fileName.value = newName
        playbackRepo.savePlaybackState(uri.toString(), player.currentPosition.coerceAtLeast(0L), newName)
    }

    fun togglePlayPause() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.playWhenReady = false
            _isPlaying.value = false
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                _currentPosition.value = 0
            }
            player.playWhenReady = true
            _isPlaying.value = true
        }
    }

    fun skipToNext() {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return
        val currentIdx = _currentIndex.value
        val nextIdx = if (_shuffleEnabled.value) {
            (playlist.indices).random()
        } else when {
            currentIdx >= playlist.size - 1 -> 0
            else -> currentIdx + 1
        }
        if (nextIdx in playlist.indices) {
            _currentIndex.value = nextIdx
            val (uri, name) = playlist[nextIdx]
            selectFileInternal(uri, name, autoplay = true)
        }
    }

    fun skipToPrevious() {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return
        val currentIdx = _currentIndex.value
        val prevIdx = if (_shuffleEnabled.value) {
            (playlist.indices).random()
        } else when {
            currentIdx <= 0 -> playlist.size - 1
            else -> currentIdx - 1
        }
        if (prevIdx in playlist.indices) {
            _currentIndex.value = prevIdx
            val (uri, name) = playlist[prevIdx]
            selectFileInternal(uri, name, autoplay = true)
        }
    }

    fun addPlaylist(name: String) {
        _savedPlaylists.value = _savedPlaylists.value + name
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        player.shuffleModeEnabled = _shuffleEnabled.value
        val app = getApplication<Application>()
        val msg = if (_shuffleEnabled.value) app.getString(R.string.toast_shuffle_on) else app.getString(R.string.toast_shuffle_off)
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val playlist = _currentPlaylist.value.toMutableList()
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices || fromIndex == toIndex) return
        val item = playlist.removeAt(fromIndex)
        playlist.add(toIndex, item)
        _currentPlaylist.value = playlist
        val currIdx = _currentIndex.value
        _currentIndex.value = when {
            currIdx == fromIndex -> toIndex
            fromIndex < currIdx && toIndex >= currIdx -> currIdx - 1
            fromIndex > currIdx && toIndex <= currIdx -> currIdx + 1
            else -> currIdx
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null) {
            _sleepTimerRemainingMinutes.value = null
            return
        }
        val minutesClamped = minutes.coerceIn(1, 600)
        val endAtMs = System.currentTimeMillis() + minutesClamped * 60_000L
        _sleepTimerRemainingMinutes.value = minutesClamped
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                val remaining = ((endAtMs - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(0)
                _sleepTimerRemainingMinutes.value = if (remaining <= 0) null else remaining
                if (remaining <= 0) {
                    player.pause()
                    _isPlaying.value = false
                    return@launch
                }
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(null)
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        player.repeatMode = next
        val app = getApplication<Application>()
        val msg = if (next == Player.REPEAT_MODE_OFF) app.getString(R.string.toast_repeat_off) else app.getString(R.string.toast_repeat_on)
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }

    fun seekBackward() {
        val newPos = (player.currentPosition - 2000).coerceAtLeast(0L)
        player.seekTo(newPos)
        _currentPosition.value = newPos
        lastSeekAtMs = System.currentTimeMillis()
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        eqProcessor.eqEnabled = enabled
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0..9) {
            val newGains = _eqBandGains.value.toMutableList()
            newGains[bandIndex] = gainDb.coerceIn(-15f, 15f)
            _eqBandGains.value = newGains
            eqProcessor.setBandGain(bandIndex, gainDb)
            saveEqState()
        }
    }

    fun setEqPreamp(gainDb: Float) {
        val clamped = gainDb.coerceIn(-15f, 15f)
        _eqPreampDb.value = clamped
        eqProcessor.setPreamp(clamped)
        saveEqState()
    }

    fun resetEq() {
        _eqBandGains.value = List(10) { 0f }
        _eqPreampDb.value = 0f
        for (i in 0..9) eqProcessor.setBandGain(i, 0f)
        eqProcessor.setPreamp(0f)
        saveEqState()
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMinutes.value = null
        PlaybackService.onSkipToNextCallback = null
        PlaybackService.onSkipToPreviousCallback = null
        _selectedUri.value?.toString()?.let { uri ->
            playbackRepo.savePlaybackState(uri, player.currentPosition, _fileName.value ?: defaultTrackName())
        }
        super.onCleared()
    }
}
