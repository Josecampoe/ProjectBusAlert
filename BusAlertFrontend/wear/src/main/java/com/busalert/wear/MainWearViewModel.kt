package com.busalert.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.NearbyStop
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripOrigin
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.PreferenceDefaults
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.UserPreferences
import com.busalert.shared.preferences.recentPlaces
import com.busalert.wear.location.LocationSource
import com.busalert.wear.voice.VoiceError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pantallas/estados del flujo del reloj:
 *
 * IDLE ─► LOCATING (GPS + /api/stops/nearby) ──────────┐ hay parada cercana
 *      └► ASKING_LOCATION (voz) ⇄ TYPING_LOCATION         │ sin permiso / sin GPS / sin paradas cerca
 *          └────────────► ASKING_DESTINATION ⇄ TYPING_DESTINATION ◄┘
 *                              └► PROCESSING ─► RESULT | NO_RESULT | ERROR
 * IDLE ⇄ SETTINGS
 */
enum class AppState {
    IDLE,
    SETTINGS,
    LOCATING,
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
    private val transitRepository: TransitRepository,
    private val locationSource: LocationSource,
    private val preferencesRepository: PreferencesRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _plan = MutableStateFlow<TripPlan?>(null)
    val plan: StateFlow<TripPlan?> = _plan.asStateFlow()

    private val _error = MutableStateFlow<ErrorCode?>(null)
    val error: StateFlow<ErrorCode?> = _error.asStateFlow()

    private val _typedText = MutableStateFlow("")
    val typedText: StateFlow<String> = _typedText.asStateFlow()

    /** Origen elegido: ubicación GPS o lugar dicho/escrito. */
    private val _origin = MutableStateFlow<TripOrigin?>(null)
    val origin: StateFlow<TripOrigin?> = _origin.asStateFlow()

    /** Parada más cercana a la ubicación GPS (se muestra al preguntar el destino). */
    private val _nearestStop = MutableStateFlow<NearbyStop?>(null)
    val nearestStop: StateFlow<NearbyStop?> = _nearestStop.asStateFlow()

    /** true si el GPS funcionó pero no hay paradas ubicables cerca: se pide el lugar. */
    private val _noNearbyStops = MutableStateFlow(false)
    val noNearbyStops: StateFlow<Boolean> = _noNearbyStops.asStateFlow()

    /** Último problema del reconocedor de voz (se muestra como aviso). */
    private val _voiceNotice = MutableStateFlow<VoiceError?>(null)
    val voiceNotice: StateFlow<VoiceError?> = _voiceNotice.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    /** Sugerencias al escribir: paradas favoritas primero y luego lugares recientes. */
    val recentPlaces: StateFlow<List<String>> = combine(
        preferencesRepository.favorites,
        preferencesRepository.history,
    ) { favorites, history ->
        (favorites.filter { it.kind == FavoriteKind.STOP }.map { it.label } + history.recentPlaces())
            .distinctBy { it.lowercase() }
            .take(PreferenceDefaults.MAX_RECENT_PLACES)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Última consulta enviada, para reintentar sin volver a preguntar. */
    private var lastQuery: TripQuery? = null
    private var requestJob: Job? = null

    // ── Preferencias y ajustes ────────────────────────────────────────────

    fun openSettings() {
        _appState.value = AppState.SETTINGS
    }

    fun closeSettings() {
        _appState.value = AppState.IDLE
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkTheme(enabled) }
    }

    fun setSpeakResults(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSpeakResults(enabled) }
    }

    fun toggleLanguage() {
        val next = if (preferences.value.language == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
        viewModelScope.launch { preferencesRepository.setLanguage(next) }
    }

    fun clearHistory() {
        viewModelScope.launch { preferencesRepository.clearHistory() }
    }

    // ── Flujo de consulta ─────────────────────────────────────────────────

    /**
     * Inicia una consulta: primero intenta el GPS y busca la parada más cercana
     * con `/api/stops/nearby`. Si no hay permiso, señal, respuesta a tiempo o
     * ninguna parada ubicable cerca, pide el lugar por voz (y teclado como alternativa).
     */
    fun startQuery() {
        cancelRequest()
        clearQuery()
        _appState.value = AppState.LOCATING
        requestJob = viewModelScope.launch {
            val point = locationSource.currentLocation()
            if (_appState.value != AppState.LOCATING) return@launch
            if (point == null) {
                _appState.value = AppState.ASKING_LOCATION
                return@launch
            }
            val nearby = transitRepository.nearbyStops(point)
            if (_appState.value != AppState.LOCATING) return@launch
            if (nearby is Outcome.Success && nearby.value.nearest == null) {
                _noNearbyStops.value = true
                _appState.value = AppState.ASKING_LOCATION
                return@launch
            }
            // Si la consulta falla (red), se sigue con las coordenadas: el backend
            // vuelve a resolver la parada al planificar y reporta el error si persiste.
            _nearestStop.value = (nearby as? Outcome.Success)?.value?.nearest
            _origin.value = TripOrigin.Located(point)
            _appState.value = AppState.ASKING_DESTINATION
        }
    }

    /** El usuario prefiere decir dónde está en lugar de usar el GPS. */
    fun useSpokenLocation() {
        cancelRequest()
        _origin.value = null
        _nearestStop.value = null
        _appState.value = AppState.ASKING_LOCATION
    }

    fun onLocationReceived(location: String) {
        val text = location.trim()
        if (text.isEmpty()) return
        _origin.value = TripOrigin.Named(text)
        _noNearbyStops.value = false
        _typedText.value = ""
        _voiceNotice.value = null
        _appState.value = AppState.ASKING_DESTINATION
    }

    fun onDestinationReceived(destination: String) {
        val text = destination.trim()
        val origin = _origin.value
        if (text.isEmpty()) return
        if (origin == null) {
            // Sin origen se vuelve a preguntar en vez de suponer uno.
            _appState.value = AppState.ASKING_LOCATION
            return
        }
        _typedText.value = ""
        _voiceNotice.value = null
        submit(TripQuery(destination = text, origin = origin, language = preferences.value.language))
    }

    /** Reintenta la última consulta (por ejemplo tras un error de red). */
    fun retry() {
        val query = lastQuery
        if (query == null) startQuery() else submit(query.copy(language = preferences.value.language))
    }

    /** Cancela lo que esté en curso y vuelve al inicio. */
    fun resetToIdle() {
        cancelRequest()
        clearQuery()
        lastQuery = null
        _appState.value = AppState.IDLE
    }

    private fun submit(query: TripQuery) {
        lastQuery = query
        _error.value = null
        _plan.value = null
        _appState.value = AppState.PROCESSING
        cancelRequest()
        requestJob = viewModelScope.launch {
            when (val outcome = repository.planTrip(query)) {
                is Outcome.Success -> {
                    val result = outcome.value
                    _plan.value = result
                    _appState.value = if (result.status == TripStatus.OK) AppState.RESULT else AppState.NO_RESULT
                    if (result.status == TripStatus.OK) saveToHistory(query, result)
                }
                is Outcome.Failure -> {
                    _error.value = outcome.error
                    _appState.value = AppState.ERROR
                }
            }
        }
    }

    private suspend fun saveToHistory(query: TripQuery, result: TripPlan) {
        preferencesRepository.addToHistory(
            QueryHistoryEntry(
                originLabel = (query.origin as? TripOrigin.Named)?.name,
                destination = query.destination,
                routeCodes = result.recommended?.legs?.map { it.routeCode }.orEmpty(),
                timestampMillis = clock(),
            ),
        )
    }

    private fun cancelRequest() {
        requestJob?.cancel()
        requestJob = null
    }

    private fun clearQuery() {
        _origin.value = null
        _nearestStop.value = null
        _noNearbyStops.value = false
        _plan.value = null
        _error.value = null
        _typedText.value = ""
        _voiceNotice.value = null
    }

    // ── Alternancia voz / teclado ─────────────────────────────────────────

    /** Texto final del reconocedor: es el origen o el destino según la pregunta en pantalla. */
    fun onSpeechResult(text: String) {
        when (_appState.value) {
            AppState.ASKING_LOCATION -> onLocationReceived(text)
            AppState.ASKING_DESTINATION -> onDestinationReceived(text)
            else -> Unit // Resultado tardío de una escucha ya cancelada.
        }
    }

    /** Error del reconocedor: se muestra el aviso y, si conviene, se pasa al teclado. */
    fun onVoiceError(error: VoiceError) {
        _voiceNotice.value = error
        if (error.suggestsKeyboard) switchToKeyboard()
    }

    fun switchToKeyboard() {
        _appState.value = when (_appState.value) {
            AppState.ASKING_DESTINATION -> AppState.TYPING_DESTINATION
            AppState.ASKING_LOCATION -> AppState.TYPING_LOCATION
            else -> _appState.value
        }
    }

    fun switchToVoice() {
        _voiceNotice.value = null
        _appState.value = when (_appState.value) {
            AppState.TYPING_DESTINATION -> AppState.ASKING_DESTINATION
            AppState.TYPING_LOCATION -> AppState.ASKING_LOCATION
            else -> _appState.value
        }
    }

    fun onTypedTextChange(text: String) {
        _typedText.value = text
    }

    /** Envía lo escrito como origen o destino, según la pantalla actual. */
    fun submitTypedText() {
        when (_appState.value) {
            AppState.TYPING_LOCATION -> onLocationReceived(_typedText.value)
            AppState.TYPING_DESTINATION -> onDestinationReceived(_typedText.value)
            else -> Unit
        }
    }

    class Factory(
        private val repository: TripAssistantRepository,
        private val transitRepository: TransitRepository,
        private val locationSource: LocationSource,
        private val preferencesRepository: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainWearViewModel::class.java)) { "ViewModel desconocido: $modelClass" }
            return MainWearViewModel(repository, transitRepository, locationSource, preferencesRepository) as T
        }
    }
}
