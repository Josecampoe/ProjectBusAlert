package com.busalert.shared.data

import com.busalert.shared.domain.AIAssistantRepository
import com.busalert.shared.domain.NavigationInstruction
import kotlinx.coroutines.delay

class MockAIAssistantRepositoryImpl : AIAssistantRepository {

    override suspend fun processVoiceCommand(
        voiceText: String, 
        currentLocationName: String,
        language: String
    ): NavigationInstruction {
        // Simulamos la latencia de red (como si el reloj estuviera consultando a los servidores de Gemini/OpenAI)
        delay(1500)
        
        val lowerText = voiceText.lowercase()

        // 1. Caso Complejo: Enrutamiento en Pasto (Ej: Boyacá -> Centro)
        val mencionaCentro = lowerText.contains("centro") || lowerText.contains("plaza")
        val estaEnBoyaca = lowerText.contains("boyaca") || lowerText.contains("boyacá") || currentLocationName.lowercase().contains("boyaca")

        if (mencionaCentro && estaEnBoyaca) {
            return NavigationInstruction(
                spokenText = "Claro. Camina dos cuadras a la derecha hacia la avenida. Ahí puedes tomar la ruta C16 que va para el centro. Viene en unos 4 minutos.",
                displayTitle = "Ruta C16",
                displaySubtitle = "Camina 2 cuadras • Llega en 4 min",
                estimatedMinutes = 4,
                recommendedRouteId = "C16"
            )
        }

        // 2. Caso Básico: "Dónde viene el E1"
        if (lowerText.contains("e1")) {
            return NavigationInstruction(
                spokenText = "Tu bus E1 viene en camino. Llegará a tu parada en aproximadamente 5 minutos.",
                displayTitle = "Bus E1",
                displaySubtitle = "A 5 minutos",
                estimatedMinutes = 5,
                recommendedRouteId = "E1"
            )
        }
        
        // 3. Caso Básico: "Cuándo pasa el E2"
        if (lowerText.contains("e2")) {
            return NavigationInstruction(
                spokenText = "La ruta E2 está un poco retrasada por tráfico, llegará en 10 minutos.",
                displayTitle = "Bus E2",
                displaySubtitle = "A 10 minutos",
                estimatedMinutes = 10,
                recommendedRouteId = "E2"
            )
        }

        // 4. Caso Fallo: Repetir exactamente lo que escuchó para probar que el micrófono funciona
        return NavigationInstruction(
            spokenText = "Escuché perfectamente que dijiste: $voiceText. Aún debes conectar el backend real para que yo pueda analizar esto con inteligencia artificial.",
            displayTitle = "Micrófono Real OK",
            displaySubtitle = "Dijiste: \"$voiceText\"",
            estimatedMinutes = null,
            recommendedRouteId = null
        )
    }
}
