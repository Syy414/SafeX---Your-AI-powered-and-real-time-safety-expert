package com.safex.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safex.app.data.AlertRepository
import com.safex.app.data.InsightsRepository
import com.safex.app.data.InsightsWeekly
import com.safex.app.data.local.CategoryCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InsightsUiState(
    val personalWeeklyCount: Int = 0,
    val personalTopCategories: List<CategoryCount> = emptyList(),
    val personalTopTactics: List<CategoryCount> = emptyList(),
    val communityWeekly: InsightsWeekly? = null,
    val communityLoading: Boolean = false,
    val communityError: String? = null
)

class InsightsViewModel(
    private val alertRepository: AlertRepository,
    private val insightsRepository: InsightsRepository = InsightsRepository()
) : ViewModel() {

    // Internal mutable state for community data (fetched once)
    private val _communityState = MutableStateFlow<CommunityState>(CommunityState.Loading)

    // Combine local streams + community state into one UI state
    val uiState: StateFlow<InsightsUiState> = combine(
        alertRepository.weeklyAlertCount(),
        alertRepository.getWeeklyCategories(),
        alertRepository.getWeeklyTactics(),
        _communityState
    ) { count, categories, tactics, commState ->
        InsightsUiState(
            personalWeeklyCount = count,
            personalTopCategories = categories.take(3), // Top 3 only
            personalTopTactics = tactics.take(3),       // Top 3 only
            communityWeekly = (commState as? CommunityState.Success)?.data,
            communityLoading = commState is CommunityState.Loading,
            communityError = (commState as? CommunityState.Error)?.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(communityLoading = true)
    )

    init {
        loadCommunityInsights()
    }

    private fun loadCommunityInsights() {
        viewModelScope.launch {
            _communityState.value = CommunityState.Loading
            val result = insightsRepository.fetchCurrentWeek()
            _communityState.value = when (result) {
                is InsightsRepository.Result.Success -> CommunityState.Success(result.data)
                is InsightsRepository.Result.Empty -> CommunityState.Empty
                is InsightsRepository.Result.Error -> CommunityState.Error(result.message)
            }
        }
    }

    // Helper sealed class for internal community fetch state
    private sealed class CommunityState {
        object Loading : CommunityState()
        object Empty : CommunityState()
        data class Success(val data: InsightsWeekly) : CommunityState()
        data class Error(val message: String) : CommunityState()
    }

    // Factory for manual dependency injection (since no Hilt yet)
    class Factory(private val alertRepository: AlertRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(alertRepository) as T
        }
    }
}
