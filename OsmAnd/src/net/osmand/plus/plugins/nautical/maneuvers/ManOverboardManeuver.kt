package net.osmand.plus.plugins.nautical.maneuvers

import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.*

class ManOverboardManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    private val log = PlatformUtil.getLog(ManOverboardManeuver::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mobLat: Double = 0.0
    private var mobLon: Double = 0.0
    private val handler = Handler(Looper.getMainLooper())
    private val audioRunnable = object : Runnable {
        override fun run() {
            if (currentState == ManeuverStateMachine.State.EXECUTING) {
                announceGuidance()
                handler.postDelayed(this, 10000)
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        // High priority: No safety checks
        return true
    }

    fun activate(lat: Double, lon: Double) {
        mobLat = lat
        mobLon = lon
        
        // Immediate autopilot disengage for safety
        val apm = NauticalPlugin.autopilotManager
        if (apm?.state?.value != "standby") {
            apm?.disengage()
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention("Man Overboard. Autopilot disengaged."))
            }
        }

        // Broadcast MOB via network concurrently with local alarms
        broadcastMobNetwork(lat, lon)

        transitionToExecuting()
    }

    private fun broadcastMobNetwork(lat: Double, lon: Double) {
        scope.launch {
            val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
            val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
            if (ip.isEmpty()) return@launch

            val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
            val protocol = if (useSecure) "https" else "http"
            val url = "$protocol://$ip:$port/signalk/v1/api/vessels/self/navigation/manOverboard"

            val timestamp = System.currentTimeMillis()
            val payload = """{ "value": { "position": { "latitude": $lat, "longitude": $lon }, "timestamp": "$timestamp" } }"""

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody(JSON))

            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    log.error("SignalK MOB HTTP request failed: ${response.code}. Falling back to delta notification.")
                    sendDeltaFallback(lat, lon)
                }
                response.close()
            } catch (e: IOException) {
                log.error("SignalK MOB network error: ${e.message}. Falling back to delta notification.")
                sendDeltaFallback(lat, lon)
            }
        }
    }

    private fun sendDeltaFallback(lat: Double, lon: Double) {
        val engine = NauticalPlugin.engine ?: return
        val deltaPayload = """{
            "updates": [{
                "values": [{
                    "path": "notifications.security.mob",
                    "value": { "state": "emergency", "message": "Man Overboard at $lat, $lon" }
                }]
            }]
        }"""
        engine.deltaSender?.invoke(deltaPayload)
        log.info("Emitted SignalK delta notification path 'notifications.security.mob'.")
    }

    override fun transitionToExecuting() {
        // High priority thread handling
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        }.start()

        super.transitionToExecuting()
        
        handler.post(audioRunnable)
    }

    private fun announceGuidance() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val curLat = state.latitude ?: return
        val curLon = state.longitude ?: return
        
        val dist = calculateDistance(curLat, curLon, mobLat, mobLon)
        val bearing = calculateBearing(curLat, curLon, mobLat, mobLon)
        
        app.player?.let { player ->
            val msg = app.getString(R.string.nautical_mob_target_bearing, dist.toInt(), bearing.toInt())
            player.playCommands(player.newCommandBuilder().attention(msg))
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        return sqrt(((lat2Rad - lat1Rad) * 6371000).pow(2.0) + ((lon2Rad - lon1Rad) * 6371000 * cos(lat1Rad)).pow(2.0))
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        val y = sin(lon2Rad - lon1Rad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(lon2Rad - lon1Rad)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    override fun transitionToAborted(reason: String?) {
        handler.removeCallbacks(audioRunnable)
        scope.cancel()
        super.transitionToAborted(reason)
    }
}
