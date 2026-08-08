package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

class DockingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        return true
    }

    override fun transitionToArmed() {
        super.transitionToArmed()
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_docking_ap_active)))
            }
        }
    }

    override fun transitionToExecuting() {
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_docking_ap_disengaged)))
            }
        }

        super.transitionToExecuting()
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_docking_executing)))
        }
    }

    // Logic for monitoring SOG at distance < 10m
    fun updateState(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val dist = calculateDistanceToTarget(state) ?: 100.0
            val sog = state.speedOverGround ?: 0.0
            
            if (dist < 10.0 && sog > 2.5) {
                transitionToAborted(app.getString(R.string.nautical_docking_speed_too_high))
                return
            }

            // Automatic completion when close and stationary
            if (dist < 5.0 && sog < 0.2) {
                pushInstruction(app.getString(R.string.nautical_docking_successful))
                transitionToCompleted()
            } else {
                val distStr = if (dist < 1000) "${dist.toInt()}m" else String.format(java.util.Locale.US, "%.1fNM", dist/1852.0)
                pushInstruction(app.getString(R.string.nautical_docking_approaching, distStr, String.format(java.util.Locale.US, "%.1f", sog)))
                pushProgress(((1.0 - (dist / 500.0).coerceIn(0.0, 1.0)) * 100).toInt())
            }
        }
    }

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        // Simple distance approximation in meters
        return sqrt(((lat - targetLat) * 111000).pow(2.0) + ((lon - targetLon) * 111000 * cos(Math.toRadians(lat))).pow(2.0))
    }
}
