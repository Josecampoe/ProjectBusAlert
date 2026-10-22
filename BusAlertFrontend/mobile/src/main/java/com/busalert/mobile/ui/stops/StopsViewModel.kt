package com.busalert.mobile.ui.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.mobile.ui.LoadState
import com.busalert.mobile.ui.toLoadState
import com.busalert.shared.domain.StopWithRoutes
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado de búsqueda de paradas: aún sin buscar, o una carga. */
sealed interface StopSearchState {
    data object Idle : StopSearchState
    data class Searching(val load: LoadState<List<StopWithRoutes>>) : StopSearchState
}

object StopSearchDefaults {
    /** Letras mínimas antes de consultar al servidor. */
    const val MIN_QUERY_LENGTH = 3

    /** Espera tras la última tecla antes de buscar (ms). */
    const val DEBOUNCE_MS = 300L
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StopsViewModel(
    private val transit: TransitRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Cada incremento repite la última búsqueda (botón "Reintentar"). */
    private val retries = MutableStateFlow(0)

    val results: StateFlow<StopSearchState> = combine(_query.debounce(StopSearchDefaults.DEBOUNCE_MS), retries) { text, _ -> text }
        .flatMapLatest { text ->
            val q = text.trim()
            flow {
                if (q.length < StopSearchDefaults.MIN_QUERY_LENGTH) {
                    emit(StopSearchState.Idle)
                } else {
                    emit(StopSearchState.Searching(LoadState.Loading))
                    emit(StopSearchState.Searching(transit.searchStops(q).toLoadState()))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StopSearchState.Idle)

    val favoriteStops: StateFlow<Set<String>> = preferences.favorites
        .map { list -> list.filter { it.kind == FavoriteKind.STOP }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun retry() {
        retries.value += 1
    }

    /** Las paradas favoritas se identifican por nombre: el reloj las ofrece como sugerencia. */
    fun toggleFavorite(stop: StopWithRoutes) {
        viewModelScope.launch {
            preferences.toggleFavorite(Favorite(FavoriteKind.STOP, stop.stop.name, stop.stop.name))
        }
    }

    class Factory(
        private val transit: TransitRepository,
        private val preferences: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StopsViewModel(transit, preferences) as T
    }
}
