package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKPutBody
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

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
    private var cachedRestService: SignalKRestService? = null
    private var lastUrl: String? = null

    private fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val url = "$protocol://$ip:$port"

        if (url == lastUrl && cachedRestService != null) return cachedRestService

        lastUrl = url
        cachedRestService = SignalKRestService.create(url, client)
        return cachedRestService
    }

    fun setSwitchState(path: String, state: Boolean) {
        // Safety Guard for Windlass (Item 20 fix)
        if (path.contains("windlass", ignoreCase = true) && state) {
            val isEngineRunning = dataBroker.marineState.value.isEngineRunning
            if (!isEngineRunning) {
                app.runInUIThread {
                    app.showToastMessage(net.osmand.plus.R.string.nautical_windlass_engine_guard)
                }
                return
            }
        }
        val fullPath = normalizePath(path)
        val putPath = "$fullPath.state"
        sendCommand(putPath, state)
    }

    fun setDimmerValue(path: String, value: Double) {
        val fullPath = normalizePath(path)
        val putPath = "$fullPath.dimmingLevel"
        sendCommand(putPath, value)
    }

    private fun normalizePath(path: String): String {
        return when {
            path.startsWith("electrical.switches.") -> path
            path.startsWith("shelly.") -> "electrical.switches.$path"
            path.startsWith("empirbus.") -> "electrical.switches.$path"
            path.contains(".") -> path
            else -> "electrical.switches.$path"
        }
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

    fun setSailActive(sailId: String, active: Boolean) {
        sendCommand("sails.inventory.$sailId.active", active)
        sendCommand("steering.sails.inventory.$sailId.active", active)
        sendCommand("steering.sails.active.$sailId", active)
    }

    fun setSailReefs(sailId: String?, reefs: Int) {
        if (sailId != null) {
            sendCommand("sails.inventory.$sailId.reefs", reefs)
            sendCommand("steering.sails.inventory.$sailId.reefs", reefs)
        }
        sendCommand("sails.reefs", reefs)
        sendCommand("steering.sails.reefs", reefs)
    }

    fun setSailPlan(planName: String) {
        sendCommand("sails.activeSailPlan", planName)
        sendCommand("steering.sails.activeSailPlan", planName)
    }

    private fun sendCommand(path: String, value: Any) {
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        if (!useSecure && (ip != "127.0.0.1" && ip != "localhost")) {
            log.error("Rejected state mutation command '$path': Secure connection (HTTPS) is required.")
            app.runInUIThread {
                app.showToastMessage(net.osmand.plus.R.string.nautical_error_insecure_connection)
            }
            return
        }

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

                // Watchdog to clear pending if server never responds
                val currentJob = coroutineContext[Job]
                scope.launch {
                    delay(5000L.milliseconds)
                    if (pendingCommands[path] == currentJob) {
                        clearPending(path)
                    }
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
                    app.runInUIThread {
                        if (response.code() == 401 || response.code() == 403) {
                            app.showToastMessage(net.osmand.plus.R.string.nautical_auth_failed)
                        } else {
                            app.showToastMessage(net.osmand.plus.R.string.nautical_toast_server_error, response.code())
                        }
                    }
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
