package com.example.spoticious.player

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PlaybackStateRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastPlaybackState(): PlaybackState? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        val position = prefs.getLong(KEY_POSITION, 0L)
        val fileName = prefs.getString(KEY_FILE_NAME, "Utwór") ?: "Utwór"
        return PlaybackState(uri = uri, position = position, fileName = fileName)
    }

    fun savePlaybackState(uri: String, position: Long, fileName: String) {
        prefs.edit()
            .putString(KEY_URI, uri)
            .putLong(KEY_POSITION, position)
            .putString(KEY_FILE_NAME, fileName)
            .apply()
    }

    fun clearPlaybackState() {
        prefs.edit().clear().apply()
    }

    val lastPlaybackState: Flow<PlaybackState?> = flowOf(getLastPlaybackState())

    data class PlaybackState(
        val uri: String,
        val position: Long,
        val fileName: String
    )

    companion object {
        private const val PREFS_NAME = "playback_state"
        private const val KEY_URI = "last_uri"
        private const val KEY_POSITION = "last_position"
        private const val KEY_FILE_NAME = "last_file_name"
    }
}
