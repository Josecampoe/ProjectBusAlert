package com.busalert.wear

import app.cash.turbine.test
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private lateinit var viewModel: MainWearViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = MainWearViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun askFullQuery() {
        viewModel.startQuery()
        viewModel.onLocationReceived("Udenar")
        viewModel.onDestinationReceived("Parque Bolívar")
    }

    @Test
    fun `recorre el flujo completo hasta el resultado`() = runTest(dispatcher) {
        viewModel.appState.test {
            assertEquals(AppState.IDLE, awaitItem())
            askFullQuery()
            assertEquals(AppState.ASKING_LOCATION, awaitItem())
            assertEquals(AppState.ASKING_DESTINATION, awaitItem())
            assertEquals(AppState.PROCESSING, awaitItem())
            assertEquals(AppState.RESULT, awaitItem())
        }
        assertEquals(okPlan, viewModel.plan.value)
        assertEquals(TripQuery("Parque Bolívar", "Udenar", AppLanguage.SPANISH), repository.queries.single())
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
        viewModel.onLocationSpeechFailed()
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
        viewModel.retryLocationSpeech()
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.onLocationSpeechFailed()
        viewModel.onTypedTextChange("  Centro  ")
        viewModel.submitTypedLocation()
        assertEquals(AppState.ASKING_DESTINATION, viewModel.appState.value)
        assertEquals("Centro", viewModel.capturedLocation.value)
        assertEquals("", viewModel.typedText.value)

        viewModel.onDestinationSpeechFailed()
        assertEquals(AppState.TYPING_DESTINATION, viewModel.appState.value)
        viewModel.onTypedTextChange("Terminal")
        viewModel.submitTypedDestination()
        advanceUntilIdle()
        assertEquals("Terminal", repository.queries.single().destination)
    }

    @Test
    fun `ignora textos vacíos`() {
        viewModel.startQuery()
        viewModel.onLocationReceived("   ")
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.onLocationSpeechFailed()
        viewModel.submitTypedLocation()
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
    }

    @Test
    fun `sin origen no consulta y vuelve a pedir la ubicación`() {
        viewModel.startQuery()
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

    private companion object {
        val board = StopRef(1, "T01", "Udenar Torobajo", null, null)
        val alight = StopRef(3, "T03", "Parque Bolívar", null, null)
        val okPlan = TripPlan(
            status = TripStatus.OK,
            source = AnswerSource.PLANNER,
            message = null,
            recommended = TripOption(
                legs = listOf(TripLeg("FX1", "FX1", "Udenar → Centro", board, alight, 2)),
                transfers = 0,
                totalStops = 2,
                isDemo = true,
            ),
            alternatives = emptyList(),
            isDemoData = true,
        )
    }
}
