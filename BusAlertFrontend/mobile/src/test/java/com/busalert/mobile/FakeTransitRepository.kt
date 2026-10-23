package com.busalert.mobile

import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.GeoPoint
import com.busalert.shared.domain.NearbyStops
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteStop
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.StopRoute
import com.busalert.shared.domain.StopWithRoutes
import com.busalert.shared.domain.TransitRepository

/** Datos ficticios (rutas FX*, como los fixtures del backend). */
object MobileTestData {
    val fx1 = RouteSummary("FX1", "FX1 — Udenar / Centro", "prueba", "Udenar", "Centro", "#1565C0", 2, false, isDemo = true)
    val fx2 = RouteSummary("FX2", "FX2 — Centro / Terminal", "prueba", null, null, null, 1, true, isDemo = false)
    val e1 = RouteSummary("E1", "E1 — Dolores / Udenar", "estrategica", "Dolores", "Udenar", null, 2, true, isDemo = false)

    private val udenar = StopRef(1, "T01", "Udenar Torobajo", 1.2335, -77.2925)
    private val narino = StopRef(4, "T04", "Parque Nariño", null, null)
    private val bolivar = StopRef(3, "T03", "Parque Bolívar", null, null)

    val fx1Directions = listOf(
        RouteDirection("FX1_D1", "Udenar → Centro", listOf(RouteStop(1, udenar), RouteStop(2, bolivar), RouteStop(3, narino))),
        RouteDirection("FX1_D2", "Centro → Udenar", listOf(RouteStop(1, narino), RouteStop(2, bolivar), RouteStop(3, udenar))),
    )

    val parqueResults = listOf(
        StopWithRoutes(narino, "Centro", isDemo = true, routes = listOf(StopRoute("FX1", "Udenar → Centro"), StopRoute("FX2", "Centro → Terminal"))),
        StopWithRoutes(bolivar, null, isDemo = true, routes = listOf(StopRoute("FX1", "Udenar → Centro"))),
    )
}

/** Repositorio de transporte configurable para pruebas. */
class FakeTransitRepository : TransitRepository {
    var routesResult: Outcome<List<RouteSummary>> = Outcome.Success(listOf(MobileTestData.fx2, MobileTestData.fx1, MobileTestData.e1))
    var directionsResult: Outcome<List<RouteDirection>> = Outcome.Success(MobileTestData.fx1Directions)
    val searches = mutableListOf<String>()
    var searchResult: Outcome<List<StopWithRoutes>> = Outcome.Success(MobileTestData.parqueResults)

    override suspend fun routes() = routesResult
    override suspend fun routeDirections(code: String) = directionsResult
    override suspend fun searchStops(query: String): Outcome<List<StopWithRoutes>> {
        searches += query
        return searchResult
    }

    var nearbyResult: Outcome<NearbyStops> = Outcome.Success(NearbyStops(emptyList(), 0))
    override suspend fun nearbyStops(point: GeoPoint) = nearbyResult

    companion object {
        val networkError: Outcome.Failure = Outcome.Failure(ErrorCode.NETWORK)
    }
}
