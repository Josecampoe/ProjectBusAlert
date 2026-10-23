package com.busalert.shared.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato HTTP del backend BusAlert. */
interface BusAlertApi {
    @POST("api/voice")
    suspend fun planTrip(@Body request: VoiceRequestDto): TripResponseDto

    @GET("api/routes")
    suspend fun routes(): RouteListDto

    @GET("api/routes/{code}/stops")
    suspend fun routeStops(@Path("code") code: String): RouteStopsDto

    @GET("api/stops/search")
    suspend fun searchStops(@Query("q") query: String): StopSearchDto

    @GET("api/stops/nearby")
    suspend fun nearbyStops(@Query("lat") latitude: Double, @Query("lng") longitude: Double): NearbyStopsDto
}

// Los DTO son anulables a propósito: Gson no respeta la nulidad de Kotlin,
// así que la validación se hace al convertirlos al dominio.

/** Origen por texto (currentLocationName) o por GPS (latitude + longitude). Gson omite los null. */
data class VoiceRequestDto(
    val voiceText: String,
    val currentLocationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val language: String,
)

data class StopRefDto(
    val id: Long?,
    val externalId: String?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class OriginCandidateDto(
    val id: Long?,
    val name: String?,
    val distanceMeters: Int?,
)

data class EstimateDto(
    val rideMinutes: Int?,
    val waitMinutes: Int?,
    val totalMinutes: Int?,
    val withinServiceHours: Boolean?,
)

data class LegDto(
    val routeCode: String?,
    val routeName: String?,
    val routeColor: String?,
    val directionName: String?,
    val board: StopRefDto?,
    val alight: StopRefDto?,
    val stopsCount: Int?,
    val estimate: EstimateDto?,
)

data class TransferStepDto(
    val from: StopRefDto?,
    val to: StopRefDto?,
    val walkMeters: Int?,
    val walkMinutes: Int?,
)

data class OptionDto(
    val legs: List<LegDto>?,
    val transfers: Int?,
    val transferSteps: List<TransferStepDto>?,
    val totalStops: Int?,
    val estimatedMinutes: Int?,
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
    val originCandidates: List<OriginCandidateDto>?,
    val isDemoData: Boolean?,
)

data class ErrorBodyDto(val error: ErrorDto?)

data class ErrorDto(val code: String?, val message: String?)
