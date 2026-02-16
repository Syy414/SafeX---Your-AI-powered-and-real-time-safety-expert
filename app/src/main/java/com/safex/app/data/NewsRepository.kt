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
     * Observable stream of cached articles, automatically translated to user's language.
     */
    fun observeNews(): Flow<List<NewsArticleEntity>> {
        val prefs = UserPrefs(context)
        // Combine DB data with Language Preference
        return kotlinx.coroutines.flow.combine(
            dao.getByRegion("GLOBAL"),
            prefs.languageTag
        ) { articles, langTag ->
            val targetLangCode = when (langTag) {
                "ms" -> TranslateLanguage.MALAY
                "zh" -> TranslateLanguage.CHINESE
                else -> TranslateLanguage.ENGLISH
            }

            if (targetLangCode == TranslateLanguage.ENGLISH) {
                articles
            } else {
                translateArticles(articles, targetLangCode)
            }
        }
    }

    private suspend fun translateArticles(
        articles: List<NewsArticleEntity>,
        targetLang: String
    ): List<NewsArticleEntity> {
        if (articles.isEmpty()) return articles

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        
        val translator = Translation.getClient(options)
        
        return try {
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder().requireWifi().build()
            translator.downloadModelIfNeeded(conditions).await()

            articles.map { article ->
                val newTitle = try {
                    translator.translate(article.title).await()
                } catch (e: Exception) {
                    article.title
                }
                
                val newSummary = if (!article.summary.isNullOrBlank()) {
                    try {
                        translator.translate(article.summary).await()
                    } catch (e: Exception) {
                        article.summary
                    }
                } else null

                article.copy(title = newTitle, summary = newSummary)
            }
        } catch (e: Exception) {
            Log.e("NewsRepo", "Translation failed", e)
            articles
        } finally {
            translator.close()
        }
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
            // Always fetch English news from backend, we translate locally
            // Backend "language" param might be used for query source, but likely returns English too.
            // Let's pass "en" to backend to ensure we get English source for consistent translation.
            val articles = CloudFunctionsClient.INSTANCE.fetchNewsDigest(region, "en")

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
