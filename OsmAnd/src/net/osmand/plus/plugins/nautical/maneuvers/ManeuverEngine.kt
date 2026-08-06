package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

abstract class ManeuverEngine(
    protected val app: OsmandApplication
) : ManeuverStateMachine {

    private val _instructionFlow = MutableStateFlow<String?>(null)
    val instructionFlow: StateFlow<String?> = _instructionFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow(0)
    val progressFlow: StateFlow<Int> = _progressFlow.asStateFlow()

    override var currentState: ManeuverStateMachine.State = ManeuverStateMachine.State.ARMED
        protected set
    
    private var maneuverTimeoutTimer: Timer? = null

    protected open val shouldCheckWindSafety: Boolean = false
    protected open val isTackingManeuver: Boolean = true

    protected fun pushInstruction(text: String?) {
        _instructionFlow.value = text
    }

    protected fun pushProgress(percent: Int) {
        _progressFlow.value = percent.coerceIn(0, 100)
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (shouldCheckWindSafety) {
            val autopilot = NauticalPlugin.autopilot ?: return false
            val isSafe = autopilot.isWindSafeForManeuver(isTackingManeuver) 
            
            if (!isSafe) {
                val msg = app.getString(R.string.nautical_warn_unsafe_maneuver)
                app.player?.let { player ->
                    player.playCommands(player.newCommandBuilder().attention(msg))
                }
                val mm = net.osmand.plus.plugins.PluginsHelper.getEnabledPlugin(NauticalPlugin::class.java)?.maneuverManager
                mm?.abort(msg, isAlarm = true)
                return false
            }
        }
        return true
    }

    override fun transitionToArmed() {
        currentState = ManeuverStateMachine.State.ARMED
    }

    override fun transitionToExecuting() {
        currentState = ManeuverStateMachine.State.EXECUTING
        startTimeoutTimer()
    }

    private fun startTimeoutTimer() {
        maneuverTimeoutTimer?.cancel()
        maneuverTimeoutTimer = Timer()
        maneuverTimeoutTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (currentState == ManeuverStateMachine.State.EXECUTING) {
                    transitionToAborted("Maneuver timed out")
                }
            }
        }, 60000)
    }

    override fun transitionToCompleted() {
        currentState = ManeuverStateMachine.State.COMPLETED
        maneuverTimeoutTimer?.cancel()
    }

    override fun transitionToAborted(reason: String?) {
        currentState = ManeuverStateMachine.State.ABORTED
        maneuverTimeoutTimer?.cancel()
    }

    open fun onStateUpdate(state: MarineState) {
        // To be overridden by subclasses
    }
}
