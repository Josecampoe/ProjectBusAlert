package com.busalert.wear.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripMessage
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripStatus
import com.busalert.wear.theme.BusAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setScreen(language: AppLanguage = AppLanguage.SPANISH, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BusAlertTheme(isDark = true) { content() }
            }
        }
    }

    @Test
    fun resultado_sin_texto_de_ia_se_arma_con_recursos() {
        setScreen { ResultScreen(plan = plannerPlan, onDismiss = {}) }
        compose.onNodeWithText("Sube en Udenar Torobajo").assertIsDisplayed()
        compose.onNodeWithText("Baja en Parque Bolívar • 2 paradas").assertIsDisplayed()
        compose.onNodeWithText("Datos de demostración").assertIsDisplayed()
    }

    @Test
    fun resultado_usa_el_texto_de_la_ia_cuando_existe() {
        val plan = plannerPlan.copy(
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
    fun resultado_en_ingles() {
        setScreen(AppLanguage.ENGLISH) { ResultScreen(plan = plannerPlan, onDismiss = {}) }
        compose.onNodeWithText("Board at Udenar Torobajo").assertIsDisplayed()
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
        setScreen { NoResultScreen(status = TripStatus.DESTINATION_NOT_FOUND, onNewQuery = { newQuery++ }) }
        compose.onNodeWithText("No reconozco ese destino. Prueba con el nombre de una parada.").assertIsDisplayed()
        compose.onNodeWithText("Nueva consulta").performClick()
        assertEquals(1, newQuery)
    }

    private companion object {
        val plannerPlan = TripPlan(
            status = TripStatus.OK,
            source = AnswerSource.PLANNER,
            message = null,
            recommended = TripOption(
                legs = listOf(
                    TripLeg(
                        routeCode = "FX1",
                        routeName = "FX1",
                        directionName = "Udenar → Centro",
                        board = StopRef(1, "T01", "Udenar Torobajo", null, null),
                        alight = StopRef(3, "T03", "Parque Bolívar", null, null),
                        stopsCount = 2,
                    ),
                ),
                transfers = 0,
                totalStops = 2,
                isDemo = true,
            ),
            alternatives = emptyList(),
            isDemoData = true,
        )
    }
}
