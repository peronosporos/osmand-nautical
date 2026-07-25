package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.cos

object WindManeuverHelper {

    fun calculateVMC(state: MarineState): Double? {
        val sog = state.speedOverGround ?: return null
        val awa = state.windDirectionApparent ?: return null
        
        // VMC = SOG * cos(AWA). AWA must be in Radians.
        return sog * cos(awa)
    }

    fun isCrossingHeadToWind(oldState: MarineState, newState: MarineState): Boolean {
        val oldAwa = oldState.windDirectionApparent ?: return false
        val newAwa = newState.windDirectionApparent ?: return false
        
        // Check for sign change in apparent wind angle
        return (oldAwa < 0 && newAwa > 0) || (oldAwa > 0 && newAwa < 0)
    }
}
