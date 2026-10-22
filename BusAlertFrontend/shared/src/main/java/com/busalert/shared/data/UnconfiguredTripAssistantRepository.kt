package com.busalert.shared.data

import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery

/** Se usa cuando el build no trae URL del backend o clave de aplicación. */
object UnconfiguredTripAssistantRepository : TripAssistantRepository {
    override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> = Outcome.Failure(ErrorCode.CONFIGURATION)
}
