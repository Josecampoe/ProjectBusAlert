package com.busalert.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.*
import com.busalert.wear.voice.VoiceRecognitionManager
import com.busalert.wear.voice.VoiceSpeaker

class MainActivity : ComponentActivity() {
    private val viewModel: MainWearViewModel by viewModels()
    private lateinit var voiceSpeaker: VoiceSpeaker
    private var voiceManager: VoiceRecognitionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSpeaker = VoiceSpeaker(this)

        setContent {
            val isDarkMode    by viewModel.isDarkMode.collectAsState()
            val isEnglish     by viewModel.isEnglish.collectAsState()
            val appState      by viewModel.appState.collectAsState()
            val lastResult    by viewModel.lastResult.collectAsState()
            val errorMessage  by viewModel.errorMessage.collectAsState()
            val typedText     by viewModel.typedText.collectAsState()

            var partialVoiceText by remember { mutableStateOf("") }
            var voiceStatusMessage by remember { mutableStateOf<String?>(null) }

            // Sincroniza el idioma del sintetizador de voz (TTS)
            LaunchedEffect(isEnglish) {
                voiceSpeaker.setLanguage(isEnglish)
            }

            // Inicializa el administrador de voz nativo In-App (sin pantalla negra externa)
            DisposableEffect(Unit) {
                voiceManager = VoiceRecognitionManager(
                    context = this@MainActivity,
                    onReady = {
                        voiceStatusMessage = null
                    },
                    onPartialResult = { partial ->
                        partialVoiceText = partial
                    },
                    onResult = { resultText ->
                        partialVoiceText = resultText
                        if (appState == AppState.ASKING_LOCATION || appState == AppState.LISTENING) {
                            viewModel.onLocationReceived(resultText)
                        } else if (appState == AppState.ASKING_DESTINATION) {
                            viewModel.onDestinationReceived(resultText)
                        }
                    },
                    onError = { err ->
                        voiceStatusMessage = err
                    }
                )
                onDispose {
                    voiceManager?.stopListening()
                }
            }

            BusAlertTheme(isDark = isDarkMode) {
                // ── Launcher de permiso de micrófono ──────────────────────────────
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        partialVoiceText = ""
                        voiceStatusMessage = null
                        voiceManager?.startListening(isEnglish = isEnglish)
                    } else {
                        when (appState) {
                            AppState.ASKING_LOCATION    -> viewModel.onLocationSpeechFailed()
                            AppState.ASKING_DESTINATION -> viewModel.onDestinationSpeechFailed()
                            else                        -> viewModel.onLocationSpeechFailed()
                        }
                    }
                }

                // ── TTS: se dispara cuando llega un resultado ─────────────────────
                LaunchedEffect(lastResult) {
                    if (appState == AppState.RESULT) {
                        lastResult?.spokenText?.let { voiceSpeaker.speak(it) }
                    }
                }

                // ── Control de voz In-App según el estado de la app ──────────────
                LaunchedEffect(appState) {
                    when (appState) {
                        AppState.ASKING_LOCATION,
                        AppState.ASKING_DESTINATION -> {
                            partialVoiceText = ""
                            voiceStatusMessage = null

                            val hasPerm = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPerm) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                voiceManager?.startListening(isEnglish = isEnglish)
                            }
                        }
                        else -> {
                            voiceManager?.stopListening()
                        }
                    }
                }

                // ── Navegación por pantallas ──────────────────────────────────────
                when (appState) {
                    AppState.IDLE -> HomeScreen(
                        isDark           = isDarkMode,
                        isEnglish        = isEnglish,
                        onToggleDark     = { viewModel.toggleDarkMode() },
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onMicClick       = { viewModel.onMicrophoneClick() }
                    )

                    // Pantalla de escucha 100% integrada (se queda fija en la app)
                    AppState.ASKING_LOCATION,
                    AppState.ASKING_DESTINATION,
                    AppState.LISTENING -> ListeningScreen(
                        prompt = when (appState) {
                            AppState.ASKING_DESTINATION -> if (isEnglish) "Where to?" else "¿A dónde vas?"
                            else                        -> if (isEnglish) "Where are you?" else "¿Dónde estás?"
                        },
                        partialText   = partialVoiceText,
                        statusMessage = voiceStatusMessage,
                        isEnglish     = isEnglish,
                        onRetryVoice  = {
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
                        }
                    )

                    AppState.PROCESSING -> ProcessingScreen(isEnglish = isEnglish)

                    AppState.RESULT -> lastResult?.let { result ->
                        ResultScreen(
                            result    = result,
                            isEnglish = isEnglish,
                            onDismiss = { viewModel.resetToIdle() }
                        )
                    }

                    AppState.ERROR -> ErrorScreen(
                        message   = errorMessage,
                        isEnglish = isEnglish,
                        onRetry   = { viewModel.resetToIdle() }
                    )

                    // Fallback escritura — ubicación (con opción de regresar a voz)
                    AppState.TYPING_LOCATION -> TypingScreen(
                        prompt       = if (isEnglish) "Where are you?" else "¿Dónde estás?",
                        placeholder  = if (isEnglish) "e.g. Nariño Park" else "Ej: Parque Nariño",
                        inputText    = typedText,
                        onTextChange = { viewModel.onTypedTextChange(it) },
                        onSubmit     = { viewModel.submitTypedLocation() },
                        onCancel     = { viewModel.resetToIdle() },
                        onUseVoice   = { viewModel.retryLocationSpeech() }
                    )

                    // Fallback escritura — destino (con opción de regresar a voz)
                    AppState.TYPING_DESTINATION -> TypingScreen(
                        prompt       = if (isEnglish) "Where to?" else "¿A dónde vas?",
                        placeholder  = if (isEnglish) "e.g. Lorenzo Aldana" else "Ej: Lorenzo de Aldana",
                        inputText    = typedText,
                        onTextChange = { viewModel.onTypedTextChange(it) },
                        onSubmit     = { viewModel.submitTypedDestination() },
                        onCancel     = { viewModel.resetToIdle() },
                        onUseVoice   = { viewModel.retryDestinationSpeech() }
                    )
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
