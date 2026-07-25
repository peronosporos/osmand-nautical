package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

object DriftVectorCalculator {
    data class Vector(val magnitude: Double, val angleDegrees: Double)

    fun calculateDriftVector(state: MarineState): Vector? {
        val cog = state.courseOverGroundTrue ?: return null
        val hdg = state.headingTrue ?: return null
        val sog = state.speedOverGround ?: return null
        
        // Simplified drift: The angular difference between heading and COG, 
        // scaled by speed (SOG). Angles are in Radians.
        val angleDiff = cog - hdg
        val driftMagnitude = sog * sin(abs(angleDiff))
        
        return Vector(driftMagnitude, Math.toDegrees(angleDiff))
    }
}
