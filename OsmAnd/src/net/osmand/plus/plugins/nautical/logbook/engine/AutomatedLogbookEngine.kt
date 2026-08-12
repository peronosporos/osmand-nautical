package net.osmand.plus.plugins.nautical.logbook.engine

import kotlinx.coroutines.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.shared.util.KMapUtils
import kotlin.math.sign
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class AutomatedLogbookEngine(
    private val app: OsmandApplication,
    private val repository: MarineLogbookRepository,
    private val signalKEngine: SignalKEngine,
    private val performanceRepository: SailingPerformanceRepository
) {
    private val log = net.osmand.PlatformUtil.getLog(AutomatedLogbookEngine::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loggingJob: Job? = null
    
    private var lastLoggedLat: Double? = null
    private var lastLoggedLon: Double? = null
    private var lastLoggedTime: Long = 0

    private var lastEngineRunning: Boolean? = null
    private var lastTwaSign: Double? = null
    private var lastTwaValue: Double? = null
    private var lastSailPlan: String? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        scope.launch {
            checkTacticalEvents(state)
        }
    }

    private val routeStepListener: () -> Unit = {
        scope.launch {
            triggerLog(app.getString(net.osmand.plus.R.string.nautical_log_waypoint_reached))
        }
    }

    fun start() {
        signalKEngine.registerListener(marineStateListener)
        signalKEngine.addRouteStepListener(routeStepListener)
        
        restartLoggingJob()
    }

    private fun restartLoggingJob() {
        loggingJob?.cancel()
        loggingJob = scope.launch {
            while (isActive) {
                val intervalHours = app.settings.NAUTICAL_LOGBOOK_INTERVAL.get() ?: 0
                if (intervalHours > 0) {
                    // Check every minute if interval changed or it's time to log
                    val now = TemporalUtils.now()
                    if (now - lastLoggedTime >= intervalHours.toLong() * 3600 * 1000L) {
                        triggerLog("Periodic")
                    }
                    delay(60000L.milliseconds) 
                } else {
                    delay(60000L.milliseconds) // Check again in a minute if it was disabled
                }
            }
        }
    }

    fun stop() {
        signalKEngine.unregisterListener(marineStateListener)
        signalKEngine.removeRouteStepListener(routeStepListener)
        
        loggingJob?.cancel()
        loggingJob = null
        scope.cancel()
    }

    fun onAppBackgrounded() {
        // Item 16: Don't suspend if periodic logging is enabled. 
        // Mariners want dock logging for remote monitoring.
        val intervalHours = app.settings.NAUTICAL_LOGBOOK_INTERVAL.get() ?: 0
        if (intervalHours == 0 && !signalKEngine.isFollowingRoute && app.settings.NAUTICAL_ANCHOR_LAT.get() == 0.0) {
            log.info("LogbookEngine: Suspending background logging.")
            loggingJob?.cancel()
            loggingJob = null
        }
    }

    fun onAppForegrounded() {
        if (loggingJob == null) {
            log.info("LogbookEngine: Resuming background logging.")
            restartLoggingJob()
        }
    }

    private suspend fun checkTacticalEvents(state: MarineState) {
        // 1. Engine Start/Stop
        val rpm = state.engineRpm ?: 0.0
        val running = rpm > 50.0 // Threshold for running
        if (lastEngineRunning != null && lastEngineRunning != running) {
            triggerLog(if (running) app.getString(net.osmand.plus.R.string.nautical_log_engine_started) else app.getString(net.osmand.plus.R.string.nautical_log_engine_stopped))
        }
        lastEngineRunning = running

        // 2. Sail Plan Change
        val sailPlan = app.settings.NAUTICAL_ACTIVE_SAIL_PLAN.get() ?: ""
        if (lastSailPlan != null && lastSailPlan != sailPlan) {
            triggerLog(app.getString(net.osmand.plus.R.string.nautical_log_sail_plan_changed, sailPlan))
        }
        lastSailPlan = sailPlan

        // 3. Tack/Gybe Detection with Deadband (TASK-022)
        val twa = state.trueWindAngle
        if (twa != null) {
            val twaSign = sign(twa)
            
            // Item 4: Handle TWA == 0.0 (sign is 0) to avoid double logs at head-to-wind
            if (lastTwaSign != null && lastTwaSign != 0.0 && twaSign != 0.0 && lastTwaSign != twaSign && (state.speedOverGround ?: 0.0) > 1.0) {
                // Deadband: Require at least 15 degrees of total change or crossing a threshold 
                // to avoid yaw-induced tack logs deep downwind.
                val prevTwa = lastTwaValue ?: 0.0
                if (abs(twa - prevTwa) > Math.toRadians(15.0)) {
                    triggerLog(if (twaSign > 0) app.getString(net.osmand.plus.R.string.nautical_log_tacked_starboard) else app.getString(net.osmand.plus.R.string.nautical_log_tacked_port))
                    lastTwaSign = twaSign
                }
            } else if (lastTwaSign == null || (lastTwaSign == 0.0 && twaSign != 0.0)) {
                lastTwaSign = twaSign
            }
            lastTwaValue = twa
        }
    }

    suspend fun triggerLog(reason: String? = null) {
        val actualReason = reason ?: app.getString(net.osmand.plus.R.string.nautical_log_periodic)
        val state = signalKEngine.getCurrentState()
        val perf = performanceRepository.livePerformanceData.value
        
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val now = TemporalUtils.now()
        
        // Jitter & Bloat Prevention: Add 1-minute debounce for event-based logs (TASK-021)
        if (actualReason != app.getString(net.osmand.plus.R.string.nautical_log_periodic) && actualReason != app.getString(net.osmand.plus.R.string.nautical_log_manual)) {
            if (now - lastLoggedTime < 60000L) { // 1 minute
                 // Still check distance if it's a movement log, but strictly
                 val lLat = lastLoggedLat
                 val lLon = lastLoggedLon
                 if (lLat != null && lLon != null) {
                     val distMeters = KMapUtils.getDistance(lLat, lLon, lat, lon)
                     if (distMeters < 50.0) return // Increased threshold for debounced events
                 }
            }
        }

        val entry = LogbookEntry(
            timestamp = now,
            latitude = lat,
            longitude = lon,
            sog = state.speedOverGround,
            cog = state.courseOverGroundTrue,
            heading = state.headingTrue,
            tws = state.windSpeedTrue ?: perf.windSpeedTrue,
            twa = state.trueWindAngle ?: perf.windAngleTrueWater,
            twd = state.windDirectionTrue,
            pressure = state.outsidePressure,
            waterDepth = state.depthBelowTransducer,
            waterTemp = state.waterTemperature,
            batteryVoltage = state.batteryVoltage,
            engineHours = state.engineRunTime?.let { it / 3600.0 },
            sailPlan = app.settings.NAUTICAL_ACTIVE_SAIL_PLAN.get() ?: "",
            notes = actualReason.takeIf { it != app.getString(net.osmand.plus.R.string.nautical_log_periodic) && it != app.getString(net.osmand.plus.R.string.nautical_log_manual) } ?: ""
        )

        repository.insertEntry(entry)
        
        // Sync to Signal K
        val pushTitle = if (entry.sailPlan.isNotEmpty()) "Marine Log: ${entry.sailPlan}" else "Marine Log Entry"
        try {
            // Item 6: Better error logging for push failures
            signalKEngine.resourceManager.pushNoteToServer(lat, lon, pushTitle, entry.notes)
        } catch (e: Exception) {
            log.error("Failed to push logbook entry to Signal K server", e)
        }

        lastLoggedLat = lat
        lastLoggedLon = lon
        lastLoggedTime = now
    }
}
