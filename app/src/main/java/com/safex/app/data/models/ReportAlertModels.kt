package com.safex.app.data.models

/**
 * Request payload for the `reportAlert` callable function.
 * Sends only aggregated metadata — never raw content.
 */
data class ReportAlertRequest(
    val category: String,
    val tactics: List<String> = emptyList(),
    val domainPattern: String? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("category", category)
        put("tactics", tactics)
        if (domainPattern != null) put("domainPattern", domainPattern.take(120))
    }
}

/**
 * Parsed response from the `reportAlert` callable function.
 */
data class ReportAlertResponse(
    val ok: Boolean,
    val weekId: String
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ReportAlertResponse = ReportAlertResponse(
            ok = (map["ok"] as? Boolean) ?: false,
            weekId = (map["weekId"] as? String) ?: ""
        )
    }
}
