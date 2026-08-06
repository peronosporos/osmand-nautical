package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.shared.util.KMapUtils
import java.util.Locale

class WeighingAnchorManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var dropLat: Double = 0.0
    private var dropLon: Double = 0.0

    fun setDropPoint(lat: Double, lon: Double) {
        dropLat = lat
        dropLon = lon
    }

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        pushInstruction("Weighing Anchor")
        pushProgress(0)
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Weighing anchor. Tracking distance to drop point."))
        }

        // Task 11: Auto-trigger Windlass UP
        val plugin = NauticalPlugin.getInstance()
        val caps = plugin?.capabilityManager?.capabilities?.value
        val state = NauticalPlugin.engine?.getCurrentState()
        if (caps?.hasWindlassControl == true && state?.isEngineRunning == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.up", true)
        }
    }

    override fun onStateUpdate(state: MarineState) {
        updateState(state)
    }

    fun updateState(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val lat = state.latitude ?: return
            val lon = state.longitude ?: return
            
            val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
            if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                pushInstruction(String.format(Locale.US, "Rode In: %.1fm", state.rodeDeployed))
                if (state.rodeDeployed <= 1.0) {
                    if (caps.hasWindlassControl) {
                        NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.up", false)
                    }
                    pushInstruction("Anchor Aweigh")
                }
            }

            val dist = KMapUtils.getDistance(dropLat, dropLon, lat, lon)
            val sog = state.speedOverGround ?: 0.0
            
            // Active Helm Assistance: Keep bow pointed at anchor
            val bearingToAnchor = Math.toDegrees(KMapUtils.getBearing(lat, lon, dropLat, dropLon))
            NauticalPlugin.autopilot?.setTargetHeading(bearingToAnchor)
            NauticalPlugin.autopilot?.setAutopilotMode("auto")

            pushInstruction(String.format(Locale.US, "Dist to Anchor: %.1fm", dist))
            pushProgress(((1.0 - (dist / 15.0).coerceIn(0.0, 1.0)) * 100).toInt())

            if (dist < 2.0) {
                pushInstruction("Over Anchor")
                app.player?.let { player ->
                    player.playCommands(player.newCommandBuilder().attention("Over anchor."))
                }
            }
            
            // Terminate watch once SOG > 1.5 knots moving away
            if (dist > 10.0 && sog > 0.77) { // 0.77 m/s ≈ 1.5 knots
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
}
