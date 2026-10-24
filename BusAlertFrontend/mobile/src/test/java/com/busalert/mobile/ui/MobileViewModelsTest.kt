package com.busalert.mobile.ui

import com.busalert.mobile.FakePreferencesRepository
import com.busalert.mobile.FakeTransitRepository
import com.busalert.mobile.MobileTestData
import com.busalert.mobile.ui.routes.RouteDetailViewModel
import com.busalert.mobile.ui.routes.RoutesViewModel
import com.busalert.mobile.ui.settings.UserDataViewModel
import com.busalert.mobile.ui.stops.StopSearchDefaults
import com.busalert.mobile.ui.stops.StopSearchState
import com.busalert.mobile.ui.stops.StopsViewModel
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val transit = FakeTransitRepository()
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> LoadState<T>.ready(): T = (this as LoadState.Ready<T>).value

    @Test
    fun `rutas - carga, ordena y filtra sin importar tildes ni mayúsculas`() = runTest(dispatcher) {
        val vm = RoutesViewModel(transit, preferences)
        assertEquals(LoadState.Loading, vm.routes.value)
        advanceUntilIdle()
        assertEquals(listOf("E1", "FX1", "FX2"), vm.routes.value.ready().map { it.code })

        vm.onQueryChange("  UDENAR ")
        advanceUntilIdle()
        assertEquals(listOf("E1", "FX1"), vm.routes.value.ready().map { it.code })
        vm.onQueryChange("términal")
        advanceUntilIdle()
        assertEquals(listOf("FX2"), vm.routes.value.ready().map { it.code })
    }

    @Test
    fun `rutas - las favoritas van primero y se sincronizan como favorito de ruta`() = runTest(dispatcher) {
        val vm = RoutesViewModel(transit, preferences)
        advanceUntilIdle()
        vm.toggleFavorite(MobileTestData.fx2)
        advanceUntilIdle()
        assertEquals(listOf("FX2", "E1", "FX1"), vm.routes.value.ready().map { it.code })
        assertEquals(listOf(Favorite(FavoriteKind.ROUTE, "FX2", MobileTestData.fx2.name)), preferences.favorites.value)
    }

    @Test
    fun `rutas - un error se muestra con su código y se puede reintentar`() = runTest(dispatcher) {
        transit.routesResult = FakeTransitRepository.networkError
        val vm = RoutesViewModel(transit, preferences)
        advanceUntilIdle()
        assertEquals(LoadState.Failed(ErrorCode.NETWORK), vm.routes.value)
        transit.routesResult = Outcome.Success(listOf(MobileTestData.fx1))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("FX1"), vm.routes.value.ready().map { it.code })
    }

    @Test
    fun `detalle - sentidos, selección y favorito`() = runTest(dispatcher) {
        val vm = RouteDetailViewModel("FX1", transit, preferences)
        advanceUntilIdle()
        assertEquals(listOf("FX1_D1", "FX1_D2"), vm.directions.value.ready().map { it.key })
        vm.selectDirection(1)
        assertEquals(1, vm.selectedDirection.value)
        assertFalse(vm.isFavorite.value)
        vm.toggleFavorite("FX1 — Udenar / Centro")
        advanceUntilIdle()
        assertTrue(vm.isFavorite.value)
    }

    @Test
    fun `paradas - espera a que el usuario deje de escribir y exige letras mínimas`() = runTest(dispatcher) {
        val vm = StopsViewModel(transit, preferences)
        vm.onQueryChange("pa")
        advanceUntilIdle()
        assertEquals(StopSearchState.Idle, vm.results.value)
        assertTrue(transit.searches.isEmpty())

        vm.onQueryChange("par")
        advanceTimeBy(StopSearchDefaults.DEBOUNCE_MS / 2)
        vm.onQueryChange("parque")
        advanceUntilIdle()
        assertEquals(listOf("parque"), transit.searches) // solo la última búsqueda
        val state = vm.results.value as StopSearchState.Searching
        assertEquals(listOf("Parque Nariño", "Parque Bolívar"), state.load.ready().map { it.stop.name })
    }

    @Test
    fun `paradas - reintentar repite la búsqueda y los favoritos se guardan por nombre`() = runTest(dispatcher) {
        transit.searchResult = FakeTransitRepository.networkError
        val vm = StopsViewModel(transit, preferences)
        vm.onQueryChange("parque")
        advanceUntilIdle()
        assertEquals(StopSearchState.Searching(LoadState.Failed(ErrorCode.NETWORK)), vm.results.value)
        transit.searchResult = Outcome.Success(MobileTestData.parqueResults)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, transit.searches.size)
        vm.toggleFavorite(MobileTestData.parqueResults.first())
        advanceUntilIdle()
        assertEquals(setOf("Parque Nariño"), vm.favoriteStops.value)
    }

    @Test
    fun `ajustes - cambian las preferencias sincronizadas`() = runTest(dispatcher) {
        val vm = UserDataViewModel(preferences)
        vm.setLanguage(AppLanguage.ENGLISH)
        vm.setDarkTheme(false)
        vm.setSpeakResults(false)
        advanceUntilIdle()
        assertEquals(AppLanguage.ENGLISH, vm.preferences.value.language)
        assertFalse(vm.preferences.value.darkTheme)
        assertFalse(vm.preferences.value.speakResults)
    }
}
