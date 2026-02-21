package com.safex.app.data.models

/**
 * Request payload for the `explainAlert` callable function.
 * Fields match functions/src/index.ts L68-76.
 */
data class ExplainAlertRequest(
    val alertType: String,
    val language: String = "en",
    val category: String = "unknown",
    val tactics: List<String> = emptyList(),
    val snippet: String = "",
    val extractedUrl: String? = null,
    val doSafeBrowsingCheck: Boolean = false,
    val heuristicScore: Float? = null,
    val tfliteScore: Float? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("alertType", alertType)
        put("language", language)
        put("category", category)
        put("tactics", tactics)
        put("snippet", snippet.take(500))
        if (extractedUrl != null) put("extractedUrl", extractedUrl)
        put("doSafeBrowsingCheck", doSafeBrowsingCheck)
        if (heuristicScore != null) put("heuristicScore", heuristicScore)
        if (tfliteScore != null) put("tfliteScore", tfliteScore)
    }
}

/**
 * Parsed response from the `explainAlert` callable function.
 * Schema matches the Gemini JSON output defined in functions/src/index.ts L112-120.
 */
data class ExplainAlertResponse(
    val category: String,
    val riskLevel: String,
    val headline: String,
    val whyFlagged: List<String>,
    val whatToDoNow: List<String>,
    val whatNotToDo: List<String>,
    val confidence: Double,
    val notes: String
) {
    companion object {
        /** Parse the raw Map returned by the callable into a typed response. */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): ExplainAlertResponse = ExplainAlertResponse(
            category = (map["category"] as? String) ?: "Unknown",
            riskLevel = (map["riskLevel"] as? String) ?: "MEDIUM",
            headline = (map["headline"] as? String) ?: "Suspicious activity detected",
            whyFlagged = (map["whyFlagged"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: listOf("Message matched known scam patterns."),
            whatToDoNow = (map["whatToDoNow"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: listOf("Do not respond yet.", "Ask a trusted person if unsure."),
            whatNotToDo = (map["whatNotToDo"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: listOf("Do not share OTP or banking details.", "Do not send money."),
            confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.5,
            notes = (map["notes"] as? String) ?: ""
        )

        /** Fallback when the function call fails entirely. */
        val FALLBACK = ExplainAlertResponse(
            category = "Unknown",
            riskLevel = "MEDIUM",
            headline = "Suspicious message detected",
            whyFlagged = listOf("Message matched known scam manipulation patterns."),
            whatToDoNow = listOf(
                "Do not respond yet.",
                "Use SafeX Scan to test any links.",
                "Ask a trusted person if unsure."
            ),
            whatNotToDo = listOf(
                "Do not share OTP or banking details.",
                "Do not send money."
            ),
            confidence = 0.5,
            notes = "Fallback response — could not reach the server."
        )
    }
}
