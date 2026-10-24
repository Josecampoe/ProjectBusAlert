package com.busalert.shared.data

import com.busalert.shared.data.api.BusAlertApi
import com.busalert.shared.data.api.toDomain
import com.busalert.shared.data.api.toDto
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery

/** Implementación real: llama a `POST /api/voice` del backend. */
class RemoteTripAssistantRepository(private val api: BusAlertApi) : TripAssistantRepository {
    override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> =
        safeApiCall { api.planTrip(query.toDto()).toDomain() }
}
