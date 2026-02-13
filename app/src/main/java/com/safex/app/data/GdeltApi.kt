package com.safex.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches scam-related news articles from GDELT DOC 2.0 API.
 * No API key required. No new dependencies — uses HttpURLConnection + org.json.
 */
object GdeltApi {

    private const val BASE =
        "https://api.gdeltproject.org/api/v2/doc/doc"

    /**
     * @param region "MY" for Malaysia, "GLOBAL" for worldwide
     * @param maxRecords cap at 30 to keep payload small
     * @return list of parsed articles (empty on failure)
     */
    suspend fun fetchScamNews(
        region: String,
        maxRecords: Int = 30
    ): List<NewsArticleEntity> = withContext(Dispatchers.IO) {
        val query = buildQuery(region)
        val urlStr = "$BASE?query=${enc(query)}" +
            "&mode=ArtList&format=json&maxrecords=$maxRecords&sort=DateDesc"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
        }

        try {
            if (conn.responseCode != 200) return@withContext emptyList()

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseArticles(body, region)
        } finally {
            conn.disconnect()
        }
    }

    // ----- internals -----

    private fun buildQuery(region: String): String {
        val keywords = "scam OR fraud OR phishing OR \"online scam\" OR \"money scam\""
        return if (region == "MY") {
            "$keywords sourcecountry:malaysia"
        } else {
            keywords
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun parseArticles(json: String, region: String): List<NewsArticleEntity> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("articles") ?: return emptyList()
            val now = System.currentTimeMillis()

            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url", "").ifBlank { return@mapNotNull null }
                val title = obj.optString("title", "").ifBlank { return@mapNotNull null }

                NewsArticleEntity(
                    url = url,
                    title = title,
                    domain = obj.optString("domain", ""),
                    imageUrl = obj.optString("socialimage", "").ifBlank { null },
                    seenDate = obj.optString("seendate", ""),
                    region = region,
                    cachedAt = now
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
