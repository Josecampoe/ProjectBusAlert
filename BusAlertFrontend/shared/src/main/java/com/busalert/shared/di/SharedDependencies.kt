package com.busalert.shared.di

import com.busalert.shared.data.GeminiDirectAssistantRepositoryImpl
import com.busalert.shared.data.MockTransitRepositoryImpl
import com.busalert.shared.data.api.gemini.GeminiApiService
import com.busalert.shared.data.api.gemini.GeminiConfig
import com.busalert.shared.domain.AIAssistantRepository
import com.busalert.shared.domain.TransitRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SharedDependencies {

    // Cliente Retrofit conectado directamente a Google Gemini API (Sin Backend local)
    private val geminiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GeminiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val geminiApiService: GeminiApiService by lazy {
        geminiRetrofit.create(GeminiApiService::class.java)
    }

    // Repositorio de IA con conexión directa a la API de Gemini (cero backend requerido)
    val aiAssistantRepository: AIAssistantRepository by lazy {
        GeminiDirectAssistantRepositoryImpl(geminiApiService)
    }

    val transitRepository: TransitRepository by lazy {
        MockTransitRepositoryImpl()
    }
}
