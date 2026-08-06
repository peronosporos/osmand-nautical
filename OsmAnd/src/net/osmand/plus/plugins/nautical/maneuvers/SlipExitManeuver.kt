package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.*

class SlipExitManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var startLat: Double? = null
    private var startLon: Double? = null
    private var startHeading: Double? = null

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        startLat = state.latitude
        startLon = state.longitude
        val heading = state.headingTrue?.let { Math.toDegrees(it) } ?: 0.0
        startHeading = heading
        
        // Active Helm Assistance: Maintain steady heading until clear
        NauticalPlugin.autopilot?.setTargetHeading(heading)
        NauticalPlugin.autopilot?.setAutopilotMode("auto")
        
        pushInstruction("Exiting Slip: Holding Heading")
        pushProgress(0)

        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Slip exit maneuver starting. Holding heading."))
        }
    }

    fun updateState(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val curLat = state.latitude ?: return
            val curLon = state.longitude ?: return
            
            val dist = calculateDistance(startLat!!, startLon!!, curLat, curLon)
            pushProgress((dist / 30.0 * 100).toInt())

            if (dist > 30.0) {
                pushInstruction("Slip Cleared")
                transitionToCompleted()
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return sqrt(((lat2 - lat1) * 111000).pow(2.0) + ((lon2 - lon1) * 111000 * cos(Math.toRadians(lat1))).pow(2.0))
    }
}
