package com.busalert.shared.data.api

import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.LegEstimate
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TransferStep
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripMessage
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripOrigin
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus

/** Respuesta del backend que no cumple el contrato. */
class MalformedResponseException(message: String) : RuntimeException(message)

private fun <T> T?.required(field: String): T = this ?: throw MalformedResponseException("Falta $field")

fun TripQuery.toDto(): VoiceRequestDto = when (val o = origin) {
    is TripOrigin.Named -> VoiceRequestDto(destination, o.name, null, null, language.code)
    is TripOrigin.Located -> VoiceRequestDto(destination, null, o.point.latitude, o.point.longitude, language.code)
}

fun StopRefDto.toDomain() = StopRef(
    id = id.required("stop.id"),
    externalId = externalId,
    name = name.required("stop.name"),
    latitude = latitude,
    longitude = longitude,
)

/** La estimación es opcional: si viene incompleta se ignora en vez de rechazar todo el plan. */
fun EstimateDto.toDomainOrNull(): LegEstimate? {
    val ride = rideMinutes ?: return null
    val total = totalMinutes ?: return null
    return LegEstimate(ride, waitMinutes, total, withinServiceHours)
}

fun LegDto.toDomain() = TripLeg(
    routeCode = routeCode.required("leg.routeCode"),
    routeName = routeName.required("leg.routeName"),
    routeColor = routeColor,
    directionName = directionName.required("leg.directionName"),
    board = board.required("leg.board").toDomain(),
    alight = alight.required("leg.alight").toDomain(),
    stopsCount = stopsCount.required("leg.stopsCount"),
    estimate = estimate?.toDomainOrNull(),
)

fun TransferStepDto.toDomain() = TransferStep(
    from = from.required("transfer.from").toDomain(),
    to = to.required("transfer.to").toDomain(),
    walkMeters = walkMeters ?: 0,
    walkMinutes = walkMinutes ?: 0,
)

fun OptionDto.toDomain(): TripOption {
    val mapped = legs.required("option.legs").map { it.toDomain() }
    if (mapped.isEmpty()) throw MalformedResponseException("option.legs vacío")
    return TripOption(
        legs = mapped,
        transfers = transfers.required("option.transfers"),
        transferSteps = transferSteps.orEmpty().map { it.toDomain() },
        totalStops = totalStops.required("option.totalStops"),
        estimatedMinutes = estimatedMinutes,
        isDemo = isDemo ?: false,
    )
}

private fun MessageDto.toDomainOrNull(): TripMessage? {
    val spoken = spokenText?.takeIf { it.isNotBlank() } ?: return null
    val title = displayTitle?.takeIf { it.isNotBlank() } ?: return null
    val subtitle = displaySubtitle?.takeIf { it.isNotBlank() } ?: return null
    return TripMessage(spoken, title, subtitle)
}

private val STATUS_BY_WIRE = mapOf(
    "ok" to TripStatus.OK,
    "same_place" to TripStatus.SAME_PLACE,
    "no_route" to TripStatus.NO_ROUTE,
    "origin_not_found" to TripStatus.ORIGIN_NOT_FOUND,
    "destination_not_found" to TripStatus.DESTINATION_NOT_FOUND,
    "no_nearby_stops" to TripStatus.NO_NEARBY_STOPS,
)

fun TripResponseDto.toDomain(): TripPlan {
    val tripStatus = STATUS_BY_WIRE[status] ?: throw MalformedResponseException("status desconocido: $status")
    val recommendedOption = recommended?.toDomain()
    if (tripStatus == TripStatus.OK && recommendedOption == null) {
        throw MalformedResponseException("status ok sin recommended")
    }
    val boardId = recommendedOption?.firstLeg?.board?.id
    val nearest = originCandidates.orEmpty().firstOrNull { it.id == boardId } ?: originCandidates?.firstOrNull()
    return TripPlan(
        status = tripStatus,
        source = if (source == "ai") AnswerSource.AI else AnswerSource.PLANNER,
        message = message?.toDomainOrNull(),
        recommended = recommendedOption,
        alternatives = alternatives.orEmpty().map { it.toDomain() },
        nearestStopDistanceMeters = nearest?.distanceMeters,
        isDemoData = isDemoData ?: false,
    )
}
