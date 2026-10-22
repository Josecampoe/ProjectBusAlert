package com.busalert.shared

import com.busalert.shared.domain.Route
import com.busalert.shared.domain.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelsTest {
    @Test
    fun testStopModelCreation() {
        val stop = Stop(
            id = "E1_01",
            name = "Estacion UIS",
            latitude = 7.1402,
            longitude = -73.1210
        )
        assertEquals("E1_01", stop.id)
        assertEquals("Estacion UIS", stop.name)
    }

    @Test
    fun testRouteModel() {
        val route = Route(
            id = "E1",
            name = "Expreso UIS",
            stops = listOf()
        )
        assertNotNull(route)
        assertEquals("E1", route.id)
    }
}
