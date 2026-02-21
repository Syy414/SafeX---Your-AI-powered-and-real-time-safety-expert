package com.safex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.safex.app.data.AlertRepository
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.local.SafeXDatabase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AlertViewModel(context: Context) : ViewModel() {
    private val database = SafeXDatabase.getInstance(context)
    private val repository = AlertRepository.getInstance(database)

    val alerts: Flow<List<AlertEntity>> = repository.alerts
    val weeklyCount: Flow<Int> = repository.weeklyAlertCount()
    
    // For manual handling of analysis requests
    private val functions = com.safex.app.data.CloudFunctionsClient.INSTANCE

    fun analyzeAlert(alertId: String, text: String, language: String) {
        viewModelScope.launch {
            // Check if already analyzed? (Optional, UI can check too)
            // But good to clear any previous loading state if we want to re-analyze
            
            val request = com.safex.app.data.models.ExplainAlertRequest(
                alertType = "manual_reanalysis",
                category = "unknown", // or fetch from alert
                snippet = text, // Full message mapped to snippet
                language = language
            )
            
            // Call Cloud Function (Mocked or Real)
            // For now, let's assume CloudFunctionsClient is ready or we mock it locally if needed
            // But since CloudFunctionsClient exists, we use it.
            
            try {
                // Determine category from alert if possible, but here we just need explanation
                // Actually ExplainAlertRequest needs category.
                // Let's fetch alert first to be safe
                val alert = repository.getAlert(alertId)
                val category = alert?.category ?: "unknown"
                
                val realRequest = request.copy(category = category)
                
                val response = functions.explainAlert(realRequest)
                
                // Store as JSON so AlertDetailViewModel.parseCachedGemini() and
                // the inline GeminiAnalysisSummaryView can both parse it correctly.
                val analysisJson = org.json.JSONObject().apply {
                    put("category", response.category)
                    put("riskLevel", response.riskLevel)
                    put("headline", response.headline)
                    put("whyFlagged", org.json.JSONArray(response.whyFlagged))
                    put("whatToDoNow", org.json.JSONArray(response.whatToDoNow))
                    put("whatNotToDo", org.json.JSONArray(response.whatNotToDo))
                    put("confidence", response.confidence)
                    put("notes", response.notes)
                }.toString()

                repository.updateGeminiAnalysis(
                    id = alertId,
                    analysis = analysisJson,
                    language = "en"
                )
                
            } catch (e: Exception) {
                // UI will just show "Analyze" button again or error state
                // Ideally we update DB with error or handle via a StateFlow, 
                // but for now, let's keep it simple: just try to update analysis.
            }
        }
    }
}
