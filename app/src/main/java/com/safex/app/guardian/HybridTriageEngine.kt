package com.safex.app.guardian

import android.content.Context
import android.util.Log
import com.safex.app.data.CloudFunctionsClient
import com.safex.app.data.models.ExplainAlertRequest
import com.safex.app.ml.ScamDetector

private const val TAG = "SafeX-HybridTriage"

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
 *   if TFLite >= 0.35 OR Heuristics >= 0.50 → Gemini is called for confirmation + explanation.
 *                             Alert + notification created. Gemini JSON cached.
 *   else → No alert. User is not disturbed.
 *
 * Risk label derived from combined score (for display only):
 *   ≥ 0.75 → HIGH
 *   ≥ 0.40 → MEDIUM
 *   < 0.40 → LOW  (no alert)
 */
class HybridTriageEngine(private val context: Context) : TriageEngine {

    private val heuristics = HeuristicTriageEngine()
    private val tflite = ScamDetector(context)
    
    override suspend fun analyze(text: String): TriageResult {

        // ── Level 1: Heuristics (20% weight) ──────────────────────────
        val (hScore, tactics) = heuristics.scoreText(text)
        Log.d(TAG, "L1 heuristic score=%.3f tactics=$tactics".format(hScore))

        // ── Level 2: TFLite (80% weight) ──────────────────────────────
        val modelAvailable = tflite.isAvailable
        val tScore: Float = if (modelAvailable) {
            val s = tflite.predictScore(text).coerceIn(0f, 1f)
            Log.d(TAG, "L2 TFLite score=%.3f".format(s))
            s
        } else {
            Log.w(TAG, "L2 TFLite unavailable — heuristics-only mode")
            -1f
        }

        // ── Probability SUM combination ─────────────────────────────────
        // When TFLite is available: Combined = Heuristic×0.20 + TFLite×0.80
        // When TFLite is unavailable: Combined = Heuristic×1.00 (full weight)
        var combined = if (modelAvailable) {
            (hScore * 0.20f) + (tScore * 0.80f)
        } else {
            hScore  // fallback: 100% heuristics when model is missing
        }
        Log.d(TAG, "Raw Combined=%.3f (modelAvailable=$modelAvailable)".format(combined))

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
        // Strictly use the weighted COMBINED score (20% Rules + 80% TFLite AI)
        // If the human-readable combined score (e.g. 30/100) is >= 0.30, we escalate to Gemini.
        if (combined < 0.30f) {
            Log.d(TAG, "Combined score below threshold ($combined) — no alert")
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
        Log.d(TAG, "Score ≥ threshold ($combined) → $riskLevel — calling Gemini for explanation")

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
            val finalProbability = if (finalRiskLevel == "LOW") 0.49f else combined

            TriageResult(
                riskLevel       = finalRiskLevel,
                riskProbability = finalProbability,
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
            // Network unavailable / timeout → Gemini couldn't confirm, so don't alert
            Log.w(TAG, "Gemini call failed — downgrading to LOW (unconfirmed): ${e.message}")
            TriageResult(
                riskLevel       = "LOW",
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
