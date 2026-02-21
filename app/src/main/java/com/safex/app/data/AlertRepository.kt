package com.safex.app.data

import com.safex.app.data.local.AlertDao
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.local.CategoryCount
import com.safex.app.data.local.SafeXDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

/**
 * Single source of truth for local alerts.
 * Agent 3 (notification detection) writes here; Agent 1 (UI) reads via Flows.
 */
class AlertRepository(private val dao: AlertDao) {

    /** Observe all alerts (newest first). Filters out 'manual_hidden' type. */
    val alerts: Flow<List<AlertEntity>> = dao.observeAll().map { list ->
        list.filter { it.type != "manual_hidden" }
    }

    /** Single alert by id, or null. */
    suspend fun getAlert(id: String): AlertEntity? =
        withContext(Dispatchers.IO) { dao.getById(id) }

    /**
     * Create and persist a new alert. Returns the generated id.
     * Called by detection pipelines (notification listener, gallery worker, manual scan).
     */
    suspend fun createAlert(
        type: String,
        riskLevel: String,
        category: String,
        tacticsJson: String,
        snippetRedacted: String,
        extractedUrl: String? = null,
        headline: String? = null,
        sender: String? = null,
        fullMessage: String? = null,
        geminiAnalysis: String? = null,  // pre-fetched from Level 3, null for L1-caught alerts
        analysisLanguage: String? = null,
        heuristicScore: Float? = null,
        tfliteScore: Float? = null
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val entity = AlertEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            type = type,
            riskLevel = riskLevel,
            category = category,
            tacticsJson = tacticsJson,
            snippetRedacted = snippetRedacted,
            extractedUrl = extractedUrl,
            headline = headline,
            sender = sender,
            fullMessage = fullMessage,
            geminiAnalysis = geminiAnalysis,
            analysisLanguage = analysisLanguage,
            heuristicScore = heuristicScore,
            tfliteScore = tfliteScore
        )
        dao.insert(entity)
        
        // Hook: Update Community Trends Mock Data immediately
        try {
            InsightsRepository.getInstance().incrementCategory(category)
        } catch (e: Exception) {
            // Ignore
        }
        
        id
    }

    /**
     * Updates an existing alert with Gemini analysis.
     * Uses DAO's @Update or @Query.
     */
    suspend fun updateGeminiAnalysis(id: String, analysis: String, language: String) =
        withContext(Dispatchers.IO) {
            val alert = dao.getById(id)
            if (alert != null) {
                val updated = alert.copy(
                    geminiAnalysis = analysis,
                    analysisLanguage = language
                )
                dao.update(updated)
            }
        }

    /** Delete after review (Report / Mark safe). */
    suspend fun deleteAlert(id: String) =
        withContext(Dispatchers.IO) { dao.deleteById(id) }

    /** Delete all alerts (Settings → Reset local data). */
    suspend fun deleteAllAlerts() =
        withContext(Dispatchers.IO) { dao.deleteAll() }

    /**
     * Live count of alerts created this calendar week (Mon-Sun).
     * Drives "Threats detected this week" on Home.
     */
    fun weeklyAlertCount(): Flow<Int> {
        return dao.countSince(startOfWeekMillis())
    }

    /**
     * Top categories this week (for Insights).
     */
    fun getWeeklyCategories(): Flow<List<CategoryCount>> {
        return dao.getWeeklyCategories(startOfWeekMillis())
    }

    /**
     * Top tactics this week (for Insights).
     * Parses raw JSON from alerts and aggregates in-memory.
     */
    fun getWeeklyTactics(): Flow<List<CategoryCount>> {
        return dao.getWeeklyAlerts(startOfWeekMillis()).map { alerts ->
            val tacticCounts = mutableMapOf<String, Int>()
            alerts.forEach { alert ->
                // Basic JSON array parsing: ["a","b"] -> remove [] and " then split
                val raw = alert.tacticsJson ?: "[]"
                val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
                if (cleaned.isNotBlank()) {
                    cleaned.split(",").forEach { token ->
                        val tactic = token.trim().removeSurrounding("\"")
                        if (tactic.isNotBlank()) {
                            tacticCounts[tactic] = (tacticCounts[tactic] ?: 0) + 1
                        }
                    }
                }
            }
            tacticCounts.map { CategoryCount(it.key, it.value) }
                .sortedByDescending { it.count }
        }
    }

    private fun startOfWeekMillis(): Long {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Generate sample data for testing/demo purposes.
     * Inserts a mix of High, Medium, and Low risk alerts.
     */
    suspend fun generateDemoData() = withContext(Dispatchers.IO) {
        // 1. High Risk Investment Scam
        createAlert(
            type = "SMS",
            riskLevel = "HIGH",
            category = "Investment Scam",
            tacticsJson = "[\"Urgency\", \"Too Good To Be True\", \"Unverified URL\"]",
            snippetRedacted = "Your account #8821 yielded $5,400 profit! Withdraw NOW at http://bit.ly/fake-invest",
            headline = "Suspicious Crypto Scheme"
        )
        // 2. Medium Risk Delivery Scam
        createAlert(
            type = "WhatsApp",
            riskLevel = "MEDIUM",
            category = "Phishing",
            tacticsJson = "[\"Impersonation\", \"Request for Info\"]",
            snippetRedacted = "PosLaju: Parcel pending delivery. Pay RM5 fee here: http://pos-laju-tax.com",
            headline = "Fake Delivery Fee Request"
        )
        // 3. Low Risk Spam
        createAlert(
            type = "SMS",
            riskLevel = "LOW",
            category = "Spam",
            tacticsJson = "[\"Marketing\"]",
            snippetRedacted = "Get 50% off at ShoeMaster! Valid until Sunday.",
            headline = "Promotional Spam"
        )
         // 4. High Risk Bank Impersonation
        createAlert(
            type = "Telegram",
            riskLevel = "HIGH",
            category = "Impersonation",
            tacticsJson = "[\"Fear\", \"Authority\"]",
            snippetRedacted = "bank_admin: Your Maybank2u is LOCKED due to suspicious activity. Click to unlock.",
            headline = "Bank Account Lockdown Scam"
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AlertRepository? = null

        fun getInstance(db: SafeXDatabase): AlertRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlertRepository(db.alertDao()).also { INSTANCE = it }
            }
    }
}
