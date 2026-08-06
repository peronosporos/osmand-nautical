package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.utils.OsmAndFormatter
import java.util.Locale

/**
 * Centralized utility for converting Signal K SI units to OsmAnd user preferences.
 * Leverages OsmAnd core formatters for consistent UI presentation.
 */
object SignalKUnitConverter {

    const val KELVIN_ZERO_CELSIUS = 273.15
    const val KELVIN_TO_CELSIUS = 1.0
    const val PASCAL_TO_HPA = 0.01
    const val PASCAL_TO_BAR = 1.0e-5
    const val METERS_TO_NM = 1.0 / 1852.0
    const val METERS_TO_FEET = 3.2808399
    const val METERS_TO_MILES = 1.0 / 1609.344

    fun kelvinToCelsius(k: Double): Double = (k - KELVIN_ZERO_CELSIUS) * KELVIN_TO_CELSIUS
    fun radToDeg(rad: Double): Double = Math.toDegrees(rad)
    fun msToKnots(ms: Double): Double = ms * 1.94384449
    fun pascalToHpa(pa: Double): Double = pa * PASCAL_TO_HPA
    fun pascalToBar(pa: Double): Double = pa * PASCAL_TO_BAR
    fun metersToNm(m: Double): Double = m * METERS_TO_NM
    fun nmToMeters(nm: Double): Double = nm * 1852.0
    fun metersToFeet(m: Double): Double = m * METERS_TO_FEET
    fun hertzToRpm(hz: Double): Double = hz * 60.0

    fun getUserDistanceToMeters(value: Double, settings: OsmandSettings): Double {
        return when (settings.METRIC_SYSTEM.get().getDistanceUnit()) {
            net.osmand.shared.units.LengthUnits.NAUTICAL_MILES -> nmToMeters(value)
            net.osmand.shared.units.LengthUnits.MILES -> value * 1609.344
            net.osmand.shared.units.LengthUnits.KILOMETERS -> value * 1000.0
            else -> value
        }
    }

    fun metersToUserDistance(meters: Double, settings: OsmandSettings): Double {
        return when (settings.METRIC_SYSTEM.get().getDistanceUnit()) {
            net.osmand.shared.units.LengthUnits.NAUTICAL_MILES -> metersToNm(meters)
            net.osmand.shared.units.LengthUnits.MILES -> meters * METERS_TO_MILES
            net.osmand.shared.units.LengthUnits.KILOMETERS -> meters / 1000.0
            else -> meters
        }
    }

    fun transformAngle(rad: Double, variation: Double, toMagnetic: Boolean): Double {
        val result = if (toMagnetic) (rad - variation) else (rad + variation)
        return (result % (2 * Math.PI) + (2 * Math.PI)) % (2 * Math.PI)
    }

    fun formatValue(
        context: Context,
        settings: OsmandSettings,
        value: Double?,
        path: String,
        variation: Double? = null,
    ): Pair<String, String> {
        if ((value == null) || value.isNaN() || value.isInfinite()) return context.getString(R.string.n_a) to ""

        val isAngle = path.contains("angle", ignoreCase = true) || path.contains("heading", ignoreCase = true) ||
                      path.contains("course", ignoreCase = true) || path.contains("direction", ignoreCase = true) ||
                      path.contains("roll") || path.contains("pitch") || path.contains("yaw")
        
        var effectiveValue = value
        if (isAngle && variation != null) {
            val useMagnetic = settings.NAUTICAL_HEADING_REFERENCE.get() == net.osmand.plus.settings.enums.HeadingReference.MAGNETIC
            val isTruePath = path.contains("True", ignoreCase = true) || path == "navigation.headingTrue"
            val isMagPath = path.contains("Magnetic", ignoreCase = true) || path == "navigation.headingMagnetic"
            
            if (useMagnetic && isTruePath) {
                effectiveValue = transformAngle(value, variation, true)
            } else if (!useMagnetic && isMagPath) {
                effectiveValue = transformAngle(value, variation, false)
            }
        }

        // Extreme outlier prevention
        val isOutlier = when {
            path.contains("speed", ignoreCase = true) -> (effectiveValue < -5.0) || (effectiveValue > 100.0)
            path.contains("depth", ignoreCase = true) -> (effectiveValue < -20.0) || (effectiveValue > 15000.0)
            path.contains("temperature", ignoreCase = true) -> (effectiveValue < 100.0) || (effectiveValue > 500.0)
            path.contains("voltage", ignoreCase = true) -> (effectiveValue < 0.0) || (effectiveValue > 600.0)
            else -> false
        }
        if (isOutlier) return "---" to ""

        val app = context.applicationContext as OsmandApplication

        return when {
            path.contains("speed", ignoreCase = true) || path.endsWith("STW") || path.endsWith("SOG") -> {
                val fv = OsmAndFormatter.getFormattedSpeedValue(effectiveValue.toFloat(), app)
                fv.value to fv.unit
            }
            path.contains("temperature", ignoreCase = true) -> {
                format(kelvinToCelsius(effectiveValue), context.getString(R.string.nautical_unit_celsius), "%.1f")
            }
            path.contains("pressure", ignoreCase = true) -> {
                if (path.contains("oil", ignoreCase = true)) {
                    format(pascalToBar(effectiveValue), context.getString(R.string.nautical_unit_bar), "%.1f")
                } else {
                    format(pascalToHpa(effectiveValue), context.getString(R.string.nautical_unit_hpa), "%.0f")
                }
            }
            path.contains("depth", ignoreCase = true) -> {
                val fv = OsmAndFormatter.getFormattedAltitudeValue(effectiveValue, app, settings.ALTITUDE_METRIC.get())
                fv.value to fv.unit
            }
            path.contains("log", ignoreCase = true) || path.contains("distance", ignoreCase = true) || path.contains("cpa", ignoreCase = true) -> {
                val fv = OsmAndFormatter.getFormattedDistanceValue(effectiveValue.toFloat(), app)
                fv.value to fv.unit
            }
            isAngle -> {
                format(radToDeg(effectiveValue), context.getString(R.string.nautical_unit_deg), "%.0f")
            }
            path.contains("currentLevel", ignoreCase = true) || path.contains("stateOfCharge", ignoreCase = true) ||
            path.contains("polarSpeedRatio", ignoreCase = true) || path.contains("engineLoad", ignoreCase = true) ||
            path.contains("humidity", ignoreCase = true) -> {
                format(effectiveValue * 100.0, context.getString(R.string.nautical_unit_percent), "%.0f")
            }
            path.contains("voltage", ignoreCase = true) -> {
                format(effectiveValue, context.getString(R.string.nautical_unit_volt), "%.2f")
            }
            path.contains("current", ignoreCase = true) -> {
                format(effectiveValue, context.getString(R.string.nautical_unit_ampere), "%.1f")
            }
            path.endsWith("revolutions") -> {
                format(hertzToRpm(effectiveValue), context.getString(R.string.nautical_unit_rpm), "%.0f")
            }
            path.contains("runTime", ignoreCase = true) || path.contains("engineHours", ignoreCase = true) -> {
                format(effectiveValue / 3600.0, context.getString(R.string.nautical_unit_hours), "%.1f")
            }
            path.contains("rawDistance") -> {
                format(metersToUserDistance(effectiveValue, settings), "")
            }
            else -> format(effectiveValue, "")
        }
    }

    private fun format(value: Double, unit: String, pattern: String = "%.1f"): Pair<String, String> {
        return String.format(Locale.US, pattern, value) to unit
    }
}
