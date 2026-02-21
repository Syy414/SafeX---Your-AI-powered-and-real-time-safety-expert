package com.safex.app.data

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around ML Kit On-Device Translate.
 * Downloads language models on demand (Wi-Fi or any connection).
 * Thread-safe via object singleton.
 */
object MlKitTranslator {

    private const val TAG = "SafeX-Translate"

    /**
     * Translates [text] from English to [targetLanguage] (BCP-47 code: "zh", "ms", "en", …).
     * Returns the original [text] unchanged if the target is English, empty, or translation fails.
     */
    suspend fun translate(text: String, targetLanguage: String): String {
        // BCP-47 can be "zh-CN", "zh-TW" – ML Kit needs the 2-letter base tag
        val target = targetLanguage.lowercase().take(2)

        // No-op when target is already English, or unknown
        if (target == "en" || target.isBlank() || text.isBlank()) return text

        val mlkitLang = localeToMlkit(target) ?: run {
            Log.d(TAG, "Unsupported ML Kit language: $target – returning original text")
            return text
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(mlkitLang)
            .build()

        val translator = Translation.getClient(options)

        return suspendCancellableCoroutine { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            translator.close()
                            cont.resume(translated)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Translation failed", e)
                            translator.close()
                            cont.resume(text)   // fallback to original
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Model download failed", e)
                    translator.close()
                    cont.resume(text)   // fallback to original
                }
        }
    }

    /** Maps a 2-letter locale tag to the ML Kit [TranslateLanguage] constant, or null if unsupported. */
    private fun localeToMlkit(tag: String): String? = when (tag) {
        "zh" -> TranslateLanguage.CHINESE
        "ms" -> TranslateLanguage.MALAY
        "ar" -> TranslateLanguage.ARABIC
        "hi" -> TranslateLanguage.HINDI
        "ta" -> TranslateLanguage.TAMIL
        "th" -> TranslateLanguage.THAI
        "vi" -> TranslateLanguage.VIETNAMESE
        "id" -> TranslateLanguage.INDONESIAN
        "ja" -> TranslateLanguage.JAPANESE
        "ko" -> TranslateLanguage.KOREAN
        else -> null
    }
}
