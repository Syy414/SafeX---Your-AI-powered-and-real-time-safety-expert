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
            withContext(Dispatchers.IO) {
                // TODO: Replace with real Safe Browsing call via CloudFunctionsClient
                // when Agent 6 ships the checkLink callable.
                val result = ScanResult(
                    riskLevel = RiskLevel.UNKNOWN,
                    headline = "Link check — backend not ready yet",
                    reasons = listOf(
                        "🔗 URL submitted: $url",
                        "⏳ Safe Browsing backend is not deployed yet.",
                        "Result will be available once the backend is connected."
                    ),
                    nextSteps = listOf(
                        "⚠️ Do not enter passwords or OTP on unfamiliar sites",
                        "🔍 Check the domain manually on Google Safe Browsing Transparency Report"
                    ),
                    extractedUrl = url,
                    scanType = ScanType.LINK
                )
                _state.value = ScanUiState.Result(result)
            }
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

                val triageResult = TextTriageEngine.triage(combinedText, ScanType.IMAGE)

                // Override extractedUrl with QR URL if OCR didn't find one
                if (triageResult.extractedUrl == null && qrUrls.isNotEmpty()) {
                    triageResult.copy(extractedUrl = qrUrls.first())
                } else {
                    triageResult
                }
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

    suspend fun saveToAlerts(result: ScanResult) {
        withContext(Dispatchers.IO) {
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
