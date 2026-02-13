package com.safex.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wraps [UserPrefs] (DataStore) and adds Guardian-specific toggle reads/writes.
 * UI (Settings tab) and detection services both use this.
 */
class SettingsRepository(private val prefs: UserPrefs) {

    /* ── delegated from UserPrefs ── */
    val mode: Flow<String> = prefs.mode
    val languageTag: Flow<String> = prefs.languageTag
    val onboarded: Flow<Boolean> = prefs.onboarded

    /* ── Guardian toggles ── */
    val notificationMonitoring: Flow<Boolean> = prefs.notificationMonitoringEnabled
    val galleryMonitoring: Flow<Boolean> = prefs.galleryMonitoringEnabled

    /* ── last-scan timestamp (for Home status) ── */
    val lastScanTimestamp: Flow<Long> = prefs.lastScanTimestamp

    /* ── writers ── */
    suspend fun setMode(mode: String) = prefs.setMode(mode)
    suspend fun setNotificationMonitoring(enabled: Boolean) = prefs.setNotificationMonitoring(enabled)
    suspend fun setGalleryMonitoring(enabled: Boolean) = prefs.setGalleryMonitoring(enabled)
    suspend fun setLastScanTimestamp(epochMillis: Long) = prefs.setLastScanTimestamp(epochMillis)

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(UserPrefs(context)).also { INSTANCE = it }
            }
    }
}
