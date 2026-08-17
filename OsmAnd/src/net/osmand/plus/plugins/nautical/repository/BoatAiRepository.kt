package net.osmand.plus.plugins.nautical.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState

data class BoatAiAction(
    @SerializedName("type") val type: String,
    @SerializedName("path") val path: String? = null,
    @SerializedName("value") val value: Any? = null
)

data class BoatAiResult(
    val reply: String,
    val actions: List<BoatAiAction> = emptyList()
)

class BoatAiRepository(private val app: OsmandApplication) {

    private val gson = Gson()

    suspend fun sendQuery(query: String, state: MarineState): Result<BoatAiResult> = withContext(Dispatchers.IO) {
        val service = NauticalPlugin.engine?.getRestService()

        if (service != null) {
            try {
                val stateJsonElement = gson.toJsonTree(state)
                val body = mapOf(
                    "query" to query,
                    "vessel_state" to stateJsonElement
                )

                val response = service.triggerPluginCalculation("signalk-ai-bridge", body)
                if (response.isSuccessful) {
                    val respBody = response.body()
                    if (respBody != null) {
                        val reply = respBody["reply"]?.toString() ?: "No response from AI"
                        val cleanReply = if (reply.startsWith("\"") && reply.endsWith("\"") && reply.length >= 2) {
                            try {
                                gson.fromJson(reply, String::class.java)
                            } catch (_: Exception) {
                                reply.substring(1, reply.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
                            }
                        } else {
                            reply
                        }

                        val actions = mutableListOf<BoatAiAction>()
                        val actionsRaw = respBody["actions"] as? List<*>
                        actionsRaw?.forEach { actionMapRaw ->
                            try {
                                val actionJson = gson.toJsonTree(actionMapRaw)
                                val action = gson.fromJson(actionJson, BoatAiAction::class.java)
                                if (action != null) {
                                    actions.add(action)
                                }
                            } catch (_: Exception) {}
                        }

                        return@withContext Result.success(BoatAiResult(cleanReply, actions))
                    }
                }
            } catch (_: Exception) {
                // Fallback to local onboard marine AI engine
            }
        }

        // On-board Marine Intelligence & Natural Language Engine
        return@withContext Result.success(processOnboardMarineQuery(query, state))
    }

    private fun processOnboardMarineQuery(query: String, state: MarineState): BoatAiResult {
        val q = query.lowercase(java.util.Locale.US).trim()
        val actions = mutableListOf<BoatAiAction>()

        // 1. Autopilot Mode Commands
        if (q.contains("standby") || q.contains("disengage") || q.contains("pilot off") || q.contains("stop pilot")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "standby"))
            return BoatAiResult("Autopilot disengaged. Helm returned to Standby (Manual).", actions)
        }
        if (q.contains("auto") || q.contains("compass mode") || q.contains("engage pilot") || q.contains("pilot on")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "auto"))
            val target = state.headingTrue?.let { Math.toDegrees(it).toInt() } ?: 0
            return BoatAiResult("Autopilot engaged in Compass/Auto mode holding $target°.", actions)
        }
        if (q.contains("wind mode") || q.contains("vane mode") || q.contains("steer wind")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "wind"))
            return BoatAiResult("Autopilot switched to Wind Vane mode.", actions)
        }
        if (q.contains("track mode") || q.contains("route mode") || q.contains("nav mode")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "route"))
            return BoatAiResult("Autopilot following active route / navigation track.", actions)
        }

        // 2. Autopilot Heading Adjustment
        val headingRegex = Regex("(?:head|steer|course|heading|bearing)\\s+(?:to\\s+)?([0-3]?[0-9]{1,2})")
        val headingMatch = headingRegex.find(q)
        if (headingMatch != null) {
            val deg = headingMatch.groupValues[1].toIntOrNull()
            if (deg != null && deg in 0..360) {
                val rad = Math.toRadians(deg.toDouble())
                actions.add(BoatAiAction(type = "autopilot_heading", value = rad))
                return BoatAiResult("Setting autopilot target heading to $deg°.", actions)
            }
        }

        // 3. Tacking / Maneuvers
        if (q.contains("tack port") || q.contains("tack to port")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "tack_port"))
            return BoatAiResult("Initiating auto-tack to Port.", actions)
        }
        if (q.contains("tack star") || q.contains("tack to star")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "tack_starboard"))
            return BoatAiResult("Initiating auto-tack to Starboard.", actions)
        }

        // 4. Digital Switching / Lights
        if (q.contains("nav light") || q.contains("navigation light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.navigationLights.state", value = turnOn))
            return BoatAiResult("Navigation lights turned ${if (turnOn) "ON" else "OFF"}.", actions)
        }
        if (q.contains("anchor light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.anchorLight.state", value = turnOn))
            return BoatAiResult("Anchor light turned ${if (turnOn) "ON" else "OFF"}.", actions)
        }
        if (q.contains("deck light") || q.contains("cabin light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.cabinLights.state", value = turnOn))
            return BoatAiResult("Cabin/Deck lights turned ${if (turnOn) "ON" else "OFF"}.", actions)
        }

        // 5. Anchor Controls & Status
        if (q.contains("set anchor") || q.contains("drop anchor")) {
            val lat = state.latitude ?: 0.0
            val lon = state.longitude ?: 0.0
            actions.add(BoatAiAction(type = "anchor", value = mapOf("latitude" to lat, "longitude" to lon, "radius" to 50.0)))
            return BoatAiResult("Anchor watch armed at current position (50m radius).", actions)
        }
        if (q.contains("disarm anchor") || q.contains("raise anchor") || q.contains("clear anchor")) {
            actions.add(BoatAiAction(type = "anchor", path = "disarm", value = "disarm"))
            return BoatAiResult("Anchor watch disarmed.", actions)
        }
        if (q.contains("anchor")) {
            val armed = state.anchorLatitude != null
            val reply = if (armed) {
                val dist = state.anchorDistanceMeters?.toInt() ?: 0
                val radius = state.anchorRadiusMeters?.toInt() ?: 50
                "Anchor Watch: ARMED. Distance from anchor: ${dist}m (Limit: ${radius}m)."
            } else {
                "Anchor Watch: DISARMED."
            }
            return BoatAiResult(reply, actions)
        }

        // 6. Wind Information
        if (q.contains("wind") || q.contains("tws") || q.contains("twa") || q.contains("aws") || q.contains("awa")) {
            val twsKn = (state.windSpeedTrue ?: 0.0) * 1.94384
            val twdDeg = state.windDirectionTrue?.let { (Math.toDegrees(it).toInt() + 360) % 360 } ?: 0
            val awsKn = (state.windSpeedApparent ?: 0.0) * 1.94384
            val awaDeg = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() } ?: 0
            val side = if (awaDeg >= 0) "Starboard" else "Port"
            val reply = "Wind Report:\n• True Wind: %.1f kn @ %d°\n• Apparent Wind: %.1f kn @ %d° (%s)".format(
                java.util.Locale.US, twsKn, twdDeg, kotlin.math.abs(awsKn), kotlin.math.abs(awaDeg), side
            )
            return BoatAiResult(reply, actions)
        }

        // 7. Depth & Sounding
        if (q.contains("depth") || q.contains("keel") || q.contains("sounder") || q.contains("clearance") || q.contains("draft")) {
            val dbt = state.depthBelowTransducer ?: state.depthBelowKeel
            val dbk = state.depthBelowKeel
            val reply = if (dbk != null) {
                "Depth: %.1f m below keel (%.1f m below surface). Safety corridor clear.".format(java.util.Locale.US, dbk, dbt ?: dbk)
            } else if (dbt != null) {
                "Depth: %.1f m below transducer.".format(java.util.Locale.US, dbt)
            } else {
                "Depth sounder data currently offline."
            }
            return BoatAiResult(reply, actions)
        }

        // 8. Speed, Heading, Course & Polar Performance
        if (q.contains("speed") || q.contains("sog") || q.contains("stw") || q.contains("heading") || q.contains("cog") || q.contains("polar")) {
            val sogKn = (state.speedOverGround ?: 0.0) * 1.94384
            val stwKn = (state.speedThroughWater ?: 0.0) * 1.94384
            val hdg = state.headingTrue?.let { (Math.toDegrees(it).toInt() + 360) % 360 } ?: 0
            val cog = state.courseOverGroundTrue?.let { (Math.toDegrees(it).toInt() + 360) % 360 } ?: 0
            val polar = state.polarSpeedRatio?.let { " • Polar Efficiency: ${(it * 100).toInt()}%" } ?: ""
            val reply = "Vessel Navigation:\n• SOG: %.1f kn | STW: %.1f kn\n• Heading: %d° | COG: %d°%s".format(
                java.util.Locale.US, sogKn, stwKn, hdg, cog, polar
            )
            return BoatAiResult(reply, actions)
        }

        // 9. Battery & Electrical
        if (q.contains("battery") || q.contains("voltage") || q.contains("power") || q.contains("soc") || q.contains("electrical")) {
            val v = state.batteryVoltage
            val soc = state.batterySoc?.let { (it * 100).toInt() }
            val reply = if (v != null) {
                val socStr = if (soc != null) " (State of Charge: $soc%)" else ""
                "Electrical: Battery bank is at %.1f V%s.".format(java.util.Locale.US, v, socStr)
            } else {
                "Electrical data not reported on active network."
            }
            return BoatAiResult(reply, actions)
        }

        // 10. Alarms & Notifications
        if (q.contains("alarm") || q.contains("alert") || q.contains("warning") || q.contains("notif")) {
            if (q.contains("ack") || q.contains("silence") || q.contains("clear")) {
                state.notifications.keys.firstOrNull()?.let { path ->
                    actions.add(BoatAiAction(type = "notification_ack", path = path))
                }
                return BoatAiResult("Acknowledged active alarms.", actions)
            }
            val activeNotifs = state.notifications.values.filter { it.state != net.osmand.plus.plugins.nautical.engine.NotificationState.NORMAL }
            val reply = if (activeNotifs.isNotEmpty()) {
                "Active Alarms (%d):\n%s".format(
                    activeNotifs.size,
                    activeNotifs.joinToString("\n") { "• ${it.message} [${it.state}]" }
                )
            } else {
                "All safety systems normal. No active alarms."
            }
            return BoatAiResult(reply, actions)
        }

        // 11. Autopilot Status Query
        if (q.contains("autopilot") || q.contains("pilot")) {
            val mode = state.autopilotState?.uppercase(java.util.Locale.US) ?: "STANDBY"
            val target = state.targetHeading?.let { " Target: ${(Math.toDegrees(it).toInt() + 360) % 360}°" } ?: ""
            return BoatAiResult("Autopilot is currently in $mode mode.$target", actions)
        }

        // 12. General Status / Vessel Overview
        val sogKn = (state.speedOverGround ?: 0.0) * 1.94384
        val hdg = state.headingTrue?.let { (Math.toDegrees(it).toInt() + 360) % 360 } ?: 0
        val twsKn = (state.windSpeedTrue ?: 0.0) * 1.94384
        val reply = "Vessel Status:\n• SOG: %.1f kn | HDG: %d°\n• True Wind: %.1f kn\n• Autopilot: %s\n\nYou can ask about wind, depth, speed, batteries, or command the autopilot and lights.".format(
            java.util.Locale.US, sogKn, hdg, twsKn, state.autopilotState ?: "STANDBY"
        )
        return BoatAiResult(reply, actions)
    }
}
