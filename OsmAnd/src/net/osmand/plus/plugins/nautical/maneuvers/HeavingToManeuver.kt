package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

class HeavingToManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_heaving_to
    override val iconRes: Int = R.drawable.ic_action_sail_boat_dark
    override val isHighRisk: Boolean = false

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val sog = state.speedOverGround ?: 0.0
            val twa = state.trueWindAngle ?: 0.0
            val twaDeg = abs(Math.toDegrees(twa))
            
            // Check stabilization conditions: SOG < 0.5 knots, drift angle 40-70 deg off wind
            if (sog < 0.5 && twaDeg in 40.0..70.0) {
                transitionToCompleted()
            }
        }
    }
}
