package net.osmand.plus.plugins.nautical

import net.osmand.plus.plugins.nautical.engine.CircularBuffer
import net.osmand.plus.plugins.nautical.utils.AngleEMA
import net.osmand.plus.plugins.nautical.utils.EMA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Unit tests for the pure-logic nautical helpers (no Android dependencies).
 */
class NauticalLogicTest {

	@Test
	fun emaFirstValuePassesThrough() {
		assertEquals(7.0, EMA(0.1).update(7.0), 1e-12)
	}

	@Test
	fun emaConvergesToSteadyInput() {
		val e = EMA(0.5)
		e.update(10.0); e.update(10.0); e.update(10.0)
		// alpha = 0.5: 10,10,10 then 20 -> 0.5*20 + 0.5*10 = 15
		assertEquals(15.0, e.update(20.0), 1e-9)
		var v = 0.0
		repeat(20) { v = e.update(20.0) }
		assertTrue("EMA should converge to 20, was $v", v > 19.9)
	}

	@Test
	fun angleEmaHandlesNorthWraparound() {
		val a = AngleEMA(1.0) // alpha = 1 -> passthrough of latest value
		a.update(Math.toRadians(355.0))
		val deg = Math.toDegrees(a.update(Math.toRadians(5.0)))
		assertTrue("normalized out of range: $deg", deg in 0.0..360.0)
		assertEquals(5.0, deg, 1e-6)
	}

	@Test
	fun angleEmaSmoothsAcrossNorth() {
		// 350 deg and 10 deg average to 0 deg, not 180 deg.
		val a = AngleEMA(0.5)
		a.update(350.0 * PI / 180.0)
		val mid = Math.toDegrees(a.update(10.0 * PI / 180.0))
		assertEquals("smoothed angle across north should be ~0 deg, was $mid", 0.0, mid, 0.5)
	}

	@Test
	fun circularBufferEvictsOldest() {
		val b = CircularBuffer<Int>(3)
		(1..5).forEach { b.add(it) }
		assertEquals(listOf(3, 4, 5), b.getAll())
	}

	@Test
	fun circularBufferCapacityShrinkKeepsNewest() {
		val b = CircularBuffer<Int>(10)
		(1..10).forEach { b.add(it) }
		b.setCapacity(2)
		assertEquals(listOf(9, 10), b.getAll())
		assertEquals(2, b.capacity)
	}
}
