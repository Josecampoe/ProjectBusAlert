package com.busalert.shared.data.api

import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripMessage
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripStatus

/** Respuesta del backend que no cumple el contrato. */
class MalformedResponseException(message: String) : RuntimeException(message)

private fun <T> T?.required(field: String): T = this ?: throw MalformedResponseException("Falta $field")

fun StopRefDto.toDomain() = StopRef(
    id = id.required("stop.id"),
    externalId = externalId,
    name = name.required("stop.name"),
    latitude = latitude,
    longitude = longitude,
)

fun LegDto.toDomain() = TripLeg(
    routeCode = routeCode.required("leg.routeCode"),
    routeName = routeName.required("leg.routeName"),
    directionName = directionName.required("leg.directionName"),
    board = board.required("leg.board").toDomain(),
    alight = alight.required("leg.alight").toDomain(),
    stopsCount = stopsCount.required("leg.stopsCount"),
)

fun OptionDto.toDomain(): TripOption {
    val mapped = legs.required("option.legs").map { it.toDomain() }
    if (mapped.isEmpty()) throw MalformedResponseException("option.legs vacío")
    return TripOption(
        legs = mapped,
        transfers = transfers.required("option.transfers"),
        totalStops = totalStops.required("option.totalStops"),
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
)

fun TripResponseDto.toDomain(): TripPlan {
    val tripStatus = STATUS_BY_WIRE[status] ?: throw MalformedResponseException("status desconocido: $status")
    val recommendedOption = recommended?.toDomain()
    if (tripStatus == TripStatus.OK && recommendedOption == null) {
        throw MalformedResponseException("status ok sin recommended")
    }
    return TripPlan(
        status = tripStatus,
        source = if (source == "ai") AnswerSource.AI else AnswerSource.PLANNER,
        message = message?.toDomainOrNull(),
        recommended = recommendedOption,
        alternatives = alternatives.orEmpty().map { it.toDomain() },
        isDemoData = isDemoData ?: false,
    )
}
