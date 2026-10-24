package com.busalert.shared.data

import com.busalert.shared.data.api.BusAlertApi
import com.busalert.shared.data.api.toDomain
import com.busalert.shared.domain.GeoPoint
import com.busalert.shared.domain.NearbyStops
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopWithRoutes
import com.busalert.shared.domain.TransitRepository

/** Rutas y paradas desde el backend (`/api/routes`, `/api/stops/search`, `/api/stops/nearby`). */
class RemoteTransitRepository(private val api: BusAlertApi) : TransitRepository {
    override suspend fun routes(): Outcome<List<RouteSummary>> = safeApiCall { api.routes().toDomain() }

    override suspend fun routeDirections(code: String): Outcome<List<RouteDirection>> =
        safeApiCall { api.routeStops(code).toDomain() }

    override suspend fun searchStops(query: String): Outcome<List<StopWithRoutes>> =
        safeApiCall { api.searchStops(query).toDomain() }

    override suspend fun nearbyStops(point: GeoPoint): Outcome<NearbyStops> =
        safeApiCall { api.nearbyStops(point.latitude, point.longitude).toDomain() }
}
