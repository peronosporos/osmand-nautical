package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import android.widget.Toast
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class AutopilotController(private val app: OsmandApplication, private val connection: OkHttpSignalKConnection) {
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun isConnected(): Boolean {
        return connection.isConnected()
    }

    fun sendActiveWaypoint(latitude: Double, longitude: Double) {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) {
            Log.e("NauticalAutopilot", "Cannot send waypoint: Server IP is missing.")
            return
        }

        val url = "http://$ip:$port/signalk/v1/api/vessels/self/navigation/course/activeWaypoint"

        val payload = """
            {
              "value": {
                "position": {
                  "latitude": $latitude,
                  "longitude": $longitude
                }
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .put(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NauticalAutopilot", "Failed: ${e.message}")
                app.runInUIThread {
                    Toast.makeText(app, app.getString(R.string.nautical_toast_conn_failed), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    app.runInUIThread {
                        Toast.makeText(app, app.getString(R.string.nautical_toast_heading_sent), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    app.runInUIThread {
                        Toast.makeText(app, app.getString(R.string.nautical_toast_server_error, response.code), Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }

    fun stopNavigation() {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) return

        val url = "http://$ip:$port/signalk/v1/api/vessels/self/navigation/course/activeWaypoint"
        val payload = """{ "value": null }"""

        val request = Request.Builder()
            .url(url)
            .put(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NauticalAutopilot", "Stop failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun holdHeading(heading: Double) {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        if (ip.isNullOrEmpty()) return

        val url = "http://$ip:$port/signalk/v1/api/vessels/self/navigation/course/bearingTrue"
        val payload = """{ "value": $heading }"""

        val request = Request.Builder()
            .url(url)
            .put(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                app.runInUIThread {
                    Toast.makeText(app, app.getString(R.string.nautical_toast_conn_failed), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("NauticalAutopilot", "Hold Heading Error: ${response.code} ${response.body?.string()}")
                }
                response.close()
            }
        })
    }
}