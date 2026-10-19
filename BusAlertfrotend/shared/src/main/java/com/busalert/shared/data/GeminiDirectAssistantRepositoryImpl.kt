package com.busalert.shared.data

import com.busalert.shared.domain.AIAssistantRepository

class GeminiDirectAssistantRepositoryImpl : AIAssistantRepository {
    // Cliente REST base para la comunicacion directa con Gemini API
    override suspend fun askAssistant(query: String): String {
        return "Consulta procesada: " + query
    }
}
