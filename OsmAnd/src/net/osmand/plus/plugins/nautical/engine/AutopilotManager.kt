package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Interfaces with standard SignalK Autopilot API for singlehanded maneuvering.
 */
class AutopilotManager(
    private val app: OsmandApplication,
    private val client: OkHttpClient,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(AutopilotManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    val state: StateFlow<String> = dataBroker.autopilotState
    val targetHeadingMag: StateFlow<Double?> = dataBroker.autopilotTargetHeadingMag

    fun engage() = setAutopilotMode("auto")
    fun disengage() = setAutopilotMode("standby")

    /**
     * Reconciles local UI state with the physical autopilot actuator.
     * Prevents desynchronization after app restart or process death.
     */
    fun reconcileState() {
        val url = buildAutopilotUrl("state") ?: return
        scope.launch {
            val requestBuilder = Request.Builder().url(url).get()
            
            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val hardwareMode = json.optString("value", "standby").lowercase(java.util.Locale.US)
                        log.info("Nautical: Autopilot reconciliation: physical state is $hardwareMode")
                        
                        dataBroker.updateAutopilotState(hardwareMode)
                    }
                }
                response.close()
            } catch (e: Exception) {
                log.error("Autopilot reconciliation failed: ${e.message}")
            }
        }
    }

    fun tack(direction: String) {
        val url = buildAutopilotUrl("actions/tack") ?: return
        val payload = """{ "value": "$direction" }"""
        executePut(url, payload, "Tacking $direction")
    }

    fun gybe(direction: String) {
        val url = buildAutopilotUrl("actions/gybe") ?: return
        val payload = """{ "value": "$direction" }"""
        executePut(url, payload, "Gybing $direction")
    }

    fun setTargetHeading(angleDegrees: Double) {
        val rad = Math.toRadians(angleDegrees)
        val url = buildAutopilotUrl("target/headingMagnetic") ?: return
        NauticalPlugin.engine?.updatePendingCommand(targetHeading = rad)
        val payload = """{ "value": $rad }"""
        executePut(url, payload, "Setting heading to ${angleDegrees.toInt()} degrees")
    }

    fun setTargetWindAngle(angleDegrees: Double) {
        val rad = Math.toRadians(angleDegrees)
        val url = buildAutopilotUrl("target/windAngleApparent") ?: return
        val payload = """{ "value": $rad }"""
        executePut(url, payload, "Setting wind angle to ${angleDegrees.toInt()} degrees")
    }

    private fun setAutopilotMode(mode: String) {
        val url = buildAutopilotUrl("state") ?: return
        NauticalPlugin.engine?.updatePendingCommand(mode = mode)
        val payload = """{ "value": "$mode" }"""
        executePut(url, payload, "Changing mode to $mode")
    }

    private fun buildAutopilotUrl(path: String): String? {
        val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        if (ip.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$ip:$port/signalk/v1/api/vessels/self/steering/autopilot/$path"
    }

    private fun executePut(url: String, payload: String, actionName: String) {
        scope.launch {
            val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody(JSON))
            
            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    log.error("Autopilot command failed: ${response.code} ${response.message}")
                    warnSailor("Autopilot command failed. Take the helm.")
                }
                response.close()
            } catch (e: IOException) {
                log.error("Autopilot network error during $actionName: ${e.message}")
                warnSailor("Autopilot offline. Take the helm.")
            }
        }
    }

    private fun warnSailor(message: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(message))
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
