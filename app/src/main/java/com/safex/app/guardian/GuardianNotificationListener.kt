package com.safex.app.guardian

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.local.SafeXDatabase
import com.safex.app.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray

class GuardianNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Use Hybrid engine: Rules First -> then AI
    private val triageEngine: TriageEngine by lazy { 
        HybridTriageEngine(this) 
    }

    private val dao by lazy { SafeXDatabase.getInstance(this).alertDao() }
    private val prefs by lazy { UserPrefs(this) }

    // ContentObserver to trigger instant scans
    private val galleryObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "Gallery change detected. Triggering instant scan.")
            GalleryScanWork.runOnceNow(this@GuardianNotificationListener)
        }
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
        SafeXNotificationHelper.createChannel(this)

        // Register observer for instant gallery scanning
        try {
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                galleryObserver
            )
            Log.d(TAG, "Gallery observer registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register gallery observer", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        try {
            contentResolver.unregisterContentObserver(galleryObserver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister gallery observer", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // ... (rest of the method remains valid, checking bounds)
        // Skip our own notifications to avoid feedback loop
        if (sbn.packageName == packageName) return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
            .orEmpty()

        val combined = listOf(title, text, bigText, subText, lines)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()

        if (combined.isBlank()) return

        scope.launch {
            try {
                // Guard: only run if Guardian mode AND notification monitoring enabled
                val mode = prefs.mode.first()
                if (mode != "guardian") return@launch

                val monitoringEnabled = prefs.notificationMonitoringEnabled.first()
                if (!monitoringEnabled) return@launch

                Log.d(TAG, "Triaging notification from ${sbn.packageName}")

                val result = triageEngine.analyze(combined)

                // Only create alerts for HIGH (and optionally MEDIUM for demo data)
                if (result.riskLevel != "HIGH" && result.riskLevel != "MEDIUM") return@launch

                val snippet = combined.take(500)
                // Convert list of tactics to JSON string
                val tacticsJson = JSONArray(result.tactics).toString()

                val alert = AlertEntity(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    type = "NOTIFICATION",
                    riskLevel = result.riskLevel,
                    category = result.category,
                    tacticsJson = tacticsJson,
                    snippetRedacted = snippet,
                    extractedUrl = if (result.containsUrl) extractFirstUrl(combined) else null,
                    headline = result.headline
                )

                dao.insert(alert)
                Log.d(TAG, "Alert saved: ${alert.id} [${alert.riskLevel}]")

                SafeXNotificationHelper.postWarningNotification(this@GuardianNotificationListener, alert)
                Log.d(TAG, "Warning notification posted for alert ${alert.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex(
            """(https?://[^\s]+|www\.[^\s]+)""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(text)?.value
    }

    companion object {
        private const val TAG = "SafeX-Guardian"
    }
}