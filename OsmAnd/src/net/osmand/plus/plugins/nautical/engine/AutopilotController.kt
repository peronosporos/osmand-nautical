package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.settings.enums.HeadingReference
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME

class AutopilotController(
    private val app: OsmandApplication,
    private val connection: OkHttpSignalKConnection,
    private val client: OkHttpClient,
    private val broker: SignalKDataBroker? = null,
) {
    private val log = PlatformUtil.getLog(AutopilotController::class.java)
    private val activeCalls = java.util.concurrent.CopyOnWriteArrayList<Call>()
    private var lastCommandTime = 0L
    private val commandLockMs = 1500L
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reconciliationJob: Job? = null
    private var overrideJob: Job? = null

    private var serverIp: String = ""
    private var serverPort: String = "3000"

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == app.settings.NAUTICAL_SERVER_IP.id) {
            serverIp = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        } else if (key == app.settings.NAUTICAL_SERVER_PORT.id) {
            serverPort = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        }
    }

    init {
        serverIp = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
        serverPort = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
        app.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefChangeListener)

        broker?.let { b ->
            overrideJob = controllerScope.launch {
                b.manualOverrideTriggered.collect {
                    log.warn("Manual Override Detected via Shadow Drive")
                    setAutopilotMode("standby")
                    NauticalPlugin.hudManager?.get()?.showBanner(
                        app.getString(R.string.nautical_manual_override_detected),
                        5000L,
                        isWarning = true,
                    )
                    vibrateShort()
                    try {
                        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app)
                            .dispatchAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.WARNING, loop = false)
                    } catch (_: Exception) {}
                }
            }

            // Task 2: Passive listener for server-managed waypoints
            controllerScope.launch {
                b.marineState.collect { state ->
                    val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
                    if (caps?.hasCourseAutoAdvance == true) {
                        val serverNext = state.serverNextPoint
                        if (serverNext != null) {
                            // Update active route HUD or local state as a passive listener
                            log.debug("Nautical: Autopilot following server next point: $serverNext")
                        }
                    }
                }
            }
            
            // Watch MarineState for reconciliation confirmation
            controllerScope.launch {
                b.marineState.collect { state ->
                    if ((state.pendingCommandPath == null) && (state.pendingTargetHeading == null) && (state.pendingAutopilotState == null)) {
                        // All commands reconciled, cancel the timeout job.
                        // The lock release is handled in the reconciliation job's finally block.
                        if (reconciliationJob?.isActive == true) {
                             reconciliationJob?.cancel()
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private var pendingManualDelta = 0.0
    private var manualAdjustJob: Job? = null

    private var pendingAutomatedDelta = 0.0
    private var automatedAdjustJob: Job? = null
    
    private var lastAppliedWaveBias = 0.0
    val activeWaveBias: Double get() = lastAppliedWaveBias
    private var lastWaveBiasTime = 0L
    private val biasMutex = Mutex()

    private fun vibrateShort() {
        val vibrator = app.getSystemService(Vibrator::class.java)
        if ((vibrator != null) && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private fun showPersistentError(messageRes: Int, code: Int? = null) {
        val msg = if (code != null) app.getString(messageRes) + " (Error $code)" else app.getString(messageRes)
        app.runInUIThread {
            NauticalPlugin.hudManager?.get()?.showBanner(msg, 0L, isWarning = true)
        }
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
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_ACTIVE_ROUTING, app.getString(R.string.nautical_ap_priority_routing))
            
            // Task 1: Mutual Exclusion - Clear existing route before engaging manual waypoint
            NauticalPlugin.engine?.clearRoute()

            val url = buildUrl("activeWaypoint")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            val payload = """{ "value": { "position": { "latitude": $latitude, "longitude": $longitude } } }"""
            executePut(url, payload, R.string.nautical_toast_heading_sent, showToast = true)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        } finally {
            arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_ACTIVE_ROUTING)
        }
    }

    fun processRouteStep() {
        val engine = NauticalPlugin.engine
        val caps = engine?.capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (caps.hasCourseAutoAdvance) return // Offload arrival checks and queuing to server

        if (engine?.isFollowingRoute == true) {
            val next = engine.getNextWaypoint()
            val secondNext = engine.getSecondNextWaypoint()
            val state = engine.getCurrentState()
            
            if (next != null) {
                // Route Smoothing: Start transition to second waypoint early if within 0.1NM
                if ((secondNext != null) && ((state.distanceToWaypoint ?: 1000.0) < 185.0)) {
                    log.info("Route Smoothing: Start dynamic cornering toward next waypoint.")
                    sendActiveWaypoint(secondNext.first, secondNext.second)
                } else {
                    sendActiveWaypoint(next.first, next.second)
                }
            }
        }
    }

    fun stopNavigation() {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB, app.getString(R.string.nautical_ap_priority_emergency))
            
            NauticalPlugin.engine?.clearRoute()
            val url = buildUrl("activeWaypoint")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            executePut(url, """{ "value": null }""", R.string.nautical_toast_stopped, showToast = true, priority = true)
            setAutopilotMode("standby")
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        } finally {
            arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB)
        }
    }

    @Suppress("unused")
    fun holdHeading(heading: Double) {
        val url = buildUrl("bearingTrue") ?: return
        executePut(url, """{ "value": $heading }""", null, showToast = false)
    }

    fun setTargetHeading(degrees: Double) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL, app.getString(R.string.nautical_ap_priority_manual))
            
            val reference = app.settings.NAUTICAL_HEADING_REFERENCE.get()
            val state = NauticalPlugin.engine?.getCurrentState()
            val variation = state?.magneticVariation ?: 0.0
            
            val (path, finalRad) = if (reference == HeadingReference.MAGNETIC) {
                val radMag = Math.toRadians((degrees + 360) % 360)
                "target/headingMagnetic" to radMag
            } else {
                val degreesTrue = degrees + Math.toDegrees(variation)
                val radTrue = Math.toRadians((degreesTrue + 360) % 360)
                "target/headingTrue" to radTrue
            }

            // Virtual Rudder Fallback
            if (state?.rudderAngle == null) {
                val currentHdg = state?.headingTrue ?: finalRad
                var diff = finalRad - currentHdg
                while (diff > PI) diff -= 2 * PI
                while (diff < -PI) diff += 2 * PI
                val simRudder = diff.coerceIn(-Math.toRadians(35.0), Math.toRadians(35.0))
                broker?.updateSimulatedRudder(simRudder)
            }

            val url = buildAutopilotUrl(path)
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL)
                return
            }
            
            val skPath = "steering.autopilot.${path.replace("/", ".")}"
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(targetHeading = finalRad, path = skPath)
            val payload = """{ "value": $finalRad }"""
            executePut(url, payload, null, showToast = false)

            startReconciliation(skPath, NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }

    private fun showArbitrationWarning(e: HelmLockedException? = null, suppressionReason: String? = null) {
        if (suppressionReason == app.getString(R.string.nautical_predictive_steering)) return // Bug #8: Suppress automated warnings
        
        val maneuver = NauticalHelmArbitrator.getInstance(app).getActiveManeuver()
        val displayReason = maneuver ?: e?.let { "Priority ${it.activePriority}" } ?: app.getString(R.string.nautical_target_vessel)
        NauticalPlugin.hudManager?.get()?.showBanner(
            app.getString(R.string.nautical_autopilot_rejected_maneuver, app.getString(R.string.nautical_autopilot_rejected), displayReason),
            5000L,
            isWarning = true,
        )
    }

    private fun startReconciliation(path: String, priority: Int? = null) {
        reconciliationJob?.cancel()
        val timeoutMs = app.settings.NAUTICAL_COMMAND_TIMEOUT_MS.get().toLong()
        reconciliationJob = controllerScope.launch {
            try {
                delay(timeoutMs.milliseconds)
                val engine = NauticalPlugin.engine
                val currentState = engine?.getCurrentState()
                
                val isPending = when (path) {
                    "steering.autopilot.target.headingTrue" -> currentState?.pendingTargetHeading != null
                    "steering.autopilot.state" -> currentState?.pendingAutopilotState != null
                    else -> currentState?.pendingCommandPath != null
                }

                if (isPending) {
                    log.warn("Autopilot command confirmation timeout (${timeoutMs}ms) for $path. Reverting pending state.")
                    engine?.updatePendingCommand(targetHeading = null, mode = null, path = null)
                    showPersistentError(R.string.nautical_toast_conn_failed)
                    NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                        AlarmType.AUTOPILOT_COMMAND_REJECTED,
                        voiceText = app.getString(R.string.nautical_autopilot_rejected),
                    )
                    vibrateShort()
                }
            } finally {
                // LOCK RECONCILIATION FIX: Use NonCancellable to ensure lock release even on early success (cancellation)
                withContext(NonCancellable) {
                    priority?.let { 
                        // Separate standalone tactical locks from maneuver-managed locks (owned by ManeuverEngine).
                        val force = (priority == NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER) && 
                                   (path.contains("actions") || path.contains("williamson") || path.contains("anderson") || path.contains("scharnow"))
                        NauticalHelmArbitrator.getInstance(app).releaseLock(it, force = force) 
                    }
                }
            }
        }
    }

    fun setTargetWindAngle(degrees: Double) {
        val rad = Math.toRadians(degrees)
        val url = buildAutopilotUrl("target/windAngleApparent")
        if (url == null) {
            showPersistentError(R.string.nautical_autopilot_not_connected)
            return
        }
        val payload = """{ "value": $rad }"""
        executePut(url, payload, null, showToast = false)
    }

    fun setRudderAngle(radians: Double) {
        val url = buildAutopilotUrl("target/rudderAngle")
        if (url == null) {
            showPersistentError(R.string.nautical_autopilot_not_connected)
            return
        }
        val payload = """{ "value": $radians }"""
        executePut(url, payload, null, showToast = false)
    }

    fun setTargetTrueWindAngle(degrees: Double) {
        val rad = Math.toRadians(degrees)
        val url = buildAutopilotUrl("target/windAngleTrue")
        if (url == null) {
            showPersistentError(R.string.nautical_autopilot_not_connected)
            return
        }
        val payload = """{ "value": $rad }"""
        executePut(url, payload, null, showToast = false)
    }

    fun buildVesselUrl(path: String): String? {
        val rawIp = (if (serverIp.isNotEmpty()) serverIp else app.settings.NAUTICAL_SERVER_IP.get())?.trim() ?: ""
        if (rawIp.isEmpty()) return null

        val cleanHost = rawIp.substringAfter("://").substringBefore("/").substringBefore(":")
        if (cleanHost.isEmpty()) return null

        val cleanPort = if (rawIp.contains(":") && rawIp.substringAfter("://").contains(":")) {
            rawIp.substringAfter("://").substringAfter(":").substringBefore("/")
        } else {
            (if (serverPort.isNotEmpty()) serverPort else app.settings.NAUTICAL_SERVER_PORT.get())?.trim()?.ifEmpty { "3000" } ?: "3000"
        }

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure || rawIp.startsWith("https://") || rawIp.startsWith("wss://")) "https" else "http"

        return "$protocol://$cleanHost:$cleanPort/signalk/v1/api/vessels/self/$path"
    }

    private fun buildUrl(path: String): String? {
        return buildVesselUrl("navigation/course/$path")
    }

    private fun buildAutopilotUrl(path: String): String? {
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        val vendor = caps?.autopilotVendor
        
        return when (vendor) {
            "signalk-autopilot-garmin" -> buildVesselUrl("plugins/signalk-autopilot-garmin/$path")
            "signalk-autopilot-furuno" -> buildVesselUrl("plugins/signalk-autopilot-furuno/$path")
            "signalk-ac42-autopilot" -> buildVesselUrl("plugins/signalk-ac42-autopilot/$path")
            else -> buildVesselUrl("steering/autopilot/$path")
        }
    }

    val state: kotlinx.coroutines.flow.StateFlow<String> = broker?.autopilotState ?: kotlinx.coroutines.flow.MutableStateFlow("standby")

    fun isEngaged(): Boolean {
        val currentMode = state.value.lowercase(Locale.US)
        return currentMode != "standby" && currentMode != "off"
    }

    val targetHeadingMag: kotlinx.coroutines.flow.StateFlow<Double?> = broker?.autopilotTargetHeadingMag ?: kotlinx.coroutines.flow.MutableStateFlow(null)

    fun engage() = setAutopilotMode("auto")
    fun disengage() = setAutopilotMode("standby")

    fun setAutopilotMode(mode: String) {
        val modeLower = mode.lowercase(Locale.US)
        val priority = if (modeLower == "standby") 
            NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB 
        else NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL
        
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(priority, "Mode Change: $mode")
            
            val now = System.currentTimeMillis()
            if ((now - lastCommandTime) < commandLockMs) {
                log.warn("Autopilot command throttled: $mode")
                vibrateShort()
                return
            }
            lastCommandTime = now

            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            
            when (modeLower) {
                "wind", "twa" -> {
                    if ((state?.windDirectionApparent == null) && (state?.trueWindAngle == null)) {
                        val twa = calculateTwaFallback(state)
                        if (twa == null) {
                            showPersistentError(R.string.nautical_error_no_wind_data)
                            return
                        }
                    }
                }
                "track", "route" -> {
                    if (engine?.isFollowingRoute != true) {
                        showPersistentError(R.string.nautical_error_no_route)
                        return
                    }
                }
                "standby" -> {
                    engine?.updatePendingCommand(targetHeading = null, mode = "standby")
                }
            }

            val url = buildAutopilotUrl("state")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            engine?.updatePendingCommand(mode = mode, path = "steering.autopilot.state")
            val payload = """{ "value": "$mode" }"""
            executePut(url, payload, R.string.nautical_toast_mode_changed, showToast = true, priority = (modeLower == "standby"))

            startReconciliation("steering.autopilot.state", priority)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    fun engageSmart() {
        val engine = NauticalPlugin.engine ?: return
        val currentState = engine.getCurrentState()
        
        if (engine.isFollowingRoute && (currentState.autopilotState.lowercase(Locale.US) == "standby")) {
            setAutopilotMode("track")
        }
    }

    /**
     * Engage Point Lock (Virtual Anchor): maintains current GPS position using autopilot 'track' mode.
     */
    @Suppress("unused")
    fun engagePointLock() {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        
        log.info("Engaging Point Lock (Virtual Anchor) at $lat, $lon")
        engine.loadRoute(listOf(lat to lon))
        setAutopilotMode("track")
        NauticalPlugin.hudManager?.get()?.showBanner(app.getString(R.string.nautical_point_lock_active), 3000L)
        vibrateShort()
    }

    fun executePattern(waypoints: List<Pair<Double, Double>>) {
        val engine = NauticalPlugin.engine ?: return
        engine.loadRoute(waypoints)
        if (engine.getCurrentState().autopilotState.lowercase(Locale.US) == "standby") {
            setAutopilotMode("track")
        }
        app.showToastMessage(R.string.nautical_pattern_steering_active)
        
        // Advanced Synergy: Sync pattern to server resources
        controllerScope.launch {
            engine.resourceManager.uploadActiveRouteToSignalK(app.getString(R.string.nautical_sar_pattern_name))
        }
    }

    fun adjustHeading(deltaDegrees: Double) {
        pendingManualDelta += deltaDegrees
        manualAdjustJob?.cancel()
        manualAdjustJob = controllerScope.launch {
            delay(300.milliseconds)
            val totalDelta = pendingManualDelta
            pendingManualDelta = 0.0
            dispatchAdjustHeading(totalDelta, app.getString(R.string.nautical_ap_priority_manual))
        }
    }

    private fun dispatchAutomatedNudge(deltaDegrees: Double) {
        pendingAutomatedDelta += deltaDegrees
        
        automatedAdjustJob?.cancel()
        automatedAdjustJob = controllerScope.launch {
            // Bug #7: If manual job is active, wait and retry after a short delay
            while (manualAdjustJob?.isActive == true) {
                delay(200)
            }
            
            val totalDelta = pendingAutomatedDelta
            if (abs(totalDelta) > 0.1) {
                pendingAutomatedDelta = 0.0
                dispatchAdjustHeading(totalDelta, app.getString(R.string.nautical_predictive_steering))
            }
        }
    }

    private fun dispatchAdjustHeading(deltaDegrees: Double, reason: String) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL, reason)
            
            val currentState = NauticalPlugin.engine?.getCurrentState()
            val mode = currentState?.autopilotState?.uppercase(Locale.US) ?: "STANDBY"

            // ITEM 8 FIX: Damping for virtual rudder
            if (currentState?.rudderAngle == null) {
                val currentSim = currentState?.simulatedRudderAngle ?: 0.0
                // Apply 30% damping to nudges for smoother virtual wheel movement
                val smoothedDelta = Math.toRadians(deltaDegrees) * 0.7 
                val nextSim = (currentSim + smoothedDelta).coerceIn(-Math.toRadians(35.0), Math.toRadians(35.0))
                broker?.updateSimulatedRudder(nextSim)
            }

            when (mode) {
                "WIND" -> {
                    val currentTarget = currentState?.targetWindAngleApparent ?: currentState?.windDirectionApparent ?: 0.0
                    var newTargetRad = currentTarget + Math.toRadians(deltaDegrees)
                    if (newTargetRad > Math.PI) newTargetRad -= 2 * Math.PI
                    if (newTargetRad < -Math.PI) newTargetRad += 2 * Math.PI
                    setTargetWindAngle(Math.toDegrees(newTargetRad))
                    
                    // ITEM 3 FIX: Add voice feedback for physical nudge buttons
                    if (reason == app.getString(R.string.nautical_ap_priority_manual)) {
                        NauticalPlugin.getInstance()?.speakHeading(Math.toDegrees(newTargetRad).toInt())
                    }
                }
                "TWA" -> {
                    val currentTarget = currentState?.trueWindAngle ?: 0.0
                    var newTargetRad = currentTarget + Math.toRadians(deltaDegrees)
                    if (newTargetRad > Math.PI) newTargetRad -= 2 * Math.PI
                    if (newTargetRad < -Math.PI) newTargetRad += 2 * Math.PI
                    setTargetTrueWindAngle(Math.toDegrees(newTargetRad))
                }
                else -> {
                    val currentTarget = currentState?.targetHeading ?: currentState?.headingTrue ?: 0.0
                    val newTargetRad = (currentTarget + Math.toRadians(deltaDegrees)) % (2 * Math.PI)
                    val finalTarget = if (newTargetRad < 0) newTargetRad + (2 * Math.PI) else newTargetRad
                    setTargetHeading(Math.toDegrees(finalTarget))
                    
                    // ITEM 3 FIX: Add voice feedback for physical nudge buttons
                    if (reason == app.getString(R.string.nautical_ap_priority_manual)) {
                        NauticalPlugin.getInstance()?.speakHeading(Math.toDegrees(finalTarget).toInt())
                    }
                }
            }
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e, reason)
        } finally {
            arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_STANDBY_MANUAL)
        }
    }

    fun setSeaState(level: Int) {
        val url = buildAutopilotUrl("seaState")
        if (url == null) {
            showPersistentError(R.string.nautical_autopilot_not_connected)
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
        if ((now - lastCalculationTime) < 30000) return 
        lastCalculationTime = now

        val engine = NauticalPlugin.engine ?: return
        val rolls = engine.getHistory("${SignalKPaths.NAV_ATTITUDE}.roll").asSequence().filter { (now - it.second) < 60000 }.map { it.first }.toList()
        val pitches = engine.getHistory("${SignalKPaths.NAV_ATTITUDE}.pitch").asSequence().filter { (now - it.second) < 60000 }.map { it.first }.toList()
        
        if (rolls.isEmpty() && pitches.isEmpty()) return
        
        val rollStd = if (rolls.isNotEmpty()) calculateStdDev(rolls) else 0.0
        val pitchStd = if (pitches.isNotEmpty()) calculateStdDev(pitches) else 0.0
        
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
        return sqrt(data.asSequence().map { (it - mean).pow(2) }.average())
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

    // Pypilot Specialized Controls (Phase 9)
    fun setPypilotGain(key: String, value: Double) {
        val url = buildAutopilotUrl("config/$key")
        url?.let { executePut(it, """{ "value": $value }""", null, showToast = false) }
    }

    fun setPypilotProfile(profile: String) {
        val url = buildAutopilotUrl("config/profile")
        url?.let { executePut(it, """{ "value": "$profile" }""", R.string.nautical_command_sent, showToast = true) }
    }

    fun startPypilotCalibration(type: String) {
        val url = buildAutopilotUrl("calibration/$type/start")
        url?.let { executePut(it, """{ "value": true }""", R.string.nautical_command_sent, showToast = true) }
    }

    fun stopPypilotCalibration(type: String) {
        val url = buildAutopilotUrl("calibration/$type/stop")
        url?.let { executePut(it, """{ "value": false }""", R.string.nautical_command_sent, showToast = true) }
    }

    /**
     * Reconciles local UI state with the physical autopilot actuator.
     * Prevents desynchronization after app restart or process death.
     */
    fun reconcileState() {
        val url = buildAutopilotUrl("state") ?: return
        controllerScope.launch(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url).get()
            
            val token = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
            if (!token.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            } else {
                val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
                val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
                if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
                }
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = org.json.JSONObject(body)
                        val hardwareMode = json.optString("value", "standby").lowercase(java.util.Locale.US)
                        log.info("Nautical: Autopilot reconciliation: physical state is $hardwareMode")
                        
                        broker?.updateAutopilotState(hardwareMode)
                    }
                }
                response.close()
            } catch (e: Exception) {
                log.error("Autopilot reconciliation failed: ${e.message}")
            }
        }
    }

    fun tack(direction: String, manageLock: Boolean = true) = tack(direction.lowercase(Locale.US) == "port", manageLock)
    fun gybe(direction: String, manageLock: Boolean = true) = gybe(direction.lowercase(Locale.US) == "port", manageLock)

    fun tack(port: Boolean, manageLock: Boolean = true) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            if (manageLock) {
                arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, app.getString(R.string.nautical_ap_priority_maneuver))
            }
            
            val url = buildAutopilotUrl("actions/tack")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                if (manageLock) arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER)
                return
            }
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(path = "steering.autopilot.actions.tack")
            
            val value = if (port) "port" else "starboard"
            val payload = """{ "value": "$value" }"""
            executePut(url, payload, R.string.nautical_command_sent, showToast = true)
            
            startReconciliation("steering.autopilot.actions.tack", if (manageLock) NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER else null)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }

    fun gybe(port: Boolean, manageLock: Boolean = true) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            if (manageLock) {
                arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, app.getString(R.string.nautical_ap_priority_maneuver))
            }
            
            val url = buildAutopilotUrl("actions/gybe")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                if (manageLock) arbitrator.releaseLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER)
                return
            }
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(path = "steering.autopilot.actions.gybe")

            val value = if (port) "port" else "starboard"
            val payload = """{ "value": "$value" }"""
            executePut(url, payload, R.string.nautical_command_sent, showToast = true)

            startReconciliation("steering.autopilot.actions.gybe", if (manageLock) NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER else null)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    fun shunt(manageWorkflow: Boolean = true) {
        if (manageWorkflow) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("shunting")
            mm?.execute()
            return
        }

        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, app.getString(R.string.nautical_shunting))
            
            val engine = NauticalPlugin.engine ?: return
            val currentState = engine.getCurrentState()
            
            engine.setShunted(!currentState.isShunted)
            
            val url = buildAutopilotUrl("actions/shunt")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            engine.updatePendingCommand(path = "steering.autopilot.actions.shunt")
            val payload = """{ "value": "true" }"""
            executePut(url, payload, R.string.nautical_command_sent, showToast = true)

            startReconciliation("steering.autopilot.actions.shunt", NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    fun williamsonTurn(port: Boolean = true) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB, "MOB Williamson Turn")
            
            val url = buildAutopilotUrl("actions/williamsonTurn")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(path = "steering.autopilot.actions.williamsonTurn")
            val value = if (port) "port" else "starboard"
            val payload = """{ "value": "$value" }"""
            executePut(url, payload, R.string.nautical_mob_autopilot_active, showToast = true, priority = true)

            startReconciliation("steering.autopilot.actions.williamsonTurn", NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    fun andersonTurn(port: Boolean = true) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB, "MOB Anderson Turn")
            
            val url = buildAutopilotUrl("actions/andersonTurn")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(path = "steering.autopilot.actions.andersonTurn")
            val value = if (port) "port" else "starboard"
            val payload = """{ "value": "$value" }"""
            executePut(url, payload, R.string.nautical_mob_anderson_active, showToast = true, priority = true)

            startReconciliation("steering.autopilot.actions.andersonTurn", NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    /**
     * Predictive Steering (Wave Anticipation): Adjusts heading bias based on GRIB wave data.
     */
    fun applyWaveBias(state: MarineState) {
        if (!app.settings.NAUTICAL_PREDICTIVE_STEERING.get()) return
        
        val mode = state.autopilotState.lowercase(Locale.US)
        if (mode == "standby" || mode == "off") {
            if (lastAppliedWaveBias != 0.0) {
                updateWaveBias(0.0)
            }
            return
        }
        
        val now = System.currentTimeMillis()
        // Item 16: Throttle to 1Hz
        if (now - lastWaveBiasTime < 1000) return
        lastWaveBiasTime = now

        val grib = SailingDependencyContainer.gribRepository?.gridData
        if (grib == null) {
            if (lastAppliedWaveBias != 0.0) updateWaveBias(0.0)
            return
        }
        
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        
        // Bug #3: GRIB TTL & Range Check
        val firstStep = grib.timeSteps.minByOrNull { it.timestamp }
        val latestStep = grib.timeSteps.maxByOrNull { it.timestamp }
        
        if (firstStep == null || latestStep == null || now < firstStep.timestamp || now > (latestStep.timestamp + 3600 * 1000L)) {
            if (lastAppliedWaveBias != 0.0) updateWaveBias(0.0)
            return
        }

        val wave = net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine(grib).getWaveData(lat, lon, now)
        if (wave == null) {
            if (lastAppliedWaveBias != 0.0) updateWaveBias(0.0)
            return
        }
        
        // Item 9: Adjustable Threshold
        val threshold = app.settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.get()
        
        // Bug #6: Motion Correlation (Roll Std Dev) - Default to FALSE if missing
        val rollHistory = NauticalPlugin.engine?.getHistory("${SignalKPaths.NAV_ATTITUDE}.roll") ?: emptyList()
        val recentRolls = rollHistory.filter { (now - it.second) < 30000 }.map { it.first }
        
        val significantMotion = if (recentRolls.size > 5) {
            calculateStdDev(recentRolls) > Math.toRadians(1.0)
        } else false 

        if (wave.height < threshold || !significantMotion) {
             updateWaveBias(0.0)
             return
        }

        // Item 6: Refine wave direction logic & alignment check
        var waveDirection = wave.direction
        val liveWind = state.windDirectionTrue
        if (liveWind != null) {
            val gribWind = net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine(grib).getWindVector(lat, lon, now)
            if (gribWind != null) {
                 val diff = abs(net.osmand.util.MapUtils.degreesDiff(gribWind.direction, Math.toDegrees(liveWind)))
                 if (diff > 120) { // Slightly tighter threshold for convention reversal
                     waveDirection = (waveDirection + 180.0) % 360.0
                     log.warn("Predictive Steering: GRIB convention mismatch detected. Reversing wave direction.")
                 }
                 
                 // Bug #4: Relax misalignment threshold to 135°
                 val finalDiff = abs(net.osmand.util.MapUtils.degreesDiff(waveDirection, Math.toDegrees(liveWind)))
                 if (finalDiff > 135.0) {
                     log.warn("Predictive Steering: Wave/Wind misalignment too high ($finalDiff°). Disabling nudge.")
                     updateWaveBias(0.0)
                     return
                 }
            }
        }

        val hdg = state.headingTrue ?: return
        val waveDirRad = Math.toRadians(waveDirection)
        
        var relativeWave = waveDirRad - hdg
        while (relativeWave > PI) relativeWave -= 2 * PI
        while (relativeWave < -PI) relativeWave += 2 * PI
        
        val absRelWave = abs(relativeWave)
        
        // Item 7: Angle Weighting (Max bias at beam sea, 0 at head/following)
        val weight = sin(absRelWave).coerceAtLeast(0.0)
        
        // Item 6: Subtle base nudge (2.0 instead of 5.0)
        val sensitivity = app.settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get() / 100.0
        val baseNudge = 2.0 * sensitivity
        
        // Item 10: Linearity (Scale by wave height relative to 2m)
        val heightFactor = (wave.height / 2.0).coerceIn(0.5, 2.5)
        
        val desiredBias = (if (relativeWave > 0) 1.0 else -1.0) * baseNudge * weight * heightFactor
        
        updateWaveBias(desiredBias)
    }

    private fun updateWaveBias(targetBias: Double) {
        controllerScope.launch {
            biasMutex.withLock {
                val delta = targetBias - lastAppliedWaveBias
                // Item 2 fix: Only nudge the DIFF. If diff < 0.5 deg, ignore chatter.
                if (abs(delta) >= 0.5) {
                    lastAppliedWaveBias = targetBias
                    log.info("Predictive Steering: Applying ${String.format(Locale.US, "%.1f", delta)}° delta for ${String.format(Locale.US, "%.1f", targetBias)}° total bias.")
                    dispatchAutomatedNudge(delta)
                }
            }
        }
    }

    fun scharnowTurn(port: Boolean = true) {
        val arbitrator = NauticalHelmArbitrator.getInstance(app)
        try {
            arbitrator.acquireLock(NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB, "MOB Scharnow Turn")
            
            val url = buildAutopilotUrl("actions/scharnowTurn")
            if (url == null) {
                showPersistentError(R.string.nautical_autopilot_not_connected)
                return
            }
            val engine = NauticalPlugin.engine
            engine?.updatePendingCommand(path = "steering.autopilot.actions.scharnowTurn")
            val value = if (port) "port" else "starboard"
            val payload = """{ "value": "$value" }"""
            executePut(url, payload, R.string.nautical_mob_scharnow_active, showToast = true, priority = true)

            startReconciliation("steering.autopilot.actions.scharnowTurn", NauticalHelmArbitrator.PRIORITY_EMERGENCY_MOB)
        } catch (e: HelmLockedException) {
            showArbitrationWarning(e)
        }
    }



    fun setEngineState(instance: String, started: Boolean) {
        val stateValue = if (started) "started" else "stopped"
        val url = buildPropulsionUrl(instance)
        if (url == null) {
            showPersistentError(R.string.nautical_autopilot_not_connected)
            return
        }
        val engine = NauticalPlugin.engine
        val path = "propulsion.$instance.state"
        engine?.updatePendingCommand(path = path)
        
        val payload = """{ "value": "$stateValue" }"""
        executePut(url, payload, R.string.nautical_command_sent, showToast = true)

        startReconciliation(path, null)
    }

    private fun buildPropulsionUrl(instance: String): String? {
        if (serverIp.isEmpty()) return null

        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "https" else "http"

        return "$protocol://$serverIp:$serverPort/signalk/v1/api/vessels/self/propulsion/$instance/state"
    }

    fun isWindSafeForManeuver(tacking: Boolean): Boolean {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return false
        val awa = state.windDirectionApparent ?: return false
        val awaDeg = Math.toDegrees(awa)
        
        return if (tacking) {
            abs(awaDeg) < 90.0
        } else {
            abs(awaDeg) > 90.0
        }
    }

    private fun calculateTwaFallback(state: MarineState?): Double? {
        if (state == null) return null
        val awa = state.windDirectionApparent ?: return null
        val aws = state.windSpeedApparent ?: return null
        val stw = if (state.isStwUnreliable) state.speedOverGround ?: 0.0 else state.speedThroughWater ?: 0.0
        val leeway = state.leeway ?: 0.0

        val ax = aws * sin(awa)
        val ay = aws * cos(awa)

        val bx = stw * sin(leeway)
        val by = stw * cos(leeway)

        val tx = ax - bx
        val ty = ay - by

        return atan2(tx, ty)
    }

    fun stop() {
        app.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        controllerScope.cancel()
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        
        // ITEM 5 FIX: Release any lingering tactical locks on stop
        NauticalHelmArbitrator.getInstance(app).releaseLock(NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, force = true)
    }

    fun executePut(url: String, payload: String, successToastRes: Int?, showToast: Boolean, priority: Boolean = false, retryCount: Int = 0) {
        val rawIp = (if (serverIp.isNotEmpty()) serverIp else app.settings.NAUTICAL_SERVER_IP.get())?.trim() ?: ""
        val cleanHost = rawIp.substringAfter("://").substringBefore("/").substringBefore(":")
        val isLocal = cleanHost.startsWith("127.") || 
                     cleanHost.equals("localhost", ignoreCase = true) ||
                     cleanHost.startsWith("192.168.") ||
                     cleanHost.startsWith("10.") ||
                     cleanHost.endsWith(".local", ignoreCase = true) ||
                     cleanHost.endsWith(".lan", ignoreCase = true) ||
                     (cleanHost.startsWith("172.") && cleanHost.split(".").getOrNull(1)?.toIntOrNull() in 16..31) ||
                     app.settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get() ||
                     !app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()

        if (!url.startsWith("https") && !isLocal) {
            log.warn("Nautical: Refusing to send command over insecure HTTP to public IP: $url")
            if (showToast) {
                app.runInUIThread {
                    showPersistentError(R.string.nautical_error_insecure_connection)
                }
            }
            return
        }

        val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody(JSON))
        
        if (priority) {
            requestBuilder.tag("PRIORITY")
        }

        val token = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else {
            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }
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

                    // ITEM 20: Retry logic for priority commands
                    if (priority && retryCount < 3) {
                        log.info("Nautical: Retrying priority command ($retryCount)...")
                        controllerScope.launch {
                            delay(1000)
                            executePut(url, payload, successToastRes, showToast, priority, retryCount + 1)
                        }
                        return
                    }

                    if (showToast) {
                        app.runInUIThread {
                            showPersistentError(R.string.nautical_toast_conn_failed)
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
                        
                        // ITEM 20: Retry logic for priority commands on server errors too (5xx)
                        if (priority && response.code >= 500 && retryCount < 3) {
                             response.close()
                             controllerScope.launch {
                                 delay(1000)
                                 executePut(url, payload, successToastRes, showToast, priority, retryCount + 1)
                             }
                             return
                        }

                        if (showToast) {
                            app.runInUIThread {
                                if ((response.code == 401) || (response.code == 403)) {
                                    NauticalPlugin.engine?.triggerAuthError()
                                } else {
                                    showPersistentError(R.string.nautical_toast_server_error, response.code)
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
