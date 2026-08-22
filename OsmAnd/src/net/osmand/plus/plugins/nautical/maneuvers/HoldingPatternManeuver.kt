package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.abs

class HoldingPatternManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_holding_pattern
    override val iconRes: Int = R.drawable.ic_action_direction_compass
    override val isHighRisk: Boolean = false

    private var initialHeading: Double = Double.NaN
    private var totalTurnDegrees: Double = 0.0
    private var lastHeading: Double = Double.NaN

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        initialHeading = Double.NaN
        totalTurnDegrees = 0.0
        lastHeading = Double.NaN
        speak("Holding pattern 360 turn initiated.")
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val hdg = (state.headingTrue ?: state.courseOverGroundTrue)?.let { Math.toDegrees(it) } ?: return
            if (initialHeading.isNaN()) {
                initialHeading = hdg
                lastHeading = hdg
                return
            }

            var diff = hdg - lastHeading
            while (diff < -180.0) diff += 360.0
            while (diff > 180.0) diff -= 360.0

            totalTurnDegrees += abs(diff)
            lastHeading = hdg

            if (totalTurnDegrees >= 350.0) {
                transitionToCompleted()
                speak("Holding pattern completed.")
            }
        }
    }
}
