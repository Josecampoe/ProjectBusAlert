package com.busalert.shared.data

import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripOrigin
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus

/**
 * Implementación de DEMOSTRACIÓN para desarrollar la UI sin backend.
 * Solo se activa en builds debug con `busalert.useMock=true`.
 * Los datos son ficticios: la ruta se llama "DEMO" y todo va marcado como demo.
 */
class DemoTripAssistantRepository : TripAssistantRepository {

    override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> {
        val originName = (query.origin as? TripOrigin.Named)?.name ?: DEMO_ROUTE_CODE
        val board = StopRef(id = DEMO_BOARD_ID, externalId = null, name = originName, latitude = null, longitude = null)
        val alight = StopRef(id = DEMO_ALIGHT_ID, externalId = null, name = query.destination, latitude = null, longitude = null)
        val option = TripOption(
            legs = listOf(
                TripLeg(
                    routeCode = DEMO_ROUTE_CODE,
                    routeName = DEMO_ROUTE_CODE,
                    routeColor = null,
                    directionName = DEMO_ROUTE_CODE,
                    board = board,
                    alight = alight,
                    stopsCount = DEMO_STOPS_COUNT,
                    estimate = null,
                ),
            ),
            transfers = 0,
            transferSteps = emptyList(),
            totalStops = DEMO_STOPS_COUNT,
            estimatedMinutes = null,
            isDemo = true,
        )
        return Outcome.Success(
            TripPlan(
                status = TripStatus.OK,
                source = AnswerSource.PLANNER,
                message = null,
                recommended = option,
                alternatives = emptyList(),
                nearestStopDistanceMeters = null,
                isDemoData = true,
            ),
        )
    }

    private companion object {
        const val DEMO_ROUTE_CODE = "DEMO"
        const val DEMO_BOARD_ID = -1L
        const val DEMO_ALIGHT_ID = -2L
        const val DEMO_STOPS_COUNT = 3
    }
}
