package com.busalert.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.busalert.shared.di.SharedDependencies
import com.busalert.shared.domain.NavigationInstruction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estados de la app:
 *
 * IDLE              → pantalla de inicio (logo + selector modo/idioma + botón micrófono)
 * ASKING_LOCATION   → escuchando/preguntando "¿Dónde estás?"
 * ASKING_DESTINATION→ escuchando/preguntando "¿A dónde vas?"
 * LISTENING         → animación de barras de audio (STT activo)
 * PROCESSING        → spinner mientras el backend calcula la ruta
 * RESULT            → muestra la ruta recomendada
 * ERROR             → error de red o de IA
 * TYPING_LOCATION   → fallback: el usuario escribe su ubicación
 * TYPING_DESTINATION→ fallback: el usuario escribe su destino
 */
enum class AppState {
    IDLE,
    ASKING_LOCATION,
    ASKING_DESTINATION,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR,
    TYPING_LOCATION,
    TYPING_DESTINATION
}

class MainWearViewModel : ViewModel() {
    private val aiRepository = SharedDependencies.aiAssistantRepository

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _lastResult = MutableStateFlow<NavigationInstruction?>(null)
    val lastResult: StateFlow<NavigationInstruction?> = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _typedText = MutableStateFlow("")
    val typedText: StateFlow<String> = _typedText.asStateFlow()

    // ── Preferencias del usuario: Modo Oscuro e Idioma ────────────────
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isEnglish = MutableStateFlow(false)
    val isEnglish: StateFlow<Boolean> = _isEnglish.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleLanguage() {
        _isEnglish.value = !_isEnglish.value
    }

    // Almacena la ubicación capturada en el paso 1
    private val _capturedLocation = MutableStateFlow<String?>(null)
    val capturedLocation: StateFlow<String?> = _capturedLocation.asStateFlow()

    // ─── Paso 1: el usuario pulsa el botón en HomeScreen ──────────────
    fun onMicrophoneClick() {
        _capturedLocation.value = null
        _lastResult.value = null
        _errorMessage.value = null
        _typedText.value = ""
        _appState.value = AppState.ASKING_LOCATION
    }

    // ─── Inicia STT (la UI lo llama cuando está lista para escuchar) ──
    fun onStartListening() {
        _appState.value = AppState.LISTENING
    }

    // ─── Recibe la ubicación hablada (Paso 1 completado) ──────────────
    fun onLocationReceived(location: String) {
        _capturedLocation.value = location.trim()
        _appState.value = AppState.ASKING_DESTINATION
    }

    // ─── Recibe el destino hablado (Paso 2 completado → llama al backend)
    fun onDestinationReceived(destination: String) {
        val location = _capturedLocation.value ?: "Pasto, Nariño"
        processVoiceInput(destination.trim(), location)
    }

    // ─── Llama al backend con ubicación + destino + idioma ────────────
    private fun processVoiceInput(destination: String, location: String) {
        _appState.value = AppState.PROCESSING
        val lang = if (_isEnglish.value) "en" else "es"

        viewModelScope.launch {
            try {
                val instruction = aiRepository.processVoiceCommand(destination, location, lang)
                val isError = instruction.recommendedRouteId == null && (
                    instruction.displayTitle.contains("Error", ignoreCase = true) ||
                    instruction.displayTitle.contains("Apagado", ignoreCase = true) ||
                    instruction.displayTitle.contains("Offline", ignoreCase = true) ||
                    instruction.displayTitle.contains("Unavailable", ignoreCase = true) ||
                    instruction.displayTitle.contains("conexión", ignoreCase = true) ||
                    instruction.displayTitle.contains("conexion", ignoreCase = true)
                )
                if (isError) {
                    _errorMessage.value = instruction.spokenText
                    _appState.value = AppState.ERROR
                } else {
                    _lastResult.value = instruction
                    _appState.value = AppState.RESULT
                }
            } catch (e: Exception) {
                _errorMessage.value = if (_isEnglish.value) {
                    "Could not connect to Gemini AI. Check your internet connection."
                } else {
                    "No se pudo conectar con la IA de Gemini. Verifica tu conexión a internet."
                }
                _appState.value = AppState.ERROR
            }
        }
    }

    // ─── Fallbacks de escritura ────────────────────────────────────────

    /** STT falló en el paso de ubicación → modo escritura */
    fun onLocationSpeechFailed() {
        _appState.value = AppState.TYPING_LOCATION
    }

    /** STT falló en el paso de destino → modo escritura */
    fun onDestinationSpeechFailed() {
        _appState.value = AppState.TYPING_DESTINATION
    }

    /** Cambia de escritura a voz en el paso de ubicación */
    fun retryLocationSpeech() {
        _appState.value = AppState.ASKING_LOCATION
    }

    /** Cambia de escritura a voz en el paso de destino */
    fun retryDestinationSpeech() {
        _appState.value = AppState.ASKING_DESTINATION
    }

    fun onTypedTextChange(text: String) {
        _typedText.value = text
    }

    /** Envía el texto escrito como ubicación */
    fun submitTypedLocation() {
        val text = _typedText.value.trim()
        if (text.isNotEmpty()) {
            _typedText.value = ""
            onLocationReceived(text)
        }
    }

    /** Envía el texto escrito como destino */
    fun submitTypedDestination() {
        val text = _typedText.value.trim()
        if (text.isNotEmpty()) {
            _typedText.value = ""
            onDestinationReceived(text)
        }
    }

    fun resetToIdle() {
        _appState.value = AppState.IDLE
        _lastResult.value = null
        _errorMessage.value = null
        _capturedLocation.value = null
        _typedText.value = ""
    }
}
