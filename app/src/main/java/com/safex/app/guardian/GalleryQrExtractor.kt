package com.safex.app.guardian

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/**
 * Extracts QR-code / barcode URLs from an image URI using ML Kit.
 */
object GalleryQrExtractor {

    /**
     * Returns raw values from any QR/barcode found in the image.
     * Typically these are URLs, but may also be plain text.
     * Returns an empty list if nothing is detected or on error.
     */
    suspend fun extractQrValues(context: Context, uri: Uri): List<String> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val scanner = BarcodeScanning.getClient()
            val barcodes: List<Barcode> = scanner.process(image).await()
            barcodes.mapNotNull { it.rawValue }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            // ML Kit model not downloaded yet or image unreadable — graceful fallback
            android.util.Log.w("SafeX-QR", "QR extraction failed: ${e.message}")
            emptyList()
        }
    }
}
