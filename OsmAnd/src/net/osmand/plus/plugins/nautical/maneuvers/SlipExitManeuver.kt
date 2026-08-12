package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator
import net.osmand.shared.util.KMapUtils

class SlipExitManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_slip_exit
    override val iconRes: Int = R.drawable.ic_action_building
    override val isHighRisk: Boolean = false

    private var startLat: Double? = null
    private var startLon: Double? = null
    private var startHeading: Double? = null

    override val maneuverTimeoutMs: Long
        get() = app.settings.NAUTICAL_SLIP_EXIT_TIMEOUT_MS.get()

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false

        if (state.latitude == null || state.longitude == null || state.stalePaths.contains("navigation.position")) {
            val msg = app.getString(R.string.nautical_error_no_gps)
            speak(msg)
            return false
        }

        if (state.headingTrue == null && state.headingMagnetic == null) {
            val msg = app.getString(R.string.nautical_hdg_no_data)
            speak(msg)
            return false
        }

        return true
    }

    override fun transitionToExecuting() {
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null || state.latitude == null || state.longitude == null) {
            transitionToAborted("Missing initial state data")
            return
        }

        // Lock Helm for Slip Exit
        NauticalHelmArbitrator.getInstance(app).acquireLock(
            NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Slip Exit"
        )

        super.transitionToExecuting()

        startLat = state.latitude
        startLon = state.longitude
        
        // Fallback heading logic: True -> Magnetic
        val heading = state.headingTrue?.let { Math.toDegrees(it) } 
            ?: state.headingMagnetic?.let { Math.toDegrees(it) } 
            ?: 0.0
        startHeading = heading
        
        // Active Helm Assistance: Maintain steady heading until clear
        NauticalPlugin.autopilot?.setTargetHeading(heading)
        NauticalPlugin.autopilot?.setAutopilotMode("auto")
        
        pushInstruction(app.getString(R.string.nautical_maneuver_slip_exit_executing))
        pushProgress(0)

        speak(app.getString(R.string.nautical_maneuver_slip_exit_tts_start))
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val curLat = state.latitude ?: return
            val curLon = state.longitude ?: return
            
            val sLat = startLat ?: return
            val sLon = startLon ?: return

            val dist = KMapUtils.getDistance(sLat, sLon, curLat, curLon)
            val targetDist = app.settings.NAUTICAL_SLIP_EXIT_DISTANCE.get().toDouble()
            pushProgress((dist / targetDist * 100).toInt())

            if (dist > targetDist) {
                pushInstruction(app.getString(R.string.nautical_maneuver_slip_exit_cleared))
                transitionToCompleted()
            }
        }
    }

    override fun transitionToCompleted() {
        releaseHelm()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        releaseHelm()
        super.transitionToAborted(reason)
    }

    private fun releaseHelm() {
        // Disengage autopilot to return control to manual
        NauticalPlugin.autopilot?.disengage()
    }
}
