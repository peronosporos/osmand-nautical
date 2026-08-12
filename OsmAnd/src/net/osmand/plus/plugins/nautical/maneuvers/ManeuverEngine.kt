package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.engine.MarineState

abstract class ManeuverEngine(
    protected val app: OsmandApplication
) : ManeuverStateMachine {

    interface ManeuverEngineListener {
        fun onManeuverCompleted(maneuver: ManeuverEngine)
        fun onManeuverAborted(maneuver: ManeuverEngine, reason: String?)
    }

    private val engineListeners = mutableListOf<ManeuverEngineListener>()

    private val _instructionFlow = MutableStateFlow<String?>(null)
    val instructionFlow: StateFlow<String?> = _instructionFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow(0)
    val progressFlow: StateFlow<Int> = _progressFlow.asStateFlow()

    override var currentState: ManeuverStateMachine.State = ManeuverStateMachine.State.ARMED
        protected set
    
    protected val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timeoutJob: Job? = null

    protected open val maneuverTimeoutMs: Long = 60000L

    abstract val displayNameRes: Int
    abstract val iconRes: Int
    open val isHighRisk: Boolean = false

    protected open val shouldCheckWindSafety: Boolean = false
    protected open val isTackingManeuver: Boolean = true

    fun registerEngineListener(listener: ManeuverEngineListener) {
        if (!engineListeners.contains(listener)) {
            engineListeners.add(listener)
        }
    }

    fun unregisterEngineListener(listener: ManeuverEngineListener) {
        engineListeners.remove(listener)
    }

    protected fun pushInstruction(text: String?) {
        _instructionFlow.value = text
    }

    protected fun pushProgress(percent: Int) {
        _progressFlow.value = percent.coerceIn(0, 100)
    }

    fun speak(text: String) {
        NauticalAudioArbiter.getInstance(app).dispatchTts(text, AlarmType.TTS_INSTRUCTION)
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

    protected var executionStartTime: Long = 0

    override fun transitionToArmed() {
        currentState = ManeuverStateMachine.State.ARMED
    }

    override fun transitionToExecuting() {
        currentState = ManeuverStateMachine.State.EXECUTING
        executionStartTime = System.currentTimeMillis()
        startTimeoutTimer()
    }

    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = engineScope.launch {
            delay(maneuverTimeoutMs)
            if (currentState == ManeuverStateMachine.State.EXECUTING) {
                transitionToAborted("Maneuver timed out")
            }
        }
    }

    override fun transitionToCompleted() {
        currentState = ManeuverStateMachine.State.COMPLETED
        timeoutJob?.cancel()
        engineListeners.forEach { it.onManeuverCompleted(this) }
    }

    override fun transitionToAborted(reason: String?) {
        currentState = ManeuverStateMachine.State.ABORTED
        timeoutJob?.cancel()
        engineListeners.forEach { it.onManeuverAborted(this, reason) }
    }

    open fun onStateUpdate(state: MarineState) {
        // To be overridden by subclasses
    }
}
