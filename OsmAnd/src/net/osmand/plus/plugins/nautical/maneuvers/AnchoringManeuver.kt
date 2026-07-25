package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R

class AnchoringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var dropCoordinate: Pair<Double, Double>? = null
    private var rodeLength: Double = 0.0

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        // Run standard checks first
        if (!super.checkSafetyPreconditions(state)) return false

        // Anchoring specific: Ensure we have depth data
        val depthBelow = state.depthBelowTransducer
        if (depthBelow == null) {
             val msg = app.getString(R.string.nautical_depth_unavailable)
             app.player?.let { player -> player.playCommands(player.newCommandBuilder().attention(msg)) }
             transitionToAborted(msg)
             return false
        }
        return true
    }

    override fun transitionToExecuting() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        
        val depthBelow = state.depthBelowTransducer ?: 0.0
        val offset = state.depthSurfaceToTransducer ?: 1.0
        val totalDepth = depthBelow + offset
        
        val windSpeed = state.windSpeedTrue ?: 0.0
        val scopeRatio = if (windSpeed > 20.0) 7.0 else 5.0
        rodeLength = totalDepth * scopeRatio
        
        dropCoordinate = state.latitude?.let { lat -> state.longitude?.let { lon -> Pair(lat, lon) } }
        
        app.player?.let { player ->
            val msg = app.getString(R.string.nautical_anchoring_at_depth, totalDepth.toInt()) +
                    " Scope set to ${scopeRatio.toInt()}. Paying out ${rodeLength.toInt()} meters of rode."
            player.playCommands(player.newCommandBuilder().attention(msg))
        }

        super.transitionToExecuting()
    }

    override fun transitionToCompleted() {
        dropCoordinate?.let { (lat, lon) ->
            app.settings.NAUTICAL_ANCHOR_LAT.set(lat)
            app.settings.NAUTICAL_ANCHOR_LON.set(lon)
            app.settings.NAUTICAL_ANCHOR_RADIUS.set((rodeLength * 1.5).toFloat()) // Simple safety margin
        }
        
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention(app.getString(R.string.nautical_anchor_set)))
        }
        
        super.transitionToCompleted()
    }
}
