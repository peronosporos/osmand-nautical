package net.osmand.plus.plugins.nautical.engine

import android.widget.Toast
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
) {
    private val log = PlatformUtil.getLog(AutopilotController::class.java)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
    }

    @Suppress("unused")
    fun holdHeading(heading: Double) {
        val url = buildUrl("bearingTrue") ?: return
        executePut(url, """{ "value": $heading }""", null, showToast = false)
    }

    private var cachedIp: String = ""
    private var cachedPort: String = ""

    init {
        updateCache()
        // Optional: Add a listener here if you want real-time updates when settings change
    }

    private fun updateCache() {
        cachedIp = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        cachedPort = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
    }

    private fun buildUrl(path: String): String? {
        if (cachedIp.isEmpty()) {
            updateCache()
            if (cachedIp.isEmpty()) return null
        }
        return "http://$cachedIp:$cachedPort/signalk/v1/api/vessels/self/navigation/course/$path"
    }

    fun setAutopilotMode(mode: String) {
        val url = buildUrl("state")
        if (url == null) {
            showConnectionError()
            return
        }
        val payload = """{ "value": "$mode" }"""
        executePut(url, payload, R.string.nautical_toast_mode_changed, showToast = true)
    }

    private fun showConnectionError() {
        app.runInUIThread {
            Toast.makeText(app, app.getString(R.string.nautical_autopilot_not_connected), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(app, app.getString(R.string.nautical_toast_conn_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    log.error("Server error: ${response.code}")
                    if (showToast) {
                        app.runInUIThread {
                            Toast.makeText(app, app.getString(R.string.nautical_toast_server_error, response.code), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (successToastRes != null) {
                    app.runInUIThread {
                        Toast.makeText(app, app.getString(successToastRes), Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        },
        )
    }
}
