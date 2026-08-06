package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication

class ElectricalController(
    private val app: OsmandApplication,
    private val autopilot: AutopilotController,
) {
    fun setSwitchState(path: String, state: Boolean) {
        if (!autopilot.isConnected()) {
            app.showToastMessage(net.osmand.plus.R.string.nautical_autopilot_not_connected)
            return
        }
        net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.setSwitch(path, state)
    }

    fun toggleSwitch(path: String) {
        val engine = net.osmand.plus.plugins.nautical.NauticalPlugin.engine
        val currentState = engine?.getCurrentState()
        val currentVal = currentState?.switches?.get(path) ?: false
        setSwitchState(path, !currentVal)
    }
}
