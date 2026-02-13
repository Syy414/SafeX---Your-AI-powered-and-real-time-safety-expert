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

    companion object {
        // Singleton for simplicity in this MVP
        val INSTANCE by lazy { CloudFunctionsClient() }
    }
}
