package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import java.util.Timer
import java.util.TimerTask

class GybingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val shouldCheckWindSafety: Boolean = true
    override val isTackingManeuver: Boolean = false
    private var countdownTimer: Timer? = null

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        val state = NauticalPlugin.engine?.getCurrentState()
        
        // Asynchronous TTS Dispatch with high-priority preemption (Phase 8.0R)
        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            "Prepare to gybe. Secure boom in 3, 2, 1.", 
            AlarmType.TACTICAL_GYBE
        )
        
        countdownTimer?.cancel()
        val timer = Timer()
        countdownTimer = timer
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (currentState != ManeuverStateMachine.State.EXECUTING) return

                // Autopilot control after boom is secured
                val apm = NauticalPlugin.autopilotManager
                val apState = apm?.state?.value ?: "standby"
                if (apState == "auto" || apState == "wind") {
                    val awa = state?.windDirectionApparent ?: 0.0
                    val direction = if (awa < 0) "starboard" else "port"
                    apm?.gybe(direction)
                }
            }
        }, 3000)
    }

    override fun transitionToCompleted() {
        countdownTimer?.cancel()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        countdownTimer?.cancel()
        val apm = NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
        }

        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            "Gybe aborted. Autopilot disengaged.", 
            AlarmType.TACTICAL_GYBE
        )
        super.transitionToAborted(reason)
    }
}
