package net.osmand.plus.plugins.nautical.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalKUnitConverterTest {

    @Test
    fun testSpeedConversion() {
        // 5.14444 m/s ≈ 10 knots
        assertEquals(10.0, SignalKUnitConverter.msToKnots(5.14444), 0.001)
    }

    @Test
    fun testTemperatureConversion() {
        assertEquals(20.0, SignalKUnitConverter.kelvinToCelsius(293.15), 0.001)
        assertEquals(0.0, SignalKUnitConverter.kelvinToCelsius(273.15), 0.001)
        assertEquals(-273.15, SignalKUnitConverter.kelvinToCelsius(0.0), 0.001)
    }

    @Test
    fun testPressureConversion() {
        assertEquals(1013.25, SignalKUnitConverter.pascalToHpa(101325.0), 0.001)
        assertEquals(1.0, SignalKUnitConverter.pascalToBar(100000.0), 0.001)
    }

    @Test
    fun testDistanceConversion() {
        assertEquals(1.0, SignalKUnitConverter.metersToNm(1852.0), 0.001)
        assertEquals(1852.0, SignalKUnitConverter.nmToMeters(1.0), 0.001)
        assertEquals(3.2808, SignalKUnitConverter.metersToFeet(1.0), 0.001)
    }

    @Test
    fun testAngleConversion() {
        assertEquals(180.0, SignalKUnitConverter.radToDeg(Math.PI), 0.001)
        assertEquals(90.0, SignalKUnitConverter.radToDeg(Math.PI / 2), 0.001)
    }

    @Test
    fun testEdgeCases() {
        assertEquals(Double.NaN, SignalKUnitConverter.msToKnots(Double.NaN), 0.0)
    }
}
