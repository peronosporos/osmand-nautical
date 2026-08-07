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

        // Ensure we have active GPS fix
        if (state.latitude == null || state.longitude == null || state.stalePaths.contains("navigation.position")) {
            val msg = app.getString(R.string.nautical_error_no_gps)
            app.player?.let { player -> player.playCommands(player.newCommandBuilder().attention(msg)) }
            transitionToAborted(msg)
            return false
        }

        return true
    }

    override fun transitionToExecuting() {
        // Lock Helm for Anchoring
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Anchoring"
        )

        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        
        val depthBelow = state.depthBelowTransducer ?: app.settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
        val tideRise = state.tide?.heightNow ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
        val freeboard = app.settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
        
        val windSpeed = state.windSpeedTrue ?: 0.0
        val scopeRatio = if (windSpeed > 10.0) 7.0 else 5.0 // ~20 knots threshold (10 m/s)
        
        rodeLength = net.osmand.plus.plugins.nautical.AnchorCalculator.calculateRodeLength(
            depthBelow, tideRise, freeboard, scopeRatio
        )
        
        dropCoordinate = state.latitude?.let { lat -> state.longitude?.let { lon -> Pair(lat, lon) } }
        
        app.player?.let { player ->
            val (depthVal, depthUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, depthBelow, "depth")
            val (rodeVal, rodeUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, rodeLength, "distance")
            
            val msg = app.getString(R.string.nautical_anchoring_at_depth_localized, depthVal, depthUnit) +
                    " Scope set to ${String.format(java.util.Locale.US, "%.1f", scopeRatio)}. Paying out $rodeVal $rodeUnit of rode."
            player.playCommands(player.newCommandBuilder().attention(msg))
        }
        
        pushInstruction("Dropping Anchor")
        pushProgress(20)

        // Active Helm Assistance: Make optional via banner
        val twd = state.windDirectionTrue
        if (twd != null) {
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_confirm_helm_into_wind),
                10000,
                "AUTO-HELM",
                false
            ) {
                NauticalPlugin.autopilot?.setTargetHeading(Math.toDegrees(twd))
                NauticalPlugin.autopilot?.setAutopilotMode("auto")
            }
        }

        // Task 11: Prompt for Windlass instead of auto-trigger
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true && state.isEngineRunning) {
            NauticalPlugin.hudManager?.get()?.showBanner(
                "Windlass Control Available. Lower anchor now?",
                15000,
                "LOWER",
                false
            ) {
                NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", true)
            }
        }

        super.transitionToExecuting()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
            if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                if (state.rodeDeployed >= rodeLength) {
                    pushInstruction("Target Rode Reached")
                    if (caps.hasWindlassControl) {
                        NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
                    }
                    transitionToCompleted()
                } else {
                    val progress = ((state.rodeDeployed / rodeLength) * 100).toInt()
                    pushProgress(progress)
                    pushInstruction(String.format(java.util.Locale.US, "Paying out: %.1fm / %.1fm", state.rodeDeployed, rodeLength))
                }
            }
        }
    }

    override fun transitionToCompleted() {
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
        }
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
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

    override fun transitionToAborted(reason: String?) {
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
        }
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
        super.transitionToAborted(reason)
    }
}
