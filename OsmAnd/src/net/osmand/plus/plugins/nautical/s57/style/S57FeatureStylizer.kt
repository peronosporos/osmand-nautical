package net.osmand.plus.plugins.nautical.s57.style

import net.osmand.plus.plugins.nautical.s57.S57Object
import kotlin.math.roundToInt

/**
 * Translates S-57 objects into visual style rules based on attributes and safety parameters.
 */
object S57FeatureStylizer {

    enum class DepthUnit(val factor: Double, val suffix: String) {
        METERS(1.0, "m"),
        FEET(3.28084, "ft"),
        FATHOMS(0.546807, "fm");

        companion object {
            fun fromIndex(index: Int): DepthUnit {
                return when (index) {
                    1 -> FEET
                    2 -> FATHOMS
                    else -> METERS
                }
            }
        }
    }

    fun convertDepth(depthMeters: Double, unit: DepthUnit): Double {
        return depthMeters * unit.factor
    }

    fun formatSounding(depthMeters: Double, unit: DepthUnit, tideOffsetMeters: Double = 0.0): Pair<String, String> {
        val effectiveDepth = (depthMeters + tideOffsetMeters).coerceAtLeast(0.0)
        val converted = convertDepth(effectiveDepth, unit)
        val intVal = converted.toInt()
        val intPart = intVal.toString()
        val fracDigit = ((converted - intVal) * 10.0).roundToInt().coerceIn(0, 9).toString()
        return Pair(intPart, fracDigit)
    }

    fun getStyleForFeature(feature: S57Object, safetyContour: Double, shallowContour: Double): S57StyleRule {
        return when (feature.acronym) {
            "DEPCNT" -> styleDepthContour(feature, safetyContour)
            "DEPARE" -> styleDepthArea(feature, safetyContour, shallowContour)
            "LNDARE" -> S57StyleRule(fillColor = NauticalColor.LAND_AREA, priority = 10)
            "BOYLAT" -> styleBuoyLateral(feature)
            "BCNLAT" -> styleBuoyLateral(feature)
            "BOYCAR" -> styleBuoyCardinal(feature)
            "BCNCAR" -> styleBuoyCardinal(feature)
            "BOYSAW" -> S57StyleRule(symbolId = SymbolId.SAFE_WATER, priority = 80)
            "BCNSAW" -> S57StyleRule(symbolId = SymbolId.SAFE_WATER, priority = 80)
            "BOYISD" -> S57StyleRule(symbolId = SymbolId.ISOLATED_DANGER, priority = 80)
            "LIGHTS" -> styleLight(feature)
            "OBSTRN" -> styleObstruction(feature)
            "UWTROC" -> S57StyleRule(symbolId = SymbolId.ROCK_AWASH, priority = 50)
            "WRECKS" -> S57StyleRule(symbolId = SymbolId.WRECK, priority = 60)
            "BOYSPP" -> S57StyleRule(symbolId = SymbolId.SPECIAL_PURPOSE, priority = 80)
            "BCNSPP" -> S57StyleRule(symbolId = SymbolId.SPECIAL_PURPOSE, priority = 80)
            "SOUNDG" -> styleSounding(feature, safetyContour)
            else -> S57StyleRule(strokeColor = NauticalColor.DEEP_WATER)
        }
    }

    private fun styleDepthContour(feature: S57Object, safetyContour: Double): S57StyleRule {
        val valco = feature.attributes["VALCO"]?.toDoubleOrNull() ?: 0.0
        return if (valco <= safetyContour) {
            S57StyleRule(strokeWidth = 2.5f, strokeColor = NauticalColor.SAFETY_CONTOUR, priority = 40)
        } else {
            S57StyleRule(strokeWidth = 1f, strokeColor = NauticalColor.DEEP_WATER, priority = 30)
        }
    }

    private fun styleDepthArea(feature: S57Object, safetyContour: Double, shallowContour: Double): S57StyleRule {
        val drval1 = feature.attributes["DRVAL1"]?.toDoubleOrNull() ?: 0.0
        return when {
            drval1 < shallowContour -> S57StyleRule(fillColor = NauticalColor.SHALLOW_WATER, priority = 5)
            drval1 < safetyContour -> S57StyleRule(fillColor = NauticalColor.SAFETY_ZONE, priority = 3)
            else -> S57StyleRule(fillColor = NauticalColor.DEEP_WATER, priority = 0)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun styleSounding(feature: S57Object, safetyContour: Double): S57StyleRule {
        // Sounding value is usually in SG3D or attributes. 
        // For stylizer, we just define the rule.
        return S57StyleRule(priority = 150) 
    }

    private fun styleBuoyLateral(feature: S57Object): S57StyleRule {
        val colour = feature.attributes["COLOUR"]?.split(",")?.firstOrNull()
        val acronym = feature.acronym
        val symbol = if (acronym == "BOYLAT") {
            when (colour) {
                "3" -> SymbolId.BUOY_PORT // Red
                "4" -> SymbolId.BUOY_STARBOARD // Green
                else -> SymbolId.SPECIAL_PURPOSE
            }
        } else {
            when (colour) {
                "3" -> SymbolId.BEACON_PORT
                "4" -> SymbolId.BCN_STARBOARD
                else -> SymbolId.SPECIAL_PURPOSE
            }
        }
        return S57StyleRule(symbolId = symbol, priority = 80)
    }

    private fun styleBuoyCardinal(feature: S57Object): S57StyleRule {
        val category = feature.attributes["CATCAM"] // 1: North, 2: East, 3: South, 4: West
        val acronym = feature.acronym
        val symbol = if (acronym == "BOYCAR") {
            when (category) {
                "1" -> SymbolId.BUOY_NORTH
                "2" -> SymbolId.BUOY_EAST
                "3" -> SymbolId.BUOY_SOUTH
                "4" -> SymbolId.BUOY_WEST
                else -> SymbolId.SPECIAL_PURPOSE
            }
        } else {
            when (category) {
                "1" -> SymbolId.CARDINAL_NORTH
                "2" -> SymbolId.CARDINAL_EAST
                "3" -> SymbolId.CARDINAL_SOUTH
                "4" -> SymbolId.CARDINAL_WEST
                else -> SymbolId.SPECIAL_PURPOSE
            }
        }
        return S57StyleRule(symbolId = symbol, priority = 80)
    }

    private fun styleLight(feature: S57Object): S57StyleRule {
        val strokeColor = when (feature.attributes["COLOUR"]?.split(",")?.firstOrNull()) {
            "3" -> NauticalColor.BUOY_PORT
            "4" -> NauticalColor.BUOY_STARBOARD
            "11" -> NauticalColor.DANGER // Orange
            else -> NauticalColor.DEEP_WATER // White
        }
        val symbol = if ((feature.attributes["HEIGHT"]?.toDoubleOrNull() ?: 0.0) > 10.0) {
            SymbolId.LIGHT_MAJOR
        } else {
            SymbolId.LIGHT_MINOR
        }
        return S57StyleRule(symbolId = symbol, strokeColor = strokeColor, priority = 100)
    }

    private fun styleObstruction(feature: S57Object): S57StyleRule {
        val watlev = feature.attributes["WATLEV"] // 1: permanent out of water, 2: always under water, etc.
        return if (watlev == "2") {
            S57StyleRule(symbolId = SymbolId.WRECK, priority = 60)
        } else {
            S57StyleRule(symbolId = SymbolId.ISOLATED_DANGER, priority = 70)
        }
    }
}
