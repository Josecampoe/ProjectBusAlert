package com.busalert.shared.domain

/**
 * Repositorio que maneja la comunicación con la Inteligencia Artificial (ej. Gemini/OpenAI).
 */
interface AIAssistantRepository {
    /**
     * Envía el texto dictado por el usuario y su ubicación aproximada a la IA.
     * La IA analiza el contexto y devuelve una instrucción de navegación estructurada.
     */
    suspend fun processVoiceCommand(
        voiceText: String,
        currentLocationName: String,
        language: String = "es"
    ): NavigationInstruction
}

/**
 * Repositorio que maneja la capa de datos de tránsito (Rutas, Paradas).
 */
interface TransitRepository {
    suspend fun getAvailableRoutes(): List<Route>
    suspend fun getEtaForRoute(routeId: String, stopId: String): Int
}
