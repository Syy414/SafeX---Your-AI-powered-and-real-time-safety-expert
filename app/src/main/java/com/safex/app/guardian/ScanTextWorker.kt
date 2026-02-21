package com.safex.app.guardian

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safex.app.data.AlertRepository
import com.safex.app.data.UserPrefs
import com.safex.app.data.local.SafeXDatabase
import kotlinx.coroutines.flow.first

class ScanTextWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userPrefs = UserPrefs(applicationContext)

        // 1. Check if gallery monitoring is enabled
        val isEnabled = userPrefs.galleryMonitoringEnabled.first()
        if (!isEnabled) {
            Log.d("SafeX-Gallery", "Gallery monitoring disabled. Skipping.")
            return Result.success()
        }

        // 2. Get last scan time (stored in millis, MediaStore uses seconds)
        val lastScanMillis = userPrefs.lastScanTimestamp.first()
        val lastScanSeconds = lastScanMillis / 1000

        var scannedCount = 0
        var newestSeenSeconds = lastScanSeconds

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf(lastScanSeconds.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val resolver = applicationContext.contentResolver
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            // Reuse existing components
            val triageEngine = HybridTriageEngine(applicationContext) 
            val database = SafeXDatabase.getInstance(applicationContext)
            val repository = AlertRepository.getInstance(database)

            resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                // Max 10 images per run to be safe
                while (cursor.moveToNext() && scannedCount < 10) {
                    val id = cursor.getLong(idCol)
                    val dateAdded = cursor.getLong(dateCol)
                    
                    if (dateAdded > newestSeenSeconds) newestSeenSeconds = dateAdded

                    val imageUri = ContentUris.withAppendedId(uri, id)
                    scannedCount++

                    // A) OCR Text
                    val ocrText = GalleryTextExtractor.extractText(applicationContext, imageUri)
                    
                    // B) QR Code
                    val qrValues = GalleryQrExtractor.extractQrValues(applicationContext, imageUri)
                    val qrText = qrValues.joinToString("\n")

                    val fullText = (ocrText + "\n" + qrText).trim()
                    if (fullText.isBlank()) continue

                    // C) Triage
                    val result = triageEngine.analyze(fullText)

                    // D) Alert if score ≥ threshold (MEDIUM or HIGH — combined ≥ 0.50)
                    if (result.riskLevel == "HIGH" || result.riskLevel == "MEDIUM") {
                        val alertId = repository.createAlert(
                            type = "gallery",
                            riskLevel = result.riskLevel,
                            category = result.category,
                            tacticsJson = result.tactics.toString(),
                            snippetRedacted = fullText.take(500),
                            extractedUrl = if (result.containsUrl) "URL detected" else null,
                            headline = result.headline,
                            sender = "Gallery",
                            fullMessage = fullText,
                            geminiAnalysis = result.geminiAnalysis,
                            analysisLanguage = result.analysisLanguage,
                            heuristicScore = result.heuristicScore,
                            tfliteScore = result.tfliteScore
                        )

                        // E) Notification
                        SafeXNotificationHelper.postWarning(
                            applicationContext,
                            alertId,
                            result.headline,
                            result.riskLevel,
                            "gallery"
                        )
                        Log.w("SafeX-Gallery", "High risk alert created: $alertId")
                    }
                }
            }

            // Update timestamp
            userPrefs.setLastScanTimestamp(newestSeenSeconds * 1000)

            return Result.success()

        } catch (e: Exception) {
            Log.e("SafeX-Gallery", "Error scanning gallery", e)
            return Result.failure()
        }
    }
}