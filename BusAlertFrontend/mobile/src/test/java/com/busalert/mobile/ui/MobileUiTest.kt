package com.busalert.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.busalert.mobile.FakePreferencesRepository
import com.busalert.mobile.FakeTransitRepository
import com.busalert.mobile.ui.settings.BackendMode
import com.busalert.mobile.ui.stops.StopSearchDefaults
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MobileUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val transit = FakeTransitRepository()

    private fun launch(
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        mode: BackendMode = BackendMode.CONFIGURED,
    ): FakePreferencesRepository {
        compose.setContent { MobileApp(MobileAppDependencies(transit, preferences, mapsEnabled = false, backendMode = mode)) }
        compose.waitForIdle()
        return preferences
    }

    @Test
    fun lista_rutas_con_estado_demo_y_abre_el_detalle() {
        launch(mode = BackendMode.DEMO)
        compose.onNodeWithText("Datos de demostración: no son información real del SETP.").assertIsDisplayed()
        compose.onNodeWithText("FX1 — Udenar / Centro").assertIsDisplayed()
        compose.onNodeWithText("Udenar → Centro").assertIsDisplayed()
        compose.onNodeWithText("FX1 — Udenar / Centro").performClick()
        compose.waitForIdle()
        // Detalle: sentidos, aviso del mapa sin clave y paradas en orden.
        compose.onNodeWithText("Centro → Udenar").assertIsDisplayed()
        compose.onNodeWithText("Mapa no disponible: falta la clave de Google Maps.").assertIsDisplayed()
        compose.onNodeWithText("Parque Bolívar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Rutas").assertIsDisplayed()
    }

    @Test
    fun buscar_y_marcar_favorito_una_ruta() {
        val prefs = launch()
        compose.onNodeWithText("Buscar ruta por código o nombre").performTextInput("terminal")
        compose.waitForIdle()
        compose.onNodeWithText("FX2 — Centro / Terminal").assertIsDisplayed()
        compose.onNodeWithText("E1 — Dolores / Udenar").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Agregar a favoritos").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf("FX2"), prefs.favorites.value.map { it.id })
        compose.onNodeWithContentDescription("Quitar de favoritos").assertIsDisplayed()
    }

    @Test
    fun error_de_red_con_reintento() {
        transit.routesResult = FakeTransitRepository.networkError
        launch()
        compose.onNodeWithText("Sin conexión a internet.").assertIsDisplayed()
        transit.routesResult = com.busalert.shared.domain.Outcome.Success(emptyList())
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No hay rutas cargadas en el servidor.").assertIsDisplayed()
    }

    @Test
    fun buscar_paradas_muestra_las_rutas_que_pasan() {
        launch()
        compose.onNodeWithText("Paradas").performClick()
        compose.onNodeWithText("Escribe al menos 3 letras para buscar.").assertIsDisplayed()
        compose.onNodeWithText("Buscar parada o lugar").performTextInput("parque")
        // La espera entre teclas corre en el hilo principal de Robolectric.
        ShadowLooper.idleMainLooper(StopSearchDefaults.DEBOUNCE_MS * 2, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        compose.onNodeWithText("Parque Nariño").assertIsDisplayed()
        compose.onNodeWithText("Rutas: FX1, FX2").assertIsDisplayed()
    }

    @Test
    fun historial_sincronizado_y_ajustes_en_ingles() {
        val prefs = FakePreferencesRepository(UserPreferences(language = AppLanguage.ENGLISH))
        runBlocking { prefs.addToHistory(QueryHistoryEntry("Centro", "Terminal", listOf("FX2"), 0L)) }
        launch(prefs)
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Centro → Terminal").assertIsDisplayed()
        compose.onNodeWithText("Clear history").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No queries yet. The ones you make on the watch will show up here.").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark theme").assertIsDisplayed()
        compose.onNode(hasText("Dark theme")).assertIsOn()
        compose.onNodeWithText("Dark theme").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Tema oscuro").or(hasText("Dark theme"))).assertIsOff()
        compose.onNodeWithText("Español").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
        assertEquals(AppLanguage.SPANISH, prefs.preferences.value.language)
    }
}
