package com.busalert.shared.data

import com.busalert.shared.config.NetworkDefaults
import com.busalert.shared.config.SharedConfig
import com.busalert.shared.di.SharedDependencies
import com.busalert.shared.domain.AnswerSource
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.busalert.shared.domain.TripStatus
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteTripAssistantRepositoryTest {

    private lateinit var server: MockWebServer
    private val query = TripQuery(destination = "Parque Bolívar", origin = "Udenar", language = AppLanguage.ENGLISH)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(readTimeoutMs: Long = NetworkDefaults.READ_TIMEOUT_MS) = SharedDependencies(
        SharedConfig(
            backendBaseUrl = server.url("/").toString(),
            apiKey = API_KEY,
            useMockBackend = false,
            readTimeoutMs = readTimeoutMs,
        ),
    ).tripAssistantRepository

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("responses/$name")).readText()

    /** Respuesta real del backend guardada por sus pruebas de contrato. */
    private fun contract(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("voice/$name")).readText()

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `envía la consulta con la clave de aplicación y el contrato esperado`() = runTest {
        enqueueJson(200, contract("ok_ai.json"))
        repository().planTrip(query)

        val recorded = server.takeRequest()
        assertEquals("/api/voice", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals(API_KEY, recorded.getHeader(NetworkDefaults.API_KEY_HEADER))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals("Parque Bolívar", body["voiceText"].asString)
        assertEquals("Udenar", body["currentLocationName"].asString)
        assertEquals("en", body["language"].asString)
    }

    @Test
    fun `convierte una respuesta exitosa al dominio`() = runTest {
        enqueueJson(200, contract("ok_ai.json"))
        val plan = (repository().planTrip(query) as Outcome.Success<TripPlan>).value

        assertEquals(TripStatus.OK, plan.status)
        assertEquals(AnswerSource.AI, plan.source)
        assertEquals("FX1", plan.message?.displayTitle)
        assertEquals("Udenar Torobajo", plan.recommended?.firstLeg?.board?.name)
        assertEquals("Parque Bolívar", plan.recommended?.firstLeg?.alight?.name)
        assertEquals(2, plan.recommended?.totalStops)
        assertTrue(plan.isDemoData)
    }

    @Test
    fun `acepta estados sin plan`() = runTest {
        enqueueJson(200, contract("no_route.json"))
        val plan = (repository().planTrip(query) as Outcome.Success<TripPlan>).value
        assertEquals(TripStatus.NO_ROUTE, plan.status)
        assertNull(plan.recommended)
        assertNull(plan.message)
    }

    @Test
    fun `entiende la respuesta del planificador sin texto de IA`() = runTest {
        enqueueJson(200, contract("ok_planner.json"))
        val plan = (repository().planTrip(query) as Outcome.Success<TripPlan>).value
        assertEquals(AnswerSource.PLANNER, plan.source)
        assertNull(plan.message)
        assertEquals("FX1", plan.recommended?.firstLeg?.routeCode)
    }

    @Test
    fun `entiende los errores reales del backend`() = runTest {
        for ((file, expected) in listOf(
            "error_unauthorized.json" to ErrorCode.UNAUTHORIZED,
            "error_validation.json" to ErrorCode.INVALID_REQUEST,
        )) {
            val recorded = JsonParser.parseString(contract(file)).asJsonObject
            enqueueJson(recorded["status"].asInt, recorded["body"].toString())
            assertEquals(Outcome.Failure(expected), repository().planTrip(query))
        }
    }

    @Test
    fun `traduce los códigos de error del backend`() = runTest {
        val cases = mapOf(
            401 to "UNAUTHORIZED" to ErrorCode.UNAUTHORIZED,
            429 to "RATE_LIMITED" to ErrorCode.RATE_LIMITED,
            400 to "VALIDATION_ERROR" to ErrorCode.INVALID_REQUEST,
            503 to "AI_UNAVAILABLE" to ErrorCode.AI_UNAVAILABLE,
            500 to "INTERNAL_ERROR" to ErrorCode.SERVER,
        )
        for ((pair, expected) in cases) {
            val (status, code) = pair
            enqueueJson(status, """{"error":{"code":"$code","message":"x"}}""")
            assertEquals(Outcome.Failure(expected), repository().planTrip(query))
        }
    }

    @Test
    fun `un error HTTP sin cuerpo JSON es UNKNOWN`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        assertEquals(Outcome.Failure(ErrorCode.UNKNOWN), repository().planTrip(query))
    }

    @Test
    fun `una respuesta que no cumple el contrato es SERVER`() = runTest {
        enqueueJson(200, resource("trip_malformed.json"))
        assertEquals(Outcome.Failure(ErrorCode.SERVER), repository().planTrip(query))
        enqueueJson(200, "esto no es json")
        assertEquals(Outcome.Failure(ErrorCode.SERVER), repository().planTrip(query))
    }

    @Test
    fun `sin red es NETWORK y sin respuesta a tiempo es TIMEOUT`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(Outcome.Failure(ErrorCode.NETWORK), repository().planTrip(query))

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(Outcome.Failure(ErrorCode.TIMEOUT), repository(readTimeoutMs = SHORT_TIMEOUT_MS).planTrip(query))
    }

    private companion object {
        const val API_KEY = "test-api-key-0123456789"
        const val SHORT_TIMEOUT_MS = 200L
    }
}
