package com.busalert.shared.domain

data class Route(
    val id: String,
    val name: String,
    val description: String
)

data class Stop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Representa la respuesta estructurada que envía la Inteligencia Artificial 
 * después de analizar la voz del usuario.
 */
data class NavigationInstruction(
    val spokenText: String, // Texto amigable y natural que el reloj leerá en voz alta
    val displayTitle: String, // Título principal para la pantalla (ej. "Ruta C16")
    val displaySubtitle: String, // Subtítulo para la pantalla (ej. "A 4 min • Camina 2 cuadras")
    val estimatedMinutes: Int? = null, // Tiempo estimado si aplica
    val recommendedRouteId: String? = null // ID de la ruta sugerida por la IA
)
