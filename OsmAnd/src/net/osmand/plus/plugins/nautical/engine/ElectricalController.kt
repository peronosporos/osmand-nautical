package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication

class ElectricalController(
    @Suppress("unused") private val app: OsmandApplication,
    @Suppress("unused") private val autopilot: AutopilotController,
) {
    fun setSwitchState(path: String, state: Boolean) {
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.setSwitch(path, state)
    }

    fun setDimmerValue(path: String, level: Double) {
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.controlManager?.setDimmerValue(path, level)
    }

    fun toggleSwitch(path: String) {
        val engine = net.osmand.plus.plugins.nautical.NauticalPlugin.engine
        val currentState = engine?.getCurrentState()
        val currentVal = currentState?.switches?.get(path) ?: false
        setSwitchState(path, !currentVal)
    }
}
