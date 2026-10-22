package com.busalert.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripMessage
import com.busalert.shared.domain.TripStatus
import com.busalert.shared.preferences.UserPreferences
import com.busalert.wear.TestData
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.screens.ErrorScreen
import com.busalert.wear.ui.screens.HomeScreen
import com.busalert.wear.ui.screens.ListeningScreen
import com.busalert.wear.ui.screens.LocatingScreen
import com.busalert.wear.ui.screens.LocationRationaleScreen
import com.busalert.wear.ui.screens.NoResultScreen
import com.busalert.wear.ui.screens.ResultScreen
import com.busalert.wear.ui.screens.SettingsScreen
import com.busalert.wear.ui.screens.TypingScreen
import com.busalert.wear.voice.VoiceError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setScreen(language: AppLanguage = AppLanguage.SPANISH, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BusAlertTheme(darkTheme = dark) { content() }
            }
        }
    }

    /** En pantallas con scroll, primero desplaza hasta el texto y luego lo verifica. */
    private fun show(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun click(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun result(plan: com.busalert.shared.domain.TripPlan, speaking: Boolean = false, onStop: () -> Unit = {}) {
        setScreen {
            ResultScreen(plan, speakEnabled = true, isSpeaking = speaking, onStopVoice = onStop, onRepeatVoice = {}, onNewQuery = {}, onHome = {})
        }
    }

    @Test
    fun cambiar_idioma_conserva_la_activity_para_permisos_y_launchers() {
        var owner: Any? = null
        var appName = ""
        setScreen(AppLanguage.ENGLISH) {
            owner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
            appName = androidx.compose.ui.res.stringResource(com.busalert.wear.R.string.home_hint)
        }
        compose.waitForIdle()
        assertEquals(true, owner != null)
        assertEquals("Tap to ask", appName)
    }

    @Test
    fun inicio_tiene_boton_de_microfono_accesible() {
        var started = 0
        setScreen { HomeScreen(onStartQuery = { started++ }, onOpenSettings = {}) }
        compose.onNodeWithContentDescription("Iniciar consulta").performClick()
        assertEquals(1, started)
        show("Ajustes")
    }

    @Test
    fun resultado_sin_texto_de_ia_se_arma_con_recursos() {
        result(TestData.okPlan)
        show("Sube en Udenar Torobajo")
        show("≈ 12 min (estimado)")
        show("Baja en Parque Bolívar · 2 paradas")
        show("Datos de demostración")
        compose.onNodeWithContentDescription("Ruta FX1").assertIsDisplayed()
    }

    @Test
    fun resultado_usa_el_texto_de_la_ia_cuando_existe() {
        result(
            TestData.okPlan.copy(
                source = AnswerSource.AI,
                message = TripMessage("Toma la FX1.", "Ruta FX1 al centro", "Parada Udenar"),
                isDemoData = false,
            ),
        )
        show("Ruta FX1 al centro")
        show("Parada Udenar")
        compose.onNodeWithText("Datos de demostración").assertDoesNotExist()
    }

    @Test
    fun resultado_con_transbordo_gps_y_control_de_voz() {
        var stopped = 0
        result(TestData.transferPlan, speaking = true, onStop = { stopped++ })
        show("Transbordo en Parque Nariño")
        show("Parada a 40 m")
        show("≈ 21 min (estimado)")
        compose.onNodeWithContentDescription("Ruta FX2").assertIsDisplayed()
        click("Detener voz")
        assertEquals(1, stopped)
    }

    @Test
    fun resultado_en_ingles_y_tema_claro() {
        setScreen(AppLanguage.ENGLISH, dark = false) {
            ResultScreen(TestData.okPlan, speakEnabled = true, isSpeaking = false, onStopVoice = {}, onRepeatVoice = {}, onNewQuery = {}, onHome = {})
        }
        show("Board at Udenar Torobajo")
        show("≈ 12 min (estimate)")
        show("Repeat")
    }

    @Test
    fun error_reintentable_ofrece_reintentar() {
        var retried = 0
        setScreen { ErrorScreen(error = ErrorCode.NETWORK, onRetry = { retried++ }, onHome = {}) }
        show("Sin conexión. Revisa el internet del reloj.")
        click("Reintentar")
        assertEquals(1, retried)
    }

    @Test
    fun error_no_reintentable_solo_lleva_al_inicio() {
        var home = 0
        setScreen { ErrorScreen(error = ErrorCode.UNAUTHORIZED, onRetry = {}, onHome = { home++ }) }
        show("La app no está autorizada en el servidor.")
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        click("Inicio")
        assertEquals(1, home)
    }

    @Test
    fun sin_resultados_explica_el_motivo() {
        var newQuery = 0
        setScreen { NoResultScreen(status = TripStatus.NO_NEARBY_STOPS, onNewQuery = { newQuery++ }, onHome = {}) }
        show("No hay paradas registradas cerca de ti. Di o escribe dónde estás.")
        click("Nueva consulta")
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
        click("Continuar")
        click("Ahora no")
        assertEquals(1, continued)
        assertEquals(1, declined)
    }

    @Test
    fun escucha_muestra_el_texto_parcial_con_region_viva_y_botones_accesibles() {
        var keyboard = 0
        setScreen {
            ListeningScreen("¿A dónde vas?", partialText = "Termi", notice = null, onRetry = {}, onUseKeyboard = { keyboard++ }, onCancel = {})
        }
        compose.onNode(
            hasText("“Termi”").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)),
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancelar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escribir").performClick()
        assertEquals(1, keyboard)
    }

    @Test
    fun escucha_muestra_errores_de_voz_traducidos() {
        setScreen(AppLanguage.ENGLISH) {
            ListeningScreen("Where to?", partialText = "", notice = VoiceError.NO_MATCH, onRetry = {}, onUseKeyboard = {}, onCancel = {})
        }
        compose.onNodeWithText("I didn't catch that. Try again.").assertIsDisplayed()
    }

    @Test
    fun escritura_ofrece_recientes_y_buscar() {
        var picked: String? = null
        setScreen {
            TypingScreen(
                prompt = "¿Dónde estás?",
                text = "",
                recentPlaces = listOf("Parque Nariño", "Terminal"),
                notice = VoiceError.PERMISSION,
                onTextChange = {},
                onSubmit = {},
                onPickRecent = { picked = it },
                onUseVoice = {},
                onCancel = {},
            )
        }
        show("Sin permiso de micrófono. Puedes escribir.")
        show("Escribe un lugar o una parada")
        compose.onNodeWithText("Buscar").assertDoesNotExist()
        click("Terminal")
        assertEquals("Terminal", picked)
    }

    @Test
    fun ajustes_muestra_idioma_y_cambia_interruptores() {
        var dark: Boolean? = null
        setScreen {
            SettingsScreen(UserPreferences(), {}, onDarkThemeChange = { dark = it }, {}, {}, {})
        }
        show("Español")
        click("Tema oscuro")
        assertEquals(false, dark)
    }
}
