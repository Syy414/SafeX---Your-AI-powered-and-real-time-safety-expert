package com.safex.app.guardian

import android.content.Context
import android.util.Log
import com.safex.app.data.CloudFunctionsClient
import com.safex.app.data.models.ExplainAlertRequest
import com.safex.app.ml.ScamDetector

private const val TAG = "SafeX-HybridTriage"

/** Combined score threshold — below this, no alert is created. */
private const val ALERT_THRESHOLD = 0.50f

/** Weight given to the heuristic engine (20%). */
private const val HEURISTIC_WEIGHT = 0.20f

/** Weight given to the TFLite model (80%). */
private const val TFLITE_WEIGHT = 0.80f

/**
 * Parallel weighted scam detection engine.
 *
 * Both Level 1 (heuristics) and Level 2 (TFLite) always run for every message.
 * Their scores are combined using a weighted sum and a single threshold determines
 * whether the message triggers an alert.
 *
 *   combinedScore = heuristicScore × 0.20  +  tfliteScore × 0.80
 *
 *   combinedScore < 0.50  →  No alert. User is not disturbed.
 *   combinedScore ≥ 0.50  →  Gemini is called for confirmation + explanation.
 *                             Alert + notification created. Gemini JSON cached.
 *
 * Risk label derived from combined score (for display only):
 *   ≥ 0.75 → HIGH
 *   ≥ 0.50 → MEDIUM
 *   < 0.50 → LOW  (no alert)
 */
class HybridTriageEngine(private val context: Context) : TriageEngine {

    private val heuristics = HeuristicTriageEngine()
    private val tflite = ScamDetector(context)
    
    override suspend fun analyze(text: String): TriageResult {

        // ── Level 1: Heuristics (20% weight) ──────────────────────────
        val (hScore, tactics) = heuristics.scoreText(text)
        Log.d(TAG, "L1 heuristic score=%.3f tactics=$tactics".format(hScore))

        // ── Level 2: TFLite (80% weight) ──────────────────────────────
        val tScore: Float = if (tflite.isAvailable) {
            val s = tflite.predictScore(text).coerceIn(0f, 1f)
            Log.d(TAG, "L2 TFLite score=%.3f".format(s))
            s
        } else {
            Log.w(TAG, "L2 TFLite unavailable — using 0.0 (heuristics-only mode)")
            // When TFLite is unavailable, bump heuristics weight to 100%
            // so the engine still catches obvious scams.
            hScore  // effectively: hScore × 0.20 + hScore × 0.80 = hScore
        }

        // ── Probability OR combination ─────────────────────────────────
        // Combined = 1.0 - (1.0 - HeuristicScore) * (1.0 - TFLiteScore)
        // This ensures a strong heuristic hit cannot be suppressed by a weak ML score.
        val combined = if (tflite.isAvailable) {
            1.0f - ((1.0f - hScore) * (1.0f - tScore))
        } else {
            hScore  // fallback: 100% heuristics when model is missing
        }
        Log.d(TAG, "Combined=%.3f (h=%.3f OR tf=%.3f)".format(combined, hScore, tScore))

        val containsUrl = heuristics.hasAnyUrl(text)
        val category = tactics.firstOrNull()?.let {
            mapOf(
                "credential"     to "Phishing",
                "money_transfer" to "Investment Scam",
                "account_threat" to "Impersonation",
                "legal_threat"   to "Impersonation",
                "suspicious_url" to "Phishing"
            )[it]
        } ?: "unknown"

        // ── Below threshold → no alert ─────────────────────────────────
        if (combined < ALERT_THRESHOLD) {
            Log.d(TAG, "Score %.3f < threshold %.2f — no alert".format(combined, ALERT_THRESHOLD))
            return TriageResult(
                riskLevel       = "LOW",
                riskProbability = combined,
                heuristicScore  = hScore,
                tfliteScore     = tScore,
                tactics         = tactics,
                category        = category,
                headline        = "Low risk message",
                containsUrl     = containsUrl
            )
        }

        // ── At or above threshold: derive display risk level ───────────
        val riskLevel = if (combined >= 0.75f) "HIGH" else "MEDIUM"
        Log.d(TAG, "Score ≥ threshold → $riskLevel — calling Gemini for explanation")

        // ── Level 3: Gemini (explanation + confirmation) ───────────────
        val currentLanguage = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration).get(0)?.language ?: "en"
        return try {
            val request = ExplainAlertRequest(
                alertType            = "notification",
                language             = currentLanguage,
                category             = category,
                tactics              = tactics,
                snippet              = text.take(500),
                extractedUrl         = null,
                doSafeBrowsingCheck  = false,
                heuristicScore       = hScore,
                tfliteScore          = tScore
            )
            val response = CloudFunctionsClient.INSTANCE.explainAlert(request)
            Log.d(TAG, "Gemini riskLevel=${response.riskLevel} category=${response.category} confidence=${response.confidence}")

            // ── Defer to Gemini as the final decision maker ──────────
            // Even if heuristics said HIGH, if Gemini says LOW (e.g., uni poster), we downgrade it.
            val finalRiskLevel = response.riskLevel.uppercase()

            TriageResult(
                riskLevel       = finalRiskLevel,
                riskProbability = combined,
                heuristicScore  = hScore,
                tfliteScore     = tScore,
                tactics         = response.whyFlagged.ifEmpty { tactics },
                category        = response.category.ifBlank { category },
                headline        = response.headline,
                containsUrl     = containsUrl,
                geminiAnalysis  = serializeGemini(response),
                analysisLanguage = currentLanguage
            )
        } catch (e: Exception) {
            // Network unavailable / timeout → still create alert (already scored ≥ 0.50)
            // Gemini explanation will be lazy-loaded when user opens the alert detail.
            Log.w(TAG, "Gemini call failed — alert created without cached explanation: ${e.message}")
            TriageResult(
                riskLevel       = riskLevel,
                riskProbability = combined,
                heuristicScore  = hScore,
                tfliteScore     = tScore,
                tactics         = tactics,
                category        = category,
                headline        = heuristics.buildHeadline(tactics, containsUrl),
                containsUrl     = containsUrl,
                geminiAnalysis  = null,
                analysisLanguage = null
            )
        }
    }

    private fun serializeGemini(response: com.safex.app.data.models.ExplainAlertResponse, scoringStr: String? = null): String {
        return try {
            org.json.JSONObject().apply {
                put("category",    response.category)
                put("riskLevel",   response.riskLevel)
                put("headline",    response.headline)
                put("confidence",  response.confidence)
                val finalNotes = if (scoringStr != null) scoringStr + response.notes else response.notes
                put("notes",       finalNotes)
                put("whyFlagged",  org.json.JSONArray(response.whyFlagged))
                put("whatToDoNow", org.json.JSONArray(response.whatToDoNow))
                put("whatNotToDo", org.json.JSONArray(response.whatNotToDo))
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize Gemini response", e)
            ""
        }
    }
}
