package com.busalert.shared.data

import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.GeoPoint
import com.busalert.shared.domain.NearbyStops
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopWithRoutes
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery

private val notConfigured = Outcome.Failure(ErrorCode.CONFIGURATION)

/** Se usa cuando el build no trae URL del backend o clave de aplicación. */
object UnconfiguredTripAssistantRepository : TripAssistantRepository {
    override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> = notConfigured
}

/** Igual que [UnconfiguredTripAssistantRepository], para rutas y paradas. */
object UnconfiguredTransitRepository : TransitRepository {
    override suspend fun routes(): Outcome<List<RouteSummary>> = notConfigured
    override suspend fun routeDirections(code: String): Outcome<List<RouteDirection>> = notConfigured
    override suspend fun searchStops(query: String): Outcome<List<StopWithRoutes>> = notConfigured
    override suspend fun nearbyStops(point: GeoPoint): Outcome<NearbyStops> = notConfigured
}
