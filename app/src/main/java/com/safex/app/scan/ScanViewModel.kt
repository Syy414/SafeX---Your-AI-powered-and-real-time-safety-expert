package com.safex.app.scan

import android.content.Context
import android.net.Uri
import com.safex.app.data.AlertRepository
import com.safex.app.data.local.SafeXDatabase
import com.safex.app.guardian.GalleryTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Minimal state-holder for scan flow.
 * Not using Android ViewModel to keep dependency-light — just a plain class
 * with StateFlow that composables can remember { } + scope.launch.
 */
class ScanViewModel(private val context: Context) {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val alertRepo by lazy {
        AlertRepository.getInstance(SafeXDatabase.getInstance(context))
    }

    // ── Paste link scan (placeholder until Agent 6 backend) ──────────

    suspend fun scanLink(url: String) {
        _state.value = ScanUiState.Scanning
        try {
            // Call the new checkLink Cloud Function
            val response = com.safex.app.data.CloudFunctionsClient.INSTANCE.checkLink(url)
            
            val result = ScanResult(
                riskLevel = RiskLevel.valueOf(response.riskLevel), // Ensure RiskLevel enum matches or map safely
                headline = response.headline,
                reasons = response.reasons,
                nextSteps = if (response.safe) 
                    listOf("✅ Link appears safe", "Use caution even with safe links") 
                else 
                    listOf("⛔ Do not visit this link", "Block the sender if possible"),
                extractedUrl = url,
                scanType = ScanType.LINK
            )
            _state.value = ScanUiState.Result(result)

        } catch (e: Exception) {
            _state.value = ScanUiState.Error(e.message ?: "Link scan failed")
        }
    }

    // ── Image scan (Photo Picker → OCR + QR → triage) ────────────────

    suspend fun scanImage(uri: Uri) {
        _state.value = ScanUiState.Scanning
        try {
            val result = withContext(Dispatchers.IO) {
                // 1) OCR
                val ocrText = try {
                    GalleryTextExtractor.extractText(context, uri)
                } catch (_: Exception) { "" }

                // 2) QR barcode URLs
                val qrUrls = try {
                    BarcodeExtractor.extractUrls(context, uri)
                } catch (_: Exception) { emptyList() }

                // 3) Combine text + QR URLs for triage
                val combinedText = buildString {
                    append(ocrText)
                    if (qrUrls.isNotEmpty()) {
                        append("\n")
                        qrUrls.forEach { append("$it\n") }
                    }
                }

                // Use Hybrid Engine (Rules + AI)
                val triageResult = com.safex.app.guardian.HybridTriageEngine(context).analyze(combinedText)

                // 4) Map TriageResult -> ScanResult
                val finalRiskLevel = try {
                    RiskLevel.valueOf(triageResult.riskLevel)
                } catch (_: Exception) {
                    RiskLevel.UNKNOWN
                }

                // Determine URL (QR code priority, else Regex find)
                val finalUrl = if (qrUrls.isNotEmpty()) {
                    qrUrls.first()
                } else {
                    // Simple regex to find URL in text if triage says it has one
                    if (triageResult.containsUrl) {
                        Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)
                            .find(combinedText)?.value
                    } else null
                }

                ScanResult(
                    riskLevel = finalRiskLevel,
                    headline = triageResult.headline,
                    // tactics are the reasons
                    reasons = triageResult.tactics.ifEmpty { listOf("Analysis completed") },
                    nextSteps = when (finalRiskLevel) {
                        RiskLevel.HIGH -> listOf("Do not trust this content", "Block sender", "Delete immediately")
                        RiskLevel.MEDIUM -> listOf("Verify source carefully", "Do not click links", "Ask a friend")
                        else -> listOf("Content appears safe", "Stay vigilant")
                    },
                    extractedUrl = finalUrl,
                    extractedText = combinedText,
                    scanType = ScanType.IMAGE
                )
            }
            _state.value = ScanUiState.Result(result)
        } catch (e: Exception) {
            _state.value = ScanUiState.Error(e.message ?: "Image scan failed")
        }
    }

    // ── Camera scan (capture bitmap → OCR + QR → triage) ─────────────

    suspend fun scanCameraCapture(uri: Uri) {
        // Same pipeline as image scan — camera capture is saved to temp URI first
        scanImage(uri)
    }

    // ── Save result to Alerts (optional) ─────────────────────────────

    suspend fun saveToAlerts(result: ScanResult): String {
        return withContext(Dispatchers.IO) {
            alertRepo.createAlert(
                type = "manual",
                riskLevel = result.riskLevel.name,
                category = "unknown", // final category comes from Gemini on detail screen
                tacticsJson = result.reasons.joinToString(",", "[", "]") { "\"$it\"" },
                snippetRedacted = result.extractedText?.take(500) ?: "",
                extractedUrl = result.extractedUrl,
                headline = result.headline
            )
        }
    }

    // ── Reset ────────────────────────────────────────────────────────

    fun reset() {
        _state.value = ScanUiState.Idle
    }
}
