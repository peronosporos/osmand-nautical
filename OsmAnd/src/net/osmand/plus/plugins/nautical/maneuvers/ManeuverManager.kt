package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.SafetyPreflightController

class ManeuverManager(private val app: OsmandApplication) : ManeuverEngine.ManeuverEngineListener {

    private val safetyPreflightController by lazy {
        val broker = NauticalPlugin.engine?.dataBroker
        val ap = NauticalPlugin.autopilot
        if ((broker != null) && (ap != null)) {
            SafetyPreflightController(app, broker, ap)
        } else {
            null
        }
    }

    private val maneuvers = mutableMapOf<String, ManeuverEngine>()
    var activeManeuver: ManeuverEngine? = null
        private set

    var state = ManeuverState.IDLE
        private set
    
    var lastAbortReason: String? = null
        private set

    private val listeners = mutableListOf<ManeuverStateListener>()

    init {
        registerDefaultManeuvers()
    }

    private fun registerDefaultManeuvers() {
        val tacking = TackingManeuver(app)
        registerManeuver("tack_port", tacking)
        registerManeuver("tack_stbd", tacking)
        registerManeuver("tacking", tacking)

        val gybing = GybingManeuver(app)
        registerManeuver("gybe_port", gybing)
        registerManeuver("gybe_stbd", gybing)
        registerManeuver("gybing", gybing)

        val shunting = ShuntingManeuver(app)
        registerManeuver("shunt", shunting)
        registerManeuver("shunting", shunting)

        val heaveTo = HeavingToManeuver(app)
        registerManeuver("heave_to", heaveTo)
        registerManeuver("heaving_to", heaveTo)

        val anchoring = AnchoringManeuver(app)
        registerManeuver("anchoring", anchoring)

        val weighAnchor = WeighingAnchorManeuver(app)
        registerManeuver("weigh_anchor", weighAnchor)
        registerManeuver("weighing_anchor", weighAnchor)

        val mooring = MooringManeuver(app)
        registerManeuver("mooring", mooring)

        val medMooring = MedMooringManeuver(app)
        registerManeuver("med_mooring", medMooring)

        val docking = DockingManeuver(app)
        registerManeuver("docking", docking)

        val slipExit = SlipExitManeuver(app)
        registerManeuver("slip_exit", slipExit)

        val holdingPattern = HoldingPatternManeuver(app)
        registerManeuver("holding_pattern", holdingPattern)
    }

    interface ManeuverStateListener {
        fun onStateChanged(newState: ManeuverState)
    }

    fun registerManeuver(id: String, maneuver: ManeuverEngine) {
        maneuvers[id] = maneuver
    }

    fun setActiveManeuver(id: String) {
        activeManeuver?.unregisterEngineListener(this)
        activeManeuver = maneuvers[id]
        if (activeManeuver != null) {
            activeManeuver?.registerEngineListener(this)
            arm()
        } else {
            abort()
        }
    }

    fun getManeuverId(maneuver: ManeuverEngine): String? {
        return maneuvers.entries.find { it.value == maneuver }?.key
    }

    fun getManeuverById(id: String): ManeuverEngine? {
        return maneuvers[id]
    }

    fun registerListener(listener: ManeuverStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: ManeuverStateListener) {
        listeners.remove(listener)
    }

    fun arm() {
        if ((state == ManeuverState.IDLE) && (activeManeuver != null)) {
            activeManeuver?.transitionToArmed()
            updateState(ManeuverState.ARMED)
        }
    }

    fun execute(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        if (state == ManeuverState.ARMED) {
            val maneuver = activeManeuver ?: return
            val maneuverId = getManeuverId(maneuver) ?: "maneuver"

            val preflight = safetyPreflightController
            if (preflight != null) {
                scope.launch {
                    val (success, reason) = preflight.runPreflightCheck(maneuverId)
                    if (success) {
                        maneuver.transitionToExecuting()
                        updateState(ManeuverState.EXECUTING)
                        
                        // TASK-047: Workflow Touch Lock Integration
                        if (app.settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.get() == true) {
                            NauticalPlugin.getInstance()?.workflowManager?.getScreenTouchLockManager()?.setTouchLockActive(active = true)
                        }
                    } else {
                        val failMsg = reason ?: app.getString(R.string.nautical_error_preflight_failed)
                        // Task: Don't trigger emergency brightness for standard preflight failures unless it's MOB
                        val isEmergency = maneuverId == "man_overboard"
                        abort(failMsg, isAlarm = isEmergency)
                        app.runInUIThread {
                            app.showToastMessage(failMsg)
                        }
                    }
                }
            } else {
                maneuver.transitionToExecuting()
                updateState(ManeuverState.EXECUTING)
            }
        }
    }

    fun abort(reason: String? = "User cancelled", isAlarm: Boolean = false) {
        if (state != ManeuverState.IDLE) {
            val maneuver = activeManeuver
            val maneuverId = maneuver?.let { getManeuverId(it) } ?: "unknown"
            
            lastAbortReason = reason
            maneuver?.unregisterEngineListener(this)
            maneuver?.transitionToAborted(reason)
            updateState(ManeuverState.IDLE)
            activeManeuver = null

            // Release helm lock and touch lock
            releaseLocks()

            // Execute recovery logic if available
            SailingDependencyContainer.recoveryEngine?.executeRecovery(maneuverId, reason)

            if (isAlarm) {
                triggerAlarmPowerState()
            }
        }
    }

    /**
     * Forcefully completes the active maneuver, bypassing sensor-based automatic completion.
     * Useful for manual anchoring or docking where hardware counters/sensors are absent.
     */
    fun completeActiveManeuver() {
        if (state == ManeuverState.EXECUTING) {
            activeManeuver?.unregisterEngineListener(this)
            activeManeuver?.transitionToCompleted()
            updateState(ManeuverState.IDLE)
            activeManeuver = null
            
            // Release helm lock and touch lock
            releaseLocks()
        }
    }

    override fun onManeuverCompleted(maneuver: ManeuverEngine) {
        app.runInUIThread {
            if (activeManeuver == maneuver) {
                updateState(ManeuverState.IDLE)
                activeManeuver = null
                releaseLocks()
            }
        }
    }

    override fun onManeuverAborted(maneuver: ManeuverEngine, reason: String?) {
        app.runInUIThread {
            if (activeManeuver == maneuver) {
                lastAbortReason = reason
                updateState(ManeuverState.IDLE)
                activeManeuver = null
                releaseLocks()
            }
        }
    }

    private fun releaseLocks() {
        NauticalPlugin.getInstance()?.workflowManager?.getScreenTouchLockManager()?.setTouchLockActive(active = false)
        val arbitrator = net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app)
        
        // Refactored Release: Only release tactical maneuver lock unless it's explicitly an MOB maneuver (Item 9)
        // Standard maneuvers use PRIORITY_TACTICAL_MANEUVER.
        arbitrator.releaseLock(
            net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, 
            force = true
        )
    }

    fun updateState(state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        app.runInUIThread {
            activeManeuver?.onStateUpdate(state)
        }
    }

    private fun triggerAlarmPowerState() {
        val plugin = net.osmand.plus.plugins.PluginsHelper.getEnabledPlugin(NauticalPlugin::class.java)
        plugin?.forceEmergencyBrightness()
    }

    private fun updateState(newState: ManeuverState) {
        if (state != newState) {
            state = newState
            listeners.forEach { it.onStateChanged(newState) }
        }
    }
}
