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
        assertEquals(28.9841042, station.constituents[0].speed, 0.0000001)
    }

    @Test
    fun testCalculation() {
        val m2 = HarmonicConstituent("M2", 1.0, 0.0, 28.9841042)
        val station = TideStation("1", "Test", 0.0, 0.0, 0, listOf(m2))
        val engine = TideCalculationEngine()
        
        // At t=0, cos(0) = 1, so height = 1.0
        val h0 = engine.calculateHeight(station, 0L)
        assertEquals(1.0, h0, 0.001)
        
        // Half period of M2 is ~6.21 hours. cos(pi) = -1
        val halfPeriodMs = (((180.0 / 28.9841042) * 3600) * 1000).toLong()
        val h1 = engine.calculateHeight(station, halfPeriodMs)
        assertEquals(-1.0, h1, 0.001)
    }

    @Test
    fun testPrediction() {
        val m2 = HarmonicConstituent("M2", 1.0, 0.0, 28.9841042)
        val station = TideStation("1", "Test", 0.0, 0.0, 0, listOf(m2))
        val engine = TideCalculationEngine()
        
        val predictions = engine.predictTides(station, 0L)
        
        // M2 has a period of ~12.42 hours. 
        // In 24 hours we expect 2 highs and 1 or 2 lows depending on start.
        assertTrue(predictions.isNotEmpty())
        assertTrue(predictions.any { it.isHighTide == true })
        assertTrue(predictions.any { it.isHighTide == false })
    }
}
