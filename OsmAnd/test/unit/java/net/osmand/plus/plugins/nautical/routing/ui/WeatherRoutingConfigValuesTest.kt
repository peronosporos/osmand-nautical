package net.osmand.plus.plugins.nautical.routing.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRoutingConfigValuesTest {

    @Test
    fun testSanitizeDepartureOffsetHoursKeepsValidValues() {
        listOf(0L, 1L, 3L, 6L).forEach { hours ->
            assertEquals(hours, WeatherRoutingConfigValues.sanitizeDepartureOffsetHours(hours))
        }
    }

    @Test
    fun testSanitizeDepartureOffsetHoursDefaultsInvalidValues() {
        assertEquals(0L, WeatherRoutingConfigValues.sanitizeDepartureOffsetHours(2L))
        assertEquals(0L, WeatherRoutingConfigValues.sanitizeDepartureOffsetHours(-1L))
        assertEquals(0L, WeatherRoutingConfigValues.sanitizeDepartureOffsetHours(12L))
    }

    @Test
    fun testSanitizeTimeStepHoursKeepsValidValues() {
        listOf(0.25, 0.5, 1.0, 3.0).forEach { step ->
            assertEquals(step, WeatherRoutingConfigValues.sanitizeTimeStepHours(step), 0.0)
        }
    }

    @Test
    fun testSanitizeTimeStepHoursDefaultsInvalidValues() {
        assertEquals(1.0, WeatherRoutingConfigValues.sanitizeTimeStepHours(0.75), 0.0)
        assertEquals(1.0, WeatherRoutingConfigValues.sanitizeTimeStepHours(-1.0), 0.0)
        assertEquals(1.0, WeatherRoutingConfigValues.sanitizeTimeStepHours(6.0), 0.0)
    }

    @Test
    fun testSanitizeComfortWeightClamps() {
        assertEquals(0, WeatherRoutingConfigValues.sanitizeComfortWeight(-5))
        assertEquals(0, WeatherRoutingConfigValues.sanitizeComfortWeight(0))
        assertEquals(50, WeatherRoutingConfigValues.sanitizeComfortWeight(50))
        assertEquals(100, WeatherRoutingConfigValues.sanitizeComfortWeight(100))
        assertEquals(100, WeatherRoutingConfigValues.sanitizeComfortWeight(150))
    }

    @Test
    fun testSanitizeMinDepthClearanceBounds() {
        assertEquals(2.5, WeatherRoutingConfigValues.sanitizeMinDepthClearance(0.5), 0.0)
        assertEquals(2.5, WeatherRoutingConfigValues.sanitizeMinDepthClearance(-1.0), 0.0)
        assertEquals(2.5, WeatherRoutingConfigValues.sanitizeMinDepthClearance(2.5), 0.0)
        assertEquals(1.0, WeatherRoutingConfigValues.sanitizeMinDepthClearance(1.0), 0.0)
        assertEquals(20.0, WeatherRoutingConfigValues.sanitizeMinDepthClearance(20.0), 0.0)
        assertEquals(4.0, WeatherRoutingConfigValues.sanitizeMinDepthClearance(4.0), 0.0)
        assertEquals(2.5, WeatherRoutingConfigValues.sanitizeMinDepthClearance(20.5), 0.0)
    }
}
