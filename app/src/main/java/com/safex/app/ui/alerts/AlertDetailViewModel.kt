package com.safex.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safex.app.data.AlertRepository
import com.safex.app.data.CloudFunctionsClient
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.models.ExplainAlertRequest
import com.safex.app.data.models.ExplainAlertResponse
import com.safex.app.data.models.ReportAlertRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AlertDetailUiState {
    data object Loading : AlertDetailUiState()
    data class Success(
        val alert: AlertEntity,
        val explanation: ExplainAlertResponse
    ) : AlertDetailUiState()
    data class Error(val message: String) : AlertDetailUiState()
}

class AlertDetailViewModel(
    private val alertId: String,
    private val repository: AlertRepository,
    private val functionsClient: CloudFunctionsClient,
    private var languageCode: String = "en"   // current app language (BCP-47 base tag)
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertDetailUiState>(AlertDetailUiState.Loading)
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

    private var baseExplanation: ExplainAlertResponse? = null
    private var currentAlert: AlertEntity? = null

    init {
        loadAndExplain()
    }

    private fun loadAndExplain() {
        viewModelScope.launch {
            // 1. Load local alert
            val alert = repository.getAlert(alertId)
            if (alert == null) {
                _uiState.value = AlertDetailUiState.Error("Alert not found.")
                return@launch
            }
            currentAlert = alert

            // 2. Use cached English Gemini analysis if stored
            val cachedJson = alert.geminiAnalysis
            if (!cachedJson.isNullOrBlank()) {
                val explanation = parseCachedGemini(cachedJson)
                if (explanation != null) {
                    baseExplanation = explanation
                    _uiState.value = AlertDetailUiState.Success(alert, ensureTranslated(explanation))
                    return@launch
                }
            }

            // 3. No cache / language mismatch — call Gemini with the correct language
            val request = ExplainAlertRequest(
                alertType = alert.type,
                category = alert.category,
                tactics = emptyList(),
                snippet = alert.snippetRedacted,
                extractedUrl = alert.extractedUrl,
                doSafeBrowsingCheck = alert.extractedUrl != null && alert.type == "manual",
                language = languageCode,
                heuristicScore = alert.heuristicScore,
                tfliteScore = alert.tfliteScore
            )

            try {
                val explanation = functionsClient.explainAlert(request)
                baseExplanation = explanation
                _uiState.value = AlertDetailUiState.Success(alert, ensureTranslated(explanation))
            } catch (e: Exception) {
                _uiState.value = AlertDetailUiState.Error(e.message ?: "Failed to load explanation")
            }
        }
    }

    fun updateLanguage(newLang: String) {
        if (newLang != languageCode) {
            languageCode = newLang
            val baseExp = baseExplanation
            val alert = currentAlert
            if (baseExp != null && alert != null) {
                viewModelScope.launch {
                    _uiState.value = AlertDetailUiState.Success(alert, ensureTranslated(baseExp))
                }
            }
        }
    }

    /**
     * Fallback ML Kit translation. If Gemini failed to follow the language prompt,
     * or if the database cached an English result, this provides an instant local translation guarantee.
     */
    private suspend fun ensureTranslated(explanation: ExplainAlertResponse): ExplainAlertResponse {
        val hl = com.safex.app.data.MlKitTranslator.translate(explanation.headline, languageCode)
        val wf = explanation.whyFlagged.map { com.safex.app.data.MlKitTranslator.translate(it, languageCode) }
        val wtdn = explanation.whatToDoNow.map { com.safex.app.data.MlKitTranslator.translate(it, languageCode) }
        val wntd = explanation.whatNotToDo.map { com.safex.app.data.MlKitTranslator.translate(it, languageCode) }
        val notes = com.safex.app.data.MlKitTranslator.translate(explanation.notes, languageCode)
        return explanation.copy(
            headline = hl,
            whyFlagged = wf,
            whatToDoNow = wtdn,
            whatNotToDo = wntd,
            notes = notes
        )
    }

    /**
     * Parses the JSON string cached by HybridTriageEngine (Level 3) back into a response object.
     * Returns null if parsing fails so the caller can fall back to a fresh Gemini call.
     */
    private suspend fun parseCachedGemini(json: String): ExplainAlertResponse? {
        return try {
            val obj = org.json.JSONObject(json)
            fun jsonArrayToList(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.getString(it) }
            }
            ExplainAlertResponse(
                category    = obj.optString("category", "Unknown"),
                riskLevel   = obj.optString("riskLevel", "MEDIUM"),
                headline    = obj.optString("headline", "Suspicious activity detected"),
                whyFlagged  = jsonArrayToList("whyFlagged"),
                whatToDoNow = jsonArrayToList("whatToDoNow"),
                whatNotToDo = jsonArrayToList("whatNotToDo"),
                confidence  = obj.optDouble("confidence", 0.5),
                notes       = obj.optString("notes", "")
            )
        } catch (e: Exception) {
            null  // fall through to live Gemini call
        }
    }

    fun reportAlert(onComplete: () -> Unit) {
        val state = _uiState.value as? AlertDetailUiState.Success ?: return
        viewModelScope.launch {
            functionsClient.reportAlert(
                ReportAlertRequest(
                    category = state.explanation.category,
                    tactics = state.explanation.whyFlagged
                )
            )
            repository.deleteAlert(alertId)
            onComplete()
        }
    }

    fun markSafe(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAlert(alertId)
            onComplete()
        }
    }

    class Factory(
        private val alertId: String,
        private val repository: AlertRepository,
        private val languageCode: String = "en",
        private val functionsClient: CloudFunctionsClient = CloudFunctionsClient.INSTANCE
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlertDetailViewModel(alertId, repository, functionsClient, languageCode) as T
        }
    }
}
