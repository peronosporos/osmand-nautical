package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication

class ManeuverManager(private val app: OsmandApplication) {

    private val maneuvers = mutableMapOf<String, ManeuverEngine>()
    private var activeManeuver: ManeuverEngine? = null

    var state = ManeuverState.IDLE
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
        if (state == ManeuverState.IDLE && activeManeuver != null) {
            activeManeuver?.transitionToArmed()
            updateState(ManeuverState.ARMED)
        }
    }

    fun execute() {
        if (state == ManeuverState.ARMED) {
            activeManeuver?.let {
                it.transitionToExecuting()
                updateState(ManeuverState.EXECUTING)
            }
        }
    }

    fun abort(reason: String? = "User cancelled", isAlarm: Boolean = false) {
        if (state != ManeuverState.IDLE) {
            activeManeuver?.transitionToAborted(reason)
            updateState(ManeuverState.IDLE)
            activeManeuver = null
            if (isAlarm) {
                triggerAlarmPowerState()
            }
        }
    }

    fun updateState(state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        activeManeuver?.onStateUpdate(state)
    }

    private fun triggerAlarmPowerState() {
        val plugin = net.osmand.plus.plugins.PluginsHelper.getEnabledPlugin(net.osmand.plus.plugins.nautical.NauticalPlugin::class.java)
        plugin?.forceEmergencyBrightness()
    }

    private fun updateState(newState: ManeuverState) {
        if (state != newState) {
            state = newState
            listeners.forEach { it.onStateChanged(newState) }
        }
    }
}
