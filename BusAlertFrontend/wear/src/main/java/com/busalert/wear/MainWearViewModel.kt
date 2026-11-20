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
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.PreferenceDefaults
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.SavedTrip
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pantallas/estados del flujo del reloj. Hay una sola pregunta ("¿a dónde vas?"):
 * el origen sale del GPS (parada más cercana y barrio), que se busca mientras
 * el usuario habla. Solo si el reloj no logra ubicarse se pregunta dónde está.
 *
 * IDLE ─► ASKING_DESTINATION ⇄ TYPING_DESTINATION   (GPS + /api/stops/nearby en paralelo)
 *            │ "estoy en X y voy a Y" ─────────────────────────────┐
 *            │ "voy a Y" + GPS listo ──────────────────────────────┤
 *            │ "voy a Y" + GPS sin terminar ─► LOCATING ───────────┤ hay ubicación
 *            │                                  └─► ASKING_LOCATION ⇄ TYPING_LOCATION ─┤
 *            │                                      (sin permiso o sin señal GPS)      │
 *            └──────────────────────────────────────────► PROCESSING ◄────────────────┘
 *                                                            └► RESULT | NO_RESULT | ERROR
 * IDLE ⇄ SETTINGS
 * IDLE ⇄ FAVORITES ⇄ FAVORITE_DETAIL   (ruta guardada: no consulta ni pregunta nada)
 */
enum class AppState {
    IDLE,
    SETTINGS,
    FAVORITES,
    FAVORITE_DETAIL,
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

    /** Barrio donde está el usuario según el GPS (si el backend lo reconoce). */
    private val _neighborhood = MutableStateFlow<String?>(null)
    val neighborhood: StateFlow<String?> = _neighborhood.asStateFlow()

    /** Destino de la consulta en pantalla (para guardarla en favoritos). */
    private val _currentDestination = MutableStateFlow<String?>(null)

    /** Favorito abierto en el detalle (ruta guardada). */
    private val _selectedFavorite = MutableStateFlow<Favorite?>(null)
    val selectedFavorite: StateFlow<Favorite?> = _selectedFavorite.asStateFlow()

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

    /** Viajes guardados en favoritos, del más reciente al más antiguo. */
    val tripFavorites: StateFlow<List<Favorite>> = preferencesRepository.favorites
        .map { list -> list.filter { it.kind == FavoriteKind.TRIP } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** true si el viaje del resultado en pantalla ya está en favoritos. */
    val isResultFavorite: StateFlow<Boolean> = combine(tripFavorites, _currentDestination) { favorites, destination ->
        destination != null && favorites.any { it.id.equals(destination, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    // ── Favoritos ─────────────────────────────────────────────────────────

    fun openFavorites() {
        _selectedFavorite.value = null
        _appState.value = AppState.FAVORITES
    }

    /** Muestra la ruta guardada de un favorito tal cual: no consulta ni pregunta nada. */
    fun openFavorite(favorite: Favorite) {
        _selectedFavorite.value = favorite
        _appState.value = AppState.FAVORITE_DETAIL
    }

    /** Guarda el viaje del resultado en favoritos o, si ya estaba, lo quita. */
    fun toggleResultFavorite() {
        val destination = _currentDestination.value ?: return
        val existing = tripFavorites.value.firstOrNull { it.id.equals(destination, ignoreCase = true) }
        val favorite = existing ?: tripFavorite(destination, _plan.value, origin = originLabel()) ?: return
        viewModelScope.launch { preferencesRepository.toggleFavorite(favorite) }
    }

    /** Quita un favorito y vuelve a la lista. */
    fun removeFavorite(favorite: Favorite) {
        viewModelScope.launch { preferencesRepository.toggleFavorite(favorite) }
        openFavorites()
    }

    /** De dónde salió la consulta: barrio del GPS, lugar dicho o null (se usa la parada). */
    private fun originLabel(): String? =
        _neighborhood.value ?: _plan.value?.originPlace ?: (lastQuery?.origin as? TripOrigin.Named)?.name

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
     * Busca la ubicación GPS y la deja como origen (a menos que el usuario ya haya
     * dicho dónde está). La parada y el barrio cercanos solo se muestran: aunque no
     * haya paradas a pocos metros, el backend busca la más cercana al planificar.
     */
    private suspend fun locate(): GpsFix {
        val point = locationSource.currentLocation() ?: return GpsFix.Unavailable
        if (_origin.value == null) _origin.value = TripOrigin.Located(point)
        // Si la consulta falla (red), se sigue con las coordenadas: el backend
        // vuelve a resolver la parada al planificar y reporta el error si persiste.
        (transitRepository.nearbyStops(point) as? Outcome.Success)?.value?.let { nearby ->
            _nearestStop.value = nearby.nearest
            _neighborhood.value = nearby.neighborhood
        }
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
            submitDestination(phrase.destination, TripOrigin.Named(phrase.origin))
            return
        }
        val origin = TripOrigin.Named(text)
        _origin.value = origin
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
                // El reloj no pudo ubicarse (sin permiso o sin señal): solo entonces se pregunta.
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
        _currentDestination.value = query.destination
        _error.value = null
        _plan.value = null
        _appState.value = AppState.PROCESSING
        cancelRequest()
        requestJob = viewModelScope.launch {
            when (val outcome = repository.planTrip(query)) {
                is Outcome.Success -> {
                    val result = outcome.value
                    _plan.value = result
                    result.originNeighborhood?.let { _neighborhood.value = it }
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
        _neighborhood.value = null
        _currentDestination.value = null
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

    /** Sin permiso, sin señal o sin respuesta a tiempo. */
    data object Unavailable : GpsFix
}

/**
 * Viaje favorito con la ruta recomendada guardada, p. ej.
 * "Fátima → Universidad Cooperativa" en la C5. [origin] es el barrio o lugar de
 * salida; si no se conoce, se usa la parada donde se sube.
 */
internal fun tripFavorite(destination: String, plan: TripPlan?, origin: String?): Favorite? {
    val option = plan?.recommended ?: return null
    val first = option.firstLeg
    val last = option.legs.last()
    val trip = SavedTrip(
        from = origin?.trim()?.takeIf { it.isNotEmpty() } ?: first.board.name,
        to = plan.destinationPlace ?: last.alight.name,
        routeCodes = option.legs.map { it.routeCode },
        boardStop = first.board.name,
        alightStop = last.alight.name,
        transferStop = option.transferSteps.firstOrNull()?.to?.name,
        estimatedMinutes = option.estimatedMinutes,
        totalStops = option.totalStops,
    )
    return Favorite(FavoriteKind.TRIP, destination.trim(), "${trip.from} → ${trip.to}", trip)
}
