package net.osmand.plus.plugins.nautical.utils

import kotlin.math.pow

/**
 * Dynamic Leeway Model based on heel angle and boat speed.
 * Formula: Leeway_angle = K * (Heel_Angle / STW^2)
 */
object LeewayCalculator {

    private const val MIN_STW_KNOTS = 0.5
    private const val MS_TO_KNOTS = 1.94384

    /**
     * Calculates leeway in Radians.
     * @param heelRadians Current vessel heel (roll).
     * @param stwMs Speed through water in m/s.
     * @param coefficient K value (Nautical Leeway Coefficient) in degrees.
     * @return Leeway angle in Radians.
     */
    fun calculateLeewayRadians(heelRadians: Double, stwMs: Double, coefficient: Float): Double {
        if (stwMs < 0.0) return 0.0
        
        val stwKnots = stwMs * MS_TO_KNOTS
        val heelDegrees = Math.toDegrees(heelRadians) // Keep sign
        
        // Decay smoothly to 0 if STW is very low to avoid division by zero
        if (stwKnots < MIN_STW_KNOTS) {
            val factor = stwKnots / MIN_STW_KNOTS
            val peakLeeway = coefficient.toDouble() * (heelDegrees / MIN_STW_KNOTS.pow(2.0))
            return Math.toRadians(peakLeeway * factor)
        }
        
        val leewayDegrees = coefficient.toDouble() * (heelDegrees / stwKnots.pow(2.0))
        return Math.toRadians(leewayDegrees)
    }
}
