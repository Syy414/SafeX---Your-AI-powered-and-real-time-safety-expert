package com.safex.app.guardian

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.safex.app.data.local.SafeXDatabase
import com.safex.app.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.LinkedHashMap

class GuardianNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val triageEngine: TriageEngine by lazy { HybridTriageEngine(this) }

    private val alertRepo by lazy { com.safex.app.data.AlertRepository.getInstance(SafeXDatabase.getInstance(this)) }
    private val prefs by lazy { UserPrefs(this) }

    /**
     * Deduplication cache: stores MD5 hashes of recently alerted notification texts.
     * Evicts oldest entry when size exceeds 50.
     */
    private val recentHashes: LinkedHashMap<String, Boolean> = object : LinkedHashMap<String, Boolean>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>) = size > 50
    }

    // ContentObserver to trigger instant scans
    private val galleryObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "Gallery change detected. Triggering instant scan.")
            // Use unique work name to prevent multiple scans stacking up
            GalleryScanWork.runOnceUnique(this@GuardianNotificationListener)
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
                // Fix: MainActivity saves "Guardian", so we must ignore case or match exact string
                if (!mode.equals("guardian", ignoreCase = true)) return@launch

                val monitoringEnabled = prefs.notificationMonitoringEnabled.first()
                if (!monitoringEnabled) return@launch

                Log.d(TAG, "Triaging notification from ${sbn.packageName}")

                // ── Deduplication: skip if we already alerted on identical text within 10 minutes ──
                val timeWindow = System.currentTimeMillis() / (10 * 60 * 1000) // 10-minute buckets
                val contentHash = "${combined.hashCode()}_$timeWindow"
                if (recentHashes.containsKey(contentHash)) {
                    Log.d(TAG, "Skipping duplicate notification (same content hash within 10m).")
                    return@launch
                }
                recentHashes[contentHash] = true

                val result = triageEngine.analyze(combined)

                // Only create alerts for HIGH (and optionally MEDIUM for demo data)
                
                // Extract Sender (Title) and Full Message (Text)
                val sender = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Unknown"
                val fullMessage = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: combined

                if (result.riskLevel == "HIGH" || result.riskLevel == "MEDIUM") {
                    val alertId = alertRepo.createAlert(
                        type = "Notification",
                        riskLevel = result.riskLevel,
                        category = result.category,
                        tacticsJson = JSONArray(result.tactics).toString(),
                        snippetRedacted = if (combined.length > 100) combined.take(100) + "..." else combined,
                        extractedUrl = if (result.containsUrl) "URL detected" else null,
                        headline = result.headline,
                        sender = sender,
                        fullMessage = fullMessage,
                        geminiAnalysis = result.geminiAnalysis,
                        analysisLanguage = result.analysisLanguage,
                        heuristicScore = result.heuristicScore,
                        tfliteScore = result.tfliteScore
                    )
                    
                    // Post Warning Notification
                    SafeXNotificationHelper.postWarning(
                        context = this@GuardianNotificationListener,
                        id = alertId,
                        headline = result.headline,
                        riskLevel = result.riskLevel,
                        type = "notification"
                    )
                }
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