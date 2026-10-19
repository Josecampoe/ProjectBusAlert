package com.busalert.wear

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class UiState {
    object Idle : UiState()
    object Listening : UiState()
    object Typing : UiState()
    object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Error(val error: String) : UiState()
}

class MainWearViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState
}
