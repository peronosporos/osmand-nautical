package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

class HeavingToManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    fun updateState(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val sog = state.speedOverGround ?: 0.0
            val twa = state.trueWindAngle ?: 0.0
            val twaDeg = abs(Math.toDegrees(twa))
            
            // Check stabilization conditions: SOG < 1 knot, drift angle 45-60 deg off wind
            if (sog < 1.0 && twaDeg in 45.0..60.0) {
                transitionToCompleted()
            }
        }
    }
}
