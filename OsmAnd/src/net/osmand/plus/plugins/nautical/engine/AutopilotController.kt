package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AutopilotController(
    private val app: OsmandApplication,
    private val connection: OkHttpSignalKConnection,
    private val client: OkHttpClient,
) {
    private val log = PlatformUtil.getLog(AutopilotController::class.java)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
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

    private fun buildUrl(path: String): String? {
        val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        if (ip.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$ip:$port/signalk/v1/api/vessels/self/navigation/course/$path"
    }

    private fun buildAutopilotUrl(path: String): String? {
        val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        if (ip.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$ip:$port/signalk/v1/api/vessels/self/steering/autopilot/$path"
    }

    fun setAutopilotMode(mode: String) {
        val url = buildAutopilotUrl("state")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": "$mode" }"""
        executePut(url, payload, R.string.nautical_toast_mode_changed, showToast = true)
    }

    fun adjustHeading(deltaDegrees: Double) {
        val currentState = NauticalPlugin.engine?.getCurrentState()
        val currentTarget = currentState?.targetHeading ?: currentState?.headingTrue ?: 0.0
        
        // SignalK uses radians for heading
        val newTargetRad = (currentTarget + Math.toRadians(deltaDegrees)) % (2 * Math.PI)
        val finalTarget = if (newTargetRad < 0) newTargetRad + (2 * Math.PI) else newTargetRad
        
        val url = buildAutopilotUrl("target/headingTrue")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": $finalTarget }"""
        executePut(url, payload, null, showToast = false)
    }

    fun setSeaState(level: Int) {
        val url = buildAutopilotUrl("seaState")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": $level }"""
        executePut(url, payload, null, showToast = true)
    }

    fun executePattern(pattern: String) {
        val url = buildAutopilotUrl("pattern")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": "$pattern" }"""
        executePut(url, payload, null, showToast = true)
    }

    private fun showConnectionError() {
        app.runInUIThread {
            app.showToastMessage(R.string.nautical_autopilot_not_connected)
        }
    }

    private fun executePut(url: String, payload: String, successToastRes: Int?, showToast: Boolean) {
        val request = Request.Builder().url(url).put(payload.toRequestBody(JSON)).build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                log.error("Request failed: ${e.message}")
                if (showToast) {
                    app.runInUIThread {
                        app.showToastMessage(R.string.nautical_toast_conn_failed)
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    log.error("Server error: ${response.code}")
                    if (showToast) {
                        app.runInUIThread {
                            app.showToastMessage(R.string.nautical_toast_server_error, response.code)
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
