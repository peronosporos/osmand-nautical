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
    fun testConvergingCourses() {
        // Head-on closing: own west heading east, other east heading west.
        val own = AisLocation(0.0, 0.0, 5f, 90f)
        val other = AisLocation(0.0, 2.0 / 60.0, 5f, 270f) // ~2 NM east

        val tcpa = AisTrackerMath.getTcpa(own, other)
        // Each vessel covers 5 m/s = 9.719 kn; closing rate 19.438 kn over 2 NM.
        assertTrue(tcpa > 0.1 && tcpa < 0.11, "TCPA should be ~0.103 h for head-on closing, was $tcpa")

        val result = AisCpa()
        AisTrackerMath.getCpa(own, other, result)
        assertTrue(result.valid, "CPA should be valid for head-on closing")
        assertEquals(0.0, result.cpa.toDouble(), 0.05, "Head-on CPA should be near zero, was ${result.cpa}")
    }

    @Test
    fun testDivergingCourses() {        // Own is north of other and heading north, other is south of own and heading south:
        // the separation grows, so this is a genuinely diverging scenario.
        val own = AisLocation(0.01, 0.0, 5f, 0f) // North of other, moving North
        val other = AisLocation(0.0, 0.0, 5f, 180f) // South of own, moving South

        val tcpa = AisTrackerMath.getTcpa(own, other)
        assertEquals(
            AisObjectConstants.INVALID_TCPA,
            tcpa,
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
