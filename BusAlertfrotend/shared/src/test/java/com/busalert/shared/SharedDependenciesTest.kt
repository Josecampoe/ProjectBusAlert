package com.busalert.shared

import com.busalert.shared.di.SharedDependencies
import org.junit.Assert.assertNotNull
import org.junit.Test

class SharedDependenciesTest {
    @Test
    fun testTransitRepositoryAvailability() {
        val repo = SharedDependencies.transitRepository
        assertNotNull(repo)
    }

    @Test
    fun testAIAssistantRepositoryAvailability() {
        val assistant = SharedDependencies.aiAssistantRepository
        assertNotNull(assistant)
    }
}
