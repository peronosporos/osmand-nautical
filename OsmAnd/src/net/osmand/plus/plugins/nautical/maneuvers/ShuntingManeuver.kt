package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState

class ShuntingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        // SOG must be effectively 0
        val sog = state.speedOverGround ?: 0.0
        return sog < 0.5 // Knots tolerance
    }

    override fun transitionToExecuting() {
        // Stop -> Reverse -> Shift sail
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Shunting maneuver starting."))
        }
        super.transitionToExecuting()
    }
}
