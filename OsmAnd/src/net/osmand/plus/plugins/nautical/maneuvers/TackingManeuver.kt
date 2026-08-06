package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.audio.AlarmType
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs

class TackingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private var inIronsTimer: Timer? = null
    private var sheetReleaseTriggered = false
    private var sheetPullTriggered = false
    private var initialAwa: Double? = null
    private var initialVmg: Double? = null
    private var minVmg: Double? = null

    override val shouldCheckWindSafety: Boolean = true
    override val isTackingManeuver: Boolean = true

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        val windSpeed = state.windSpeedTrue ?: 0.0
        val limit = app.settings.NAUTICAL_TACKING_WIND_LIMIT.get().toDouble()
        if (windSpeed > limit) {
            return false
        }
        return true
    }

    override fun transitionToExecuting() {
        // Lock Helm for Tacking
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Tacking"
        )
        super.transitionToExecuting()
        sheetReleaseTriggered = false
        sheetPullTriggered = false
        
        pushInstruction("Tacking: Approaching Wind")
        pushProgress(10)
        
        val state = NauticalPlugin.engine?.getCurrentState()
        initialAwa = state?.windDirectionApparent
        initialVmg = state?.velocityMadeGood
        minVmg = state?.velocityMadeGood
        
        // Asynchronous TTS Dispatch (Phase 8.0R)
        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            "Tacking. Prepare to tack.", 
            AlarmType.TACTICAL_TACK
        )

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
            pushInstruction("Sheet Release!")
            pushProgress(40)
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync("Sheet Release", AlarmType.TACTICAL_TACK)
        }

        // Sheet Pull: Bow crossed wind and on new tack (AWA > 15 on opposite side)
        val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
        val crossed = if (initial > 0) awa < -10.0 else awa > 10.0

        if (sheetReleaseTriggered && !sheetPullTriggered && crossed) {
            sheetPullTriggered = true
            pushInstruction("Sheet Pull!")
            pushProgress(70)
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync("Sheet Pull", AlarmType.TACTICAL_TACK)
            
            if (absAwa > 30.0) {
                pushInstruction("Tack Completed")
                pushProgress(100)
                reportPerformance()
                transitionToCompleted()
            }
        }
        
        state.velocityMadeGood?.let { vmg ->
            minVmg = kotlin.math.min(minVmg ?: vmg, vmg)
        }
    }

    private fun reportPerformance() {
        val currentVmg = NauticalPlugin.engine?.getCurrentState()?.velocityMadeGood ?: return
        val initial = initialVmg ?: return
        
        if (initial > 0.1) {
            val recovery = (currentVmg / initial * 100.0).toInt()
            app.runInUIThread {
                app.showToastMessage(app.getString(R.string.nautical_vmg_recovery, recovery))
            }
        }
    }

    private fun startInIronsDetection() {
        inIronsTimer = Timer()
        inIronsTimer?.schedule(object : TimerTask() {
            override fun run() {
                val state = NauticalPlugin.engine?.getCurrentState() ?: return
                val awa = state.windDirectionApparent?.let { abs(Math.toDegrees(it)) } ?: 0.0
                if (awa < 5.0) {
                    transitionToAborted(app.getString(R.string.nautical_abort_stalled_in_irons))
                }
            }
        }, 8000, 1000)
    }

    override fun transitionToCompleted() {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
        inIronsTimer?.cancel()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER
        )
        inIronsTimer?.cancel()
        
        val apm = NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
        }

        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            "Maneuver aborted: $reason. Autopilot disengaged.", 
            AlarmType.TACTICAL_TACK
        )
        super.transitionToAborted(reason)
    }
}
