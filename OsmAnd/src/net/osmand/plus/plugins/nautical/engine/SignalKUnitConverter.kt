package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.R
import net.osmand.shared.units.SpeedUnits
import java.util.Locale

/**
 * Centralized utility for converting Signal K SI units to OsmAnd user preferences.
 */
object SignalKUnitConverter {

    const val KELVIN_TO_CELSIUS = -273.15
    const val PASCAL_TO_HPA = 0.01
    const val PASCAL_TO_BAR = 1.0 / 100000.0
    const val METERS_TO_NM = 1.0 / 1852.0

    fun kelvinToCelsius(k: Double): Double = k + KELVIN_TO_CELSIUS
    fun pascalToHpa(pa: Double): Double = pa * PASCAL_TO_HPA
    fun pascalToBar(pa: Double): Double = pa * PASCAL_TO_BAR
    fun metersToNm(m: Double): Double = m * METERS_TO_NM
    fun msToKnots(ms: Double): Double = ms * SpeedUnits.KNOTS.conversionCoefficient
    fun radToDeg(rad: Double): Double = Math.toDegrees(rad)

    fun formatValue(
        context: Context,
        @Suppress("UNUSED_PARAMETER") settings: OsmandSettings,
        value: Double?,
        path: String,
        meta: Map<String, Any>? = null
    ): Pair<String, String> {
        if (value == null) return context.getString(R.string.n_a) to ""

        // If meta provides units, we could theoretically handle them. 
        // Signal K spec says default units for keys are SI.
        @Suppress("UNUSED_VARIABLE") val metaUnits = meta?.get("units") as? String
        
        return when {
            path.contains("speed", ignoreCase = true) || path.endsWith("STW") || path.endsWith("SOG") -> {
                val coeff = SpeedUnits.KNOTS.conversionCoefficient
                format(value * coeff, context.getString(R.string.nautical_unit_knots))
            }
            path.contains("temperature", ignoreCase = true) -> {
                // Signal K is Kelvin
                format(value + KELVIN_TO_CELSIUS, context.getString(R.string.nautical_unit_celsius), "%.1f")
            }
            path.contains("pressure", ignoreCase = true) -> {
                if (path.contains("oil", ignoreCase = true)) {
                    format(value * PASCAL_TO_BAR, context.getString(R.string.nautical_unit_bar), "%.1f")
                } else {
                    format(value * PASCAL_TO_HPA, context.getString(R.string.nautical_unit_hpa), "%.0f")
                }
            }
            path.contains("depth", ignoreCase = true) || path.endsWith("length") || path.endsWith("beam") -> {
                format(value, context.getString(R.string.nautical_unit_meters))
            }
            path.contains("angle", ignoreCase = true) || path.contains("heading", ignoreCase = true) || 
            path.contains("course", ignoreCase = true) || path.contains("direction", ignoreCase = true) ||
            path.contains("roll") || path.contains("pitch") || path.contains("yaw") -> {
                format(Math.toDegrees(value), context.getString(R.string.nautical_unit_deg), "%.0f")
            }
            path.contains("currentLevel", ignoreCase = true) || path.contains("stateOfCharge", ignoreCase = true) ||
            path.contains("polarSpeedRatio", ignoreCase = true) || path.contains("engineLoad", ignoreCase = true) -> {
                format(value * 100.0, context.getString(R.string.nautical_unit_percent), "%.0f")
            }
            path.contains("voltage", ignoreCase = true) -> {
                format(value, context.getString(R.string.nautical_unit_volt), "%.2f")
            }
            path.contains("current", ignoreCase = true) -> {
                format(value, context.getString(R.string.nautical_unit_ampere), "%.1f")
            }
            path.endsWith("revolutions") -> {
                // Signal K revolutions is Hz (rev/sec), we want RPM
                format(value * 60.0, context.getString(R.string.nautical_unit_rpm), "%.0f")
            }
            path.contains("log", ignoreCase = true) || path.contains("distance", ignoreCase = true) -> {
                format(value * METERS_TO_NM, context.getString(R.string.nautical_unit_nm), "%.1f")
            }
            else -> format(value, "")
        }
    }

    private fun format(value: Double, unit: String, pattern: String = "%.1f"): Pair<String, String> {
        return String.format(Locale.US, pattern, value) to unit
    }
}
