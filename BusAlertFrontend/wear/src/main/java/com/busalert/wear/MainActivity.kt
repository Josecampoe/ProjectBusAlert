package com.busalert.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripPlan
import com.busalert.wear.theme.AppStrings
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.ErrorScreen
import com.busalert.wear.ui.HomeScreen
import com.busalert.wear.location.FusedLocationSource
import com.busalert.wear.location.LocationDefaults
import com.busalert.wear.location.hasLocationPermission
import com.busalert.wear.ui.ListeningScreen
import com.busalert.wear.ui.LocatingScreen
import com.busalert.wear.ui.LocationRationaleScreen
import com.busalert.wear.ui.NoResultScreen
import com.busalert.wear.ui.ProcessingScreen
import com.busalert.wear.ui.ProvideAppLanguage
import com.busalert.wear.ui.ResultScreen
import com.busalert.wear.ui.TypingScreen
import com.busalert.wear.voice.VoiceRecognitionManager
import com.busalert.wear.voice.VoiceSpeaker

class MainActivity : ComponentActivity() {

    private val viewModel: MainWearViewModel by viewModels {
        MainWearViewModel.Factory(
            repository = (application as BusAlertWearApp).dependencies.tripAssistantRepository,
            locationSource = FusedLocationSource(applicationContext),
        )
    }
    private lateinit var voiceSpeaker: VoiceSpeaker
    private var voiceManager: VoiceRecognitionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSpeaker = VoiceSpeaker(this)

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val language by viewModel.language.collectAsState()
            val appState by viewModel.appState.collectAsState()
            val plan by viewModel.plan.collectAsState()
            val error by viewModel.error.collectAsState()
            val typedText by viewModel.typedText.collectAsState()
            val isEnglish = language == AppLanguage.ENGLISH
            val strings = remember(isEnglish) { AppStrings(isEnglish) }

            var partialVoiceText by remember { mutableStateOf("") }
            var showLocationRationale by remember { mutableStateOf(false) }
            var voiceStatusMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(isEnglish) { voiceSpeaker.setLanguage(isEnglish) }

            DisposableEffect(Unit) {
                voiceManager = VoiceRecognitionManager(
                    context = this@MainActivity,
                    onReady = { voiceStatusMessage = null },
                    onPartialResult = { partialVoiceText = it },
                    onResult = { resultText ->
                        partialVoiceText = resultText
                        when (viewModel.appState.value) {
                            AppState.ASKING_LOCATION -> viewModel.onLocationReceived(resultText)
                            AppState.ASKING_DESTINATION -> viewModel.onDestinationReceived(resultText)
                            else -> Unit
                        }
                    },
                    onError = { voiceStatusMessage = it },
                )
                onDispose { voiceManager?.stopListening() }
            }

            ProvideAppLanguage(language) {
                BusAlertTheme(isDark = isDarkMode) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) {
                            partialVoiceText = ""
                            voiceStatusMessage = null
                            voiceManager?.startListening(isEnglish = isEnglish)
                        } else if (viewModel.appState.value == AppState.ASKING_DESTINATION) {
                            viewModel.onDestinationSpeechFailed()
                        } else {
                            viewModel.onLocationSpeechFailed()
                        }
                    }

                    // Permiso de ubicación: con o sin él se inicia la consulta (sin él se pide el lugar por voz).
                    val locationLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { viewModel.startQuery() }
                    val startQuery = {
                        when {
                            hasLocationPermission() -> viewModel.startQuery()
                            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                                showLocationRationale = true
                            else -> locationLauncher.launch(LocationDefaults.PERMISSIONS)
                        }
                    }

                    SpeakResult(plan, voiceSpeaker)

                    LaunchedEffect(appState) {
                        when (appState) {
                            AppState.ASKING_LOCATION, AppState.ASKING_DESTINATION -> {
                                partialVoiceText = ""
                                voiceStatusMessage = null
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    voiceManager?.startListening(isEnglish = isEnglish)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            else -> voiceManager?.stopListening()
                        }
                    }

                    if (showLocationRationale) {
                        LocationRationaleScreen(
                            onContinue = {
                                showLocationRationale = false
                                locationLauncher.launch(LocationDefaults.PERMISSIONS)
                            },
                            onNotNow = {
                                showLocationRationale = false
                                viewModel.startQuery()
                            },
                        )
                    } else when (appState) {
                        AppState.IDLE -> HomeScreen(
                            isDark = isDarkMode,
                            isEnglish = isEnglish,
                            onToggleDark = viewModel::toggleDarkMode,
                            onToggleLanguage = viewModel::toggleLanguage,
                            onMicClick = startQuery,
                        )

                        AppState.LOCATING -> LocatingScreen(onUseVoice = viewModel::useSpokenLocation)

                        AppState.ASKING_LOCATION, AppState.ASKING_DESTINATION -> ListeningScreen(
                            prompt = if (appState == AppState.ASKING_DESTINATION) {
                                strings.promptDestination
                            } else {
                                strings.promptLocation
                            },
                            partialText = partialVoiceText,
                            statusMessage = voiceStatusMessage,
                            isEnglish = isEnglish,
                            onRetryVoice = {
                                partialVoiceText = ""
                                voiceStatusMessage = null
                                voiceManager?.startListening(isEnglish = isEnglish)
                            },
                            onUseKeyboard = {
                                voiceManager?.stopListening()
                                if (appState == AppState.ASKING_DESTINATION) {
                                    viewModel.onDestinationSpeechFailed()
                                } else {
                                    viewModel.onLocationSpeechFailed()
                                }
                            },
                            onCancel = viewModel::resetToIdle,
                        )

                        AppState.PROCESSING -> ProcessingScreen(isEnglish = isEnglish)

                        AppState.RESULT -> plan?.let { ResultScreen(plan = it, onDismiss = viewModel::resetToIdle) }

                        AppState.NO_RESULT -> plan?.let {
                            NoResultScreen(status = it.status, onNewQuery = startQuery)
                        }

                        AppState.ERROR -> ErrorScreen(
                            error = error ?: ErrorCode.UNKNOWN,
                            onRetry = viewModel::retry,
                            onHome = viewModel::resetToIdle,
                        )

                        AppState.TYPING_LOCATION -> TypingScreen(
                            prompt = strings.promptLocation,
                            placeholder = strings.typingLocationPlaceholder,
                            inputText = typedText,
                            onTextChange = viewModel::onTypedTextChange,
                            onSubmit = viewModel::submitTypedLocation,
                            onCancel = viewModel::resetToIdle,
                            onUseVoice = viewModel::retryLocationSpeech,
                        )

                        AppState.TYPING_DESTINATION -> TypingScreen(
                            prompt = strings.promptDestination,
                            placeholder = strings.typingDestinationPlaceholder,
                            inputText = typedText,
                            onTextChange = viewModel::onTypedTextChange,
                            onSubmit = viewModel::submitTypedDestination,
                            onCancel = viewModel::resetToIdle,
                            onUseVoice = viewModel::retryDestinationSpeech,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceSpeaker.stop()
        voiceManager?.stopListening()
    }
}

/** Lee en voz alta el resultado: texto de la IA o, si no hay, una frase armada con recursos. */
@Composable
private fun SpeakResult(plan: TripPlan?, speaker: VoiceSpeaker) {
    val legs = plan?.recommended?.legs.orEmpty()
    val first = legs.firstOrNull()
    val second = legs.getOrNull(1)
    val spoken = plan?.message?.spokenText ?: when {
        first == null -> null
        second == null -> stringResource(
            R.string.result_spoken_template,
            first.routeCode,
            first.board.name,
            first.alight.name,
        )
        else -> stringResource(
            R.string.result_spoken_transfer_template,
            first.routeCode,
            first.board.name,
            first.alight.name,
            second.routeCode,
            second.alight.name,
        )
    }
    LaunchedEffect(spoken) { spoken?.let(speaker::speak) }
}
