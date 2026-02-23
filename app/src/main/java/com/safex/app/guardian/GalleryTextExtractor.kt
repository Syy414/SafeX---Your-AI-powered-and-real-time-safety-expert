package com.safex.app.guardian

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object GalleryTextExtractor {
    suspend fun extractText(context: Context, uri: Uri): String {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            // The Chinese recognizer inherently supports both Chinese and Latin (English/Malay) characters
            val recognizer = TextRecognition.getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            val result = recognizer.process(image).await()
            result.text ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
