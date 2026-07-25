package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import kotlin.math.*

class WeighingAnchorManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var dropLat: Double = 0.0
    private var dropLon: Double = 0.0

    fun setDropPoint(lat: Double, lon: Double) {
        dropLat = lat
        dropLon = lon
    }

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Weighing anchor. Tracking distance to drop point."))
        }
    }

    fun updateState(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val lat = state.latitude ?: return
            val lon = state.longitude ?: return
            
            val dist = calculateDistance(dropLat, dropLon, lat, lon)
            val sog = state.speedOverGround ?: 0.0
            
            if (dist < 2.0) {
                app.player?.let { player ->
                    player.playCommands(player.newCommandBuilder().attention("Over anchor."))
                }
            }
            
            // Terminate watch once SOG > 1.5 knots moving away
            if (dist > 10.0 && sog > 1.5) {
                transitionToCompleted()
            }
        }
    }

    override fun transitionToCompleted() {
        app.settings.NAUTICAL_ANCHOR_LAT.set(0.0)
        app.settings.NAUTICAL_ANCHOR_LON.set(0.0)
        app.settings.NAUTICAL_ANCHOR_RADIUS.set(0.0f)
        super.transitionToCompleted()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return sqrt(((lat2 - lat1) * 111000).pow(2.0) + ((lon2 - lon1) * 111000 * cos(Math.toRadians(lat1))).pow(2.0))
    }
}
