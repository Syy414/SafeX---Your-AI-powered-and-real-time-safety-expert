package com.safex.app.data.models

data class CheckLinkResponse(
    val safe: Boolean,
    val riskLevel: String,
    val headline: String,
    val reasons: List<String>,
    val whyFlagged: List<String> = emptyList(),
    val whatToDoNow: List<String> = emptyList(),
    val whatNotToDo: List<String> = emptyList(),
    val category: String = "unknown",
    val confidence: Double = 0.5
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): CheckLinkResponse {
            return CheckLinkResponse(
                safe = data["safe"] as? Boolean ?: false,
                riskLevel = data["riskLevel"] as? String ?: "UNKNOWN",
                headline = data["headline"] as? String ?: "",
                reasons = (data["reasons"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                whyFlagged = (data["whyFlagged"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                whatToDoNow = (data["whatToDoNow"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                whatNotToDo = (data["whatNotToDo"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                category = data["category"] as? String ?: "unknown",
                confidence = (data["confidence"] as? Number)?.toDouble() ?: 0.5
            )
        }
    }
}
