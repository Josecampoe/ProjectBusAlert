package com.busalert.wear.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripStatus
import com.busalert.shared.preferences.UserPreferences
import com.busalert.wear.AppState
import com.busalert.wear.MainWearViewModel
import com.busalert.wear.R
import com.busalert.wear.location.LocationDefaults
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.screens.ErrorScreen
import com.busalert.wear.ui.screens.HomeScreen
import com.busalert.wear.ui.screens.ListeningScreen
import com.busalert.wear.ui.screens.LocatingScreen
import com.busalert.wear.ui.screens.LocationRationaleScreen
import com.busalert.wear.ui.screens.NoResultScreen
import com.busalert.wear.ui.screens.ProcessingScreen
import com.busalert.wear.ui.screens.ResultScreen
import com.busalert.wear.ui.screens.SettingsScreen
import com.busalert.wear.ui.screens.TypingScreen
import com.busalert.wear.voice.VoiceError
import com.busalert.wear.voice.VoiceInput
import com.busalert.wear.voice.VoiceOutput
import kotlinx.coroutines.flow.StateFlow

/** Consultas de permisos que solo la Activity puede responder. */
interface PermissionGate {
    fun hasMicrophone(): Boolean
    fun hasLocation(): Boolean
    fun shouldExplainLocation(): Boolean
}

/** Raíz de la UI del reloj: tema, idioma, navegación por estado y efectos (voz, háptica, permisos). */
@Composable
fun WearApp(
    viewModel: MainWearViewModel,
    voiceInput: VoiceInput,
    voiceOutput: VoiceOutput,
    partialSpeech: StateFlow<String>,
    permissions: PermissionGate,
) {
    val preferences by viewModel.preferences.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val error by viewModel.error.collectAsState()
    val typedText by viewModel.typedText.collectAsState()
    val voiceNotice by viewModel.voiceNotice.collectAsState()
    val nearestStop by viewModel.nearestStop.collectAsState()
    val noNearbyStops by viewModel.noNearbyStops.collectAsState()
    val recentPlaces by viewModel.recentPlaces.collectAsState()
    val partial by partialSpeech.collectAsState()
    val isSpeaking by voiceOutput.isSpeaking.collectAsState()
    var explainLocation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(preferences.language) { voiceOutput.setLanguage(preferences.language) }

    ProvideAppLanguage(preferences.language) {
        BusAlertTheme(darkTheme = preferences.darkTheme) {
            val haptics = LocalHapticFeedback.current
            val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) voiceInput.start(preferences.language) else viewModel.onVoiceError(VoiceError.PERMISSION)
            }
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { viewModel.startQuery() } // Con o sin permiso: sin él se pregunta el lugar.

            val startQuery = {
                when {
                    permissions.hasLocation() -> viewModel.startQuery()
                    permissions.shouldExplainLocation() -> explainLocation = true
                    else -> locationLauncher.launch(LocationDefaults.PERMISSIONS)
                }
            }
            val listen = {
                if (permissions.hasMicrophone()) {
                    voiceInput.start(preferences.language)
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            // Escucha solo mientras la pantalla pregunta por voz.
            LaunchedEffect(appState) {
                when (appState) {
                    AppState.ASKING_LOCATION, AppState.ASKING_DESTINATION -> listen()
                    else -> voiceInput.stop()
                }
                if (appState in setOf(AppState.RESULT, AppState.NO_RESULT, AppState.ERROR)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (appState != AppState.RESULT) voiceOutput.stop()
            }

            val spoken = spokenText(plan)
            LaunchedEffect(spoken, appState, preferences.speakResults) {
                if (appState == AppState.RESULT && preferences.speakResults && spoken != null) voiceOutput.speak(spoken)
            }

            // "Atrás" (gesto de deslizar) vuelve al inicio desde cualquier pantalla.
            BackHandler(enabled = appState != AppState.IDLE || explainLocation) {
                if (explainLocation) explainLocation = false else viewModel.resetToIdle()
            }

            if (explainLocation) {
                LocationRationaleScreen(
                    onContinue = {
                        explainLocation = false
                        locationLauncher.launch(LocationDefaults.PERMISSIONS)
                    },
                    onNotNow = {
                        explainLocation = false
                        viewModel.startQuery()
                    },
                )
            } else {
                Crossfade(
                    targetState = appState,
                    animationSpec = tween(BusAlertTheme.motion.screenTransitionMs),
                    label = "screen",
                ) { state ->
                    Screen(
                        state = state,
                        viewModel = viewModel,
                        plan = plan,
                        error = error,
                        typedText = typedText,
                        voiceNotice = voiceNotice,
                        nearestStopName = nearestStop?.stop?.name,
                        noNearbyStops = noNearbyStops,
                        recentPlaces = recentPlaces,
                        partialSpeech = partial,
                        isSpeaking = isSpeaking,
                        speakEnabled = preferences.speakResults,
                        preferences = preferences,
                        onStartQuery = startQuery,
                        onListenAgain = listen,
                        voiceOutput = voiceOutput,
                    )
                }
            }
        }
    }
}

@Composable
private fun Screen(
    state: AppState,
    viewModel: MainWearViewModel,
    plan: TripPlan?,
    error: ErrorCode?,
    typedText: String,
    voiceNotice: VoiceError?,
    nearestStopName: String?,
    noNearbyStops: Boolean,
    recentPlaces: List<String>,
    partialSpeech: String,
    isSpeaking: Boolean,
    speakEnabled: Boolean,
    preferences: UserPreferences,
    onStartQuery: () -> Unit,
    onListenAgain: () -> Unit,
    voiceOutput: VoiceOutput,
) {
    val askingDestination = state == AppState.ASKING_DESTINATION || state == AppState.TYPING_DESTINATION
    val prompt = when {
        askingDestination && nearestStopName != null -> stringResource(R.string.prompt_destination_from, nearestStopName)
        askingDestination -> stringResource(R.string.prompt_destination)
        noNearbyStops -> stringResource(R.string.prompt_location_no_nearby_stops)
        else -> stringResource(R.string.prompt_location)
    }
    when (state) {
        AppState.IDLE -> HomeScreen(onStartQuery = onStartQuery, onOpenSettings = viewModel::openSettings)
        AppState.SETTINGS -> SettingsScreen(
            preferences = preferences,
            onToggleLanguage = viewModel::toggleLanguage,
            onDarkThemeChange = viewModel::setDarkTheme,
            onSpeakResultsChange = viewModel::setSpeakResults,
            onClearHistory = viewModel::clearHistory,
            onBack = viewModel::closeSettings,
        )
        AppState.LOCATING -> LocatingScreen(onUseVoice = viewModel::useSpokenLocation)
        AppState.ASKING_LOCATION, AppState.ASKING_DESTINATION -> ListeningScreen(
            prompt = prompt,
            partialText = partialSpeech,
            notice = voiceNotice,
            onRetry = onListenAgain,
            onUseKeyboard = viewModel::switchToKeyboard,
            onCancel = viewModel::resetToIdle,
        )
        AppState.TYPING_LOCATION, AppState.TYPING_DESTINATION -> TypingScreen(
            prompt = prompt,
            text = typedText,
            recentPlaces = recentPlaces,
            notice = voiceNotice,
            onTextChange = viewModel::onTypedTextChange,
            onSubmit = viewModel::submitTypedText,
            onPickRecent = { place ->
                viewModel.onTypedTextChange(place)
                viewModel.submitTypedText()
            },
            onUseVoice = viewModel::switchToVoice,
            onCancel = viewModel::resetToIdle,
        )
        AppState.PROCESSING -> ProcessingScreen(onCancel = viewModel::resetToIdle)
        AppState.RESULT -> plan?.let {
            ResultScreen(
                plan = it,
                speakEnabled = speakEnabled,
                isSpeaking = isSpeaking,
                onStopVoice = voiceOutput::stop,
                onRepeatVoice = voiceOutput::repeat,
                onNewQuery = onStartQuery,
                onHome = viewModel::resetToIdle,
            )
        }
        AppState.NO_RESULT -> NoResultScreen(
            status = plan?.status ?: TripStatus.NO_ROUTE,
            onNewQuery = onStartQuery,
            onHome = viewModel::resetToIdle,
        )
        AppState.ERROR -> ErrorScreen(
            error = error ?: ErrorCode.UNKNOWN,
            onRetry = viewModel::retry,
            onHome = viewModel::resetToIdle,
        )
    }
}

/** Texto a leer en voz alta: el de la IA o uno armado con los recursos. */
@Composable
private fun spokenText(plan: TripPlan?): String? {
    plan?.message?.spokenText?.let { return it }
    val legs = plan?.recommended?.legs.orEmpty()
    val first = legs.firstOrNull() ?: return null
    val second = legs.getOrNull(1)
    return if (second == null) {
        stringResource(R.string.result_spoken_template, first.routeCode, first.board.name, first.alight.name)
    } else {
        stringResource(
            R.string.result_spoken_transfer_template,
            first.routeCode,
            first.board.name,
            first.alight.name,
            second.routeCode,
            second.alight.name,
        )
    }
}
