package com.busalert.shared.data

import com.busalert.shared.domain.Route
import com.busalert.shared.domain.TransitRepository
import kotlinx.coroutines.delay

class MockTransitRepositoryImpl : TransitRepository {
    override suspend fun getAvailableRoutes(): List<Route> {
        // Simulamos latencia de red hacia un backend de transporte de Pasto
        delay(500) 
        return listOf(
            Route("E1", "Ruta E1", "Norte - Sur"),
            Route("E2", "Ruta E2", "Sur - Norte"),
            Route("C16", "Ruta C16", "Boyacá - Centro"),
            Route("E4", "Ruta E4", "Terminal - Centro")
        )
    }

    override suspend fun getEtaForRoute(routeId: String, stopId: String): Int {
        delay(300)
        // Valores fijos para la demostración
        return when (routeId.uppercase()) {
            "C16" -> 4
            "E1" -> 5
            "E2" -> 10
            else -> 15
        }
    }
}
