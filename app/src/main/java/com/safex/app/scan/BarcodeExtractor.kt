package com.safex.app.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/**
 * ML Kit barcode / QR scanner.
 * Returns a list of extracted URLs from any QR codes found in the image.
 */
object BarcodeExtractor {

    /**
     * Scan an image URI for QR codes and return all URL payloads.
     */
    suspend fun extractUrls(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        return scanImage(image)
    }

    /**
     * Scan an InputImage (can come from camera or file) for QR codes.
     */
    suspend fun scanImage(image: InputImage): List<String> {
        val scanner = BarcodeScanning.getClient()
        val barcodes = scanner.process(image).await()

        return barcodes
            .filter { it.valueType == Barcode.TYPE_URL || it.valueType == Barcode.TYPE_TEXT }
            .mapNotNull { barcode ->
                when (barcode.valueType) {
                    Barcode.TYPE_URL -> barcode.url?.url
                    Barcode.TYPE_TEXT -> {
                        // Check if the raw text looks like a URL
                        val raw = barcode.rawValue ?: return@mapNotNull null
                        if (raw.startsWith("http://") || raw.startsWith("https://")) raw
                        else null
                    }
                    else -> null
                }
            }
    }
}
