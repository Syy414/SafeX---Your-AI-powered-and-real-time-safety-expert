package com.safex.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    /** Observe all alerts, newest first (for Alerts tab list). */
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    /** Single lookup by id (for detail screen). */
    @Query("SELECT * FROM alerts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AlertEntity?

    /** Insert or replace an alert. Returns row ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity): Long

    /** Update an alert. */
    @androidx.room.Update
    suspend fun update(alert: AlertEntity)

    /** Delete a single alert by id (after review). Returns rows affected. */
    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Nuke all alerts (Settings → Reset local data). Returns rows affected. */
    @Query("DELETE FROM alerts")
    suspend fun deleteAll(): Int

    /**
     * Live count of alerts created since [startOfWeekMillis].
     * Used on Home tab: "Threats detected this week".
     */
    @Query("SELECT COUNT(*) FROM alerts WHERE createdAt >= :startOfWeekMillis")
    fun countSince(startOfWeekMillis: Long): Flow<Int>

    /**
     * Group alerts by category for the current week.
     */
    @Query("SELECT category, COUNT(*) as count FROM alerts WHERE createdAt >= :startOfWeekMillis GROUP BY category ORDER BY count DESC")
    fun getWeeklyCategories(startOfWeekMillis: Long): Flow<List<CategoryCount>>

    /**
     * Get all alerts for the current week (to parse tacticsJson manually).
     */
    @Query("SELECT * FROM alerts WHERE createdAt >= :startOfWeekMillis")
    fun getWeeklyAlerts(startOfWeekMillis: Long): Flow<List<AlertEntity>>
}

/** Helper for aggregations. */
data class CategoryCount(
    val category: String,
    val count: Int
)
