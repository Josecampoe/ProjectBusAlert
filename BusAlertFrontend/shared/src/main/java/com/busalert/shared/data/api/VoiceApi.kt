package com.busalert.shared.data.api

import com.busalert.shared.domain.NavigationInstruction
import retrofit2.http.Body
import retrofit2.http.POST

data class VoiceRequest(
    val voiceText: String,
    val currentLocationName: String,
    val language: String = "es"
)

interface VoiceApi {
    @POST("api/voice")
    suspend fun processVoiceCommand(@Body request: VoiceRequest): NavigationInstruction
}
