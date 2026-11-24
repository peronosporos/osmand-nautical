package net.osmand.shared.aistracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario-level tests for [AisTrackerMath] CPA/TCPA behavior that complement the
 * robustness tests in [AisTrackerMathTest]. Speeds are in m/s per [AisLocation].
 */
class AisCpaScenarioTest {

	private fun loc(lat: Double, lon: Double, speedMs: Double, brg: Double) = AisLocation(
		latitude = lat, longitude = lon, speed = speedMs.toFloat(), bearing = brg.toFloat(),
	)

	@Test
	fun headOnClosingHasPositiveTcpaAndNearZeroCpa() {
		// Two vessels ~2 NM apart on reciprocal courses, head-on closing.
		// 5 m/s = 9.719 kn each, closing rate ~19.44 kn over ~2 NM -> TCPA ~0.103 h.
		val own = loc(0.0, 0.0, 5.0, 90.0)
		val other = loc(0.0, 2.0 / 60.0, 5.0, 270.0)
		val tcpa = AisTrackerMath.getTcpa(own, other)
		assertTrue(tcpa in 0.1000..0.1060, "TCPA expected ~0.103h, got $tcpa")

		val cpa = AisTrackerMath.getCpaDistance(own, other)
		assertEquals(0.0, cpa.toDouble(), 0.05, "head-on CPA should be ~0, got $cpa")
	}

	@Test
	fun crossingScenarioCpaWithinCurrentDistance() {
		val own = loc(0.0, 0.0, 6.0, 90.0)
		val other = loc(1.0 / 60.0, 0.0, 6.0, 180.0)
		val cpa = AisTrackerMath.getCpaDistance(own, other)
		assertTrue(cpa in 0.0..1.0, "CPA out of range: $cpa")
	}

	@Test
	fun parallelEqualSpeedYieldsInvalidCpa() {
		// Parallel courses 1 NM apart, identical speed: relative speed is zero,
		// so the tracker reports INVALID (no future CPA) rather than the constant separation.
		val own = loc(0.0, 0.0, 5.0, 0.0)
		val other = loc(1.0 / 60.0, 0.0, 5.0, 0.0)
		val cpa = AisTrackerMath.getCpaDistance(own, other)
		assertEquals(AisObjectConstants.INVALID_CPA, cpa, "parallel equal speed should be INVALID_CPA, was $cpa")
	}

	@Test
	fun reciprocalDiagonalCpaObjectIsValid() {
		val own = loc(0.0, 0.0, 5.0, 45.0)
		val other = loc(0.05, 0.05, 5.0, 225.0) // reciprocal diagonal: head-on
		val cpa = AisCpa()
		AisTrackerMath.getCpa(own, other, cpa)
		assertTrue(cpa.valid, "cpa should be valid for head-on")
		assertTrue(cpa.cpa >= 0.0, "negative cpa: ${cpa.cpa}")
		assertTrue(cpa.tcpa > 0 || cpa.tcpa == 0.0, "tcpa should be >= 0, was ${cpa.tcpa}")
	}

	@Test
	fun anchoredTargetDirectlyAheadCpaNearZero() {
		// Own does 8 m/s due north toward a target anchored 30 NM due north.
		val own = loc(0.0, 0.0, 8.0, 0.0)
		val other = loc(0.5, 0.0, 0.0, 0.0)
		val cpa = AisCpa()
		AisTrackerMath.getCpa(own, other, cpa)
		assertTrue(cpa.valid, "cpa should be valid")
		assertEquals(0.0, cpa.cpa.toDouble(), 0.1, "direct approach: CPA should be ~0 within rhumb-line drift, was ${cpa.cpa}")
		assertTrue(cpa.tcpa in 1.9..2.0, "TCPA expected ~1.93h, got ${cpa.tcpa}")
	}
}
