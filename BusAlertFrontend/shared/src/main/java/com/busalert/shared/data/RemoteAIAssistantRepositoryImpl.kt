package com.busalert.shared.data

import com.busalert.shared.data.api.VoiceApi
import com.busalert.shared.data.api.VoiceRequest
import com.busalert.shared.domain.AIAssistantRepository
import com.busalert.shared.domain.NavigationInstruction

class RemoteAIAssistantRepositoryImpl(
    private val voiceApi: VoiceApi
) : AIAssistantRepository {

    override suspend fun processVoiceCommand(
        voiceText: String,
        currentLocationName: String,
        language: String
    ): NavigationInstruction {
        return try {
            val request = VoiceRequest(voiceText, currentLocationName, language)
            // Hacemos la llamada real HTTP por Retrofit al backend
            voiceApi.processVoiceCommand(request)
        } catch (e: Exception) {
            e.printStackTrace()
            val isEn = language == "en"
            // Fallback en caso de que el servidor Node.js esté apagado o sin red
            NavigationInstruction(
                spokenText = if (isEn) "Could not connect to the system backend. Make sure the Node server is running." else "No pude conectarme al cerebro del sistema. Asegúrate de que el servidor Node está encendido.",
                displayTitle = if (isEn) "Backend Offline" else "Backend Apagado",
                displaySubtitle = if (isEn) "Network error" else "Error de red",
                estimatedMinutes = null,
                recommendedRouteId = null
            )
        }
    }
}
