package com.busalert.shared.domain

/** Asistente de viaje: el backend resuelve origen/destino y calcula el plan. */
interface TripAssistantRepository {
    suspend fun planTrip(query: TripQuery): Outcome<TripPlan>
}
