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
            triggerLog("Waypoint Reached")
        }
    }

    fun start() {
        signalKEngine.registerListener(marineStateListener)
        signalKEngine.addRouteStepListener(routeStepListener)
        
        loggingJob?.cancel()
        loggingJob = scope.launch {
            while (isActive) {
                val intervalHours: Int = app.settings.NAUTICAL_LOGBOOK_INTERVAL.get() ?: 0
                if (intervalHours > 0) {
                    delay((intervalHours.toLong() * 3600 * 1000L).milliseconds)
                    triggerLog("Periodic")
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
        scope.cancel()
    }

    fun onAppBackgrounded() {
        val navigating = signalKEngine.isFollowingRoute
        val anchorActive = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0
        if (!navigating && !anchorActive) {
            log.info("LogbookEngine: Suspending background logging.")
            loggingJob?.cancel()
            loggingJob = null
        }
    }

    fun onAppForegrounded() {
        if (loggingJob == null) {
            log.info("LogbookEngine: Resuming background logging.")
            start()
        }
    }

    private suspend fun checkTacticalEvents(state: MarineState) {
        // 1. Engine Start/Stop
        val rpm = state.engineRpm ?: 0.0
        val running = rpm > 50.0 // Threshold for running
        if (lastEngineRunning != null && lastEngineRunning != running) {
            triggerLog(if (running) "Engine Started" else "Engine Stopped")
        }
        lastEngineRunning = running

        // 2. Sail Plan Change
        val sailPlan = app.settings.NAUTICAL_ACTIVE_SAIL_PLAN.get() ?: ""
        if (lastSailPlan != null && lastSailPlan != sailPlan) {
            triggerLog("Sail Plan Changed: $sailPlan")
        }
        lastSailPlan = sailPlan

        // 3. Tack/Gybe Detection with Deadband (TASK-022)
        val twa = state.trueWindAngle
        if (twa != null) {
            val twaSign = sign(twa)
            
            if (lastTwaSign != null && lastTwaSign != twaSign && (state.speedOverGround ?: 0.0) > 1.0) {
                // Deadband: Require at least 15 degrees of total change or crossing a threshold 
                // to avoid yaw-induced tack logs deep downwind.
                val prevTwa = lastTwaValue ?: 0.0
                if (abs(twa - prevTwa) > Math.toRadians(15.0)) {
                    triggerLog(if (twaSign > 0) "Tacked to Starboard" else "Tacked to Port")
                    lastTwaSign = twaSign
                }
            } else if (lastTwaSign == null) {
                lastTwaSign = twaSign
            }
            lastTwaValue = twa
        }
    }

    suspend fun triggerLog(reason: String = "Periodic") {
        val state = signalKEngine.getCurrentState()
        val perf = performanceRepository.livePerformanceData.value
        
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val now = TemporalUtils.now()
        
        // Jitter & Bloat Prevention: Add 5-minute debounce for event-based logs (TASK-021)
        if (reason != "Periodic" && reason != "Manual") {
            if (now - lastLoggedTime < 300000L) { // 5 minutes
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
            timestamp = TemporalUtils.now(),
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
            notes = reason.takeIf { it != "Periodic" && it != "Manual" } ?: ""
        )

        repository.insertEntry(entry)
        
        // Sync to Signal K
        signalKEngine.resourceManager.pushNoteToServer(lat, lon, "Log Entry", entry.notes)

        lastLoggedLat = lat
        lastLoggedLon = lon
        lastLoggedTime = now
    }
}
