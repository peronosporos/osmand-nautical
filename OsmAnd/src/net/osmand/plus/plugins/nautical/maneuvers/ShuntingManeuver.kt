package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator

class ShuntingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_shunt
    override val iconRes: Int = R.drawable.ic_action_sail_boat_dark
    override val isHighRisk: Boolean = true

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false

        // Safeguard: Only allow shunting for symmetric vessels (Proas)
        val vesselType = app.settings.NAUTICAL_VESSEL_TYPE.get()
        if (vesselType != net.osmand.plus.settings.enums.VesselType.PROA) {
            return false
        }
        
        // SOG must be effectively 0 for a safe bow/stern swap
        val sog = state.speedOverGround ?: 0.0
        val sogKnots = SignalKUnitConverter.msToKnots(sog)
        return sogKnots < 0.5 // Knots tolerance
    }

    override fun transitionToExecuting() {
        // Lock Helm for Shunting
        NauticalHelmArbitrator.getInstance(app).acquireLock(
            NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Shunting"
        )
        
        // Synchronize Hardware Autopilot state
        NauticalPlugin.autopilot?.shunt(manageWorkflow = false)
        
        pushInstruction(app.getString(R.string.nautical_shunting_maneuver_active))
        pushProgress(30)
        
        speak(app.getString(R.string.nautical_shunting_starting))
        super.transitionToExecuting()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return
        
        val sog = state.speedOverGround ?: 0.0
        val sogKnots = SignalKUnitConverter.msToKnots(sog)
        
        val heading = state.headingTrue
        val cog = state.courseOverGroundTrue
        
        // Progress based on acceleration in new direction
        if (sogKnots > 0.1) {
            pushProgress(60)
        }

        if (heading != null && cog != null && sogKnots > 0.5) {
            val diff = kotlin.math.abs(heading - cog)
            val normalizedDiff = if (diff > kotlin.math.PI) 2 * kotlin.math.PI - diff else diff
            
            // If heading and COG are within 30 degrees, the vessel is making way in the new direction
            if (normalizedDiff < Math.toRadians(30.0)) {
                pushInstruction(app.getString(R.string.nautical_shunt_completed))
                pushProgress(100)
                transitionToCompleted()
            }
        }
    }

    override fun transitionToCompleted() {
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        super.transitionToAborted(reason)
    }
}
