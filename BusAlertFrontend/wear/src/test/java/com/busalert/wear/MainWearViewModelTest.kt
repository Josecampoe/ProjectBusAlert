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
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.wear.TestData.okPlan
import com.busalert.wear.location.LocationSource
import com.busalert.wear.voice.VoiceError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val preferences = FakePreferencesRepository()

    /** Por defecto no hay GPS: el flujo pide el lugar por voz. */
    private var location: suspend () -> GeoPoint? = { null }
    private lateinit var viewModel: MainWearViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = MainWearViewModel(repository, LocationSource { location() }, preferences, clock = { NOW })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.askFullQuery() {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onSpeechResult("Udenar")
        viewModel.onSpeechResult("Parque Bolívar")
    }

    @Test
    fun `sin GPS recorre el flujo por voz hasta el resultado`() = runTest(dispatcher) {
        viewModel.appState.test {
            assertEquals(AppState.IDLE, awaitItem())
            viewModel.startQuery()
            assertEquals(AppState.LOCATING, awaitItem())
            assertEquals(AppState.ASKING_LOCATION, awaitItem())
            viewModel.onSpeechResult("Udenar")
            assertEquals(AppState.ASKING_DESTINATION, awaitItem())
            viewModel.onSpeechResult("Parque Bolívar")
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
    fun `guarda la consulta exitosa en el historial y la ofrece como reciente`() = runTest(dispatcher) {
        askFullQuery()
        advanceUntilIdle()
        assertEquals(
            listOf(QueryHistoryEntry("Udenar", "Parque Bolívar", listOf("FX1"), NOW)),
            preferences.history.value,
        )
        assertEquals(listOf("Parque Bolívar", "Udenar"), viewModel.recentPlaces.value)
    }

    @Test
    fun `con GPS salta la pregunta de origen y envía las coordenadas`() = runTest(dispatcher) {
        val point = GeoPoint(1.2143, -77.2787)
        location = { point }
        viewModel.startQuery()
        advanceUntilIdle()
        assertEquals(AppState.ASKING_DESTINATION, viewModel.appState.value)
        assertEquals(TripOrigin.Located(point), viewModel.origin.value)

        viewModel.onSpeechResult("Udenar")
        advanceUntilIdle()
        assertEquals(TripOrigin.Located(point), repository.queries.single().origin)
        assertEquals(AppState.RESULT, viewModel.appState.value)
        assertNull(preferences.history.value.single().originLabel)
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
    fun `un plan sin ruta lleva a NO_RESULT y no se guarda en el historial`() = runTest(dispatcher) {
        repository.next = { Outcome.Success(okPlan.copy(status = TripStatus.NO_ROUTE, recommended = null)) }
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppState.NO_RESULT, viewModel.appState.value)
        assertTrue(preferences.history.value.isEmpty())
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
        viewModel.switchToKeyboard()
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
        viewModel.switchToVoice()
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.switchToKeyboard()
        viewModel.onTypedTextChange("  Centro  ")
        viewModel.submitTypedText()
        assertEquals(AppState.ASKING_DESTINATION, viewModel.appState.value)
        assertEquals(TripOrigin.Named("Centro"), viewModel.origin.value)
        assertEquals("", viewModel.typedText.value)

        viewModel.switchToKeyboard()
        assertEquals(AppState.TYPING_DESTINATION, viewModel.appState.value)
        viewModel.onTypedTextChange("Terminal")
        viewModel.submitTypedText()
        advanceUntilIdle()
        assertEquals("Terminal", repository.queries.single().destination)
    }

    @Test
    fun `sin permiso de micrófono muestra el aviso y pasa al teclado`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onVoiceError(VoiceError.PERMISSION)
        assertEquals(AppState.TYPING_LOCATION, viewModel.appState.value)
        assertEquals(VoiceError.PERMISSION, viewModel.voiceNotice.value)
    }

    @Test
    fun `un error leve de voz muestra el aviso pero sigue escuchando`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onVoiceError(VoiceError.NO_MATCH)
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        assertEquals(VoiceError.NO_MATCH, viewModel.voiceNotice.value)
        viewModel.onSpeechResult("Centro")
        assertNull(viewModel.voiceNotice.value)
    }

    @Test
    fun `ignora textos vacíos y resultados de voz tardíos`() = runTest(dispatcher) {
        viewModel.startQuery()
        advanceUntilIdle()
        viewModel.onSpeechResult("   ")
        assertEquals(AppState.ASKING_LOCATION, viewModel.appState.value)
        viewModel.resetToIdle()
        viewModel.onSpeechResult("Centro")
        assertEquals(AppState.IDLE, viewModel.appState.value)
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
    fun `las preferencias se guardan y el idioma viaja en la consulta`() = runTest(dispatcher) {
        viewModel.toggleLanguage()
        viewModel.setDarkTheme(false)
        viewModel.setSpeakResults(false)
        advanceUntilIdle()
        assertEquals(AppLanguage.ENGLISH, viewModel.preferences.value.language)
        assertFalse(viewModel.preferences.value.darkTheme)
        assertFalse(viewModel.preferences.value.speakResults)
        askFullQuery()
        advanceUntilIdle()
        assertEquals(AppLanguage.ENGLISH, repository.queries.single().language)
    }

    @Test
    fun `ajustes y borrar historial`() = runTest(dispatcher) {
        askFullQuery()
        advanceUntilIdle()
        viewModel.resetToIdle()
        viewModel.openSettings()
        assertEquals(AppState.SETTINGS, viewModel.appState.value)
        viewModel.clearHistory()
        advanceUntilIdle()
        assertTrue(viewModel.recentPlaces.value.isEmpty())
        viewModel.closeSettings()
        assertEquals(AppState.IDLE, viewModel.appState.value)
    }

    private companion object {
        const val NOW = 1_761_145_200_000L
    }
}
