package net.osmand.plus.plugins.nautical.utils

import net.osmand.plus.plugins.nautical.AnchorCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorCalculatorTest {

    @Test
    fun testCalculateRodeLength() {
        val rode = AnchorCalculator.calculateRodeLength(10.0, 2.0, 1.5, 5.0)
        assertEquals(67.5, rode, 0.01)
    }

    @Test
    fun testCalculateRecommendedScope() {
        // Low wind
        assertEquals(5.0, AnchorCalculator.calculateRecommendedScope(5.0, 5.0f), 0.01)
        // High wind
        assertEquals(7.0, AnchorCalculator.calculateRecommendedScope(12.0, 5.0f), 0.01)
        // User preference higher than wind threshold
        assertEquals(8.0, AnchorCalculator.calculateRecommendedScope(5.0, 8.0f), 0.01)
        assertEquals(10.0, AnchorCalculator.calculateRecommendedScope(12.0, 8.0f), 0.01)
    }

    @Test
    fun testCalculateTotalRadius() {
        val radius = AnchorCalculator.calculateTotalRadius(50.0, 10.0, 5.0)
        assertEquals(65.0, radius, 0.01)
    }

    @Test
    fun testCalculateAnchorDrop() {
        val lat = 45.0
        val lon = 10.0
        val heading = 0.0 // North
        val bowOffset = 10.0 // 10 meters
        
        val drop = AnchorCalculator.calculateAnchorDrop(lat, lon, heading, bowOffset)
        
        // At 45 degrees lat, 10m north is approx 0.00009 degrees
        assertEquals(lat + 0.0000898, drop.latitude, 0.00001)
        assertEquals(lon, drop.longitude, 0.00001)
    }
}
