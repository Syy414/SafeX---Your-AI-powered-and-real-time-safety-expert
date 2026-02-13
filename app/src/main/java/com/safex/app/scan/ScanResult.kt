package com.safex.app.scan

/**
 * Result of a manual scan (link / image / camera).
 */
data class ScanResult(
    val riskLevel: RiskLevel,
    val headline: String,
    val reasons: List<String>,
    val nextSteps: List<String>,
    val extractedUrl: String? = null,
    val extractedText: String? = null,
    val scanType: ScanType
)

enum class RiskLevel { HIGH, MEDIUM, LOW, SAFE, UNKNOWN }

enum class ScanType { LINK, IMAGE, CAMERA }

/**
 * UI state for scan flow.
 */
sealed class ScanUiState {
    data object Idle : ScanUiState()
    data object Scanning : ScanUiState()
    data class Result(val result: ScanResult) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
