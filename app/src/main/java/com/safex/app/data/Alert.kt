package com.safex.app.data

data class Alert(
    val id: String,
    val title: String,
    val description: String,
    val riskLevel: RiskLevel,
    val timestamp: Long,
    val source: String, // "Notification" or "Gallery"
    val reasons: List<String>,
    val actions: List<String>,
    val notToDo: List<String>
)

enum class RiskLevel {
    HIGH, MEDIUM, LOW
}
