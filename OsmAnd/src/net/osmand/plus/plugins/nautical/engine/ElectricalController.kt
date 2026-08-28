package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import java.util.Locale

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
        val name = (battery.name ?: "").lowercase(Locale.US)
        val isStarter = name.contains("starter") || name.contains("engine") || battery.instance == "1"
        val isLiFePO4 = name.contains("lifepo4") || name.contains("lfp") || name.contains("lithium")

        return if (isStarter) {
            if (is24V) v < 24.0 else v < 12.0
        } else if (isLiFePO4) {
            soc < 0.15 || (if (is24V) v < 25.6 else v < 12.8)
        } else {
            soc < 0.20 || (if (is24V) v < 23.6 else v < 11.8)
        }
    }

    fun isHighVoltage(battery: Battery): Boolean {
        val v = battery.voltage ?: return false
        val is24V = v > 16.0
        val name = (battery.name ?: "").lowercase(Locale.US)
        val isLiFePO4 = name.contains("lifepo4") || name.contains("lfp") || name.contains("lithium")

        val highThreshold = if (isLiFePO4) {
            if (is24V) 29.2 else 14.6
        } else {
            if (is24V) 29.6 else 14.8
        }
        return v > highThreshold
    }

    fun getBatteryHealthWarnings(): List<String> {
        val state = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState() ?: return emptyList()
        val warnings = mutableListOf<String>()
        for ((_, b) in state.batteries) {
            val socStr = b.stateOfCharge?.let { String.format(Locale.US, " (%.0f%% SoC)", it * 100) } ?: ""
            val vStr = b.voltage?.let { String.format(Locale.US, "%.1fV", it) } ?: "--V"
            val label = b.name ?: "Battery ${b.instance}"

            if (isLowBattery(b)) {
                warnings.add("LOW BATTERY: $label: $vStr$socStr")
            } else if (isHighVoltage(b)) {
                warnings.add("OVERVOLTAGE: $label: $vStr$socStr")
            }
        }
        return warnings
    }

    fun payoutRodeMeters(meters: Double = 2.0) {
        setSwitchState("electrical.switches.windlass.down", true)
        app.showToastMessage("Paying out ${meters.toInt()}m rode to release tension")
    }
}
