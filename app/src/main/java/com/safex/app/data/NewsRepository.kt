package com.safex.app.data

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.safex.app.data.local.SafeXDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

sealed class NewsResult {
    data object Loading : NewsResult()
    data class Success(val articles: List<NewsArticleEntity>) : NewsResult()
    data class Error(val message: String, val cached: List<NewsArticleEntity>) : NewsResult()
}

class NewsRepository(private val context: Context) {

    companion object {
        /** 12-hour TTL in millis. Refresh twice a day to simulate "daily" fresh news. */
        const val TTL_MS = 12 * 60 * 60 * 1000L
    }

    private val dao = SafeXDatabase.getInstance(context).newsArticleDao()

    /**
     * Observable stream of cached articles. The articles are already translated 
     * optimally by Gemini and cached in the DB.
     */
    fun observeNews(): Flow<List<NewsArticleEntity>> {
        return dao.getByRegion("GLOBAL")
    }

    /**
     * Fetch fresh news from Global feed.
     */
    fun getNews(forceRefresh: Boolean = false): Flow<NewsResult> = flow {
        val region = "GLOBAL" // Enforce Global
        emit(NewsResult.Loading)

        // Evict globally stale entries (older than 12h)
        dao.deleteOlderThan(System.currentTimeMillis() - TTL_MS)

        val latestCached = dao.getLatestCachedAt(region)
        val isFresh = latestCached != null &&
            (System.currentTimeMillis() - latestCached) < TTL_MS

        if (isFresh && !forceRefresh) {
            emit(NewsResult.Success(emptyList())) // UI updates via observeNews
            return@flow
        }

        try {
            // Fetch English news from backend master copy
            val articles = CloudFunctionsClient.INSTANCE.fetchNewsDigest(region)

            // Get read history to filter
            val readUrls = dao.getAllReadUrls().toSet()
            val newUnreadArticles = articles.filter { !readUrls.contains(it.url) }

            if (newUnreadArticles.isNotEmpty()) {
                // Clear old global news to replace with fresh batch
                dao.deleteByRegion(region)
                dao.insertAll(newUnreadArticles)
            } else if (articles.isNotEmpty()) {
                // Fetched but all read, still clear old to match "state"
                dao.deleteByRegion(region)
            }
            
            emit(NewsResult.Success(newUnreadArticles))
        } catch (e: Exception) {
            // Fallback to cache
            emit(NewsResult.Error(
                message = e.message ?: "Unknown error",
                cached = emptyList()
            ))
        }
    }

    suspend fun markArticleAsRead(url: String) {
        val readItem = NewsReadHistoryEntity(url = url, readAt = System.currentTimeMillis())
        dao.insertReadHistory(readItem)
        dao.deleteArticles(listOf(url))
    }
}
