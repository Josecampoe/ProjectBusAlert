package com.busalert.wear.theme

/**
 * Cadenas bilingües (Español / Inglés) para todas las pantallas de BusAlert.
 */
data class AppStrings(
    val isEnglish: Boolean
) {
    val appName: String get() = "BusAlert"
    val cityName: String get() = "P A S T O"

    // HomeScreen
    val homeTapConsult: String get() = if (isEnglish) "Tap to start" else "Toca para consultar"
    val homeModeDark: String get() = if (isEnglish) "Dark" else "Oscuro"
    val homeModeLight: String get() = if (isEnglish) "Light" else "Claro"

    // Pasos guiados
    val promptLocation: String get() = if (isEnglish) "Where are you?" else "¿Dónde estás?"
    val promptDestination: String get() = if (isEnglish) "Where to?" else "¿A dónde vas?"
    val listeningSpeakNow: String get() = if (isEnglish) "Speak now..." else "Habla ahora..."
    val listeningStatus: String get() = if (isEnglish) "Listening..." else "Escuchando..."

    // Procesando
    val processingTitle: String get() = if (isEnglish) "Finding route..." else "Consultando ruta..."
    val processingSubtitle: String get() = if (isEnglish) "Searching best option" else "Buscando la mejor opción"

    // Modo escritura manual
    val typingLocationPlaceholder: String get() = if (isEnglish) "e.g. Nariño Park" else "Ej: Parque Nariño"
    val typingDestinationPlaceholder: String get() = if (isEnglish) "e.g. Lorenzo Aldana" else "Ej: Lorenzo de Aldana"

    // Resultado
    val resultClose: String get() = if (isEnglish) "Close" else "Cerrar"
    val routePrefix: String get() = if (isEnglish) "Route" else "Ruta"

    // Errores
    val errorTitle: String get() = if (isEnglish) "Something went wrong" else "Algo salió mal"
    val errorRetry: String get() = if (isEnglish) "Retry" else "Reintentar"

    // Prompts del reconocedor de voz nativo (STT)
    val sttPromptLocation: String get() = if (isEnglish) "Where are you now?" else "¿Dónde estás ahora?"
    val sttPromptDestination: String get() = if (isEnglish) "Where do you want to go?" else "¿A dónde quieres ir?"
    val sttLanguageTag: String get() = if (isEnglish) "en-US" else "es-CO"
}
