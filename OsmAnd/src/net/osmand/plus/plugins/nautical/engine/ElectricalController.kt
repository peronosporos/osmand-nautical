package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import java.util.Locale

class ElectricalController(
    private val app: OsmandApplication,
    private val autopilot: AutopilotController
) {
    fun setSwitchState(path: String, state: Boolean) {
        // Path example: electrical.switches.anchorLight
        // Signal K PUT path: v1/api/vessels/self/electrical/switches/<id>/state
        
        val id = path.substringAfterLast(".")
        val urlPath = if (path.contains("switches")) {
            "electrical/switches/$id/state"
        } else if (path.contains("relays")) {
            "electrical/relays/$id/state"
        } else {
            return
        }

        val url = autopilot.buildVesselUrl(urlPath) ?: return
        val payload = """{ "value": ${if (state) 1 else 0} }"""
        
        autopilot.executePut(url, payload, R.string.nautical_command_sent, showToast = true)
    }

    fun toggleSwitch(path: String) {
        val engine = net.osmand.plus.plugins.nautical.NauticalPlugin.engine
        val currentState = engine?.getCurrentState()
        val currentVal = currentState?.switches?.get(path) ?: false
        setSwitchState(path, !currentVal)
    }
}
