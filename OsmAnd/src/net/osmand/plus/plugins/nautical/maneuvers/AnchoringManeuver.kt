package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import java.util.Locale

class AnchoringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_anchoring
    override val iconRes: Int = R.drawable.ic_action_anchor
    override val isHighRisk: Boolean = false

    private var dropCoordinate: Pair<Double, Double>? = null
    private var rodeLength: Double = 0.0
    private var lastRodeDeployed: Double? = null
    private var lastRodeChangeTime: Long = 0
    private var windlassFailureAnnounced = false

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        // Run standard checks first
        if (!super.checkSafetyPreconditions(state)) return false

        // Ensure we have active GPS fix
        if (state.latitude == null || state.longitude == null || state.stalePaths.contains("navigation.position")) {
            val msg = app.getString(R.string.nautical_error_no_gps)
            speak(msg)
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
        executionStartTime = System.currentTimeMillis()
        windlassFailureAnnounced = false
        
        val depthBelow = state.depthBelowTransducer ?: app.settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
        val currentTideHeight = state.tide?.heightNow ?: 0.0
        var tideRise = state.tide?.heightNow ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()

        // Automated Tide-Awareness: Check for upcoming high tide
        state.tide?.let { tide ->
            val nextHeight = tide.nextExtremeHeight
            if (tide.nextExtremeType == "High" && nextHeight != null && nextHeight > currentTideHeight) {
                val predictedRise = nextHeight - currentTideHeight
                if (predictedRise > 0.1) {
                    tideRise = nextHeight
                    val (valTide, unitTide) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, nextHeight, "depth")
                    speak(app.getString(R.string.nautical_warn_tide_rising, valTide, unitTide))
                }
            }
        }

        val freeboard = app.settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
        
        val windSpeed = state.windSpeedTrue ?: 0.0
        val scopeRatio = net.osmand.plus.plugins.nautical.AnchorCalculator.calculateRecommendedScope(
            windSpeed, app.settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()
        )
        
        rodeLength = net.osmand.plus.plugins.nautical.AnchorCalculator.calculateRodeLength(
            depthBelow, tideRise, freeboard, scopeRatio
        )
        
        // GPS Bow Offset implementation
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val heading = state.headingTrue ?: 0.0
        val bowOffset = app.settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
        val dropPoint = net.osmand.plus.plugins.nautical.AnchorCalculator.calculateAnchorDrop(
            lat, lon, Math.toDegrees(heading), bowOffset
        )
        dropCoordinate = Pair(dropPoint.latitude, dropPoint.longitude)
        
        val (depthVal, depthUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, depthBelow, "depth")
        val (rodeVal, rodeUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, rodeLength, "distance")
        
        val msg = app.getString(R.string.nautical_anchoring_at_depth_localized, depthVal, depthUnit) + " " +
                app.getString(R.string.nautical_anchoring_scope_msg, scopeRatio, rodeVal, rodeUnit)
        speak(msg)
        
        pushInstruction(app.getString(R.string.nautical_anchoring_dropping))
        pushProgress(20)

        // Active Helm Assistance: Make optional via banner
        val twd = state.windDirectionTrue
        if (twd != null) {
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_confirm_helm_into_wind),
                10000L,
                "AUTO-HELM",
                false,
                onConfirm = {
                    NauticalPlugin.autopilot?.setTargetHeading(Math.toDegrees(twd))
                    NauticalPlugin.autopilot?.setAutopilotMode("auto")
                }
            )
        }

        // Task 11: Prompt for Windlass instead of auto-trigger
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true && state.isEngineRunning) {
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_anchoring_windlass_prompt),
                0L, // Persistent until used or dismissed
                "LOWER",
                false,
                onConfirm = {
                    NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", true)
                }
            )
        }

        super.transitionToExecuting()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val sog = state.speedOverGround ?: 0.0
            val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
            
            // Windlass Feedback Loop
            val windlassDown = state.switches["electrical.switches.windlass.down"] == true
            if (windlassDown && caps?.hasChainCounter == true && state.rodeDeployed != null) {
                if (lastRodeDeployed != null && state.rodeDeployed != lastRodeDeployed) {
                    lastRodeChangeTime = System.currentTimeMillis()
                    windlassFailureAnnounced = false
                } else if (lastRodeDeployed == null) {
                    lastRodeDeployed = state.rodeDeployed
                    lastRodeChangeTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastRodeChangeTime > 5000 && !windlassFailureAnnounced) {
                    speak(app.getString(R.string.nautical_error_windlass_failure))
                    windlassFailureAnnounced = true
                }
                lastRodeDeployed = state.rodeDeployed
            }

            // Auto-completion based on chain counter
            if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                if (state.rodeDeployed >= rodeLength) {
                    if (caps.hasWindlassControl) {
                        NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
                    }
                    
                    // Once rode is out, wait for vessel to settle (SOG < 0.1 kn)
                    if (sog < 0.05) {
                        pushInstruction(app.getString(R.string.nautical_anchoring_set))
                        pushProgress(100)
                        transitionToCompleted()
                    } else {
                        // Task 11: Descriptive settling state
                        val speedKnots = sog * net.osmand.shared.units.SpeedConstants.KNOTS
                        pushInstruction(app.getString(R.string.nautical_anchoring_settling_desc, String.format(Locale.US, "%.1f", speedKnots)))
                        // Progress for settling (mapping 0.5 kn to 0.05 kn as 70-100%)
                        val settleProgress = 70 + ((1.0 - (sog.coerceIn(0.05, 0.25) / 0.25)) * 30).toInt()
                        pushProgress(settleProgress)
                    }
                } else {
                    val progress = ((state.rodeDeployed / rodeLength) * 70).toInt()
                    pushProgress(progress)
                    pushInstruction(app.getString(R.string.nautical_anchoring_paying_out, state.rodeDeployed, rodeLength))
                }
            } else {
                // Fallback: If no chain counter, wait for settling after manual payout or time
                // MANUAL MODE: Require at least 30 seconds since execution start to prevent immediate completion
                val executionTime = System.currentTimeMillis() - executionStartTime
                if (sog < 0.05 && executionTime > 30000) {
                    pushInstruction(app.getString(R.string.nautical_anchoring_set))
                    pushProgress(100)
                    transitionToCompleted()
                } else {
                    val speedKnots = sog * net.osmand.shared.units.SpeedConstants.KNOTS
                    val instruction = if (executionTime < 30000) {
                        app.getString(R.string.nautical_anchoring_dropping) + " (" + (30 - executionTime / 1000) + "s)"
                    } else {
                        app.getString(R.string.nautical_anchoring_settling_manual, String.format(Locale.US, "%.1f", speedKnots))
                    }
                    pushInstruction(instruction)
                }
            }
        }
    }

    override fun transitionToCompleted() {
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
        }
        dropCoordinate?.let { (lat, lon) ->
            app.settings.NAUTICAL_ANCHOR_LAT.set(lat)
            app.settings.NAUTICAL_ANCHOR_LON.set(lon)
            
            val bowOffset = app.settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
            val radius = net.osmand.plus.plugins.nautical.AnchorCalculator.calculateTotalRadius(
                rodeLength, bowOffset, 5.0 // 5m safety margin
            )
            app.settings.NAUTICAL_ANCHOR_RADIUS.set(radius.toFloat())
        }
        
        speak(app.getString(R.string.nautical_anchor_set))
        
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.down", false)
        }
        super.transitionToAborted(reason)
    }
}
