package net.osmand.plus.plugins.nautical.ui.ai

import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.repository.BoatAiAction
import java.util.Locale

class BoatAiActionExecutor {

    /**
     * Executes an AI-suggested action on the vessel.
     * All actions are routed through SignalKControlManager to ensure hardware safety guards are respected.
     */
    fun execute(action: BoatAiAction): Boolean {
        val engine = NauticalPlugin.engine ?: return false
        val control = engine.controlManager
        
        return try {
            when (action.type.lowercase(Locale.US)) {
                "switch" -> {
                    val path = action.path ?: return false
                    val value = parseBoolean(action.value)
                    control.setSwitchState(path, value)
                    true
                }
                "dimmer" -> {
                    val path = action.path ?: return false
                    val value = parseDouble(action.value) ?: return false
                    control.setDimmerValue(path, value)
                    true
                }
                "autopilot_mode" -> {
                    val mode = action.value?.toString()?.lowercase(Locale.US) ?: return false
                    control.setAutopilotMode(mode)
                    true
                }
                "autopilot_heading" -> {
                    val heading = parseDouble(action.value) ?: return false
                    control.setAutopilotTargetHeading(heading)
                    true
                }
                "autopilot_heading_mag" -> {
                    val heading = parseDouble(action.value) ?: return false
                    control.setAutopilotTargetHeadingMagnetic(heading)
                    true
                }
                "notification_ack" -> {
                    val path = action.path ?: return false
                    control.acknowledgeNotification(path)
                    true
                }
                "anchor" -> {
                    if (action.path == "disarm" || action.value == false || action.value == "disarm") {
                        control.disarmAnchor()
                    } else if (action.value is Map<*, *>) {
                        val lat = (action.value["latitude"] as? Number)?.toDouble()
                        val lon = (action.value["longitude"] as? Number)?.toDouble()
                        val radius = (action.value["radius"] as? Number)?.toDouble() ?: 50.0
                        if (lat != null && lon != null) {
                            control.setAnchor(lat, lon, radius)
                        } else return false
                    } else return false
                    true
                }
                "media" -> {
                    val command = action.path?.uppercase(Locale.US) ?: return false
                    control.sendMediaCommand(command, action.value)
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() > 0.5
            is String -> {
                val s = value.lowercase(Locale.US)
                s == "true" || s == "on" || s == "1" || s == "active"
            }
            else -> false
        }
    }

    private fun parseDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
}
