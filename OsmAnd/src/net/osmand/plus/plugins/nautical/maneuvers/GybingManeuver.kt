package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

class GybingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_gybe
    override val iconRes: Int = R.drawable.ic_action_sail_boat_dark
    override val isHighRisk: Boolean = true
    override val maneuverTimeoutMs: Long = 120000L

    override val shouldCheckWindSafety: Boolean = true
    override val isTackingManeuver: Boolean = false
    private var countdownJob: Job? = null
    private var initialAwa: Double? = null
    private var sheetInTriggered = false
    private var sheetOutTriggered = false
    
    private val maneuverScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun transitionToExecuting() {
        // ITEM 12/8 FIX: Unified proactive security check
        if (!net.osmand.plus.plugins.nautical.utils.NauticalSecurityHelper.isConnectionSecure(app.settings)) {
            transitionToAborted(app.getString(R.string.nautical_error_insecure_connection))
            return
        }

        // ITEM 3: Suppress accidental gybe alarm during deliberate maneuver
        NauticalPlugin.engine?.acknowledgeNotification("safety.alarm.gybe")

        // Acquire Helm Lock
        net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app).acquireLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, 
            app.getString(R.string.nautical_gybe)
        )
        
        super.transitionToExecuting()
        val state = NauticalPlugin.engine?.getCurrentState()
        initialAwa = state?.windDirectionApparent
        sheetInTriggered = false
        sheetOutTriggered = false
        
        pushInstruction(app.getString(R.string.nautical_gybing_preparing))
        pushProgress(10)
        
        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            app.getString(R.string.nautical_gybing_prepare_boom), 
            AlarmType.TACTICAL_GYBE
        )
        
        val prepDelay = app.settings.NAUTICAL_GYBE_PREP_DELAY.get().coerceAtLeast(1).seconds
        
        countdownJob?.cancel()
        countdownJob = maneuverScope.launch {
            delay(prepDelay)
            if (currentState == ManeuverStateMachine.State.EXECUTING) {
                pushInstruction(app.getString(R.string.nautical_gybing_turning))
                pushProgress(30)

                // Autopilot control after boom is secured
                val apm = NauticalPlugin.autopilot
                val apState = apm?.state?.value ?: "standby"
                if (apState == "auto" || apState == "wind") {
                    val awa = state?.windDirectionApparent ?: 0.0
                    val direction = if (awa < 0) "starboard" else "port"
                    apm?.gybe(direction = direction, manageLock = false)
                }
            }
        }
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val awa = state.windDirectionApparent?.let { Math.toDegrees(it) } ?: return
        val absAwa = abs(awa)

        // ITEM 4: Dynamic thresholds for gybing
        val tws = state.windSpeedTrue ?: 5.14
        val targetTwa = NauticalPlugin.getInstance()?.tacticalProcessor?.polarDiagram?.getOptimalDownwindTwaRad(tws)?.let { Math.toDegrees(it) } ?: 150.0

        val sheetInThreshold = min(170.0, targetTwa + 15.0)
        val sheetOutThreshold = targetTwa + 5.0
        val completionThreshold = targetTwa - 10.0

        // Progress interpolation (Item 2 fix)
        val progress = when {
            !sheetInTriggered -> (30 + (absAwa.coerceIn(135.0, sheetInThreshold) - 135.0) / (sheetInThreshold - 135.0) * 20).toInt()
            !sheetOutTriggered -> {
                // Crossing 180 zone: 50% at 170, 65% at 180, 80% at 170 on new side
                val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
                val crossed180 = if (initial > 0) awa < 0 else awa > 0
                if (!crossed180) {
                     (50 + (absAwa - 170.0).coerceIn(0.0, 10.0) / 10.0 * 15).toInt()
                } else {
                     (65 + (180.0 - absAwa).coerceIn(0.0, 10.0) / 10.0 * 15).toInt()
                }
            }
            else -> (80 + (absAwa.coerceIn(completionThreshold, sheetOutThreshold) - sheetOutThreshold) / (completionThreshold - sheetOutThreshold) * 20).toInt()
        }
        pushProgress(progress)

        // Sheet In: Stern approaching wind
        if (!sheetInTriggered && absAwa > sheetInThreshold) {
            sheetInTriggered = true
            pushInstruction(app.getString(R.string.nautical_sheet_in_boom))
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync(app.getString(R.string.nautical_sheet_in_boom), AlarmType.TACTICAL_GYBE)
        }

        // ITEM 1 FIX: Logic separated to avoid stuck state
        val initial = initialAwa?.let { Math.toDegrees(it) } ?: 0.0
        val crossed180 = if (initial > 0) awa < 0 else awa > 0

        if (sheetInTriggered && !sheetOutTriggered && crossed180 && absAwa < sheetOutThreshold) {
            sheetOutTriggered = true
            pushInstruction(app.getString(R.string.nautical_sheet_out_boom))
            NauticalPlugin.getInstance()?.speechHelper?.speakAsync(app.getString(R.string.nautical_sheet_out_boom), AlarmType.TACTICAL_GYBE)
        }
        
        if (sheetOutTriggered && absAwa < completionThreshold) {
            pushInstruction(app.getString(R.string.nautical_gybe_completed))
            pushProgress(100)
            transitionToCompleted()
        }
    }

    override fun transitionToCompleted() {
        countdownJob?.cancel()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        countdownJob?.cancel()
        val apm = NauticalPlugin.autopilot
        if (apm?.state?.value != "standby") {
            apm?.disengage()
        }

        NauticalPlugin.getInstance()?.speechHelper?.speakAsync(
            app.getString(R.string.nautical_maneuver_aborted_tts, reason ?: ""), 
            AlarmType.TACTICAL_GYBE
        )
        super.transitionToAborted(reason)
    }
}
