package net.osmand.plus.plugins.nautical

import net.osmand.data.LatLon
import net.osmand.util.MapUtils

/**
 * Utility for anchor watch calculations.
 */
object AnchorCalculator {

    /**
     * Computes the recommended rode length.
     * rodeLength = (waterDepth + tideRise + freeboardHeight) * scopeRatio
     */
    fun calculateRodeLength(
        waterDepth: Double,
        tideRise: Double,
        freeboardHeight: Double,
        scopeRatio: Double,
    ): Double {
        return (waterDepth + tideRise + freeboardHeight) * scopeRatio
    }

    /**
     * Calculates the total watch radius.
     * R_total = Rode_Length + Bow_Offset + Safety_Margin
     */
    fun calculateTotalRadius(
        rodeLength: Double,
        bowOffset: Double,
        safetyMargin: Double
    ): Double {
        return rodeLength + bowOffset + safetyMargin
    }

    /**
     * Adjusts the anchor drop position forward from the vessel's current position
     * by the bowOffset along the current heading vector.
     */
    fun calculateAnchorDrop(
        currentLat: Double,
        currentLon: Double,
        headingTrueDeg: Double,
        bowOffsetMeters: Double
    ): LatLon {
        if (bowOffsetMeters <= 0) return LatLon(currentLat, currentLon)
        
        // MapUtils.greatCircleDestinationPoint takes bearing in radians
        return MapUtils.greatCircleDestinationPoint(currentLat, currentLon, Math.toRadians(headingTrueDeg), bowOffsetMeters)
    }
}
