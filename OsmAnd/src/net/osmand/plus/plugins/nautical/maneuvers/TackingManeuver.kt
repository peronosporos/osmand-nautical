package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs

class TackingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var inIronsTimer: Timer? = null
    private var sheetReleaseTriggered = false
    private var sheetPullTriggered = false
    private var initialAwa: Double? = null

    override val shouldCheckWindSafety: Boolean = true
    override val isTackingManeuver: Boolean = true

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        val windSpeed = state.windSpeedTrue ?: 0.0
        if (windSpeed > 30.0) {
            return false
        }
        return true
    }

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        sheetReleaseTriggered = false
        sheetPullTriggered = false
        
        val state = NauticalPlugin.engine?.getCurrentState()
        initialAwa = state?.windDirectionApparent
        
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Tacking. Prepare to tack."))
        }

        // Autopilot control
        val apm = NauticalPlugin.autopilotManager
        val apState = apm?.state?.value ?: "standby"
        if (apState == "auto" || apState == "wind") {
            val awa = state?.windDirectionApparent ?: 0.0
            val direction = if (awa < 0) "starboard" else "port"
            apm?.tack(direction)
        }
        
        startInIronsDetection()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val awa = state.windDirectionApparent?.let { Math.toDegrees(it) } ?: return
        val absAwa = abs(awa)

        // Sheet Release: Bow approaching wind (AWA < 10)
        if (!sheetReleaseTriggered && absAwa < 10.0) {
            sheetReleaseTriggered = true
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention("Sheet Release"))
            }
        }

        // Sheet Pull: Bow crossed wind and on new tack (AWA > 15 on opposite side)
        // Simple logic: if initial AWA was positive (port tack), new tack AWA should be negative (starboard tack)
        val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
        val crossed = if (initial > 0) awa < -10.0 else awa > 10.0

        if (sheetReleaseTriggered && !sheetPullTriggered && crossed) {
            sheetPullTriggered = true
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention("Sheet Pull"))
            }
            // If we've pulled sheets and are on a steady AWA, we are done
            if (absAwa > 30.0) {
                transitionToCompleted()
            }
        }
    }

    private fun startInIronsDetection() {
        inIronsTimer = Timer()
        inIronsTimer?.schedule(object : TimerTask() {
            override fun run() {
                val state = NauticalPlugin.engine?.getCurrentState() ?: return
                val awa = state.windDirectionApparent?.let { abs(Math.toDegrees(it)) } ?: 0.0
                if (awa < 5.0) { // More aggressive stall detection during execution
                    transitionToAborted("Stalled in irons")
                }
            }
        }, 8000, 1000)
    }

    override fun transitionToCompleted() {
        inIronsTimer?.cancel()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        inIronsTimer?.cancel()
        
        val apm = NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
        }

        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Maneuver aborted: $reason. Autopilot disengaged."))
        }
        super.transitionToAborted(reason)
    }
}
