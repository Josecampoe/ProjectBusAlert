package com.busalert.wear.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.LegEstimate
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TransferStep
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripStatus
import com.busalert.shared.preferences.UserPreferences
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.ProvideAppLanguage
import com.busalert.wear.ui.screens.ErrorScreen
import com.busalert.wear.ui.screens.HomeScreen
import com.busalert.wear.ui.screens.ListeningScreen
import com.busalert.wear.ui.screens.LocatingScreen
import com.busalert.wear.ui.screens.NoResultScreen
import com.busalert.wear.ui.screens.ProcessingScreen
import com.busalert.wear.ui.screens.ResultScreen
import com.busalert.wear.ui.screens.SettingsScreen
import com.busalert.wear.ui.screens.TypingScreen

/** Tres formas de reloj en tema oscuro y español. */
@Preview(name = "Redondo pequeño", device = "id:wearos_small_round", showSystemUi = true)
@Preview(name = "Redondo grande", device = "id:wearos_large_round", showSystemUi = true)
@Preview(name = "Cuadrado", device = "id:wearos_square", showSystemUi = true)
annotation class WearDevicePreviews

/** Variante clara en inglés. */
@Preview(name = "Claro · English", device = "id:wearos_large_round", showSystemUi = true, locale = "en")
annotation class LightEnglishPreview

@Composable
private fun PreviewFrame(dark: Boolean, language: AppLanguage, content: @Composable () -> Unit) {
    ProvideAppLanguage(language) { BusAlertTheme(darkTheme = dark, content = content) }
}

@Composable
private fun Dark(content: @Composable () -> Unit) = PreviewFrame(true, AppLanguage.SPANISH, content)

@Composable
private fun LightEnglish(content: @Composable () -> Unit) = PreviewFrame(false, AppLanguage.ENGLISH, content)

/** Datos ficticios SOLO para previews: mismos nombres que el set demo de backend-node/data/sample. */
private object PreviewData {
    private val torobajo = StopRef(1, "DEMO-01", "Demo Torobajo", null, null)
    private val centro = StopRef(5, "DEMO-05", "Demo Centro", null, null)
    private val terminal = StopRef(8, "DEMO-08", "Demo Terminal", null, null)
    private val leg = TripLeg(
        routeCode = "DEMO-1",
        routeName = "DEMO-1",
        routeColor = "#1565C0",
        directionName = "Demo Torobajo → Demo Centro",
        board = torobajo,
        alight = centro,
        stopsCount = 3,
        estimate = LegEstimate(rideMinutes = 9, waitMinutes = 5, totalMinutes = 14, withinServiceHours = true),
    )

    val plan = TripPlan(
        status = TripStatus.OK,
        source = AnswerSource.PLANNER,
        message = null,
        recommended = TripOption(
            legs = listOf(leg, leg.copy(routeCode = "DEMO-2", routeColor = null, board = centro, alight = terminal, stopsCount = 2)),
            transfers = 1,
            transferSteps = listOf(TransferStep(centro, centro, 0, 0)),
            totalStops = 5,
            estimatedMinutes = 24,
            isDemo = true,
        ),
        alternatives = emptyList(),
        nearestStopDistanceMeters = 40,
        isDemoData = true,
    )
    val recent = listOf("Demo Centro", "Demo Terminal")
}

@WearDevicePreviews
@Composable
private fun HomePreview() = Dark { HomeScreen(onStartQuery = {}, onOpenSettings = {}) }

@LightEnglishPreview
@Composable
private fun HomeLightPreview() = LightEnglish { HomeScreen(onStartQuery = {}, onOpenSettings = {}) }

@WearDevicePreviews
@Composable
private fun SettingsPreview() = Dark { SettingsScreen(UserPreferences(), {}, {}, {}, {}, {}) }

@WearDevicePreviews
@Composable
private fun ListeningPreview() = Dark {
    ListeningScreen(prompt = stringResource(R.string.prompt_destination), partialText = "Terminal", notice = null, onRetry = {}, onUseKeyboard = {}, onCancel = {})
}

@WearDevicePreviews
@Composable
private fun TypingPreview() = Dark {
    TypingScreen(stringResource(R.string.prompt_location), "", PreviewData.recent, null, {}, {}, {}, {}, {})
}

@WearDevicePreviews
@Composable
private fun LocatingPreview() = Dark { LocatingScreen(onUseVoice = {}) }

@WearDevicePreviews
@Composable
private fun ProcessingPreview() = Dark { ProcessingScreen(onCancel = {}) }

@WearDevicePreviews
@Composable
private fun ResultPreview() = Dark {
    ResultScreen(PreviewData.plan, speakEnabled = true, isSpeaking = true, {}, {}, {}, {})
}

@LightEnglishPreview
@Composable
private fun ResultLightPreview() = LightEnglish {
    ResultScreen(PreviewData.plan, speakEnabled = true, isSpeaking = false, {}, {}, {}, {})
}

@WearDevicePreviews
@Composable
private fun ErrorPreview() = Dark { ErrorScreen(ErrorCode.NETWORK, {}, {}) }

@LightEnglishPreview
@Composable
private fun NoResultLightPreview() = LightEnglish { NoResultScreen(TripStatus.NO_NEARBY_STOPS, {}, {}) }
