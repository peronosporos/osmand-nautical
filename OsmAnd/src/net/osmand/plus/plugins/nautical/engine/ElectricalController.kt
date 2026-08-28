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

    fun isLowBattery(battery: Battery): Boolean {
        val v = battery.voltage ?: return false
        val soc = battery.stateOfCharge ?: 1.0
        val is24V = v > 16.0
        val name = (battery.name ?: "").lowercase(java.util.Locale.US)
        val isHouse = name.contains("house") || name.contains("service") || battery.instance == "0"
        val isStarter = name.contains("starter") || name.contains("engine") || battery.instance == "1"

        return if (isHouse) {
            soc < 0.20 || (if (is24V) v < 23.6 else v < 11.8)
        } else if (isStarter) {
            if (is24V) v < 24.0 else v < 12.0
        } else {
            soc < 0.20 || (if (is24V) v < 23.6 else v < 11.8)
        }
    }

    fun getBatteryHealthWarnings(): List<String> {
        val state = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState() ?: return emptyList()
        val warnings = mutableListOf<String>()
        for ((_, b) in state.batteries) {
            if (isLowBattery(b)) {
                val socStr = b.stateOfCharge?.let { String.format(java.util.Locale.US, " (%.0f%% SoC)", it * 100) } ?: ""
                val vStr = b.voltage?.let { String.format(java.util.Locale.US, "%.1fV", it) } ?: "--V"
                warnings.add("${b.name ?: "Battery ${b.instance}"}: $vStr$socStr")
            }
        }
        return warnings
    }
}
