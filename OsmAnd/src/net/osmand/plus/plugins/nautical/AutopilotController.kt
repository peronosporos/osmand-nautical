package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import net.osmand.plus.OsmandApplication
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class AutopilotController(private val app: OsmandApplication) {
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun sendActiveWaypoint(latitude: Double, longitude: Double) {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) {
            Log.e("NauticalAutopilot", "Cannot send waypoint: Server IP is missing.")
            return
        }

        // Standard SignalK path for setting the active waypoint
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
                Log.e("NauticalAutopilot", "Failed to send waypoint: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.i("NauticalAutopilot", "Waypoint sent successfully. HTTP Code: ${response.code}")
                response.close()
            }
        })
    }
}