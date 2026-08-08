package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

class MooringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

    override fun transitionToArmed() {
        super.transitionToArmed()
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention("Autopilot active. Disengage before approach."))
            }
        }
    }

    override fun transitionToExecuting() {
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention("Autopilot disengaged for approach."))
            }
        }

        super.transitionToExecuting()
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Mooring maneuver executing. Monitor distance and speed."))
        }
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val dist = calculateDistanceToTarget(state) ?: 100.0
            val sog = state.speedOverGround ?: 0.0
            
            if (dist < 10.0 && sog > 2.5) {
                transitionToAborted("Speed too high for mooring")
                return
            }

            // Auto completion: within 3m and stopped
            if (dist < 3.0 && sog < 0.1) {
                pushInstruction("Mooring Completed")
                pushProgress(100)
                transitionToCompleted()
            } else if (dist < 30.0) {
                pushInstruction("Approach: ${dist.toInt()}m")
                val progress = ((1.0 - (dist / 30.0).coerceIn(0.0, 1.0)) * 100).toInt()
                pushProgress(progress)
            }
        }
    }

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        return sqrt(((lat - targetLat) * 111000).pow(2.0) + ((lon - targetLon) * 111000 * cos(Math.toRadians(lat))).pow(2.0))
    }
}
