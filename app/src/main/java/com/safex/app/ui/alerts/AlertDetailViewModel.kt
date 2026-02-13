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
    private val functionsClient: CloudFunctionsClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertDetailUiState>(AlertDetailUiState.Loading)
    val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

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

            // 2. Call Gemini (only if not already loaded - for MVP we just call every time detail opens)
            // In a real app we might cache the explanation in the DB too.
            val request = ExplainAlertRequest(
                alertType = alert.type,
                category = alert.category,
                // tacticsJson is stored as a string, primitive parsing here or assume empty
                tactics = emptyList(), // TODO: proper JSON parsing if needed
                snippet = alert.snippetRedacted,
                extractedUrl = alert.extractedUrl,
                doSafeBrowsingCheck = alert.extractedUrl != null && alert.type == "manual" // Only auto-check SB if manual
            )

            val explanation = functionsClient.explainAlert(request)

            _uiState.value = AlertDetailUiState.Success(alert, explanation)
        }
    }

    fun reportAlert(onComplete: () -> Unit) {
        val state = _uiState.value as? AlertDetailUiState.Success ?: return

        viewModelScope.launch {
            // 1. Send report
            // We use the category from the *explanation* (Gemini refined) or the local alert?
            // PRD says explainAlert returns structured data. We'll use local alert + explanation info.
            functionsClient.reportAlert(
                ReportAlertRequest(
                    category = state.alert.category,
                    tactics = state.explanation.whyFlagged // Use whyFlagged as proxy for tactics in report
                )
            )

            // 2. Delete local alert
            repository.deleteAlert(alertId)
            
            // 3. Quit
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
        private val functionsClient: CloudFunctionsClient = CloudFunctionsClient.INSTANCE
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlertDetailViewModel(alertId, repository, functionsClient) as T
        }
    }
}
