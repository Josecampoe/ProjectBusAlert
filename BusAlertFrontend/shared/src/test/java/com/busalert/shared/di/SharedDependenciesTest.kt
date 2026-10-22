package com.busalert.shared.di

import com.busalert.shared.config.SharedConfig
import com.busalert.shared.data.DemoTripAssistantRepository
import com.busalert.shared.data.RemoteTripAssistantRepository
import com.busalert.shared.data.UnconfiguredTripAssistantRepository
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedDependenciesTest {

    private val configured = SharedConfig(
        backendBaseUrl = "https://api.busalert.example/",
        apiKey = "test-api-key-0123456789",
        useMockBackend = false,
    )

    @Test
    fun `por defecto usa el backend real`() {
        assertTrue(SharedDependencies(configured).tripAssistantRepository is RemoteTripAssistantRepository)
    }

    @Test
    fun `usa la demo solo cuando el flag lo pide`() {
        val deps = SharedDependencies(configured.copy(useMockBackend = true))
        assertTrue(deps.tripAssistantRepository is DemoTripAssistantRepository)
    }

    @Test
    fun `sin URL o sin clave responde CONFIGURATION en vez de fallar`() = runTest {
        val query = TripQuery("Terminal", "Centro", AppLanguage.SPANISH)
        for (config in listOf(configured.copy(backendBaseUrl = ""), configured.copy(apiKey = ""))) {
            val repo = SharedDependencies(config).tripAssistantRepository
            assertEquals(UnconfiguredTripAssistantRepository, repo)
            assertEquals(Outcome.Failure(ErrorCode.CONFIGURATION), repo.planTrip(query))
        }
    }

    @Test
    fun `la demo marca todo como datos de demostración`() = runTest {
        val plan = DemoTripAssistantRepository().planTrip(TripQuery("Terminal", "Centro", AppLanguage.SPANISH))
        val value = (plan as Outcome.Success<TripPlan>).value
        assertTrue(value.isDemoData)
        assertTrue(value.recommended!!.isDemo)
    }
}
