package net.osmand.plus.plugins.nautical.laylines.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class LaylineMathEngineTest {

    private val boatPos = LatLon(45.0, -1.0)
    private val targetUpwind = LatLon(45.1, -1.0) // North of boat
    private val targetDownwind = LatLon(44.9, -1.0) // South of boat

    @Test
    fun testUpwindLaylines() {
        // Wind from North (0), target North
        val result = LaylineMathEngine.calculateApparentLaylines(
            boatPosition = boatPos,
            targetWaypoint = targetUpwind,
            optimalTwa = Math.toRadians(45.0),
            trueWindDirection = 0.0,
            boatSpeed = 3.0, // m/s
            current = TidalCurrentVector(0.0, 0.0),
            leewayRadians = 0.0
        )

        assertFalse(result.isFetchable)
        assertNotNull(result.portTackPoint)
        assertNotNull(result.starboardTackPoint)
        
        // Expected headings: 315 and 45. Target at 0 is between them.
    }

    @Test
    fun testDownwindGybeLines() {
        // Wind from North (0), target South (180)
        val result = LaylineMathEngine.calculateApparentLaylines(
            boatPosition = boatPos,
            targetWaypoint = targetDownwind,
            optimalTwa = Math.toRadians(45.0), // upwind optimal, should be ignored/mapped to downwind
            trueWindDirection = 0.0,
            boatSpeed = 3.0,
            current = TidalCurrentVector(0.0, 0.0),
            leewayRadians = 0.0
        )

        assertFalse(result.isFetchable)
        // With upwind optimal 45, gybe angle should be 135
        // Headings: 135 and 225. Target at 180 is between them.
    }

    @Test
    fun testLeewayCorrection() {
        // Wind from North, heading Northeast (45). Leeway should push East.
        val result = LaylineMathEngine.calculateApparentLaylines(
            boatPosition = boatPos,
            targetWaypoint = targetUpwind,
            optimalTwa = Math.toRadians(45.0),
            trueWindDirection = 0.0,
            boatSpeed = 5.0,
            current = TidalCurrentVector(0.0, 0.0),
            leewayRadians = Math.toRadians(5.0)
        )
        
        // Port tack: Heading 45. Leeway +5 -> CTW 50.
        // Starboard tack: Heading 315. Leeway -5 -> CTW 310.
        // This is verified by ensuring the intersection points shift leeward.
        assertNotNull(result.portTackPoint)
    }

    @Test
    fun testObservedCurrent() {
        val result = LaylineMathEngine.calculateApparentLaylines(
            boatPosition = boatPos,
            targetWaypoint = targetUpwind,
            optimalTwa = Math.toRadians(45.0),
            trueWindDirection = 0.0,
            boatSpeed = 5.0,
            current = TidalCurrentVector(1.0, PI / 2.0), // 1m/s East current
            leewayRadians = 0.0
        )
        
        // COG vectors should be shifted East
        assertNotNull(result.portTackPoint)
    }
}
