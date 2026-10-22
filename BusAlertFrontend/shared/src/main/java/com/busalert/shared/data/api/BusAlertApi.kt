package com.busalert.shared.data.api

import retrofit2.http.Body
import retrofit2.http.POST

/** Contrato HTTP del backend BusAlert. */
interface BusAlertApi {
    @POST("api/voice")
    suspend fun planTrip(@Body request: VoiceRequestDto): TripResponseDto
}

// Los DTO son anulables a propósito: Gson no respeta la nulidad de Kotlin,
// así que la validación se hace al convertirlos al dominio.

data class VoiceRequestDto(
    val voiceText: String,
    val currentLocationName: String,
    val language: String,
)

data class StopRefDto(
    val id: Long?,
    val externalId: String?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class LegDto(
    val routeCode: String?,
    val routeName: String?,
    val directionName: String?,
    val board: StopRefDto?,
    val alight: StopRefDto?,
    val stopsCount: Int?,
)

data class OptionDto(
    val legs: List<LegDto>?,
    val transfers: Int?,
    val totalStops: Int?,
    val isDemo: Boolean?,
)

data class MessageDto(
    val spokenText: String?,
    val displayTitle: String?,
    val displaySubtitle: String?,
)

data class TripResponseDto(
    val status: String?,
    val source: String?,
    val message: MessageDto?,
    val recommended: OptionDto?,
    val alternatives: List<OptionDto>?,
    val isDemoData: Boolean?,
)

data class ErrorBodyDto(val error: ErrorDto?)

data class ErrorDto(val code: String?, val message: String?)
