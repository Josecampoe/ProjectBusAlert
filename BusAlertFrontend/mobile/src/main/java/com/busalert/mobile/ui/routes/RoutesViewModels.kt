package com.busalert.mobile.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.mobile.ui.LoadState
import com.busalert.mobile.ui.normalizedForSearch
import com.busalert.mobile.ui.toLoadState
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Códigos de ruta marcados como favoritos. */
private fun PreferencesRepository.favoriteRouteCodes() =
    favorites.map { list -> list.filter { it.kind == FavoriteKind.ROUTE }.map { it.id }.toSet() }

/** Lista de rutas con búsqueda local y favoritos primero. */
class RoutesViewModel(
    private val transit: TransitRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val loaded = MutableStateFlow<LoadState<List<RouteSummary>>>(LoadState.Loading)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val favoriteCodes: StateFlow<Set<String>> = preferences.favoriteRouteCodes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Rutas filtradas por la búsqueda, con las favoritas arriba. */
    val routes: StateFlow<LoadState<List<RouteSummary>>> = combine(loaded, _query, favoriteCodes) { state, q, favs ->
        if (state !is LoadState.Ready) return@combine state
        val needle = q.normalizedForSearch()
        LoadState.Ready(
            state.value
                .filter { needle.isEmpty() || it.code.normalizedForSearch().contains(needle) || it.name.normalizedForSearch().contains(needle) }
                .sortedWith(compareByDescending<RouteSummary> { it.code in favs }.thenBy { it.code }),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        loaded.value = LoadState.Loading
        viewModelScope.launch { loaded.value = transit.routes().toLoadState() }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun toggleFavorite(route: RouteSummary) {
        viewModelScope.launch { preferences.toggleFavorite(Favorite(FavoriteKind.ROUTE, route.code, route.name)) }
    }

    class Factory(
        private val transit: TransitRepository,
        private val preferences: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RoutesViewModel(transit, preferences) as T
    }
}

/** Detalle de una ruta: sentidos, paradas en orden y favorito. */
class RouteDetailViewModel(
    val routeCode: String,
    private val transit: TransitRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _directions = MutableStateFlow<LoadState<List<RouteDirection>>>(LoadState.Loading)
    val directions: StateFlow<LoadState<List<RouteDirection>>> = _directions.asStateFlow()

    private val _selected = MutableStateFlow(0)
    val selectedDirection: StateFlow<Int> = _selected.asStateFlow()

    val isFavorite: StateFlow<Boolean> = preferences.favoriteRouteCodes()
        .map { routeCode in it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refresh()
    }

    fun refresh() {
        _directions.value = LoadState.Loading
        viewModelScope.launch { _directions.value = transit.routeDirections(routeCode).toLoadState() }
    }

    fun selectDirection(index: Int) {
        _selected.value = index
    }

    fun toggleFavorite(label: String) {
        viewModelScope.launch { preferences.toggleFavorite(Favorite(FavoriteKind.ROUTE, routeCode, label)) }
    }

    class Factory(
        private val routeCode: String,
        private val transit: TransitRepository,
        private val preferences: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RouteDetailViewModel(routeCode, transit, preferences) as T
    }
}
