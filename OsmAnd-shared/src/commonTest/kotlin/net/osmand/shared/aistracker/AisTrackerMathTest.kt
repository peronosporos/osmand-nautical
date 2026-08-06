package net.osmand.shared.aistracker

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.random.Random
import kotlin.test.assertEquals

class AisTrackerMathTest {

    @Test
    fun testAisTrackerMathRobustness() {
        val random = Random(42)
        val latitudes = listOf(0.0, 45.0, 85.0)

        repeat(1000) {
            val baseLat = latitudes[random.nextInt(latitudes.size)]
            val own = AisLocation(
                latitude = baseLat + (random.nextDouble() - 0.5) * 0.1,
                longitude = (random.nextDouble() - 0.5) * 360.0,
                speed = random.nextFloat() * 20f,
                bearing = random.nextFloat() * 360f
            )
            val other = AisLocation(
                latitude = baseLat + (random.nextDouble() - 0.5) * 0.1,
                longitude = own.longitude + (random.nextDouble() - 0.5) * 0.1,
                speed = random.nextFloat() * 20f,
                bearing = random.nextFloat() * 360f
            )

            val tcpa = AisTrackerMath.getTcpa(own, other)
            val cpaDist = AisTrackerMath.getCpaDistance(own, other)

            assertFalse(tcpa.isNaN(), "TCPA should not be NaN")
            assertFalse(tcpa.isInfinite(), "TCPA should not be Infinite")
            assertFalse(cpaDist.isNaN(), "CPA distance should not be NaN")
            assertFalse(cpaDist.isInfinite(), "CPA distance should not be Infinite")
        }
    }

    @Test
    fun testDivergingCourses() {
        val own = AisLocation(0.0, 0.0, 5f, 0f) // North
        val other = AisLocation(0.01, 0.0, 5f, 180f) // South, starting ahead
        
        // They are moving away from each other
        val tcpa = AisTrackerMath.getTcpa(own, other)
        assertEquals(
            tcpa,
            AisObjectConstants.INVALID_TCPA,
            "Diverging courses should return INVALID_TCPA"
        )
    }

    @Test
    fun testStationaryTarget() {
        val own = AisLocation(0.0, 0.0, 5f, 0f, hasSpeed = true, hasBearing = true) // Moving North at 5m/s
        val other = AisLocation(0.001, 0.0, 0f, 0f, hasSpeed = true, hasBearing = true) // Stationary ahead
        
        val tcpa = AisTrackerMath.getTcpa(own, other)
        
        // Distance is ~111 meters. At 5m/s, TCPA should be ~22.2 seconds = ~0.006 hours
        assertTrue(tcpa > 0.0 && tcpa < 0.01, "TCPA should be positive and small for stationary target ahead")
        
        val result = AisCpa()
        AisTrackerMath.getCpa(own, other, result)
        assertTrue(result.valid, "CPA should be valid for stationary target")
        assertTrue(result.cpa < 0.001, "CPA distance should be near zero for target directly ahead")
    }
}
