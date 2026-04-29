package com.codestudio71.spoticious.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Preferencje EQ — DataStore (persystencja po kill procesu / powrót z paska).
 */
val Context.eqPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eq_preferences"
)

object EqPrefKeys {
    val ENABLED = booleanPreferencesKey("eq_enabled")
    val PREAMP = floatPreferencesKey("eq_preamp")
    val PRESETS_JSON = stringPreferencesKey("eq_presets_json")
    /** ExoPlayer: REPEAT_MODE_OFF / ONE / ALL */
    val REPEAT_MODE = intPreferencesKey("playback_repeat_mode")

    fun band(index: Int) = floatPreferencesKey("eq_band_$index")
}
