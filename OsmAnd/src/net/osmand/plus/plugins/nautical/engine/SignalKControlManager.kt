package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKPutBody
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages bi-directional control (PUT requests) to Signal K.
 * Handles command reconciliation and timeouts.
 */
class SignalKControlManager(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker,
    private val scope: CoroutineScope,
) {
    private val log = PlatformUtil.getLog(SignalKControlManager::class.java)
    private val pendingCommands = ConcurrentHashMap<String, Job>()

    private fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        return SignalKRestService.create("$protocol://$ip:$port", client)
    }

    fun setSwitchState(path: String, state: Boolean) {
        val fullPath = when {
            path.startsWith("electrical.switches.") -> path
            path.startsWith("shelly.") -> "electrical.switches.$path"
            path.startsWith("empirbus.") -> "electrical.switches.$path"
            else -> "electrical.switches.$path"
        }
        val putPath = "$fullPath.state"
        sendCommand(putPath, state)
    }

    fun setDimmerValue(path: String, value: Double) {
        val fullPath = if (path.startsWith("electrical.switches.")) path else "electrical.switches.$path"
        val putPath = "$fullPath.dimmingLevel"
        sendCommand(putPath, value)
    }

    fun setAutopilotMode(mode: String) {
        sendCommand("steering.autopilot.state", mode.lowercase())
    }

    fun setChargerMode(instance: String, mode: String) {
        sendCommand("electrical.chargers.$instance.mode", mode)
    }

    fun setInverterMode(instance: String, mode: String) {
        sendCommand("electrical.inverters.$instance.mode", mode)
    }

    fun setAutopilotTargetHeading(radians: Double) {
        sendCommand("steering.autopilot.target.headingTrue", radians)
    }

    fun setAutopilotTargetHeadingMagnetic(radians: Double) {
        sendCommand("steering.autopilot.target.headingMagnetic", radians)
    }

    fun acknowledgeNotification(path: String) {
        // Signal K standard for acknowledging is to PUT to the path/state with 'acknowledged'
        // Or more commonly a specific action. Here we follow standard PUT value.
        sendCommand("$path.state", "normal") 
    }

    fun setAnchor(lat: Double, lon: Double, radius: Double) {
        val posPayload = mapOf("latitude" to lat, "longitude" to lon)
        sendCommand("navigation.anchor.position", posPayload)
        sendCommand("navigation.anchor.maxDrift", radius)
    }

    fun disarmAnchor() {
        // Many Signal K anchor plugins use 'state' to arm/disarm
        sendCommand("navigation.anchor.state", "disarmed")
    }

    fun sendMediaCommand(command: String, value: Any? = null) {
        val path = when (command) {
            "PLAY" -> "entertainment/device/fusion/state"
            "PAUSE" -> "entertainment/device/fusion/state"
            "SKIP_NEXT" -> "entertainment/device/fusion/skipNext"
            "SKIP_PREV" -> "entertainment/device/fusion/skipPrev"
            "VOLUME" -> "entertainment/device/fusion/volume"
            else -> return
        }
        val putValue = if (command == "PLAY") "playing" else if (command == "PAUSE") "paused" else value ?: true
        sendCommand(path.replace("/", "."), putValue)
    }

    fun updateVesselDesign(path: String, value: Any) {
        // Supported paths: design.length.overall, design.beam, design.airDraft, design.displacement
        sendCommand(path, value)
    }

    private fun sendCommand(path: String, value: Any) {

        pendingCommands[path]?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val service = getRestService() ?: return@launch
                
                // Update local state to 'pending'
                dataBroker.updateState { s ->
                    val timestamps = s.timestamps.toMutableMap()
                    timestamps["pending.$path"] = System.currentTimeMillis()
                    s.copy(timestamps = timestamps)
                }

                val response = service.putValue(path.replace(".", "/"), SignalKPutBody(value))
                if (response.isSuccessful) {
                    val body = response.body()
                    log.info("SignalK Command Success: $path -> $value (State: ${body?.state})")
                    if (body?.state == "COMPLETED") {
                        log.debug("Command $path completed immediately.")
                    } else if (body?.state == "PENDING") {
                        log.debug("Command $path is pending on server.")
                    }
                } else {
                    log.error("SignalK Command Failed: $path. Code: ${response.code()}")
                    clearPending(path)
                }
            } catch (e: Exception) {
                log.error("Error sending SignalK command: ${e.message}")
                clearPending(path)
            } finally {
                pendingCommands.remove(path)
            }
        }
        pendingCommands[path] = job
    }

    private fun clearPending(path: String) {
        dataBroker.updateState { s ->
            val timestamps = s.timestamps.toMutableMap()
            timestamps.remove("pending.$path")
            s.copy(timestamps = timestamps)
        }
    }
}
