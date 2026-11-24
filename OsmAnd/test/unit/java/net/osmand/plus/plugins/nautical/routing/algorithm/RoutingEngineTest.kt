package net.osmand.plus.plugins.nautical.routing.algorithm

import io.mockk.every
import io.mockk.mockk
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.WindVector
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import org.junit.Assert.*
import org.junit.Test

class RoutingEngineTest {

    @Test
    fun testCalculateRoute() {
        val gribEngine = mockk<GribInterpolationEngine>()
        val engine = IsochroneRoutingEngine(gribEngine)

        val polar = PolarProfile(
            name = "Test Polar",
            description = null,
            tws = listOf(6.0, 10.0),
            twa = listOf(40.0, 90.0),
            speeds = listOf(listOf(5.0, 6.0), listOf(6.0, 7.0))
        )

        val request = RoutingRequest(
            start = Waypoint(45.0, -1.0),
            destination = Waypoint(45.2, -1.0),
            departureTime = System.currentTimeMillis(),
            polarProfile = polar
        )

        every { gribEngine.getWindVector(any(), any(), any()) } returns WindVector(5.0, 0.0)

        val result = engine.calculateRoute(request)

        assertNotNull("Result should not be null", result)
        assertTrue("Path should contain at least 2 points", result!!.path.size >= 2)
        assertTrue("Total time should be positive", result.totalTimeHours > 0)
    }
}
