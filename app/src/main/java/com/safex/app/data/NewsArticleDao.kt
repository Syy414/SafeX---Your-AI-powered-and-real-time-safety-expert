package com.safex.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<NewsArticleEntity>): List<Long>

    @Query("SELECT * FROM news_articles WHERE region = :region ORDER BY cachedAt DESC")
    fun getByRegion(region: String): Flow<List<NewsArticleEntity>>

    @Query("SELECT MAX(cachedAt) FROM news_articles WHERE region = :region")
    suspend fun getLatestCachedAt(region: String): Long?

    @Query("DELETE FROM news_articles WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM news_articles WHERE region = :region")
    suspend fun deleteByRegion(region: String): Int

    // ---- Read History ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadHistory(item: NewsReadHistoryEntity)

    @Query("SELECT url FROM news_read_history")
    suspend fun getAllReadUrls(): List<String>
    
    @Query("DELETE FROM news_articles WHERE url IN (:urls)")
    suspend fun deleteArticles(urls: List<String>)
}
