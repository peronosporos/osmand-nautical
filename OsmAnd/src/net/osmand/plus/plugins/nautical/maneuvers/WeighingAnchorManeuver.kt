package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.shared.util.KMapUtils

class WeighingAnchorManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_weighing_anchor
    override val iconRes: Int = R.drawable.ic_action_anchor
    override val isHighRisk: Boolean = false

    private var dropLat: Double = 0.0
    private var dropLon: Double = 0.0
    private var initialDistance: Double = 0.0
    private var overAnchorAnnounced = false
    private var anchorAweighAnnounced = false
    private var initialApMode: String? = null
    private var lastManualOverrideTime: Long = 0
    private var lastRodeDeployed: Double? = null
    private var lastRodeChangeTime: Long = 0
    private var windlassFailureAnnounced = false

    fun setDropPoint(lat: Double, lon: Double) {
        dropLat = lat
        dropLon = lon
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false

        if (state.latitude == null || state.longitude == null || state.stalePaths.contains("navigation.position")) {
            val msg = app.getString(R.string.nautical_error_no_gps)
            speak(msg)
            transitionToAborted(msg)
            return false
        }

        if (dropLat == 0.0 || dropLon == 0.0) {
            val msg = "No anchor drop point set"
            transitionToAborted(msg)
            return false
        }

        return true
    }

    override fun transitionToExecuting() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        initialDistance = KMapUtils.getDistance(dropLat, dropLon, lat, lon)
        overAnchorAnnounced = false
        anchorAweighAnnounced = false
        initialApMode = state.autopilotState
        lastManualOverrideTime = 0
        windlassFailureAnnounced = false
        lastRodeDeployed = null
        lastRodeChangeTime = 0

        NauticalHelmArbitrator.getInstance(app).acquireLock(
            NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Weighing Anchor"
        )

        super.transitionToExecuting()
        pushInstruction(app.getString(R.string.nautical_maneuver_weighing_anchor))
        pushProgress(0)
        speak(app.getString(R.string.nautical_weighing_anchor_executing))

        // Banner prompt for Helm Assistance
        NauticalPlugin.hudManager?.get()?.showBanner(
            app.getString(R.string.nautical_confirm_helm_to_anchor),
            10000,
            "AUTO-HELM",
            false
        ) {
            NauticalPlugin.autopilot?.setAutopilotMode("auto")
        }

        // Banner prompt for Windlass instead of auto-trigger
        val plugin = NauticalPlugin.getInstance()
        val caps = plugin?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true && state.isEngineRunning) {
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_weighing_anchor_windlass_up_prompt),
                0, // Persistent
                "RAISE",
                false
            ) {
                NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.up", true)
            }
        }
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val lat = state.latitude ?: return
            val lon = state.longitude ?: return
            
            val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
            val dist = KMapUtils.getDistance(dropLat, dropLon, lat, lon)
            val sog = state.speedOverGround ?: 0.0

            // Windlass Feedback Loop
            val windlassUp = state.switches["electrical.switches.windlass.up"] == true
            if (windlassUp && caps?.hasChainCounter == true && state.rodeDeployed != null) {
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

            // 1. Helm Assistance: Keep bow pointed at anchor
            // MANUAL OVERRIDE DETECTION: If state is standby but we want auto, user disengaged
            if (state.autopilotState == "standby" && (System.currentTimeMillis() - lastManualOverrideTime) > 30000) {
                 // Do nothing, let user steer
            } else if (state.autopilotState != "standby") {
                val bearingToAnchor = Math.toDegrees(KMapUtils.getBearing(lat, lon, dropLat, dropLon))
                NauticalPlugin.autopilot?.setTargetHeading(bearingToAnchor)
            }

            // 2. Instruction Logic
            if (dist < 2.0) {
                if (!overAnchorAnnounced) {
                    speak(app.getString(R.string.nautical_weighing_anchor_over_anchor))
                    overAnchorAnnounced = true
                }
                pushInstruction(app.getString(R.string.nautical_weighing_anchor_over_anchor))
            } else if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                if (state.rodeDeployed <= 1.5) {
                    if (caps.hasWindlassControl) {
                        NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.up", false)
                    }
                    if (!anchorAweighAnnounced) {
                        speak(app.getString(R.string.nautical_weighing_anchor_aweigh))
                        anchorAweighAnnounced = true
                    }
                    pushInstruction(app.getString(R.string.nautical_weighing_anchor_aweigh))
                } else {
                    val (valRode, unitRode) = SignalKUnitConverter.formatValue(app, app.settings, state.rodeDeployed, "distance")
                    pushInstruction(app.getString(R.string.nautical_weighing_anchor_rode_in_fmt, valRode, unitRode))
                }
            } else {
                val (valDist, unitDist) = SignalKUnitConverter.formatValue(app, app.settings, dist, "distance")
                pushInstruction(app.getString(R.string.nautical_weighing_anchor_dist_fmt, valDist, unitDist))
            }

            // 3. Dynamic Progress
            val progress = if (initialDistance > 2.0) {
                ((1.0 - (dist / initialDistance).coerceIn(0.0, 1.0)) * 100).toInt()
            } else 0
            pushProgress(progress)
            
            // 4. Completion: Moving away at > 1.5 knots and distance > 10m
            val isRodeIn = if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                state.rodeDeployed <= 1.5
            } else true

            if (dist > 10.0 && sog > 0.77 && isRodeIn) { 
                transitionToCompleted()
            }
        }
    }

    override fun transitionToCompleted() {
        restoreAutopilot()
        app.settings.NAUTICAL_ANCHOR_LAT.set(0.0)
        app.settings.NAUTICAL_ANCHOR_LON.set(0.0)
        app.settings.NAUTICAL_ANCHOR_RADIUS.set(0.0f)
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        restoreAutopilot()
        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        if (caps?.hasWindlassControl == true) {
            NauticalPlugin.engine?.setSwitch("electrical.switches.windlass.up", false)
        }
        super.transitionToAborted(reason)
    }

    private fun restoreAutopilot() {
        initialApMode?.let { mode ->
            if (mode != "standby") {
                NauticalPlugin.autopilot?.setAutopilotMode(mode)
            }
        }
    }
}
