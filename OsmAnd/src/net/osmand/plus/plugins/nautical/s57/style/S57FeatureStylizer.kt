package net.osmand.plus.plugins.nautical.s57.style

import net.osmand.plus.plugins.nautical.s57.S57Object

/**
 * Translates S-57 objects into visual style rules based on attributes and safety parameters.
 */
object S57FeatureStylizer {

    fun getStyleForFeature(feature: S57Object, safetyContour: Double, shallowContour: Double): S57StyleRule {
        return when (feature.acronym) {
            "DEPCNT" -> styleDepthContour(feature, safetyContour)
            "DEPARE" -> styleDepthArea(feature, safetyContour, shallowContour)
            "LNDARE" -> S57StyleRule(fillColor = NauticalColor.LAND_AREA, priority = 10)
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

    private fun styleSounding(feature: S57Object, safetyContour: Double): S57StyleRule {
        // Sounding value is usually in SG3D or attributes. 
        // For stylizer, we just define the rule.
        return S57StyleRule(priority = 150) 
    }

    private fun styleLight(feature: S57Object): S57StyleRule {
        val colour = feature.attributes["COLOUR"] // e.g., "3" for Red, "4" for Green
        val symbol = if (feature.attributes["HEIGHT"]?.toDoubleOrNull() ?: 0.0 > 10.0) {
            SymbolId.LIGHT_MAJOR
        } else {
            SymbolId.LIGHT_MINOR
        }
        return S57StyleRule(symbolId = symbol, priority = 100)
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
