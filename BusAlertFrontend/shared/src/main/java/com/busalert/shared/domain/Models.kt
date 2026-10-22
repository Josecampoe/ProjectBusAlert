package com.busalert.shared.domain

/** Parada de bus tal como la entrega el backend. */
data class StopRef(
    val id: Long,
    val externalId: String?,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
)

/** Un tramo del viaje en una sola ruta. */
data class TripLeg(
    val routeCode: String,
    val routeName: String,
    val directionName: String,
    val board: StopRef,
    val alight: StopRef,
    val stopsCount: Int,
)

/** Una alternativa completa (uno o más tramos). */
data class TripOption(
    val legs: List<TripLeg>,
    val transfers: Int,
    val totalStops: Int,
    val isDemo: Boolean,
) {
    val firstLeg: TripLeg get() = legs.first()
}

/** Texto redactado por la IA del backend. Puede faltar: el cliente arma uno con sus recursos. */
data class TripMessage(
    val spokenText: String,
    val displayTitle: String,
    val displaySubtitle: String,
)

enum class TripStatus {
    OK,
    SAME_PLACE,
    NO_ROUTE,
    ORIGIN_NOT_FOUND,
    DESTINATION_NOT_FOUND,
}

enum class AnswerSource { AI, PLANNER }

data class TripPlan(
    val status: TripStatus,
    val source: AnswerSource,
    val message: TripMessage?,
    val recommended: TripOption?,
    val alternatives: List<TripOption>,
    val isDemoData: Boolean,
)

/** Datos del viaje que pide el usuario. */
data class TripQuery(
    val destination: String,
    val origin: String,
    val language: AppLanguage,
)

enum class AppLanguage(val code: String) {
    SPANISH("es"),
    ENGLISH("en"),
}

/**
 * Errores estables que la UI traduce con sus recursos de texto.
 * Los valores del backend se mapean en [ErrorCode.fromBackend].
 */
enum class ErrorCode {
    NETWORK,
    TIMEOUT,
    UNAUTHORIZED,
    RATE_LIMITED,
    INVALID_REQUEST,
    NOT_FOUND,
    AI_UNAVAILABLE,
    SERVER,
    CONFIGURATION,
    UNKNOWN,
    ;

    companion object {
        fun fromBackend(code: String?): ErrorCode = when (code) {
            "UNAUTHORIZED" -> UNAUTHORIZED
            "RATE_LIMITED" -> RATE_LIMITED
            "VALIDATION_ERROR", "INVALID_JSON", "PAYLOAD_TOO_LARGE" -> INVALID_REQUEST
            "NOT_FOUND", "ROUTE_NOT_FOUND", "STOP_NOT_FOUND" -> NOT_FOUND
            "AI_UNAVAILABLE" -> AI_UNAVAILABLE
            "INTERNAL_ERROR" -> SERVER
            else -> UNKNOWN
        }
    }
}

/** Resultado de una operación remota: éxito o error tipado (nunca texto). */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: ErrorCode) : Outcome<Nothing>
}
