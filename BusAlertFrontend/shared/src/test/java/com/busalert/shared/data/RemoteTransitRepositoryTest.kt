package com.busalert.shared.data

import com.busalert.shared.config.SharedConfig
import com.busalert.shared.di.SharedDependencies
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteSummary
import com.busalert.shared.domain.StopWithRoutes
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Usa las respuestas reales del backend guardadas en `contracts/transit`. */
class RemoteTransitRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val repository by lazy {
        SharedDependencies(SharedConfig(server.url("/").toString(), "test-api-key-0123456789", false)).transitRepository
    }

    private fun contract(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("transit/$name")).readText()

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `lista las rutas con color y marca demo`() = runTest {
        enqueue(200, contract("routes.json"))
        val routes = (repository.routes() as Outcome.Success<List<RouteSummary>>).value
        assertEquals(listOf("FX1", "FX2", "FX3"), routes.map { it.code })
        assertEquals("#1565C0", routes.first().colorHex)
        assertTrue(routes.all { it.isDemo })
        assertEquals("/api/routes", server.takeRequest().path)
    }

    @Test
    fun `trae los sentidos de una ruta con sus paradas en orden`() = runTest {
        enqueue(200, contract("route_stops.json"))
        val directions = (repository.routeDirections("FX1") as Outcome.Success<List<RouteDirection>>).value
        assertEquals(listOf("FX1_D1", "FX1_D2"), directions.map { it.key })
        assertEquals(listOf(1, 2, 3, 4), directions.first().stops.map { it.order })
        assertEquals("Udenar Torobajo", directions.first().stops.first().stop.name)
        assertEquals(4, directions.first().locatedStops.size)
        assertEquals("/api/routes/FX1/stops", server.takeRequest().path)
    }

    @Test
    fun `busca paradas y agrupa las rutas que pasan`() = runTest {
        enqueue(200, contract("stops_search.json"))
        val stops = (repository.searchStops("parque") as Outcome.Success<List<StopWithRoutes>>).value
        assertEquals(listOf("Parque Nariño", "Parque Bolívar"), stops.map { it.stop.name })
        assertTrue(stops.first().routes.map { it.routeCode }.containsAll(listOf("FX1", "FX2")))
        assertEquals("/api/stops/search?q=parque", server.takeRequest().path)
    }

    @Test
    fun `traduce el error real de ruta inexistente`() = runTest {
        val recorded = JsonParser.parseString(contract("error_route_not_found.json")).asJsonObject
        enqueue(recorded["status"].asInt, recorded["body"].toString())
        assertEquals(Outcome.Failure(ErrorCode.NOT_FOUND), repository.routeDirections("ZZ9"))
    }
}
