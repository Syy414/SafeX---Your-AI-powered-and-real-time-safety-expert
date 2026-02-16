package com.safex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safex.app.data.AlertRepository
import com.safex.app.data.local.AlertEntity
import com.safex.app.data.local.SafeXDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertViewModel(context: Context) {

    private val repository = AlertRepository.getInstance(SafeXDatabase.getInstance(context))

    val alerts: StateFlow<List<AlertEntity>> = repository.alerts
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO), // Ideally viewModelScope, but this is a simple class
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val weeklyCount: StateFlow<Int> = repository.weeklyAlertCount()
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun deleteAlert(id: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.deleteAlert(id)
        }
    }
}
