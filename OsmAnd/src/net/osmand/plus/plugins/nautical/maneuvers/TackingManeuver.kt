package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.audio.AlarmType
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

class TackingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_tack
    override val iconRes: Int = R.drawable.ic_action_sail_boat_dark
    override val isHighRisk: Boolean = true

    private var inIronsJob: Job? = null
    private var sheetReleaseTriggered = false
    private var sheetPullTriggered = false
    private var initialAwa: Double? = null
    private var initialVmg: Double? = null
    private var minVmg: Double? = null
    
    private val maneuverScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        // ITEM 12/8 FIX: Unified proactive security check
        if (!net.osmand.plus.plugins.nautical.utils.NauticalSecurityHelper.isConnectionSecure(app.settings)) {
            transitionToAborted(app.getString(R.string.nautical_error_insecure_connection))
            return
        }

        // Lock Helm for Tacking - Manual Acquisition
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, "Tacking"
        )
        super.transitionToExecuting()
        sheetReleaseTriggered = false
        sheetPullTriggered = false
        
        pushInstruction(app.getString(R.string.nautical_tacking_approaching_wind))
        pushProgress(10)
        
        val state = NauticalPlugin.engine?.getCurrentState()
        initialAwa = state?.windDirectionApparent
        initialVmg = state?.velocityMadeGood
        minVmg = state?.velocityMadeGood
        
        // Asynchronous TTS Dispatch
        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            app.getString(R.string.nautical_tacking_prepare), 
            AlarmType.TACTICAL_TACK
        )

        // Autopilot control - Pass manageLock = false as we already acquired it above
        val apm = NauticalPlugin.autopilot
        val apState = apm?.state?.value ?: "standby"
        if (apState == "auto" || apState == "wind") {
            val awa = state?.windDirectionApparent ?: 0.0
            val direction = if (awa < 0) "starboard" else "port"
            apm?.tack(direction = direction, manageLock = false)
        }
        
        startInIronsDetection()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val awa = state.windDirectionApparent?.let { Math.toDegrees(it) } ?: return
        val absAwa = abs(awa)
        
        // ITEM 4 FIX: Dynamic thresholds from Polar Diagram if available
        val tws = state.windSpeedTrue ?: 5.14
        val targetTwa = NauticalPlugin.getInstance()?.tacticalProcessor?.polarDiagram?.getOptimalUpwindTwaRad(tws)?.let { Math.toDegrees(it) } ?: 45.0
        
        val releaseThreshold = min(10.0, targetTwa * 0.25)
        val pullThreshold = targetTwa * 0.33
        val completionThreshold = targetTwa * 0.75

        // Smoothed progress interpolation
        val progress = when {
            !sheetReleaseTriggered -> (10 + (releaseThreshold - absAwa.coerceIn(releaseThreshold, 45.0)) / (45.0 - releaseThreshold) * 30).toInt()
            !sheetPullTriggered -> (40 + (releaseThreshold - absAwa.coerceAtMost(releaseThreshold)) / releaseThreshold * 30).toInt()
            else -> (70 + (absAwa.coerceIn(pullThreshold, completionThreshold) - pullThreshold) / (completionThreshold - pullThreshold) * 30).toInt()
        }
        pushProgress(progress)

        // Sheet Release: Bow approaching wind
        if (!sheetReleaseTriggered && absAwa < releaseThreshold) {
            sheetReleaseTriggered = true
            pushInstruction(app.getString(R.string.nautical_sheet_release))
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync(app.getString(R.string.nautical_sheet_release), AlarmType.TACTICAL_TACK)
        }

        // Sheet Pull: Bow crossed wind and on new tack
        val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
        val crossed = if (initial > 0) awa < -pullThreshold else awa > pullThreshold

        if (sheetReleaseTriggered && !sheetPullTriggered && crossed) {
            sheetPullTriggered = true
            pushInstruction(app.getString(R.string.nautical_sheet_pull))
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync(app.getString(R.string.nautical_sheet_pull), AlarmType.TACTICAL_TACK)
            
            if (absAwa > completionThreshold) {
                pushInstruction(app.getString(R.string.nautical_tack_completed))
                pushProgress(100)
                reportPerformance(state)
                transitionToCompleted()
            }
        }
        
        state.velocityMadeGood?.let { vmg ->
            minVmg = min(minVmg ?: vmg, vmg)
        }
    }

    private fun reportPerformance(state: MarineState) {
        val currentVmg = state.velocityMadeGood ?: return
        val initial = initialVmg ?: return
        
        if (initial > 0.1) {
            val recovery = (currentVmg / initial * 100.0).toInt()
            app.runInUIThread {
                app.showToastMessage(app.getString(R.string.nautical_vmg_recovery, recovery))
            }
        }
    }

    private fun startInIronsDetection() {
        inIronsJob?.cancel()
        inIronsJob = maneuverScope.launch {
            // Item 14: Reduced delay from 8s to 4s for faster stall detection
            delay(4.seconds)
            while (isActive) {
                val state = NauticalPlugin.engine?.getCurrentState() ?: break
                val awa = state.windDirectionApparent?.let { abs(Math.toDegrees(it)) } ?: 0.0
                if (awa < 5.0) {
                    val msg = app.getString(R.string.nautical_warn_stalled_in_irons)
                    NauticalPlugin.hudManager?.get()?.showBanner(
                        msg,
                        5000L,
                        "RESTART",
                        true
                    ) {
                        transitionToExecuting() // Retry the maneuver
                    }
                    
                    NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
                        app.getString(R.string.nautical_warn_stalled_in_irons_tts),
                        AlarmType.TACTICAL_TACK
                    )
                }
                delay(5.seconds)
            }
        }
    }

    override fun transitionToCompleted() {
        inIronsJob?.cancel()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        inIronsJob?.cancel()
        
        val apm = NauticalPlugin.autopilot
        if (apm?.state?.value != "standby") {
            apm?.disengage()
        }

        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            app.getString(R.string.nautical_maneuver_aborted_tts, reason ?: ""), 
            AlarmType.TACTICAL_TACK
        )
        super.transitionToAborted(reason)
    }
}
