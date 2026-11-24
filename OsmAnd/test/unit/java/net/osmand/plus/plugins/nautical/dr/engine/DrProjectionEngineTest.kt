package net.osmand.plus.plugins.nautical.dr.engine

import org.junit.Assert.*
import org.junit.Test

class DrProjectionEngineTest {

    private val precision = 0.0001

    @Test
    fun `projectPosition should return same position when speed is zero`() {
        val startFix = DrFix(45.0, -10.0, 1000L, FixSource.GPS)
        val vector = DrVector(0.0, 90.0, 0.0)
        
        val result = DrProjectionEngine.projectPosition(startFix, vector, 60)
        
        assertEquals(45.0, result.latitude, precision)
        assertEquals(-10.0, result.longitude, precision)
        assertEquals(FixSource.DEAD_RECKONING, result.source)
        assertEquals(61000L, result.timestamp)
    }

    @Test
    fun `projectPosition should project north correctly`() {
        // 1 degree latitude is approx 111,111 meters
        // We project 111,111 meters North from equator
        val startFix = DrFix(0.0, 0.0, 1000L, FixSource.GPS)
        val speedMps = 111111.0 / 3600.0 // speed to cover 1 degree in 1 hour
        val vector = DrVector(speedMps, 0.0, 0.0)
        
        val result = DrProjectionEngine.projectPosition(startFix, vector, 3600)
        
        assertEquals(1.0, result.latitude, 0.01)
        assertEquals(0.0, result.longitude, precision)
    }

    @Test
    fun `projectPosition should apply leeway correctly`() {
        // Heading 90 (East), Leeway 10 (Starboard) -> Total bearing 100
        val startFix = DrFix(0.0, 0.0, 1000L, FixSource.GPS)
        val vector = DrVector(10.0, 90.0, 10.0)
        
        val result = DrProjectionEngine.projectPosition(startFix, vector, 3600)
        
        // At total bearing 100, latitude should decrease (South of equator)
        assertTrue(result.latitude < 0.0)
        // Longitude should increase (East)
        assertTrue(result.longitude > 0.0)
    }

    @Test
    fun `projectPosition should handle longitude wrap around`() {
        // At 179.9 Longitude, heading East (90)
        val startFix = DrFix(0.0, 179.9, 1000L, FixSource.GPS)
        val vector = DrVector(50.0, 90.0, 0.0) // Fast speed to cross IDL
        
        val result = DrProjectionEngine.projectPosition(startFix, vector, 3600)
        
        // Should be in Western hemisphere (negative longitude)
        assertTrue(result.longitude < 0.0)
        assertTrue(result.longitude < -170.0)
    }
}
