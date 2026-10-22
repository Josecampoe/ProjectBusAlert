package com.busalert.wear

import app.cash.turbine.test
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.GeoPoint
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripOrigin
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus
import com.busalert.wear.TestData.okPlan
import com.busalert.wear.location.LocationSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainWearViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Repositorio falso: responde lo que se le indique y registra las consultas. */
    private class FakeRepository : TripAssistantRepository {
        val queries = mutableListOf<TripQuery>()
        var next: suspend () -> Outcome<TripPlan> = { Outcome.Success(okPlan) }
        override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> {
            queries += query
            return next()
        }
    }

    private val repository = FakeRepository()

    /** Por defecto no hay GPS: el flujo pide el lugar por voz. */
    private var location: suspend () -> GeoPoint? = { null }
    private lateinit var viewModel: MainWearViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = MainWearViewModel(repository, LocationSource { location() })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.askFullQuery() {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onLocationReceived("Udenar")
        viewModel.onDestinationReceived("Parque Bolívar")
    }

    @Test
    fun `sin GPS recorre el flujo por voz hasta el resultado`() = runTest(dispatcher) {
        viewModel.appState.test {
            assertEquals(AppState.IDLE, awaitItem())
            viewModel.startQuery()
            assertEquals(AppState.LOCATING, awaitItem())
            assertEquals(AppState.ASKING_LOCATION, awaitItem())
            viewModel.onLocationReceived("Udenar")
            assertEquals(AppState.ASKING_DESTINATION, awaitItem())
            viewModel.onDestinationReceived("Parque Bolívar")
            assertEquals(AppState.PROCESSING, awaitItem())
            assertEquals(AppState.RESULT, awaitItem())
        }
        assertEquals(okPlan, viewModel.plan.value)
        assertEquals(
            TripQuery("Parque Bolívar", TripOrigin.Named("Udenar"), AppLanguage.SPANISH),
            repository.queries.single(),
        )
    }

    @Test
    fun `con GPS salta la pregunta de origen y envía las coordenadas`() = runTest(dispatcher) {
        val point = GeoPoint(1.2143, -77.2787)
        location = { point }
        viewModel.startQuery()
        advanceUntilIdle()
        assertEquals(AppState.ASKING_DESTINATION, viewModel.appState.value)
        assertEquals(TripOrigin.Located(point), viewModel.origin.value)

        viewModel.onDestinationReceived("Udenar")
        advanceUntilIdle()
        assertEquals(TripOrigin.Located(point), repository.queries.single().origin)
        assertEquals(AppState.RESULT, viewModel.appState.value)
    }

    @Test
    fun `el usuario puede preferir decir su ubicación mientras se busca el GPS`() = runTest(dispatcher) {
        val gate = CompletableDeferred<GeoPoint?>()
        location = { gate.await() }
        viewModel.startQuery()
        advanceUntilIdle()
        assertEquals(AppState.LOCATING, viewModel.appState.value)

        viewModel.useSpokenLocation()
        gate.complete(GeoPoint(1.0, -77.0))
        advanceUntilIdle()
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        assertNull(viewModel.origin.value)
    }

    @Test
    fun `un plan sin ruta lleva a NO_RESULT`() = runTest(dispatcher) {
        repository.next = { Outcome.Success(okPlan.copy(status = TripStatus.NO_ROUTE, recommended = null)) }
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppState.NO_RESULT, viewModel.appState.value)
        assertEquals(TripStatus.NO_ROUTE, viewModel.plan.value?.status)
    }

    @Test
    fun `un error de red lleva a ERROR con su código y se puede reintentar`() = runTest(dispatcher) {
        repository.next = { Outcome.Failure(ErrorCode.NETWORK) }
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppState.ERROR, viewModel.appState.value)
        assertEquals(ErrorCode.NETWORK, viewModel.error.value)

        repository.next = { Outcome.Success(okPlan) }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(AppState.RESULT, viewModel.appState.value)
        assertNull(viewModel.error.value)
        assertEquals(2, repository.queries.size)
        assertEquals(repository.queries[0], repository.queries[1])
    }

    @Test
    fun `cancelar durante la consulta vuelve al inicio y descarta la respuesta`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Outcome<TripPlan>>()
        repository.next = { gate.await() }
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppState.PROCESSING, viewModel.appState.value)

        viewModel.resetToIdle()
        gate.complete(Outcome.Success(okPlan))
        advanceUntilIdle()
        assertEquals(AppState.IDLE, viewModel.appState.value)
        assertNull(viewModel.plan.value)
    }

    @Test
    fun `alterna entre voz y teclado y envía lo escrito`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onLocationSpeechFailed()
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
        viewModel.retryLocationSpeech()
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.onLocationSpeechFailed()
        viewModel.onTypedTextChange("  Centro  ")
        viewModel.submitTypedLocation()
        assertEquals(AppState.ASKING_DESTINATION, viewModel.appState.value)
        assertEquals(TripOrigin.Named("Centro"), viewModel.origin.value)
        assertEquals("", viewModel.typedText.value)

        viewModel.onDestinationSpeechFailed()
        assertEquals(AppState.TYPING_DESTINATION, viewModel.appState.value)
        viewModel.onTypedTextChange("Terminal")
        viewModel.submitTypedDestination()
        advanceUntilIdle()
        assertEquals("Terminal", repository.queries.single().destination)
    }

    @Test
    fun `ignora textos vacíos`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onLocationReceived("   ")
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.onLocationSpeechFailed()
        viewModel.submitTypedLocation()
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
    }

    @Test
    fun `sin origen no consulta y vuelve a pedir la ubicación`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onDestinationReceived("Terminal")
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        assertEquals(0, repository.queries.size)
    }

    @Test
    fun `envía el idioma elegido y alterna el tema`() = runTest(dispatcher) {
        viewModel.toggleLanguage()
        viewModel.toggleDarkMode()
        assertEquals(AppLanguage.ENGLISH, viewModel.language.value)
        assertEquals(false, viewModel.isDarkMode.value)
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppLanguage.ENGLISH, repository.queries.single().language)
    }
}
