package net.osmand.plus.plugins.nautical.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
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

    private val log = PlatformUtil.getLog(BoatAiRepository::class.java)
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
                        val reply = respBody["reply"]?.toString() ?: app.getString(R.string.nautical_ai_rep_no_response)
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
                            } catch (e: Exception) {
                                log.warn("Failed to parse BoatAI action: ${e.message}", e)
                            }
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
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_ap_standby), actions)
        }
        if (q.contains("auto") || q.contains("compass mode") || q.contains("engage pilot") || q.contains("pilot on")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "auto"))
            // Only quote the held heading when it is real; never invent "0°".
            val target = state.headingTrue?.let { Math.toDegrees(it).toInt() }
            val reply = if (target != null) {
                String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_ap_auto_holding), target)
            } else {
                app.getString(R.string.nautical_ai_rep_ap_auto_pending)
            }
            return BoatAiResult(reply, actions)
        }
        if (q.contains("wind mode") || q.contains("vane mode") || q.contains("steer wind")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "wind"))
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_ap_wind), actions)
        }
        if (q.contains("track mode") || q.contains("route mode") || q.contains("nav mode")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "route"))
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_ap_route), actions)
        }

        // 2. Autopilot Heading Adjustment
        val headingRegex = Regex("(?:head|steer|course|heading|bearing)\\s+(?:to\\s+)?([0-3]?[0-9]{1,2})")
        val headingMatch = headingRegex.find(q)
        if (headingMatch != null) {
            val deg = headingMatch.groupValues[1].toIntOrNull()
            if (deg != null && deg in 0..360) {
                val rad = Math.toRadians(deg.toDouble())
                actions.add(BoatAiAction(type = "autopilot_heading", value = rad))
                return BoatAiResult(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_ap_heading), deg), actions)
            }
        }

        // 3. Tacking / Maneuvers
        if (q.contains("tack port") || q.contains("tack to port")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "tack_port"))
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_tack_port), actions)
        }
        if (q.contains("tack star") || q.contains("tack to star")) {
            actions.add(BoatAiAction(type = "autopilot_mode", value = "tack_starboard"))
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_tack_starboard), actions)
        }

        // 4. Digital Switching / Lights
        if (q.contains("nav light") || q.contains("navigation light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.navigationLights.state", value = turnOn))
            return BoatAiResult(app.getString(if (turnOn) R.string.nautical_ai_rep_switch_on else R.string.nautical_ai_rep_switch_off, app.getString(R.string.nautical_ai_lights_nav)), actions)
        }
        if (q.contains("anchor light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.anchorLight.state", value = turnOn))
            return BoatAiResult(app.getString(if (turnOn) R.string.nautical_ai_rep_switch_on else R.string.nautical_ai_rep_switch_off, app.getString(R.string.nautical_ai_lights_anchor)), actions)
        }
        if (q.contains("deck light") || q.contains("cabin light")) {
            val turnOn = !q.contains("off")
            actions.add(BoatAiAction(type = "switch", path = "electrical.switches.cabinLights.state", value = turnOn))
            return BoatAiResult(app.getString(if (turnOn) R.string.nautical_ai_rep_switch_on else R.string.nautical_ai_rep_switch_off, app.getString(R.string.nautical_ai_lights_cabin)), actions)
        }

        // 5. Anchor Controls & Status
        if (q.contains("set anchor") || q.contains("drop anchor")) {
            // Refuse rather than arm a watch at a fabricated (0,0) fix.
            val lat = state.latitude
            val lon = state.longitude
            if (lat == null || lon == null) {
                return BoatAiResult(app.getString(R.string.nautical_ai_rep_no_gps_anchor), actions)
            }
            actions.add(BoatAiAction(type = "anchor", value = mapOf("latitude" to lat, "longitude" to lon, "radius" to 50.0)))
            return BoatAiResult(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_anchor_armed), 50), actions)
        }
        if (q.contains("disarm anchor") || q.contains("raise anchor") || q.contains("clear anchor")) {
            actions.add(BoatAiAction(type = "anchor", path = "disarm", value = "disarm"))
            return BoatAiResult(app.getString(R.string.nautical_ai_rep_anchor_disarmed), actions)
        }
        if (q.contains("anchor")) {
            val anchorObj = state.anchor ?: state.anchorState
            val armed = anchorObj != null && (anchorObj.latitude != null || anchorObj.position != null || anchorObj.state == "set")
            val reply = if (armed) {
                val radius = anchorObj?.radius?.toInt() ?: anchorObj?.maxRadius?.toInt() ?: 50
                String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_anchor_status_armed), radius)
            } else {
                app.getString(R.string.nautical_ai_rep_anchor_status_disarmed)
            }
            return BoatAiResult(reply, actions)
        }

        // 6. Wind Information
        if (q.contains("wind") || q.contains("tws") || q.contains("twa") || q.contains("aws") || q.contains("awa")) {
            // Report only what sensors actually provide; never a fabricated calm.
            val twsKnown = state.windSpeedTrue
            val twdKnown = state.windDirectionTrue
            val awsKnown = state.windSpeedApparent
            val awaKnown = state.windDirectionApparent
            val lines = mutableListOf<String>()
            if (twsKnown != null && twdKnown != null) {
                lines.add(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_wind_true_line), twsKnown * 1.94384, (Math.toDegrees(twdKnown).toInt() + 360) % 360))
            }
            if (awsKnown != null && awaKnown != null) {
                val awaDeg = Math.toDegrees(awaKnown).toInt()
                val side = if (awaDeg >= 0) app.getString(R.string.nautical_starboard_indicator) else app.getString(R.string.nautical_port_indicator)
                lines.add(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_wind_app_line), awsKnown * 1.94384, kotlin.math.abs(awaDeg), side))
            }
            val reply = if (lines.isEmpty()) {
                app.getString(R.string.nautical_ai_rep_wind_offline)
            } else {
                lines.joinToString("\n")
            }
            return BoatAiResult(reply, actions)
        }

        // 7. Depth & Sounding
        if (q.contains("depth") || q.contains("keel") || q.contains("sounder") || q.contains("clearance") || q.contains("draft")) {
            val dbt = state.depthBelowTransducer ?: state.depthBelowKeel
            val dbk = state.depthBelowKeel
            val reply = if (dbk != null) {
                String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_depth_keel), dbk, dbt ?: dbk)
            } else if (dbt != null) {
                String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_depth_transducer), dbt)
            } else {
                app.getString(R.string.nautical_ai_rep_depth_offline)
            }
            return BoatAiResult(reply, actions)
        }

        // 8. Speed, Heading, Course & Polar Performance
        if (q.contains("speed") || q.contains("sog") || q.contains("stw") || q.contains("heading") || q.contains("cog") || q.contains("polar")) {
            // Report only live values; never a fabricated 0.0 kn / 0° reply.
            val lines = mutableListOf<String>()
            val sogKnown = state.speedOverGround
            val stwKnown = state.speedThroughWater
            val naStr = app.getString(R.string.nautical_ai_na)
            if (sogKnown != null || stwKnown != null) {
                val sogStr = if (sogKnown != null) String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_kn_fmt), sogKnown * 1.94384) else naStr
                val stwStr = if (stwKnown != null) String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_kn_fmt), stwKnown * 1.94384) else naStr
                lines.add(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_sog_stw), sogStr, stwStr))
            }
            val hdgKnown = state.headingTrue
            val cogKnown = state.courseOverGroundTrue
            if (hdgKnown != null || cogKnown != null) {
                val hdgStr = if (hdgKnown != null) String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_deg_fmt), (Math.toDegrees(hdgKnown).toInt() + 360) % 360) else naStr
                val cogStr = if (cogKnown != null) String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_deg_fmt), (Math.toDegrees(cogKnown).toInt() + 360) % 360) else naStr
                lines.add(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_hdg_cog), hdgStr, cogStr))
            }
            state.polarSpeedRatio?.let { lines.add(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_polar), (it * 100).toInt())) }
            val reply = if (lines.isEmpty()) {
                app.getString(R.string.nautical_ai_rep_nav_offline)
            } else {
                lines.joinToString("\n")
            }
            return BoatAiResult(reply, actions)
        }

        // 9. Battery & Electrical
        if (q.contains("battery") || q.contains("voltage") || q.contains("power") || q.contains("soc") || q.contains("electrical")) {
            val v = state.batteryVoltage
            val soc = state.batterySoc?.let { (it * 100).toInt() }
            val reply = if (v != null) {
                val socStr = if (soc != null) String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_soc), soc) else ""
                String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_electrical), v, socStr)
            } else {
                app.getString(R.string.nautical_ai_rep_electrical_offline)
            }
            return BoatAiResult(reply, actions)
        }

        // 10. Alarms & Notifications
        if (q.contains("alarm") || q.contains("alert") || q.contains("warning") || q.contains("notif")) {
            if (q.contains("ack") || q.contains("silence") || q.contains("clear")) {
                state.notifications.keys.firstOrNull()?.let { path ->
                    actions.add(BoatAiAction(type = "notification_ack", path = path))
                }
                return BoatAiResult(app.getString(R.string.nautical_ai_rep_alarms_ack), actions)
            }
            val activeNotifs = state.notifications.values.filter { it.state != net.osmand.plus.plugins.nautical.engine.NotificationState.NORMAL }
            val reply = if (activeNotifs.isNotEmpty()) {
                String.format(
                    java.util.Locale.US,
                    app.getString(R.string.nautical_ai_rep_alarms_active),
                    activeNotifs.size,
                    activeNotifs.joinToString("\n") { "• ${it.message} [${it.state}]" }
                )
            } else {
                app.getString(R.string.nautical_ai_rep_alarms_none)
            }
            return BoatAiResult(reply, actions)
        }

        // 11. Autopilot Status Query
        if (q.contains("autopilot") || q.contains("pilot")) {
            val mode = state.autopilotState?.uppercase(java.util.Locale.US) ?: "STANDBY"
            val target = state.targetHeading?.let { String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_ap_target), (Math.toDegrees(it).toInt() + 360) % 360) } ?: ""
            return BoatAiResult(String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_rep_ap_status), mode) + target, actions)
        }

        // 12. General Status / Vessel Overview
        // Report only live values; never a fabricated 0.0 kn / 0 deg / 0.0 kn reply.
        val naStr = app.getString(R.string.nautical_ai_na)
        val sogStr = state.speedOverGround?.let { String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_kn_fmt), it * 1.94384) } ?: naStr
        val hdgStr = state.headingTrue?.let { String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_deg_fmt), (Math.toDegrees(it).toInt() + 360) % 360) } ?: naStr
        val twsStr = state.windSpeedTrue?.let { String.format(java.util.Locale.US, app.getString(R.string.nautical_ai_kn_fmt), it * 1.94384) } ?: naStr
        val reply = String.format(
            java.util.Locale.US,
            app.getString(R.string.nautical_ai_rep_vessel_status),
            sogStr, hdgStr, twsStr, state.autopilotState ?: "STANDBY",
            app.getString(R.string.nautical_ai_rep_vessel_hint)
        )
        return BoatAiResult(reply, actions)
    }
}
