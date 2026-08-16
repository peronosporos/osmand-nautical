package net.osmand.plus.plugins.nautical.engine

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class SignalKSessionManager(
    private val app: OsmandApplication,
    private val engineScope: CoroutineScope,
    private val dataBroker: SignalKDataBroker,
    private val resourceManager: SignalKResourceManager,
    private val historyManager: SignalKHistoryManager
) {
    private val log = PlatformUtil.getLog(SignalKSessionManager::class.java)

    var trueSelfContext: String = "vessels.self"
        internal set

    var onConnectionLost: (() -> Unit)? = null
    var onConnectionError: (() -> Unit)? = null
    var onAuthError: (() -> Unit)? = null
    var onConnectionRestored: (() -> Unit)? = null
    var deltaSender: ((String) -> Unit)? = null

    private var watchdogJob: Job? = null
    private var deltaFlushJob: Job? = null
    private val deltaQueue = mutableMapOf<String, Any>()
    private val lastAuthErrorTime = AtomicLong(0)

    @Volatile
    var lastUpdateTimestamp: Long = 0
        internal set

    fun resetWatchdog(onNotifyListeners: (MarineState) -> Unit, onUpdatePulse: () -> Unit, onResetRoute: () -> Unit) {
        lastUpdateTimestamp = TemporalUtils.now()
        if (watchdogJob?.isActive != true) {
            startWatchdog(onNotifyListeners, onUpdatePulse, onResetRoute)
        }
    }

    fun startWatchdog(
        onNotifyListeners: (MarineState) -> Unit,
        onUpdatePulse: () -> Unit,
        onResetRoute: () -> Unit
    ) {
        watchdogJob?.cancel()
        resourceManager.startSync()
        watchdogJob = engineScope.launch {
            var previouslyDisconnected = false
            while (isActive) {
                try {
                    val refreshRate = app.settings.NAUTICAL_TELEMETRY_REFRESH_RATE.get().coerceAtLeast(1)
                    val delayTime = if (historyManager.powerSaveMode) 5000L else (refreshRate * 1000L)
                    delay(delayTime.milliseconds)

                    // Periodic maintenance of historical buffers
                    val now = TemporalUtils.now()

                    val elapsed = now - lastUpdateTimestamp
                    val watchdogTimeoutMs = app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.get() * 1000L
                    if (elapsed > watchdogTimeoutMs) {
                        if (!previouslyDisconnected) {
                            previouslyDisconnected = true
                            dataBroker.updateState { s ->
                                s.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
                            }
                            onResetRoute()
                            onNotifyListeners(dataBroker.marineState.value)
                            log.error("Data timeout (${app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.get()}s)! Dispatching DISCONNECTED state.")
                            onConnectionLost?.invoke()
                        }
                    } else if (elapsed > watchdogTimeoutMs / 2) {
                        previouslyDisconnected = false
                        if (dataBroker.marineState.value.connectionStatus != ConnectionStatus.STALE) {
                            dataBroker.updateState { it.copy(connectionStatus = ConnectionStatus.STALE) }
                            onNotifyListeners(dataBroker.marineState.value)
                        }
                    } else {
                        if (previouslyDisconnected) {
                            previouslyDisconnected = false
                            onConnectionRestored?.invoke()
                        }
                        if (dataBroker.marineState.value.connectionStatus != ConnectionStatus.CONNECTED) {
                            dataBroker.updateState { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
                        }

                        var modified = false
                        val current = dataBroker.marineState.value
                        val nextStalePaths = current.stalePaths.toMutableSet()
                        val staleThreshold = 10000L
                        val timestamps = current.timestamps

                        fun checkStale(path: String): Boolean {
                            val isStale = (now - (timestamps[path] ?: 0L)) > staleThreshold
                            if (isStale) {
                                if (nextStalePaths.add(path)) modified = true
                            } else {
                                if (nextStalePaths.remove(path)) modified = true
                            }
                            return isStale
                        }

                        val sogStale = checkStale("navigation.speedOverGround")
                        val cogStale = checkStale("navigation.courseOverGroundTrue")
                        val hdgStale = checkStale("navigation.headingTrue") || checkStale("navigation.headingMagnetic")
                        val depthStale = checkStale("environment.depth.belowTransducer")
                        val windStale = checkStale("environment.wind.angleApparent") || checkStale("environment.wind.speedTrue") || checkStale("environment.wind.speedApparent")

                        checkStale("navigation.speedThroughWater")
                        checkStale("navigation.crossTrackError")
                        checkStale("navigation.attitude.roll")
                        checkStale("navigation.attitude.pitch")

                        val coreStale = sogStale || cogStale || hdgStale || depthStale || windStale
                        val nextStatus = if (coreStale) ConnectionStatus.STALE else ConnectionStatus.CONNECTED

                        if (modified || current.connectionStatus != nextStatus) {
                            dataBroker.updateState { it.copy(stalePaths = nextStalePaths, connectionStatus = nextStatus) }
                            onNotifyListeners(dataBroker.marineState.value)
                        }
                    }
                    onUpdatePulse()
                } catch (e: Exception) {
                    log.error("Watchdog loop error: ${e.message}")
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    fun handleSelfIdentity(self: String) {
        trueSelfContext = self
        val currentMmsi = dataBroker.marineState.value.vesselMmsi
        if (self.contains("mmsi:") && currentMmsi == null) {
            val extracted = self.substringAfter("mmsi:").trim()
            val mmsi = extracted.toIntOrNull()
            if (mmsi != null) {
                dataBroker.updateState { it.copy(vesselMmsi = mmsi) }
                app.settings.NAUTICAL_AIS_OWN_MMSI.set(mmsi)
            }
        }
    }

    fun resolveSelfIdentity() {
        engineScope.launch(Dispatchers.IO) {
            try {
                withTimeout(5000.milliseconds) {
                    val plugin = NauticalPlugin.getInstance() ?: return@withTimeout
                    val client = plugin.okHttpClient ?: return@withTimeout
                    val ip = app.settings.NAUTICAL_SERVER_IP.get()
                    val port = app.settings.NAUTICAL_SERVER_PORT.get()
                    val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                    val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withTimeout

                    var response = restService.getSelfIdentity()
                    if (!response.isSuccessful) {
                        log.info("SignalK v2 self identity failed, trying v1 fallback...")
                        response = restService.getV1SelfIdentity()
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        val mmsi = (body?.get("mmsi") as? String)?.toIntOrNull()
                        val name = body?.get("name") as? String
                        val uuid = body?.get("uuid") as? String
                        dataBroker.updateState { s ->
                            s.copy(
                                vesselMmsi = mmsi ?: s.vesselMmsi,
                                vesselName = name ?: s.vesselName,
                                vesselUuid = uuid ?: s.vesselUuid
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to resolve self identity (timeout or network): ${e.message}")
            }
        }
    }

    fun isAuthenticated(): Boolean {
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val token = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
        val user = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val pass = app.settings.NAUTICAL_SERVER_PASSWORD.get()

        if (!useSecure) {
            return true
        }

        if (token.isNotBlank()) {
            return validateJwtToken(token)
        }

        if (user.isNotBlank() && pass.isNotBlank()) {
            return true
        }

        return true
    }

    private fun validateJwtToken(token: String): Boolean {
        return try {
            val jwt = JWT.decode(token)
            val expiresAt = jwt.expiresAt
            if (expiresAt != null && expiresAt.before(Date())) {
                log.error("JWT token expired at $expiresAt")
                false
            } else {
                true
            }
        } catch (e: JWTDecodeException) {
            log.error("Failed to decode JWT token: ${e.message}")
            false
        }
    }

    fun triggerAuthError() {
        val now = System.currentTimeMillis()
        val last = lastAuthErrorTime.get()
        if (now - last > 5000) {
            if (lastAuthErrorTime.compareAndSet(last, now)) {
                engineScope.launch(Dispatchers.Main) {
                    onAuthError?.invoke()
                }
            }
        }
    }

    fun sendDelta(path: String, value: Any) {
        synchronized(deltaQueue) {
            deltaQueue[path] = value
            if (deltaFlushJob == null || deltaFlushJob?.isActive == false) {
                deltaFlushJob = engineScope.launch {
                    delay(100.milliseconds) // 100ms batching window
                    flushDeltas()
                }
            }
        }
    }

    fun dispatchCommand(command: String, onSetSwitch: (String, Boolean) -> Unit) {
        if (!isAuthenticated()) {
            log.error("Rejected state mutation command '$command': Session is unauthenticated or insecure!")
            triggerAuthError()
            return
        }

        NauticalLog.auditCommand(command)
        log.debug("Dispatching authenticated command: $command")
        val parts = command.split(":", limit = 2)
        if (parts.size < 2) return

        val cmd = parts[0]
        val rawValue = parts[1]

        val (path, value) = when (cmd) {
            "CALIBRATE_COMPASS" -> "steering.autopilot.actions.calibrateCompass" to (rawValue == "START")
            "TARGET_HEADING" -> "steering.autopilot.target.headingTrue" to (rawValue.toDoubleOrNull() ?: rawValue)
            "STATE" -> "steering.autopilot.state" to rawValue
            "SWITCH" -> {
                val subParts = rawValue.split(":", limit = 2)
                if (subParts.size < 2) return
                val switchPath = subParts[0]
                val state = subParts[1].lowercase(Locale.US).let { it == "true" || it == "on" || it == "1" }
                onSetSwitch(switchPath, state)
                return
            }
            "ANCHOR_STATE" -> "steering.anchor.state" to rawValue
            "ANCHOR_POS" -> {
                try {
                    "steering.anchor.position" to JSONObject(rawValue)
                } catch (_: Exception) {
                    "steering.anchor.position" to rawValue
                }
            }
            "NOTIFICATION" -> {
                val nPath = rawValue.substringBefore(":")
                val nValue = rawValue.substringAfter(":")
                nPath to nValue
            }
            "LOGBOOK_ENTRY" -> "notifications.logbook.entry" to rawValue
            "MEDIA" -> "entertainment.media.state" to rawValue
            else -> return
        }

        synchronized(deltaQueue) {
            deltaQueue[path] = value
            if (deltaFlushJob == null || deltaFlushJob?.isActive == false) {
                deltaFlushJob = engineScope.launch {
                    delay(100.milliseconds)
                    flushDeltas()
                }
            }
        }
    }

    private fun flushDeltas() {
        val toSend: Map<String, Any>
        synchronized(deltaQueue) {
            if (deltaQueue.isEmpty()) return
            toSend = deltaQueue.toMap()
            deltaQueue.clear()
        }

        try {
            val updatesArray = JSONArray()
            val valuesArray = JSONArray()

            toSend.forEach { (path, value) ->
                val entry = JSONObject()
                entry.put("path", path)
                entry.put("value", JSONObject.wrap(value))
                valuesArray.put(entry)
            }

            val update = JSONObject()
            update.put("values", valuesArray)
            updatesArray.put(update)

            val root = JSONObject()
            root.put("updates", updatesArray)

            deltaSender?.invoke(root.toString())
        } catch (e: Exception) {
            log.error("Failed to flush deltas: ${e.message}")
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        onConnectionLost = null
        onConnectionError = null
        onAuthError = null
        onConnectionRestored = null
        deltaSender = null
    }
}
