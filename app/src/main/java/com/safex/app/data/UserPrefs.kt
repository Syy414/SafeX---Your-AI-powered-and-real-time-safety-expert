package com.safex.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safex_prefs")

class UserPrefs(private val context: Context) {

    private val KEY_LANGUAGE = stringPreferencesKey("language_tag")
    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
    
    // Guardian Toggles
    private val KEY_GALLERY_MONITORING = booleanPreferencesKey("gallery_monitoring")
    private val KEY_NOTIF_MONITORING = booleanPreferencesKey("notif_monitoring")
    
    // Last scan timestamp for gallery worker
    private val KEY_LAST_SCAN = longPreferencesKey("last_scan_timestamp")

    val languageTag: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[KEY_LANGUAGE] ?: "" }

    val mode: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[KEY_MODE] ?: "" }

    val onboarded: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_ONBOARDED] ?: false }

    val galleryMonitoringEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_GALLERY_MONITORING] ?: false }

    val notificationMonitoringEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_NOTIF_MONITORING] ?: false }

    val lastScanTimestamp: Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[KEY_LAST_SCAN] ?: 0L }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = tag }
    }

    suspend fun setMode(mode: String) {
        context.dataStore.edit { it[KEY_MODE] = mode }
    }

    suspend fun setOnboarded(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDED] = done }
    }

    suspend fun setGalleryMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GALLERY_MONITORING] = enabled }
    }

    suspend fun setNotificationMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_MONITORING] = enabled }
    }

    suspend fun setLastScanTimestamp(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_SCAN] = epochMillis }
    }
}
