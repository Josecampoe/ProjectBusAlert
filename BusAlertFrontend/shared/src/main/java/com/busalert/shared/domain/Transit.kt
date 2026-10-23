package com.busalert.shared.domain

/** Ruta tal como la lista el backend. */
data class RouteSummary(
    val code: String,
    val name: String,
    val type: String,
    val origin: String?,
    val destination: String?,
    val colorHex: String?,
    val directionCount: Int,
    val verified: Boolean,
    val isDemo: Boolean,
)

/** Parada con su posición dentro de un sentido de la ruta. */
data class RouteStop(
    val order: Int,
    val stop: StopRef,
)

/** Un sentido de la ruta con sus paradas en orden. */
data class RouteDirection(
    val key: String,
    val name: String,
    val stops: List<RouteStop>,
) {
    /** Paradas con coordenadas (las únicas que se pueden dibujar en el mapa). */
    val locatedStops: List<RouteStop> get() = stops.filter { it.stop.latitude != null && it.stop.longitude != null }
}

/** Rutas que pasan por una parada (resultado de búsqueda). */
data class StopWithRoutes(
    val stop: StopRef,
    val neighborhood: String?,
    val isDemo: Boolean,
    val routes: List<StopRoute>,
)

data class StopRoute(
    val routeCode: String,
    val directionName: String,
)

/** Parada cercana a una coordenada, con la distancia calculada por el backend. */
data class NearbyStop(
    val stop: StopRef,
    val distanceMeters: Int,
    val isDemo: Boolean,
    val routes: List<StopRoute>,
)

/**
 * Paradas dentro del radio de búsqueda, de la más cercana a la más lejana.
 * [stopsWithoutCoordinates] cuenta las paradas que el backend no puede ubicar.
 */
data class NearbyStops(
    val stops: List<NearbyStop>,
    val stopsWithoutCoordinates: Int,
) {
    val nearest: NearbyStop? get() = stops.firstOrNull()
}

/** Consulta de rutas y paradas (app del teléfono y parada cercana del reloj). */
interface TransitRepository {
    suspend fun routes(): Outcome<List<RouteSummary>>
    suspend fun routeDirections(code: String): Outcome<List<RouteDirection>>
    suspend fun searchStops(query: String): Outcome<List<StopWithRoutes>>

    /** Paradas cercanas a [point] dentro del radio por defecto del backend (`/api/stops/nearby`). */
    suspend fun nearbyStops(point: GeoPoint): Outcome<NearbyStops>
}
