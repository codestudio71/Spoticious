package com.codestudio71.spoticious.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_preferences",
)

object UiPrefKeys {
    val SHOW_TRACK_NUMBERS = booleanPreferencesKey("show_track_numbers")
    /** [com.codestudio71.spoticious.ui.theme.SpoticiousLookId.storageKey]; default miami. */
    val APP_LOOK = stringPreferencesKey("app_look")
}
