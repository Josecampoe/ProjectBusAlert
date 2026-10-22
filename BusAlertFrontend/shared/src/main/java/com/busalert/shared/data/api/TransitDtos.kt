package com.busalert.shared.data.api

import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteStop
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.StopRoute
import com.busalert.shared.domain.StopWithRoutes

// DTO anulables (ver BusAlertApi.kt): se validan al convertir al dominio.

data class RouteListDto(val routes: List<RouteSummaryDto>?)

data class RouteSummaryDto(
    val code: String?,
    val name: String?,
    val type: String?,
    val origin: String?,
    val destination: String?,
    val color: String?,
    val directionCount: Int?,
    val verified: Boolean?,
    val isDemo: Boolean?,
)

data class RouteStopsDto(val routeCode: String?, val directions: List<DirectionDto>?)

data class DirectionDto(val key: String?, val name: String?, val stops: List<DirectionStopDto>?)

data class DirectionStopDto(
    val order: Int?,
    val id: Long?,
    val externalId: String?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class StopSearchDto(val stops: List<StopSearchItemDto>?)

data class StopSearchItemDto(
    val id: Long?,
    val externalId: String?,
    val name: String?,
    val neighborhood: String?,
    val latitude: Double?,
    val longitude: Double?,
    val isDemo: Boolean?,
    val routes: List<StopRouteDto>?,
)

data class StopRouteDto(val routeCode: String?, val directionName: String?)

private fun <T> T?.required(field: String): T = this ?: throw MalformedResponseException("Falta $field")

fun RouteListDto.toDomain(): List<RouteSummary> = routes.required("routes").map {
    RouteSummary(
        code = it.code.required("route.code"),
        name = it.name.required("route.name"),
        type = it.type.orEmpty(),
        origin = it.origin,
        destination = it.destination,
        colorHex = it.color,
        directionCount = it.directionCount ?: 0,
        verified = it.verified ?: false,
        isDemo = it.isDemo ?: false,
    )
}

fun RouteStopsDto.toDomain(): List<RouteDirection> = directions.required("directions").map { dir ->
    RouteDirection(
        key = dir.key.required("direction.key"),
        name = dir.name.required("direction.name"),
        stops = dir.stops.orEmpty().map { s ->
            RouteStop(
                order = s.order.required("stop.order"),
                stop = StopRef(s.id.required("stop.id"), s.externalId, s.name.required("stop.name"), s.latitude, s.longitude),
            )
        },
    )
}

fun StopSearchDto.toDomain(): List<StopWithRoutes> = stops.required("stops").map { s ->
    StopWithRoutes(
        stop = StopRef(s.id.required("stop.id"), s.externalId, s.name.required("stop.name"), s.latitude, s.longitude),
        neighborhood = s.neighborhood,
        isDemo = s.isDemo ?: false,
        routes = s.routes.orEmpty()
            .mapNotNull { r -> r.routeCode?.let { StopRoute(it, r.directionName.orEmpty()) } }
            .distinct(),
    )
}
