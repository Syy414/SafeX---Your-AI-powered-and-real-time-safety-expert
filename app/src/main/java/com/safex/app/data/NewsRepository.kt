package com.safex.app.data

import android.content.Context
import com.safex.app.data.local.SafeXDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class NewsResult {
    data object Loading : NewsResult()
    data class Success(val articles: List<NewsArticleEntity>) : NewsResult()
    data class Error(val message: String, val cached: List<NewsArticleEntity>) : NewsResult()
}

class NewsRepository(context: Context) {

    companion object {
        /** 12-hour TTL in millis. Lower to 30_000L for quick testing. */
        const val TTL_MS = 12 * 60 * 60 * 1000L
    }

    private val dao = SafeXDatabase.getInstance(context).newsArticleDao()

    /** Observable stream of cached articles for [region]. */
    fun observeNews(region: String): Flow<List<NewsArticleEntity>> =
        dao.getByRegion(region)

    /**
     * Fetch fresh news if cache is stale (or [forceRefresh]).
     * Emits Loading → Success/Error.
     */
    fun getNews(region: String, forceRefresh: Boolean = false): Flow<NewsResult> = flow {
        emit(NewsResult.Loading)

        // Evict globally stale entries
        dao.deleteOlderThan(System.currentTimeMillis() - TTL_MS)

        val latestCached = dao.getLatestCachedAt(region)
        val isFresh = latestCached != null &&
            (System.currentTimeMillis() - latestCached) < TTL_MS

        if (isFresh && !forceRefresh) {
            // Serve from cache — the observeNews Flow already emits these
            emit(NewsResult.Success(emptyList())) // signal done; UI reads from observe
            return@flow
        }

        try {
            val articles = GdeltApi.fetchScamNews(region)
            if (articles.isNotEmpty()) {
                dao.deleteByRegion(region)
                dao.insertAll(articles)
            }
            emit(NewsResult.Success(articles))
        } catch (e: Exception) {
            // Fallback to stale cache
            emit(NewsResult.Error(
                message = e.message ?: "Unknown error",
                cached = emptyList() // UI reads from observeNews
            ))
        }
    }
}
