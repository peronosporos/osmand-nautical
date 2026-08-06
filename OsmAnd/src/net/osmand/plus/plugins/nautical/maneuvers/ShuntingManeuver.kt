package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState

class ShuntingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        // SOG must be effectively 0
        val sog = state.speedOverGround ?: 0.0
        return sog < 0.5 // Knots tolerance
    }

    override fun transitionToExecuting() {
        // Lock Helm for Shunting
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Shunting"
        )
        
        // Synchronize Hardware Autopilot state
        NauticalPlugin.autopilot?.shunt()
        
        pushInstruction("Shunting Maneuver Active")
        pushProgress(50)
        
        // Stop -> Reverse -> Shift sail
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Shunting maneuver starting."))
        }
        super.transitionToExecuting()
    }

    override fun transitionToCompleted() {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
        super.transitionToAborted(reason)
    }
}
