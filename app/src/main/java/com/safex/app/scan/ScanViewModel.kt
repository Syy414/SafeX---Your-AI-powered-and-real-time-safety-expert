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

    // ── Paste link scan ──────────────────────────────────────────────

    suspend fun scanLink(url: String, language: String = "en") {
        _state.value = ScanUiState.Scanning
        try {
            // Call the Gemini-powered checkLink Cloud Function
            val response = com.safex.app.data.CloudFunctionsClient.INSTANCE.checkLink(url, language)
            
            val finalRiskLevel = try { 
                RiskLevel.valueOf(response.riskLevel) 
            } catch (_: Exception) { 
                RiskLevel.UNKNOWN 
            }

            // Build Gemini analysis JSON for the "See Why" alert detail screen
            val geminiAnalysisJson = try {
                org.json.JSONObject().apply {
                    put("category",    response.category)
                    put("riskLevel",   response.riskLevel)
                    put("headline",    response.headline)
                    put("confidence",  response.confidence)
                    put("notes",       "")
                    put("whyFlagged",  org.json.JSONArray(response.whyFlagged.ifEmpty { response.reasons }))
                    put("whatToDoNow", org.json.JSONArray(response.whatToDoNow))
                    put("whatNotToDo", org.json.JSONArray(response.whatNotToDo))
                }.toString()
            } catch (e: Exception) { null }

            val result = ScanResult(
                riskLevel = finalRiskLevel,
                headline = response.headline,
                reasons = response.whyFlagged.ifEmpty { response.reasons },
                nextSteps = response.whatToDoNow.ifEmpty {
                    if (response.safe)
                        listOf(
                            context.getString(com.safex.app.R.string.scan_safe_step1),
                            context.getString(com.safex.app.R.string.scan_safe_step2)
                        )
                    else
                        listOf(
                            context.getString(com.safex.app.R.string.scan_danger_step1),
                            context.getString(com.safex.app.R.string.scan_danger_step2)
                        )
                },
                extractedUrl = url,
                scanType = ScanType.LINK,
                geminiAnalysis = geminiAnalysisJson
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

                // Bypass Hybrid Engine completely for manual scans.
                // Go straight to Gemini since user explicitly requested it.
                val language = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration).get(0)?.language ?: "en"
                val request = com.safex.app.data.models.ExplainAlertRequest(
                    alertType            = "manual_scan",
                    language             = language,
                    category             = "unknown",
                    tactics              = emptyList(),
                    snippet              = combinedText.take(500),
                    extractedUrl         = null,
                    doSafeBrowsingCheck  = false
                )
                val response = com.safex.app.data.CloudFunctionsClient.INSTANCE.explainAlert(request)

                // 4) Map Cloud Function Response -> ScanResult
                val finalRiskLevel = try {
                    RiskLevel.valueOf(response.riskLevel.uppercase())
                } catch (_: Exception) {
                    RiskLevel.UNKNOWN
                }

                // Determine URL (QR code priority, else Regex find)
                val finalUrl = if (qrUrls.isNotEmpty()) {
                    qrUrls.first()
                } else {
                    Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)
                            .find(combinedText)?.value
                }

                // Create JSON string manually to mimic Hybrid Engine cache for AlertDetail
                val geminiAnalysisJson = try {
                    org.json.JSONObject().apply {
                        put("category",    response.category)
                        put("riskLevel",   response.riskLevel)
                        put("headline",    response.headline)
                        put("confidence",  response.confidence)
                        put("notes",       response.notes)
                        put("whyFlagged",  org.json.JSONArray(response.whyFlagged))
                        put("whatToDoNow", org.json.JSONArray(response.whatToDoNow))
                        put("whatNotToDo", org.json.JSONArray(response.whatNotToDo))
                    }.toString()
                } catch(e: Exception) { null }

                ScanResult(
                    riskLevel = finalRiskLevel,
                    headline = response.headline,
                    // Use Gemini's whyFlagged instead of heuristics tactics
                    reasons = response.whyFlagged.ifEmpty { listOf("Analysis completed") },
                    // Use Gemini's whatToDoNow instead of hardcoded strings
                    nextSteps = response.whatToDoNow.ifEmpty {
                        when (finalRiskLevel) {
                            RiskLevel.HIGH   -> listOf("Do not trust this content", "Block sender", "Delete immediately")
                            RiskLevel.MEDIUM -> listOf("Verify source carefully", "Do not click links", "Ask a friend")
                            else             -> listOf("Content appears safe", "Stay vigilant")
                        }
                    },
                    extractedUrl   = finalUrl,
                    extractedText  = combinedText,
                    scanType       = ScanType.IMAGE,
                    geminiAnalysis = geminiAnalysisJson
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
            // User feedback: "boxes appear at alert are the ones which are auto detected"
            // So manual scans should NOT appear in the main list.
            // We use a special type "manual_hidden" to filter them out in UI/Repo,
            // but still count them in weekly stats if needed.
            alertRepo.createAlert(
                type = "manual_hidden",
                riskLevel = result.riskLevel.name,
                category = "unknown",
                tacticsJson = result.reasons.joinToString(",", "[", "]") { "\"$it\"" },
                snippetRedacted = result.extractedText?.take(500) ?: "",
                extractedUrl = result.extractedUrl,
                headline = result.headline,
                sender = "Manual Scan",
                fullMessage = result.extractedText,
                geminiAnalysis = result.geminiAnalysis
            )
        }
    }

    // ── Reset ────────────────────────────────────────────────────────

    fun reset() {
        _state.value = ScanUiState.Idle
    }
}
