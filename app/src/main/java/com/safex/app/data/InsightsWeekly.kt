package com.safex.app.data

/**
 * Mirrors the Firestore document at insightsWeekly/{weekId}.
 * All maps are category/tactic/brand → count.
 */
data class InsightsWeekly(
    val weekId: String,
    val totalReports: Long = 0,
    val topCategories: Map<String, Long> = emptyMap(),
    val topTactics: Map<String, Long> = emptyMap(),
    val topBrands: Map<String, Long> = emptyMap()
)
