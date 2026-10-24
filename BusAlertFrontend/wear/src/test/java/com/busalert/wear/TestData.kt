package com.busalert.wear

import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.LegEstimate
import com.busalert.shared.domain.StopRef
import com.busalert.shared.domain.TransferStep
import com.busalert.shared.domain.TripLeg
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripStatus

/** Datos ficticios compartidos por las pruebas del reloj (rutas FX*, igual que los fixtures del backend). */
object TestData {
    val udenar = StopRef(1, "T01", "Udenar Torobajo", null, null)
    val bolivar = StopRef(3, "T03", "Parque Bolívar", null, null)
    val narino = StopRef(4, "T04", "Parque Nariño", null, null)
    val terminal = StopRef(6, "T06", "Terminal de Transportes", null, null)

    val directLeg = TripLeg(
        routeCode = "FX1",
        routeName = "FX1",
        routeColor = "#1565C0",
        directionName = "Udenar → Centro",
        board = udenar,
        alight = bolivar,
        stopsCount = 2,
        estimate = LegEstimate(rideMinutes = 7, waitMinutes = 5, totalMinutes = 12, withinServiceHours = true),
    )

    val okPlan = TripPlan(
        status = TripStatus.OK,
        source = AnswerSource.PLANNER,
        message = null,
        recommended = TripOption(
            legs = listOf(directLeg),
            transfers = 0,
            transferSteps = emptyList(),
            totalStops = 2,
            estimatedMinutes = 12,
            isDemo = true,
        ),
        alternatives = emptyList(),
        nearestStopDistanceMeters = null,
        isDemoData = true,
    )

    val transferPlan = okPlan.copy(
        recommended = TripOption(
            legs = listOf(
                directLeg.copy(alight = narino, stopsCount = 3),
                directLeg.copy(routeCode = "FX2", board = narino, alight = terminal, stopsCount = 2, estimate = null),
            ),
            transfers = 1,
            transferSteps = listOf(TransferStep(narino, narino, walkMeters = 0, walkMinutes = 0)),
            totalStops = 5,
            estimatedMinutes = 21,
            isDemo = true,
        ),
        nearestStopDistanceMeters = 40,
    )
}
