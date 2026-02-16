package com.safex.app.guardian

import android.content.Context
import android.util.Log

/**
 * Hybrid engine that prioritizes Rules/Heuristics over AI.
 * 
 * Logic:
 * 1. Run Heuristics (Rules).
 * 2. If Heuristics says HIGH, return immediately (Trust Rules).
 * 3. If Heuristics says LOW/MEDIUM, run TFLite (AI).
 * 4. Return the result with the higher risk level.
 */
class HybridTriageEngine(private val context: Context) : TriageEngine {

    private val heuristics = HeuristicTriageEngine()
    private val tflite = TFLiteTriageEngine(context)

    override fun analyze(text: String): TriageResult {
        // 1. Run Rules First
        val ruleResult = heuristics.analyze(text)
        Log.d(TAG, "Rules Risk: ${ruleResult.riskLevel}")

        // If rules detect HIGH risk (e.g. "urgent" + "bank"), stop here.
        if (ruleResult.riskLevel == "HIGH") {
            return ruleResult.copy(tactics = ruleResult.tactics + "rules_priority")
        }

        // 2. Run AI Second (if rules were not decisive)
        val aiResult = tflite.analyze(text)
        Log.d(TAG, "AI Risk: ${aiResult.riskLevel}")

        // 3. Compare and Upgrade
        // If AI finds something Rules missed, use AI.
        // Risk Hierarchy: HIGH > MEDIUM > LOW
        val isAiRiskier = getRiskScore(aiResult.riskLevel) > getRiskScore(ruleResult.riskLevel)

        return if (isAiRiskier) {
            // Use AI result, but keep any specific tactics found by rules
            aiResult.copy(
                tactics = (aiResult.tactics + ruleResult.tactics).distinct(),
                headline = "AI Detected: ${aiResult.headline}"
            )
        } else {
            // Use Rule result (it was at least as high as AI, or AI failed)
            ruleResult
        }
    }

    private fun getRiskScore(level: String): Int {
        return when (level) {
            "HIGH" -> 3
            "MEDIUM" -> 2
            else -> 1
        }
    }

    companion object {
        private const val TAG = "SafeX-Hybrid"
    }
}
