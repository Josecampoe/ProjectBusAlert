package com.busalert.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.GeoPoint
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
import com.busalert.wear.voice.TripPhraseParser
import com.busalert.wear.voice.VoiceError
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pantallas/estados del flujo del reloj. Hay una sola pregunta: el GPS se busca
 * en segundo plano mientras el usuario habla.
 *
 * IDLE ─► ASKING_DESTINATION ⇄ TYPING_DESTINATION   (GPS + /api/stops/nearby en paralelo)
 *            │ "estoy en X y voy a Y" ─────────────────────────────┐
 *            │ "voy a Y" + GPS listo ──────────────────────────────┤
 *            │ "voy a Y" + GPS sin terminar ─► LOCATING ───────────┤ hay ubicación
 *            │                                  └─► ASKING_LOCATION ⇄ TYPING_LOCATION ─┤
 *            │                                      (sin GPS / sin paradas cerca)      │
 *            └──────────────────────────────────────────► PROCESSING ◄────────────────┘
 *                                                            └► RESULT | NO_RESULT | ERROR
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
    private val phraseParser: TripPhraseParser = TripPhraseParser.NONE,
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

    /** Búsqueda de la ubicación GPS que corre mientras el usuario habla. */
    private var gpsJob: Deferred<GpsFix>? = null

    /** Destino ya dicho que espera el origen (GPS o lugar dicho). */
    private var pendingDestination: String? = null

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
     * Inicia una consulta: pregunta de una vez "¿a dónde vas?" y, mientras el usuario
     * habla, busca la ubicación GPS y la parada más cercana (`/api/stops/nearby`).
     */
    fun startQuery() {
        cancelRequest()
        cancelGps()
        clearQuery()
        _appState.value = AppState.ASKING_DESTINATION
        gpsJob = viewModelScope.async { locate() }
    }

    /**
     * Busca la ubicación GPS. Si funciona y hay paradas cerca, la deja como origen
     * (a menos que el usuario ya haya dicho dónde está).
     */
    private suspend fun locate(): GpsFix {
        val point = locationSource.currentLocation() ?: return GpsFix.Unavailable
        val nearby = transitRepository.nearbyStops(point)
        if (nearby is Outcome.Success && nearby.value.nearest == null) {
            _noNearbyStops.value = true
            return GpsFix.NoStops
        }
        // Si la consulta falla (red), se sigue con las coordenadas: el backend
        // vuelve a resolver la parada al planificar y reporta el error si persiste.
        _nearestStop.value = (nearby as? Outcome.Success)?.value?.nearest
        if (_origin.value == null) _origin.value = TripOrigin.Located(point)
        return GpsFix.Found(point)
    }

    /** El usuario prefiere decir dónde está en lugar de esperar el GPS. */
    fun useSpokenLocation() {
        cancelRequest()
        cancelGps()
        _origin.value = null
        _nearestStop.value = null
        _appState.value = AppState.ASKING_LOCATION
    }

    fun onLocationReceived(location: String) {
        val text = location.trim()
        if (text.isEmpty()) return
        // "Estoy en Fátima y voy a la Cooperativa": se consulta de una vez.
        phraseParser.split(text)?.let { phrase ->
            _origin.value = TripOrigin.Named(phrase.origin)
            _noNearbyStops.value = false
            submitDestination(phrase.destination, TripOrigin.Named(phrase.origin))
            return
        }
        val origin = TripOrigin.Named(text)
        _origin.value = origin
        _noNearbyStops.value = false
        _typedText.value = ""
        _voiceNotice.value = null
        val pending = pendingDestination
        if (pending != null) {
            submitDestination(pending, origin)
        } else {
            _appState.value = AppState.ASKING_DESTINATION
        }
    }

    fun onDestinationReceived(destination: String) {
        val text = destination.trim()
        if (text.isEmpty()) return
        _typedText.value = ""
        _voiceNotice.value = null
        // "Estoy en X y voy a Y": lo dicho manda sobre el GPS.
        phraseParser.split(text)?.let { phrase ->
            val origin = TripOrigin.Named(phrase.origin)
            _origin.value = origin
            submitDestination(phrase.destination, origin)
            return
        }
        _origin.value?.let { origin ->
            submitDestination(text, origin)
            return
        }
        // Solo dijo el destino y el GPS aún no responde: se espera sin volver a preguntar.
        pendingDestination = text
        val gps = gpsJob
        if (gps == null) {
            _appState.value = AppState.ASKING_LOCATION
            return
        }
        _appState.value = AppState.LOCATING
        cancelRequest()
        requestJob = viewModelScope.launch {
            val fix = gps.await()
            if (_appState.value != AppState.LOCATING) return@launch
            if (fix is GpsFix.Found) {
                submitDestination(text, _origin.value ?: TripOrigin.Located(fix.point))
            } else {
                // Sin GPS o sin paradas cerca: ahora sí se pregunta dónde está.
                _appState.value = AppState.ASKING_LOCATION
            }
        }
    }

    private fun submitDestination(destination: String, origin: TripOrigin) {
        pendingDestination = null
        submit(TripQuery(destination = destination, origin = origin, language = preferences.value.language))
    }

    /** Reintenta la última consulta (por ejemplo tras un error de red). */
    fun retry() {
        val query = lastQuery
        if (query == null) startQuery() else submit(query.copy(language = preferences.value.language))
    }

    /** Cancela lo que esté en curso y vuelve al inicio. */
    fun resetToIdle() {
        cancelRequest()
        cancelGps()
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

    private fun cancelGps() {
        gpsJob?.cancel()
        gpsJob = null
    }

    private fun clearQuery() {
        pendingDestination = null
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
        private val phraseParser: TripPhraseParser,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainWearViewModel::class.java)) { "ViewModel desconocido: $modelClass" }
            return MainWearViewModel(repository, transitRepository, locationSource, preferencesRepository, phraseParser) as T
        }
    }
}

/** Resultado de la búsqueda GPS en segundo plano. */
private sealed interface GpsFix {
    data class Found(val point: GeoPoint) : GpsFix

    /** Hay ubicación, pero ninguna parada con coordenadas cerca. */
    data object NoStops : GpsFix

    /** Sin permiso, sin señal o sin respuesta a tiempo. */
    data object Unavailable : GpsFix
}
