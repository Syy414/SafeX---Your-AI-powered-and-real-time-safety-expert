package com.safex.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Reads aggregated community scam trends from Firestore.
 * Collection: insightsWeekly/{weekId}  (e.g. "2026-W07")
 *
 * This is a read-only repository — writing is done by the
 * reportAlert Cloud Function (Agent 6).
 */
class InsightsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Result wrapper for Firestore fetch. */
    sealed class Result {
        data class Success(val data: InsightsWeekly) : Result()
        object Empty : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Fetches community insights for the current ISO week.
     * Returns [Result.Empty] when document doesn't exist yet.
     */
    suspend fun fetchCurrentWeek(): Result = try {
        val weekId = currentWeekId()
        fetchWeek(weekId)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error")
    }

    /**
     * Fetches a specific week's insights.
     */
    suspend fun fetchWeek(weekId: String): Result = try {
        val snapshot = firestore
            .collection("insightsWeekly")
            .document(weekId)
            .get()
            .await()

        if (!snapshot.exists()) {
            Result.Empty
        } else {
            val data = snapshot.data ?: return Result.Empty
            val weekly = InsightsWeekly(
                weekId = weekId,
                totalReports = (data["totalReports"] as? Number)?.toLong() ?: 0L,
                topCategories = toLongMap(data["topCategories"]),
                topTactics = toLongMap(data["topTactics"]),
                topBrands = toLongMap(data["topBrands"])
            )
            Result.Success(weekly)
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error")
    }

    companion object {
        /**
         * Returns the current ISO week string, e.g. "2026-W07".
         */
        fun currentWeekId(): String {
            val today = LocalDate.now()
            val weekFields = WeekFields.of(Locale.getDefault())
            val weekNum = today.get(weekFields.weekOfWeekBasedYear())
            val year = today.get(weekFields.weekBasedYear())
            return "%d-W%02d".format(year, weekNum)
        }

        /**
         * Safely converts Firestore map field to Map<String, Long>.
         */
        @Suppress("UNCHECKED_CAST")
        private fun toLongMap(raw: Any?): Map<String, Long> {
            val map = raw as? Map<String, Any?> ?: return emptyMap()
            return map.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
        }
    }
}
