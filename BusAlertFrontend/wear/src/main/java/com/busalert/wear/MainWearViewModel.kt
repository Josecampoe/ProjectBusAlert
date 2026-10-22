package com.busalert.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pantallas/estados del flujo del reloj:
 *
 * IDLE → ASKING_LOCATION (voz) ⇄ TYPING_LOCATION (teclado)
 *      → ASKING_DESTINATION (voz) ⇄ TYPING_DESTINATION (teclado)
 *      → PROCESSING → RESULT | NO_RESULT | ERROR
 */
enum class AppState {
    IDLE,
    ASKING_LOCATION,
    ASKING_DESTINATION,
    TYPING_LOCATION,
    TYPING_DESTINATION,
    PROCESSING,
    RESULT,
    NO_RESULT,
    ERROR,
}

class MainWearViewModel(
    private val repository: TripAssistantRepository,
) : ViewModel() {

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _plan = MutableStateFlow<TripPlan?>(null)
    val plan: StateFlow<TripPlan?> = _plan.asStateFlow()

    private val _error = MutableStateFlow<ErrorCode?>(null)
    val error: StateFlow<ErrorCode?> = _error.asStateFlow()

    private val _typedText = MutableStateFlow("")
    val typedText: StateFlow<String> = _typedText.asStateFlow()

    private val _capturedLocation = MutableStateFlow<String?>(null)
    val capturedLocation: StateFlow<String?> = _capturedLocation.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.SPANISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Última consulta enviada, para reintentar sin volver a preguntar. */
    private var lastQuery: TripQuery? = null
    private var requestJob: Job? = null

    // ── Preferencias ──────────────────────────────────────────────────────

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
    }

    // ── Flujo de consulta ─────────────────────────────────────────────────

    fun startQuery() {
        clearQuery()
        _appState.value = AppState.ASKING_LOCATION
    }

    fun onLocationReceived(location: String) {
        val text = location.trim()
        if (text.isEmpty()) return
        _capturedLocation.value = text
        _typedText.value = ""
        _appState.value = AppState.ASKING_DESTINATION
    }

    fun onDestinationReceived(destination: String) {
        val text = destination.trim()
        val origin = _capturedLocation.value
        if (text.isEmpty()) return
        if (origin == null) {
            // No debería ocurrir: sin origen se vuelve a preguntar en vez de suponer uno.
            _appState.value = AppState.ASKING_LOCATION
            return
        }
        _typedText.value = ""
        submit(TripQuery(destination = text, origin = origin, language = _language.value))
    }

    /** Reintenta la última consulta (por ejemplo tras un error de red). */
    fun retry() {
        val query = lastQuery
        if (query == null) startQuery() else submit(query.copy(language = _language.value))
    }

    /** Cancela lo que esté en curso y vuelve al inicio. */
    fun resetToIdle() {
        requestJob?.cancel()
        requestJob = null
        clearQuery()
        lastQuery = null
        _appState.value = AppState.IDLE
    }

    private fun submit(query: TripQuery) {
        lastQuery = query
        _error.value = null
        _plan.value = null
        _appState.value = AppState.PROCESSING
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            when (val outcome = repository.planTrip(query)) {
                is Outcome.Success -> {
                    _plan.value = outcome.value
                    _appState.value = if (outcome.value.status == TripStatus.OK) AppState.RESULT else AppState.NO_RESULT
                }
                is Outcome.Failure -> {
                    _error.value = outcome.error
                    _appState.value = AppState.ERROR
                }
            }
        }
    }

    private fun clearQuery() {
        _capturedLocation.value = null
        _plan.value = null
        _error.value = null
        _typedText.value = ""
    }

    // ── Alternancia voz / teclado ─────────────────────────────────────────

    fun onLocationSpeechFailed() {
        _appState.value = AppState.TYPING_LOCATION
    }

    fun onDestinationSpeechFailed() {
        _appState.value = AppState.TYPING_DESTINATION
    }

    fun retryLocationSpeech() {
        _appState.value = AppState.ASKING_LOCATION
    }

    fun retryDestinationSpeech() {
        _appState.value = AppState.ASKING_DESTINATION
    }

    fun onTypedTextChange(text: String) {
        _typedText.value = text
    }

    fun submitTypedLocation() = onLocationReceived(_typedText.value)

    fun submitTypedDestination() = onDestinationReceived(_typedText.value)

    class Factory(private val repository: TripAssistantRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainWearViewModel::class.java)) { "ViewModel desconocido: $modelClass" }
            return MainWearViewModel(repository) as T
        }
    }
}
