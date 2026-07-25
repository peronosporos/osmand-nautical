package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import kotlin.math.*

class AutopilotController(
    private val app: OsmandApplication,
    private val connection: OkHttpSignalKConnection,
    private val client: OkHttpClient,
) {
    private val log = PlatformUtil.getLog(AutopilotController::class.java)
    private val activeCalls = java.util.concurrent.CopyOnWriteArrayList<Call>()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    fun pushAllSettings() {
        val s = app.settings
        setRudderGain(s.NAUTICAL_RUDDER_GAIN.get().toDouble())
        setCounterRudder(s.NAUTICAL_COUNTER_RUDDER.get().toDouble())
        setAutoTrim(s.NAUTICAL_AUTO_TRIM.get().toDouble())
        setFilterSensitivity(s.NAUTICAL_FILTER_SENSITIVITY.get().toDouble())
        setRudderLimit(s.NAUTICAL_RUDDER_LIMIT.get().toDouble())
        setOffCourseAlarm(s.NAUTICAL_OFF_COURSE_ALARM.get().toDouble())
        log.info("Pushed all autopilot settings to hardware")
    }

    fun isConnected(): Boolean = connection.isConnected()

    fun sendActiveWaypoint(latitude: Double, longitude: Double) {
        val url = buildUrl("activeWaypoint")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": { "position": { "latitude": $latitude, "longitude": $longitude } } }"""
        executePut(url, payload, R.string.nautical_toast_heading_sent, showToast = true)
    }

    fun processRouteStep() {
        val engine = NauticalPlugin.engine
        if (engine?.isFollowingRoute == true) {
            engine.getNextWaypoint()?.let {
                sendActiveWaypoint(it.first, it.second)
            }
        }
    }

    fun stopNavigation() {
        NauticalPlugin.engine?.clearRoute()
        val url = buildUrl("activeWaypoint")
        if (url == null) {
            showConnectionError()
            return
        }
        executePut(url, """{ "value": null }""", R.string.nautical_toast_stopped, showToast = true)
        setAutopilotMode("standby")
    }

    @Suppress("unused")
    fun holdHeading(heading: Double) {
        val url = buildUrl("bearingTrue") ?: return
        executePut(url, """{ "value": $heading }""", null, showToast = false)
    }

    fun setTargetHeading(degrees: Double) {
        val rad = Math.toRadians(degrees)
        val url = buildAutopilotUrl("target/headingTrue")
        if (url == null) {
            showConnectionError()
            return
        }
        NauticalPlugin.engine?.updatePendingCommand(targetHeading = rad)
        val payload = """{ "value": $rad }"""
        executePut(url, payload, null, showToast = false)
    }

    fun setTargetWindAngle(degrees: Double) {
        val rad = Math.toRadians(degrees)
        val url = buildAutopilotUrl("target/windAngleApparent")
        if (url == null) {
            showConnectionError()
            return
        }
        // For wind angle we don't have a clear reconciliation yet, but could be added
        val payload = """{ "value": $rad }"""
        executePut(url, payload, null, showToast = false)
    }

    fun buildVesselUrl(path: String): String? {
        val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        if (ip.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$ip:$port/signalk/v1/api/vessels/self/$path"
    }

    private fun buildUrl(path: String): String? {
        return buildVesselUrl("navigation/course/$path")
    }

    private fun buildAutopilotUrl(path: String): String? {
        return buildVesselUrl("steering/autopilot/$path")
    }

    fun setAutopilotMode(mode: String) {
        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        
        val modeLower = mode.lowercase(Locale.US)
        when (modeLower) {
            "wind" -> {
                if (state?.windDirectionApparent == null) {
                    app.runInUIThread { app.showToastMessage(R.string.nautical_error_no_wind_data) }
                    return
                }
            }
            "track", "route" -> {
                if (engine?.isFollowingRoute != true) {
                    app.runInUIThread { app.showToastMessage(R.string.nautical_error_no_route) }
                    return
                }
            }
            "standby" -> {
                engine?.updatePendingCommand(targetHeading = null, mode = "standby")
            }
        }

        val url = buildAutopilotUrl("state")
        if (url == null) {
            showConnectionError()
            return
        }
        engine?.updatePendingCommand(mode = mode)
        val payload = """{ "value": "$mode" }"""
        executePut(url, payload, R.string.nautical_toast_mode_changed, showToast = true, priority = (modeLower == "standby"))
    }

    fun engageSmart() {
        val engine = NauticalPlugin.engine ?: return
        val currentState = engine.getCurrentState() ?: return
        
        if (engine.isFollowingRoute && (currentState.autopilotState.lowercase(Locale.US) == "standby")) {
            setAutopilotMode("track")
        }
    }

    fun adjustHeading(deltaDegrees: Double) {
        val currentState = NauticalPlugin.engine?.getCurrentState()
        val mode = currentState?.autopilotState?.uppercase(Locale.US) ?: "STANDBY"

        if (mode == "WIND") {
            val currentTarget = currentState?.targetWindAngleApparent ?: currentState?.windDirectionApparent ?: 0.0
            var newTargetRad = currentTarget + Math.toRadians(deltaDegrees)
            // Keep within -PI to PI for AWA
            if (newTargetRad > Math.PI) newTargetRad -= 2 * Math.PI
            if (newTargetRad < -Math.PI) newTargetRad += 2 * Math.PI
            setTargetWindAngle(Math.toDegrees(newTargetRad))
        } else {
            val currentTarget = currentState?.targetHeading ?: currentState?.headingTrue ?: 0.0
            val newTargetRad = (currentTarget + Math.toRadians(deltaDegrees)) % (2 * Math.PI)
            val finalTarget = if (newTargetRad < 0) newTargetRad + (2 * Math.PI) else newTargetRad
            setTargetHeading(Math.toDegrees(finalTarget))
        }
    }

    fun setSeaState(level: Int) {
        val url = buildAutopilotUrl("seaState")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": $level }"""
        executePut(url, payload, null, showToast = false)
    }

    private var lastAutoSeaState = -1
    private var lastCalculationTime = 0L

    fun updateAutoSeaState(state: MarineState) {
        if (!state.isAutoSeaStateEnabled) return
        
        val now = System.currentTimeMillis()
        if ((now - lastCalculationTime) < 30000) return // Throttle to 30s
        lastCalculationTime = now

        val engine = NauticalPlugin.engine ?: return
        val rolls = engine.getRollHistory().filter { now - it.second < 60000 }.map { it.first }
        val pitches = engine.getPitchHistory().filter { now - it.second < 60000 }.map { it.first }
        
        if (rolls.isEmpty() && pitches.isEmpty()) return
        
        val rollStd = if (rolls.isNotEmpty()) calculateStdDev(rolls) else 0.0
        val pitchStd = if (pitches.isNotEmpty()) calculateStdDev(pitches) else 0.0
        
        // Intensity in degrees
        val intensity = Math.toDegrees((rollStd + pitchStd) / 2.0)
        
        val newLevel = when {
            intensity < 1.0 -> 1
            intensity < 3.0 -> 2
            intensity < 6.0 -> 3
            intensity < 10.0 -> 4
            else -> 5
        }
        
        if (newLevel != lastAutoSeaState) {
            setSeaState(newLevel)
            lastAutoSeaState = newLevel
            log.info("Auto Sea State: Intensity $intensity°, setting level $newLevel")
        }
    }

    private fun calculateStdDev(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val mean = data.average()
        val standardDeviation = sqrt(data.map { (it - mean).pow(2) }.average())
        return standardDeviation
    }

    fun setRudderGain(gain: Double) {
        val url = buildAutopilotUrl("rudderGain")
        url?.let { executePut(it, """{ "value": $gain }""", null, showToast = false) }
    }

    fun setCounterRudder(value: Double) {
        val url = buildAutopilotUrl("counterRudder")
        url?.let { executePut(it, """{ "value": $value }""", null, showToast = false) }
    }

    fun setAutoTrim(value: Double) {
        val url = buildAutopilotUrl("autoTrim")
        url?.let { executePut(it, """{ "value": $value }""", null, showToast = false) }
    }

    fun setFilterSensitivity(value: Double) {
        val url = buildAutopilotUrl("filterSensitivity")
        url?.let { executePut(it, """{ "value": $value }""", null, showToast = false) }
    }

    fun setRudderLimit(degrees: Double) {
        val url = buildAutopilotUrl("rudderLimit")
        url?.let { executePut(it, """{ "value": ${Math.toRadians(degrees)} }""", null, showToast = false) }
    }

    fun setOffCourseAlarm(degrees: Double) {
        val url = buildAutopilotUrl("offCourseAlarm")
        url?.let { executePut(it, """{ "value": ${Math.toRadians(degrees)} }""", null, showToast = false) }
    }


    fun tack(port: Boolean) {
        val url = buildAutopilotUrl("actions/tack")
        if (url == null) {
            showConnectionError()
            return
        }
        val value = if (port) "port" else "starboard"
        val payload = """{ "value": "$value" }"""
        executePut(url, payload, R.string.nautical_command_sent, showToast = true)
    }

    fun gybe(port: Boolean) {
        val url = buildAutopilotUrl("actions/gybe")
        if (url == null) {
            showConnectionError()
            return
        }
        val value = if (port) "port" else "starboard"
        val payload = """{ "value": "$value" }"""
        executePut(url, payload, R.string.nautical_command_sent, showToast = true)
    }

    fun shunt() {
        val url = buildAutopilotUrl("actions/shunt")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": "true" }"""
        executePut(url, payload, R.string.nautical_command_sent, showToast = true)
    }

    fun setEngineState(instance: String, started: Boolean) {
        val stateValue = if (started) "started" else "stopped"
        val url = buildPropulsionUrl(instance, "state")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": "$stateValue" }"""
        executePut(url, payload, R.string.nautical_command_sent, showToast = true)
    }

    private fun buildPropulsionUrl(instance: String, path: String): String? {
        val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        if (ip.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$ip:$port/signalk/v1/api/vessels/self/propulsion/$instance/$path"
    }

    private fun showConnectionError() {
        app.runInUIThread {
            app.showToastMessage(R.string.nautical_autopilot_not_connected)
        }
    }

    fun isWindSafeForManeuver(tacking: Boolean): Boolean {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return false
        val awa = state.windDirectionApparent ?: return false
        val awaDeg = Math.toDegrees(awa)
        
        return if (tacking) {
            // Tacking: should be sailing upwind (roughly < 90 deg AWA)
            abs(awaDeg) < 90.0
        } else {
            // Gybing: should be sailing downwind (roughly > 90 deg AWA)
            abs(awaDeg) > 90.0
        }
    }

    fun stop() {
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
    }

    fun executePut(url: String, payload: String, successToastRes: Int?, showToast: Boolean, priority: Boolean = false) {
        val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody(JSON))
        
        if (priority) {
            requestBuilder.tag("PRIORITY")
        }

        val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
        }

        val request = requestBuilder.build()
        val call = client.newCall(request)
        activeCalls.add(call)

        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activeCalls.remove(call)
                    if (call.isCanceled()) return
                    log.error("Request failed: ${e.message}")
                    if (showToast) {
                        app.runInUIThread {
                            app.showToastMessage(R.string.nautical_toast_conn_failed)
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    activeCalls.remove(call)
                    if (call.isCanceled()) {
                        response.close()
                        return
                    }
                    if (!response.isSuccessful) {
                        log.error("Server error: ${response.code}")
                        if (showToast) {
                            app.runInUIThread {
                                if ((response.code == 401) || (response.code == 403)) {
                                    app.showToastMessage(R.string.nautical_auth_failed)
                                } else {
                                    app.showToastMessage(R.string.nautical_toast_server_error, response.code)
                                }
                            }
                        }
                    } else if (successToastRes != null) {
                        app.runInUIThread {
                            app.showToastMessage(successToastRes)
                        }
                    }
                    response.close()
                }
            },
        )
    }
}
