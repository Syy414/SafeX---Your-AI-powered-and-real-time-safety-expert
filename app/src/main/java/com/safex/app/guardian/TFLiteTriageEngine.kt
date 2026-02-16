package com.safex.app.guardian

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier.TextClassifierOptions
import java.io.IOException

/**
 * Triage engine backed by MediaPipe Text Classifier.
 * Replaces the older TFLite Task Library to fix 16KB page size compatibility issues.
 * Loads `model.tflite` from assets.
 */
class TFLiteTriageEngine(private val context: Context) : TriageEngine {

    private var classifier: TextClassifier? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("model.tflite")
                .build()

            val options = TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                //.setMaxResults(5) // optional
                .build()

            classifier = TextClassifier.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe Text Classifier loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MediaPipe model", e)
        }
    }

    override fun analyze(text: String): TriageResult {
        if (text.isBlank()) return safeResult()

        val results = try {
            classifier?.classify(text)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            null
        }

        if (results == null || results.classificationResult().classifications().isEmpty()) {
            return fallbackResult()
        }

        // MediaPipe structure: components -> classifications -> categories
        val categories = results.classificationResult().classifications()[0].categories()
        
        // Find "spam" or "1" label
        val spamScore = categories.find { 
            it.categoryName().equals("spam", ignoreCase = true) || it.categoryName() == "1" 
        }?.score() ?: 0f

        val riskLevel = when {
            spamScore >= 0.75f -> "HIGH"
            spamScore >= 0.50f -> "MEDIUM"
            else -> "LOW"
        }

        return TriageResult(
            riskLevel = riskLevel,
            riskProbability = spamScore,
            tactics = if (spamScore > 0.5) listOf("ai_detected_spam") else emptyList(),
            category = if (spamScore > 0.5) "ai_suspicious" else "unknown",
            headline = if (spamScore > 0.5) "AI detected suspicious content" else "Low risk content",
            containsUrl = false 
        )
    }

    private fun safeResult() = TriageResult(
        riskLevel = "LOW",
        riskProbability = 0f,
        tactics = emptyList(),
        category = "unknown",
        headline = "No content",
        containsUrl = false
    )

    private fun fallbackResult() = TriageResult(
        riskLevel = "LOW",
        riskProbability = 0f,
        tactics = listOf("model_error"),
        category = "unknown",
        headline = "Classifier error",
        containsUrl = false
    )

    companion object {
        private const val TAG = "SafeX-MediaPipe"
    }
}
