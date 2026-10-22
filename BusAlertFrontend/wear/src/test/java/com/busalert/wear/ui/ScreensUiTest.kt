package com.busalert.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripMessage
import com.busalert.shared.domain.TripStatus
import com.busalert.wear.TestData
import com.busalert.wear.theme.BusAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(language: AppLanguage = AppLanguage.SPANISH, content: @Composable () -> Unit) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BusAlertTheme(isDark = true) { content() }
            }
        }
    }

    @Test
    fun resultado_sin_texto_de_ia_se_arma_con_recursos() {
        setScreen { ResultScreen(plan = TestData.okPlan, onDismiss = {}) }
        compose.onNodeWithText("Sube en Udenar Torobajo").assertIsDisplayed()
        compose.onNodeWithText("Baja en Parque Bolívar • 2 paradas").assertIsDisplayed()
        compose.onNodeWithText("≈ 12 min (estimado)").assertIsDisplayed()
        compose.onNodeWithText("Datos de demostración").assertIsDisplayed()
    }

    @Test
    fun resultado_usa_el_texto_de_la_ia_cuando_existe() {
        val plan = TestData.okPlan.copy(
            source = AnswerSource.AI,
            message = TripMessage("Toma la FX1.", "Ruta FX1 al centro", "Parada Udenar"),
            isDemoData = false,
        )
        setScreen { ResultScreen(plan = plan, onDismiss = {}) }
        compose.onNodeWithText("Ruta FX1 al centro").assertIsDisplayed()
        compose.onNodeWithText("Parada Udenar").assertIsDisplayed()
        compose.onNodeWithText("Datos de demostración").assertDoesNotExist()
    }

    @Test
    fun resultado_con_transbordo_y_gps() {
        setScreen { ResultScreen(plan = TestData.transferPlan, onDismiss = {}) }
        compose.onNodeWithText("Transbordo en Parque Nariño").assertIsDisplayed()
        compose.onNodeWithText("Parada a 40 m").assertIsDisplayed()
        compose.onNodeWithText("≈ 21 min (estimado)").assertIsDisplayed()
    }

    @Test
    fun resultado_en_ingles() {
        setScreen(AppLanguage.ENGLISH) { ResultScreen(plan = TestData.okPlan, onDismiss = {}) }
        compose.onNodeWithText("Board at Udenar Torobajo").assertIsDisplayed()
        compose.onNodeWithText("≈ 12 min (estimate)").assertIsDisplayed()
        compose.onNodeWithText("Demo data").assertIsDisplayed()
    }

    @Test
    fun error_reintentable_ofrece_reintentar() {
        var retried = 0
        setScreen { ErrorScreen(error = ErrorCode.NETWORK, onRetry = { retried++ }, onHome = {}) }
        compose.onNodeWithText("Sin conexión. Revisa el internet del reloj.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun error_no_reintentable_lleva_al_inicio() {
        var home = 0
        setScreen { ErrorScreen(error = ErrorCode.UNAUTHORIZED, onRetry = {}, onHome = { home++ }) }
        compose.onNodeWithText("La app no está autorizada en el servidor.").assertIsDisplayed()
        compose.onNodeWithText("Inicio").performClick()
        assertEquals(1, home)
    }

    @Test
    fun sin_resultados_explica_el_motivo() {
        var newQuery = 0
        setScreen { NoResultScreen(status = TripStatus.NO_NEARBY_STOPS, onNewQuery = { newQuery++ }) }
        compose.onNodeWithText("No hay paradas registradas cerca de ti. Di o escribe dónde estás.").assertIsDisplayed()
        compose.onNodeWithText("Nueva consulta").performClick()
        assertEquals(1, newQuery)
    }

    @Test
    fun ubicando_permite_cambiar_a_voz() {
        var voice = 0
        setScreen { LocatingScreen(onUseVoice = { voice++ }) }
        compose.onNodeWithText("Buscando tu ubicación…").assertIsDisplayed()
        compose.onNodeWithText("Decir dónde estoy").performClick()
        assertEquals(1, voice)
    }

    @Test
    fun explicacion_del_permiso_de_ubicacion() {
        var continued = 0
        var declined = 0
        setScreen { LocationRationaleScreen(onContinue = { continued++ }, onNotNow = { declined++ }) }
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Ahora no").performClick()
        assertEquals(1, continued)
        assertEquals(1, declined)
    }
}
