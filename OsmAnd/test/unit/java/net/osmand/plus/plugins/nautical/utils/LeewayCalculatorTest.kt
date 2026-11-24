package net.osmand.plus.plugins.nautical.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LeewayCalculatorTest {

    @Test
    fun testCalculateLeewayNormalSpeed() {
        // Heel 10 degrees, STW 5 knots, K = 1.0
        // Leeway = 1.0 * (10 / 5^2) = 0.4 degrees
        val heelRad = Math.toRadians(10.0)
        val stwMs = 5.0 / 1.94384 // 5 knots in m/s
        val coefficient = 1.0f
        
        val resultRad = LeewayCalculator.calculateLeewayRadians(heelRad, stwMs, coefficient)
        val resultDeg = Math.toDegrees(resultRad)
        
        assertEquals(0.4, resultDeg, 0.001)
    }

    @Test
    fun testCalculateLeewayLowSpeed() {
        // Test division by zero avoidance
        val heelRad = Math.toRadians(10.0)
        val stwMs = 0.0
        val coefficient = 1.0f
        
        val resultRad = LeewayCalculator.calculateLeewayRadians(heelRad, stwMs, coefficient)
        assertEquals(0.0, resultRad, 0.0)
    }
    
    @Test
    fun testCalculateLeewayVeryLowSpeed() {
        // STW 0.25 knots (below MIN_STW_KNOTS = 0.5)
        // factor = 0.25 / 0.5 = 0.5
        // peakLeeway = 1.0 * (10 / 0.5^2) = 40.0
        // result = 40.0 * 0.5 = 20.0 degrees
        val heelRad = Math.toRadians(10.0)
        val stwMs = 0.25 / 1.94384
        val coefficient = 1.0f
        
        val resultRad = LeewayCalculator.calculateLeewayRadians(heelRad, stwMs, coefficient)
        val resultDeg = Math.toDegrees(resultRad)
        
        assertEquals(20.0, resultDeg, 0.001)
    }
}
