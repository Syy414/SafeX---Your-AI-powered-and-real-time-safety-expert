package com.safex.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Manages anonymous Firebase Auth sign-in.
 * Callable functions require auth — this ensures we always have a valid uid.
 * No API keys embedded; credentials come from google-services.json.
 */
object FirebaseAuthHelper {

    private const val TAG = "SafeX:Auth"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * Ensures user is signed in anonymously.
     * Safe to call multiple times — returns immediately if already authed.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        try {
            val result = auth.signInAnonymously().await()
            Log.d(TAG, "Anonymous auth signed in, uid=${result.user?.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous auth failed", e)
            throw e
        }
    }
}
