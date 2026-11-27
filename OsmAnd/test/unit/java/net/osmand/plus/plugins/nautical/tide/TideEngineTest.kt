package net.osmand.plus.plugins.nautical.tide

import net.osmand.plus.plugins.nautical.tide.engine.TideCalculationEngine
import net.osmand.plus.plugins.nautical.tide.model.HarmonicConstituent
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TideEngineTest {

    @Test
    fun testParser() {
        val data = """
            constituent M2 28.9841042
            constituent S2 30.0000000
            
            station "Test Harbor"
            location 45.0 -123.0
            timezone UTC-8
            M2 1.5 180.0
            S2 0.5 90.0
        """.trimIndent()

        val parser = HarmonicDataParser()
        val stations = parser.parse(ByteArrayInputStream(data.toByteArray()))

        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals("Test Harbor", station.name)
        assertEquals(45.0, station.latitude, 0.0001)
        assertEquals(-123.0, station.longitude, 0.0001)
        assertEquals(2, station.constituents.size)
        assertEquals("M2", station.constituents[0].name)
        assertEquals(28.9841042, station.constituents[0].speed ?: 0.0, 0.0000001)
    }

    @Test
    fun testCalculation() {
        val m2 = HarmonicConstituent("M2", 1.0, 0.0, 28.9841042)
        val station = TideStation("1", "Test", 0.0, 0.0, 0, constituents = listOf(m2))
        val engine = TideCalculationEngine()

        // The engine evaluates the full Doodson astronomical argument
        // (V + u - g with nodal factor f), so h(0) is NOT the naive cos(0) = 1.0:
        // the epoch angle depends on the Moon/Sun mean longitudes at the epoch.
        // For a single constituent the result is still a pure, slowly modulated
        // sinusoid at the constituent speed, so two invariants pin the frequency:
        // over one M2 half period the curve inverts, over one full period it
        // repeats (both hold to ~2e-5 for integer-millisecond offsets, verified
        // against a Python port of the engine). The historic bug that added
        // speed*t twice cycled at double frequency and fails both invariants.
        val halfPeriodMs = (((180.0 / 28.9841042) * 3600) * 1000).toLong()
        val periodMs = (((360.0 / 28.9841042) * 3600) * 1000).toLong()
        val hourMs = 3600L * 1000L
        for (t0 in longArrayOf(0L, 3L * hourMs, 27L * hourMs, 5L * 24 * hourMs, 200L * 24 * hourMs)) {
            val h0 = engine.calculateHeight(station, t0)
            assertEquals("half-period inversion at t=$t0",
                -h0, engine.calculateHeight(station, t0 + halfPeriodMs), 0.001)
            assertEquals("full-period repetition at t=$t0",
                h0, engine.calculateHeight(station, t0 + periodMs), 0.001)
        }

        // A station with no constituents returns its datum unchanged.
        val emptyStation = TideStation("2", "Empty", 0.0, 0.0, 0, datum = 2.5, constituents = emptyList())
        assertEquals(2.5, engine.calculateHeight(emptyStation, 0L), 0.000001)
    }

    @Test
    fun testPrediction() {
        val m2 = HarmonicConstituent("M2", 1.0, 0.0, 28.9841042)
        val station = TideStation("1", "Test", 0.0, 0.0, 0, constituents = listOf(m2))
        val engine = TideCalculationEngine()
        
        val predictions = engine.predictTides(station, 0L)
        
        // M2 has a period of ~12.42 hours. 
        // In 24 hours we expect 2 highs and 1 or 2 lows depending on start.
        assertTrue(predictions.isNotEmpty())
        assertTrue(predictions.any { it.isHighTide == true })
        assertTrue(predictions.any { it.isHighTide == false })
    }
}
