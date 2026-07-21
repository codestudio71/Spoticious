package com.codestudio71.spoticious.player

import android.app.Application
import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.SpoticiousApplication
import com.codestudio71.spoticious.data.EqPrefKeys
import com.codestudio71.spoticious.data.eqPreferencesDataStore
import com.codestudio71.spoticious.data.wrapped.PlayEvent
import com.codestudio71.spoticious.data.wrapped.SpoticiousDatabase
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale

data class AudioMetadata(
    val format: String,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackRepo = PlaybackStateRepository(application)
    /** Legacy SharedPreferences — tylko jednorazowa migracja do DataStore. */
    private val eqLegacyPrefs = application.getSharedPreferences("eq_settings", Context.MODE_PRIVATE)

    private val player: ExoPlayer
        get() = PlaybackService.player ?: error("PlaybackService not ready")

    val eqProcessor: EqualizerAudioProcessor
        get() = PlaybackService.eqProcessor ?: error("PlaybackService not ready")

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    /** Ten sam strumień co [selectedUri] — URI aktualnie wczytanego utworu w playerze. */
    val currentPlayingUri: StateFlow<Uri?> = selectedUri

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

    /** Master Data z [MasterDataRepository] — analiza nie ginie przy opuszczeniu FullPlayer. */
    val masterData: StateFlow<MasterData?> = combine(_selectedUri, MasterDataRepository.entries) { uri, entries ->
        MasterDataRepository.entryFor(uri, entries)?.data
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val masterDataLoading: StateFlow<Boolean> = combine(_selectedUri, MasterDataRepository.entries) { uri, entries ->
        MasterDataRepository.entryFor(uri, entries)?.loading == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val masterDataError: StateFlow<String?> = combine(_selectedUri, MasterDataRepository.entries) { uri, entries ->
        MasterDataRepository.entryFor(uri, entries)?.error
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val masterDataProgress: StateFlow<Float> = combine(_selectedUri, MasterDataRepository.entries) { uri, entries ->
        MasterDataRepository.entryFor(uri, entries)?.progress ?: 0f
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqBandGains = MutableStateFlow(List(10) { 0f })
    val eqBandGains: StateFlow<List<Float>> = _eqBandGains.asStateFlow()

    private val _eqPreampDb = MutableStateFlow(0f)
    val eqPreampDb: StateFlow<Float> = _eqPreampDb.asStateFlow()

    private val _eqPresets = MutableStateFlow<List<EqPreset>>(emptyList())
    val presets: StateFlow<List<EqPreset>> = _eqPresets.asStateFlow()

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
    private var persistEqJob: Job? = null
    private var lastSeekAtMs: Long = 0L

    /** Jedna statystyka na sesję odtworzenia danego URI; czas liczony deltą wall-clock. */
    private var listenSessionUri: Uri? = null
    private var listenQualifiedRecorded = false
    private var listenSessionAccumulatedMs = 0L
    private var listenSessionPlayEventId: Long? = null
    private var listenSessionRowRequested = false
    private var lastListenTickElapsedMs = 0L

    private fun defaultTrackName(): String = getApplication<Application>().getString(R.string.track_default)

    init {
        PlaybackService.ensureStarted(application)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                hydrateEqStateFromDataStore()
                loadEqPresetsFromDataStore()
                _repeatMode.value = loadRepeatModeFromDataStore()
            }
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
                        val pl = _currentPlaylist.value
                        val currentIndex = _currentIndex.value
                        val lastIndex = pl.lastIndex
                        when (_repeatMode.value) {
                            Player.REPEAT_MODE_OFF -> {
                                if (_shuffleEnabled.value || currentIndex < lastIndex) {
                                    skipToNext()
                                } else {
                                    finalizeListenSession()
                                    player.pause()
                                    _isPlaying.value = false
                                }
                            }
                            Player.REPEAT_MODE_ONE -> {
                                resetListenSessionForRepeat()
                                player.seekTo(0)
                                player.play()
                                _currentPosition.value = 0L
                                _isPlaying.value = true
                            }
                            Player.REPEAT_MODE_ALL -> {
                                if (currentIndex < lastIndex) {
                                    skipToNext()
                                } else {
                                    _currentIndex.value = 0
                                    val (u, n) = pl[0]
                                    selectFileInternal(u, n, autoplay = true)
                                }
                            }
                        }
                    }
                }
            })
            applyRepeatModeToPlayer()
            applyEqStateToProcessor()
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

    private suspend fun migrateLegacyEqJsonToDataStoreIfNeeded() {
        val ds = getApplication<Application>().eqPreferencesDataStore
        val existing = ds.data.first()
        if (existing.contains(EqPrefKeys.ENABLED)) return
        val jsonStr = eqLegacyPrefs.getString("eq_json", null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val preamp = json.optDouble("preamp", 0.0).toFloat().coerceIn(-15f, 15f)
            val bandsArray = json.optJSONArray("bands")
            val bands = if (bandsArray != null && bandsArray.length() == 10) {
                (0 until 10).map { bandsArray.optDouble(it, 0.0).toFloat().coerceIn(-15f, 15f) }
            } else {
                List(10) { 0f }
            }
            val inferredOn = bands.any { abs(it) > 0.01f } || abs(preamp) > 0.01f
            ds.edit { prefs ->
                prefs[EqPrefKeys.ENABLED] = inferredOn
                prefs[EqPrefKeys.PREAMP] = preamp
                bands.forEachIndexed { i, g -> prefs[EqPrefKeys.band(i)] = g }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun hydrateEqStateFromDataStore() {
        migrateLegacyEqJsonToDataStoreIfNeeded()
        val prefs = getApplication<Application>().eqPreferencesDataStore.data.first()
        _eqEnabled.value = prefs[EqPrefKeys.ENABLED] ?: false
        _eqPreampDb.value = (prefs[EqPrefKeys.PREAMP] ?: 0f).coerceIn(-15f, 15f)
        val bands = (0 until 10).map { i ->
            (prefs[EqPrefKeys.band(i)] ?: 0f).coerceIn(-15f, 15f)
        }
        _eqBandGains.value = bands
    }

    private suspend fun persistEqToDataStore() {
        getApplication<Application>().eqPreferencesDataStore.edit { prefs ->
            prefs[EqPrefKeys.ENABLED] = _eqEnabled.value
            prefs[EqPrefKeys.PREAMP] = _eqPreampDb.value.coerceIn(-15f, 15f)
            _eqBandGains.value.forEachIndexed { i, g ->
                prefs[EqPrefKeys.band(i)] = g.coerceIn(-15f, 15f)
            }
        }
    }

    private fun applyEqStateToProcessor() {
        val proc = PlaybackService.eqProcessor ?: return
        proc.eqEnabled = _eqEnabled.value
        proc.setPreamp(_eqPreampDb.value)
        _eqBandGains.value.forEachIndexed { i, g -> proc.setBandGain(i, g) }
    }

    private fun persistEqImmediate() {
        persistEqJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            persistEqToDataStore()
        }
    }

    private fun schedulePersistEqDebounced() {
        persistEqJob?.cancel()
        persistEqJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250)
            persistEqToDataStore()
        }
    }

    /** Po zakończeniu przeciągania suwaka — natychmiastowy zapis (bez czekania na debounce). */
    fun flushEqPersist() {
        persistEqJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            persistEqToDataStore()
        }
    }

    private suspend fun loadEqPresetsFromDataStore() {
        val prefs = getApplication<Application>().eqPreferencesDataStore.data.first()
        val raw = prefs[EqPrefKeys.PRESETS_JSON].orEmpty()
        _eqPresets.value = EqPresetJsonCodec.decodeOrEmpty(raw)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private suspend fun loadRepeatModeFromDataStore(): Int {
        val raw = getApplication<Application>().eqPreferencesDataStore.data.first()[EqPrefKeys.REPEAT_MODE]
            ?: Player.REPEAT_MODE_OFF
        return when (raw) {
            Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL -> raw
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun persistRepeatMode(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().eqPreferencesDataStore.edit { prefs ->
                prefs[EqPrefKeys.REPEAT_MODE] = mode
            }
        }
    }

    private suspend fun persistPresetsList(list: List<EqPreset>) {
        val sorted = list.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val encoded = EqPresetJsonCodec.encode(sorted)
        getApplication<Application>().eqPreferencesDataStore.edit { prefs ->
            prefs[EqPrefKeys.PRESETS_JSON] = encoded
        }
        _eqPresets.value = sorted
    }

    suspend fun savePreset(name: String, overwrite: Boolean = false): EqPresetSaveOutcome {
        return withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return@withContext EqPresetSaveOutcome.Error(
                    app.getString(R.string.eq_preset_error_empty_name)
                )
            }
            val current = _eqPresets.value
            val duplicateIndex = current.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
            val isDuplicate = duplicateIndex >= 0
            if (isDuplicate && !overwrite) {
                return@withContext EqPresetSaveOutcome.DuplicateRequiresConfirmation
            }
            if (!isDuplicate && current.size >= MAX_EQ_PRESETS) {
                return@withContext EqPresetSaveOutcome.Error(
                    app.getString(R.string.eq_preset_error_limit)
                )
            }
            val snapshot = EqPreset(
                name = trimmed,
                gains = _eqBandGains.value.map { it.coerceIn(-15f, 15f) },
                enabled = _eqEnabled.value,
                preamp = _eqPreampDb.value.coerceIn(-15f, 15f),
            )
            val newList = if (isDuplicate && overwrite) {
                current.mapIndexed { idx, p -> if (idx == duplicateIndex) snapshot else p }
            } else {
                current + snapshot
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
            persistPresetsList(newList)
            EqPresetSaveOutcome.Saved
        }
    }

    suspend fun loadPreset(name: String) {
        val preset = _eqPresets.value.firstOrNull { it.name == name } ?: return
        withContext(Dispatchers.Main.immediate) {
            _eqEnabled.value = preset.enabled
            _eqBandGains.value = preset.normalizedBandGains()
            _eqPreampDb.value = preset.normalizedPreamp()
            applyEqStateToProcessor()
        }
        withContext(Dispatchers.IO) {
            persistEqToDataStore()
        }
    }

    suspend fun deletePreset(name: String) {
        withContext(Dispatchers.IO) {
            val newList = _eqPresets.value.filterNot { it.name == name }
            persistPresetsList(newList)
        }
    }

    /**
     * Import presetów Audacious z URI (SAF).
     * Przy konfliktach nazw → [EqPresetImportOutcome.NeedsConflictResolution],
     * potem [commitAudaciousPresetImport].
     */
    suspend fun importAudaciousPresetsFromUri(
        uri: Uri,
    ): EqPresetImportOutcome =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val text =
                try {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                } catch (_: Exception) {
                    null
                }
            if (text.isNullOrBlank()) {
                return@withContext EqPresetImportOutcome.Error(
                    app.getString(R.string.eq_preset_import_error_read),
                )
            }
            val fallback =
                displayNameForUri(uri)
                    .substringBeforeLast('.')
                    .ifBlank { "Imported" }
            val parsed = AudaciousEqPresetParser.parse(text, fallbackName = fallback)
            if (parsed.isEmpty()) {
                return@withContext EqPresetImportOutcome.Error(
                    app.getString(R.string.eq_preset_import_error_format),
                )
            }
            val pending =
                parsed.map { p ->
                    EqPresetJsonCodec.normalize(
                        EqPreset(
                            name = p.name,
                            gains = p.bands,
                            enabled = true,
                            preamp = p.preamp,
                        ),
                    )
                }

            val current = _eqPresets.value
            val conflicts =
                pending
                    .map { it.name }
                    .filter { name ->
                        current.any { it.name.equals(name, ignoreCase = true) }
                    }
                    .distinct()

            if (conflicts.isNotEmpty()) {
                return@withContext EqPresetImportOutcome.NeedsConflictResolution(
                    pending = pending,
                    conflictNames = conflicts,
                )
            }

            mergeImportedPresets(pending, EqPresetImportConflictMode.SkipExisting)
        }

    /** Dopięcie importu po dialogu konfliktów (bez ponownego czytania pliku). */
    suspend fun commitAudaciousPresetImport(
        pending: List<EqPreset>,
        mode: EqPresetImportConflictMode,
    ): EqPresetImportOutcome =
        withContext(Dispatchers.IO) {
            mergeImportedPresets(pending, mode)
        }

    private suspend fun mergeImportedPresets(
        pending: List<EqPreset>,
        mode: EqPresetImportConflictMode,
    ): EqPresetImportOutcome {
        val app = getApplication<Application>()
        var list = _eqPresets.value.toMutableList()
        var added = 0
        var overwritten = 0
        var skipped = 0

        for (preset in pending) {
            val idx = list.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
            if (idx >= 0) {
                when (mode) {
                    EqPresetImportConflictMode.Overwrite -> {
                        list[idx] = preset
                        overwritten++
                    }
                    EqPresetImportConflictMode.SkipExisting -> skipped++
                }
                continue
            }
            if (list.size >= MAX_EQ_PRESETS) {
                skipped++
                continue
            }
            list.add(preset)
            added++
        }

        if (added == 0 && overwritten == 0) {
            return EqPresetImportOutcome.Error(
                app.getString(R.string.eq_preset_import_nothing),
            )
        }

        list = list.sortedBy { it.name.lowercase(Locale.getDefault()) }.toMutableList()
        persistPresetsList(list)
        return EqPresetImportOutcome.Imported(
            added = added,
            overwritten = overwritten,
            skipped = skipped,
        )
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
                val idx = try {
                    val targetId = ContentUris.parseId(uri)
                    audioList.indexOfFirst { row ->
                        runCatching { ContentUris.parseId(row.first) == targetId }.getOrDefault(false)
                    }
                } catch (_: Exception) {
                    audioList.indexOfFirst { it.first.toString() == state.uri }
                }
                _currentIndex.value = if (idx >= 0) idx else 0
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.seekTo(state.position.coerceAtLeast(0L))
                _currentPosition.value = state.position
                _duration.value = player.duration.coerceAtLeast(0L)
                applyRepeatModeToPlayer()
                viewModelScope.launch {
                    _metadata.value = withContext(Dispatchers.IO) { extractMetadata(uri) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        lastListenTickElapsedMs = 0L
        positionJob = viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                val pos = player.currentPosition
                val now = System.currentTimeMillis()
                if (now - lastSeekAtMs >= 150) {
                    _currentPosition.value = pos
                }
                _duration.value = player.duration.coerceAtLeast(0L)
                if (player.isPlaying) {
                    accumulateListenTime()
                    maybeRecordQualifiedListen()
                } else {
                    lastListenTickElapsedMs = 0L
                }
                saveCounter++
                if (saveCounter >= 30) {
                    saveCounter = 0
                    _selectedUri.value?.toString()?.let { uri ->
                        playbackRepo.savePlaybackState(uri, pos, _fileName.value ?: defaultTrackName())
                    }
                    // Statystyki Wrapped nie giną przy ubiciu procesu — flush co ~3 s.
                    persistListenSessionProgress()
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        lastListenTickElapsedMs = 0L
        persistListenSessionProgress()
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

    /** Porównanie z aktualnym utworem (content URI bywa różnie znormalizowany). */
    fun isCurrentTrackUri(uri: Uri): Boolean {
        val cur = _selectedUri.value ?: return false
        return urisSameAudioTrack(cur, uri)
    }

    /** W Compose przekaż [current] z collectAsState — inaczej LazyColumn może nie odświeżyć highlightu. */
    fun isSelectedTrack(uri: Uri, current: Uri?): Boolean {
        if (current == null) return false
        return urisSameAudioTrack(current, uri)
    }

    /** Ten sam plik w MediaStore mimo różnych reprezentacji URI (np. EXTERNAL vs VOLUME_EXTERNAL). */
    private fun urisSameAudioTrack(a: Uri, b: Uri): Boolean {
        if (a == b) return true
        val sa = a.toString()
        val sb = b.toString()
        if (sa == sb) return true
        return try {
            val idA = ContentUris.parseId(a)
            val idB = ContentUris.parseId(b)
            idA == idB && idA >= 0L
        } catch (_: Exception) {
            false
        }
    }

    fun selectFile(uri: Uri, displayName: String?, autoplay: Boolean = true) {
        if (isCurrentTrackUri(uri)) return
        selectFileInternal(uri, displayName ?: defaultTrackName(), autoplay)
    }

    fun selectFileWithPlaylist(uri: Uri, displayName: String?, playlist: List<Pair<Uri, String>>, index: Int) {
        if (isCurrentTrackUri(uri)) return
        _currentPlaylist.value = playlist
        _currentIndex.value = index.coerceIn(0, playlist.size - 1)
        selectFileInternal(uri, displayName ?: defaultTrackName(), autoplay = true)
    }

    /**
     * Jedna pozycja w ExoPlayer: REPEAT_MODE_ALL na playerze powtarza ten sam [MediaItem] (jak ONE).
     * REPEAT_ALL obsługujemy przy [Player.STATE_ENDED]; na playerze tylko OFF albo ONE.
     */
    private fun applyRepeatModeToPlayer() {
        player.repeatMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun selectFileInternal(uri: Uri, name: String, autoplay: Boolean) {
        player.stop()
        _selectedUri.value = uri
        _fileName.value = name
        resetListenSession(uri)
        _metadata.value = null
        playbackRepo.savePlaybackState(uri.toString(), 0L, name)
        viewModelScope.launch {
            _metadata.value = withContext(Dispatchers.IO) { extractMetadata(uri) }
        }
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (autoplay) {
            _currentPosition.value = 0L
            player.play()
            _isPlaying.value = true
        } else {
            player.seekTo(0)
            _currentPosition.value = 0
            _isPlaying.value = false
        }
        applyRepeatModeToPlayer()
    }

    private fun finalizeListenSession() {
        val rowId = listenSessionPlayEventId ?: return
        val totalListenedMs = listenSessionAccumulatedMs.coerceAtLeast(0L)
        listenSessionPlayEventId = null
        updateQualifiedListenDuration(rowId, totalListenedMs)
    }

    /** Zapisuje faktyczny czas słuchania (pauza / zmiana utworu / flush okresowy). */
    private fun persistListenSessionProgress() {
        val rowId = listenSessionPlayEventId ?: return
        updateQualifiedListenDuration(rowId, listenSessionAccumulatedMs.coerceAtLeast(0L))
    }

    private fun updateQualifiedListenDuration(rowId: Long, listenedMs: Long) {
        val app = getApplication<Application>()
        val scope =
            (SpoticiousApplication.instance ?: app as? SpoticiousApplication)?.masterDataScope
                ?: return
        scope.launch {
            SpoticiousDatabase.get(app).playEventDao().updateListenedMs(rowId, listenedMs)
        }
    }

    private fun resetListenSession(uri: Uri) {
        finalizeListenSession()
        listenSessionUri = uri
        listenQualifiedRecorded = false
        listenSessionAccumulatedMs = 0L
        listenSessionPlayEventId = null
        listenSessionRowRequested = false
        lastListenTickElapsedMs = 0L
    }

    private fun resetListenSessionForRepeat() {
        finalizeListenSession()
        listenQualifiedRecorded = false
        listenSessionUri = _selectedUri.value
        listenSessionAccumulatedMs = 0L
        listenSessionPlayEventId = null
        listenSessionRowRequested = false
        lastListenTickElapsedMs = 0L
    }

    /** min(30 s, 50% długości utworu) — liczone od faktycznego czasu grania, nie pozycji seekbara. */
    private fun qualifiedListenThresholdMs(durationMs: Long): Long {
        if (durationMs <= 0L) return 30_000L
        return minOf(30_000L, (durationMs * 0.5).toLong().coerceAtLeast(1L))
    }

    /**
     * Czas liczony deltą [SystemClock.elapsedRealtime] między tickami — dokładny wall-clock,
     * niezależny od dryfu delay(100). Wiersz w bazie powstaje od pierwszej sekundy grania
     * (qualified=false), więc statystyka czasu nie "skacze" dopiero po progu 30 s.
     */
    private fun accumulateListenTime() {
        val uri = _selectedUri.value ?: return
        if (listenSessionUri != uri) {
            resetListenSession(uri)
        }
        val now = SystemClock.elapsedRealtime()
        val last = lastListenTickElapsedMs
        lastListenTickElapsedMs = now
        if (last > 0L) {
            val delta = now - last
            // Filtr anomalii (uśpienie procesu, debugger): ignoruj przerwy > 2 s.
            if (delta in 1..2_000) {
                listenSessionAccumulatedMs += delta
            }
        }
        if (!listenSessionRowRequested) {
            listenSessionRowRequested = true
            insertListenSessionRow(uri)
        }
    }

    private fun maybeRecordQualifiedListen() {
        val uri = _selectedUri.value ?: return
        if (listenQualifiedRecorded) return
        if (listenSessionUri != uri) {
            resetListenSession(uri)
        }
        val rowId = listenSessionPlayEventId ?: return
        val durationMs = _duration.value
        if (durationMs <= 0L) return
        val threshold = qualifiedListenThresholdMs(durationMs)
        if (listenSessionAccumulatedMs < threshold) return
        listenQualifiedRecorded = true
        val app = getApplication<Application>()
        val scope = (SpoticiousApplication.instance ?: app as? SpoticiousApplication)?.masterDataScope
            ?: return
        val listenedMs = listenSessionAccumulatedMs.coerceAtLeast(0L)
        scope.launch {
            val dao = SpoticiousDatabase.get(app).playEventDao()
            dao.markQualified(rowId)
            dao.updateListenedMs(rowId, listenedMs)
        }
    }

    private fun insertListenSessionRow(uri: Uri) {
        val app = getApplication<Application>()
        val scope = (SpoticiousApplication.instance ?: app as? SpoticiousApplication)?.masterDataScope
            ?: return
        val title = _fileName.value?.takeIf { it.isNotBlank() } ?: defaultTrackName()
        val durationMs = _duration.value
        val dao = SpoticiousDatabase.get(app).playEventDao()
        scope.launch {
            val rowId =
                dao.insert(
                    PlayEvent(
                        trackUri = uri.toString(),
                        title = title,
                        artist = null,
                        durationMs = durationMs,
                        listenedMs = 0L,
                        playedAtMs = System.currentTimeMillis(),
                        qualified = false,
                    ),
                )
            withContext(Dispatchers.Main.immediate) {
                // Sesja mogła się zmienić, zanim INSERT wrócił — wtedy wiersz zostaje z 0 ms.
                if (listenSessionUri == uri && listenSessionPlayEventId == null && listenSessionRowRequested) {
                    listenSessionPlayEventId = rowId
                    persistListenSessionProgress()
                }
            }
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
        val app = getApplication<Application>()
        val durationMs = player.duration.coerceAtLeast(0L)
        val tooLong = app.getString(R.string.master_data_track_too_long)
        MasterDataRepository.requestAnalysis(app, uri, durationMs, tooLong)
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
        MasterDataRepository.remove(uri)
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
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        applyRepeatModeToPlayer()
        persistRepeatMode(next)
        val app = getApplication<Application>()
        val msg = when (next) {
            Player.REPEAT_MODE_OFF -> app.getString(R.string.repeat_off)
            Player.REPEAT_MODE_ALL -> app.getString(R.string.repeat_all)
            Player.REPEAT_MODE_ONE -> app.getString(R.string.repeat_one)
            else -> app.getString(R.string.repeat_off)
        }
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
        PlaybackService.eqProcessor?.eqEnabled = enabled
        persistEqImmediate()
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0..9) {
            val newGains = _eqBandGains.value.toMutableList()
            newGains[bandIndex] = gainDb.coerceIn(-15f, 15f)
            _eqBandGains.value = newGains
            PlaybackService.eqProcessor?.setBandGain(bandIndex, gainDb)
            schedulePersistEqDebounced()
        }
    }

    fun resetEqBand(index: Int) {
        if (index !in 0..9) return
        setEqBandGain(index, 0f)
        flushEqPersist()
    }

    fun setEqPreamp(gainDb: Float) {
        val clamped = gainDb.coerceIn(-15f, 15f)
        _eqPreampDb.value = clamped
        PlaybackService.eqProcessor?.setPreamp(clamped)
        schedulePersistEqDebounced()
    }

    fun resetEq() {
        _eqBandGains.value = List(10) { 0f }
        _eqPreampDb.value = 0f
        for (i in 0..9) PlaybackService.eqProcessor?.setBandGain(i, 0f)
        PlaybackService.eqProcessor?.setPreamp(0f)
        persistEqImmediate()
    }

    override fun onCleared() {
        finalizeListenSession()
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMinutes.value = null
        PlaybackService.onSkipToNextCallback = null
        PlaybackService.onSkipToPreviousCallback = null
        _selectedUri.value?.toString()?.let { uri ->
            playbackRepo.savePlaybackState(uri, player.currentPosition, _fileName.value ?: defaultTrackName())
        }
        super.onCleared()
    }

    companion object {
        private const val MAX_EQ_PRESETS = 20
    }
}
