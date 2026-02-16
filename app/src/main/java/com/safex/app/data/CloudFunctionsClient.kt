package com.safex.app.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.safex.app.data.models.ExplainAlertRequest
import com.safex.app.data.models.ExplainAlertResponse
import com.safex.app.data.models.ReportAlertRequest
import com.safex.app.data.models.ReportAlertResponse
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Client for SafeX Cloud Functions.
 * Handles auth, serialization, and error wrapping.
 */
class CloudFunctionsClient(
    region: String = "asia-southeast1"
) {
    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), region)

    /**
     * Call `explainAlert` to get Gemini analysis.
     * Guaranteed to return a valid response object (even on error, via fallback).
     */
    suspend fun explainAlert(request: ExplainAlertRequest): ExplainAlertResponse {
        // 1. Ensure auth
        FirebaseAuthHelper.ensureSignedIn()

        return try {
            // 2. Call function
            withTimeout(10_000) {
                val result = functions
                    .getHttpsCallable("explainAlert")
                    .call(request.toMap())
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()

                // 3. Parse response
                ExplainAlertResponse.fromMap(data)
            }

        } catch (e: Exception) {
            Log.e("SafeX:Functions", "explainAlert failed", e)
            val msg = if (e is FirebaseFunctionsException) {
                "Server error: ${e.code} - ${e.message}"
            } else {
                "Network error: ${e.message}"
            }
            // Return fallback with specific error note
            ExplainAlertResponse.FALLBACK.copy(notes = "Error: $msg")
        }
    }

    /**
     * Call `reportAlert` to update community stats.
     * Returns true if successful.
     */
    suspend fun reportAlert(request: ReportAlertRequest): Boolean {
        FirebaseAuthHelper.ensureSignedIn()

        return try {
            withTimeout(10_000) {
                val result = functions
                    .getHttpsCallable("reportAlert")
                    .call(request.toMap())
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()
                val response = ReportAlertResponse.fromMap(data)
                response.ok
            }

        } catch (e: Exception) {
            Log.e("SafeX:Functions", "reportAlert failed", e)
            false
        }
    }

    /**
     * Call `checkLink` for manual scan.
     */
    suspend fun checkLink(url: String): com.safex.app.data.models.CheckLinkResponse {
        FirebaseAuthHelper.ensureSignedIn()

        return try {
            withTimeout(10_000) {
                val result = functions
                    .getHttpsCallable("checkLink")
                    .call(mapOf("url" to url))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()
                com.safex.app.data.models.CheckLinkResponse.fromMap(data)
            }
        } catch (e: Exception) {
            Log.e("SafeX:Functions", "checkLink failed", e)
             com.safex.app.data.models.CheckLinkResponse(
                 safe = true, 
                 riskLevel = "UNKNOWN", 
                 headline = "Check failed: ${e.message}",
                 reasons = listOf("Network or server error.")
             )
        }
    }

    /**
     * Call `getScamNewsDigest` to fetches summarized news.
     */
    suspend fun fetchNewsDigest(region: String, language: String): List<com.safex.app.data.NewsArticleEntity> {
        // Auth optional? The backend checks it, so yes.
        FirebaseAuthHelper.ensureSignedIn()

        return try {
            withTimeout(20_000) { // Longer timeout for AI
                val result = functions
                    .getHttpsCallable("getScamNewsDigest")
                    .call(mapOf("region" to region, "language" to language))
                    .await()
                
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()
                val articlesList = data["articles"] as? List<Map<String, Any?>> ?: emptyList()
                
                articlesList.mapNotNull { map ->
                    val url = map["url"] as? String ?: return@mapNotNull null
                    val title = map["title"] as? String ?: return@mapNotNull null
                    val domain = map["domain"] as? String ?: ""
                    val summary = map["summary"] as? String
                    val seenDate = map["seenDate"] as? String ?: ""
                    val imageUrl = map["imageUrl"] as? String
                    
                    com.safex.app.data.NewsArticleEntity(
                        url = url,
                        title = title,
                        summary = summary,
                        domain = domain,
                        seenDate = seenDate,
                        imageUrl = imageUrl,
                        region = region,
                        cachedAt = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SafeX:Functions", "fetchNewsDigest failed", e)
            emptyList()
        }
    }

    companion object {
        // Singleton for simplicity in this MVP
        val INSTANCE by lazy { CloudFunctionsClient() }
    }
}
