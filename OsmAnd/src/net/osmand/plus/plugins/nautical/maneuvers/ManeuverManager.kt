package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.SafetyPreflightController

class ManeuverManager(private val app: OsmandApplication) {

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

    interface ManeuverStateListener {
        fun onStateChanged(newState: ManeuverState)
    }

    fun registerManeuver(id: String, maneuver: ManeuverEngine) {
        maneuvers[id] = maneuver
    }

    fun setActiveManeuver(id: String) {
        activeManeuver = maneuvers[id]
        if (activeManeuver != null) {
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
            maneuver?.transitionToAborted(reason)
            updateState(ManeuverState.IDLE)
            activeManeuver = null

            // Execute recovery logic if available
            SailingDependencyContainer.recoveryEngine?.executeRecovery(maneuverId, reason)

            if (isAlarm) {
                triggerAlarmPowerState()
            }
        }
    }

    fun updateState(state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        activeManeuver?.onStateUpdate(state)
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
