package com.safex.app.data.models

data class CheckLinkResponse(
    val safe: Boolean = true,
    val riskLevel: String = "UNKNOWN",
    val headline: String = "",
    val reasons: List<String> = emptyList(),
    val matches: List<Any> = emptyList()
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): CheckLinkResponse {
            val safe = map["safe"] as? Boolean ?: true
            val riskLevel = map["riskLevel"] as? String ?: "UNKNOWN"
            val headline = map["headline"] as? String ?: ""
            val reasons = (map["reasons"] as? List<*>)?.map { it.toString() } ?: emptyList()
            
            return CheckLinkResponse(
                safe = safe,
                riskLevel = riskLevel,
                headline = headline,
                reasons = reasons
            )
        }
    }
}
