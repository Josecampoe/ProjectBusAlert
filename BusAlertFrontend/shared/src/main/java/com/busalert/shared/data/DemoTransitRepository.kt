package com.busalert.shared.data

import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteStop
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.StopRoute
import com.busalert.shared.domain.StopWithRoutes
import com.busalert.shared.domain.TransitRepository

/**
 * Rutas y paradas de DEMOSTRACIÓN para desarrollar la UI sin backend
 * (solo en debug con `busalert.useMock=true`). Todo va marcado como demo.
 */
class DemoTransitRepository : TransitRepository {

    private val stops = (1..DEMO_STOP_COUNT).map { StopRef(it.toLong(), null, "$DEMO_PREFIX $it", null, null) }

    override suspend fun routes(): Outcome<List<RouteSummary>> = Outcome.Success(
        listOf(
            RouteSummary(
                code = DEMO_PREFIX,
                name = DEMO_PREFIX,
                type = DEMO_PREFIX,
                origin = stops.first().name,
                destination = stops.last().name,
                colorHex = null,
                directionCount = 1,
                verified = false,
                isDemo = true,
            ),
        ),
    )

    override suspend fun routeDirections(code: String): Outcome<List<RouteDirection>> = Outcome.Success(
        listOf(RouteDirection(DEMO_PREFIX, DEMO_PREFIX, stops.mapIndexed { i, s -> RouteStop(i + 1, s) })),
    )

    override suspend fun searchStops(query: String): Outcome<List<StopWithRoutes>> = Outcome.Success(
        stops.filter { it.name.contains(query, ignoreCase = true) }
            .map { StopWithRoutes(it, null, isDemo = true, routes = listOf(StopRoute(DEMO_PREFIX, DEMO_PREFIX))) },
    )

    private companion object {
        const val DEMO_PREFIX = "DEMO"
        const val DEMO_STOP_COUNT = 4
    }
}
