package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.engine.MarineState
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import java.util.Locale

class GybingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val shouldCheckWindSafety: Boolean = true
    override val isTackingManeuver: Boolean = false
    private var countdownTimer: Timer? = null
    private var initialAwa: Double? = null
    private var sheetInTriggered = false
    private var sheetOutTriggered = false

    override fun transitionToExecuting() {
        super.transitionToExecuting()
        val state = NauticalPlugin.engine?.getCurrentState()
        initialAwa = state?.windDirectionApparent
        sheetInTriggered = false
        sheetOutTriggered = false
        
        pushInstruction("Gybing: Preparing")
        pushProgress(10)
        
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
                pushInstruction("Gybing: Turning")
                pushProgress(30)

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

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val awa = state.windDirectionApparent?.let { Math.toDegrees(it) } ?: return
        val absAwa = abs(awa)

        // Sheet In: Stern approaching wind (AWA > 165)
        if (!sheetInTriggered && absAwa > 165.0) {
            sheetInTriggered = true
            pushInstruction("Sheet In / Center Boom!")
            pushProgress(50)
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync("Sheet In", AlarmType.TACTICAL_GYBE)
        }

        // Sheet Out: Stern crossed wind and on new side (AWA < 165 on opposite side)
        val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
        
        val crossed180 = if (initial > 0) awa < 0 else awa > 0

        if (sheetInTriggered && !sheetOutTriggered && crossed180) {
            if (absAwa < 160.0) {
                sheetOutTriggered = true
                pushInstruction("Sheet Out / Boom Over!")
                pushProgress(80)
                NauticalPlugin.getInstance()?.speechHelper?.speakAsync("Sheet Out", AlarmType.TACTICAL_GYBE)
                
                if (absAwa < 150.0) {
                    pushInstruction("Gybe Completed")
                    pushProgress(100)
                    transitionToCompleted()
                }
            }
        }
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
