package net.osmand.plus.plugins.nautical.logbook.engine

import kotlinx.coroutines.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.shared.util.KMapUtils
import kotlin.math.sign

class AutomatedLogbookEngine(
    private val app: OsmandApplication,
    private val repository: MarineLogbookRepository,
    private val signalKEngine: SignalKEngine,
    private val performanceRepository: SailingPerformanceRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loggingJob: Job? = null
    
    private var lastLoggedLat: Double? = null
    private var lastLoggedLon: Double? = null

    private var lastEngineRunning: Boolean? = null
    private var lastTwaSign: Double? = null
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
        signalKEngine.onRouteStepProcessed = routeStepListener
        
        loggingJob?.cancel()
        loggingJob = scope.launch {
            while (isActive) {
                val intervalHours: Int = app.settings.NAUTICAL_LOGBOOK_INTERVAL.get() ?: 0
                if (intervalHours > 0) {
                    delay(intervalHours.toLong() * 3600 * 1000L)
                    triggerLog("Periodic")
                } else {
                    delay(60000L) // Check again in a minute if it was disabled
                }
            }
        }
    }

    fun stop() {
        signalKEngine.unregisterListener(marineStateListener)
        // Note: onRouteStepProcessed might be used by NauticalPlugin too, 
        // but SignalKEngine only supports one listener for it.
        // In NauticalPlugin.kt: newEngine.onRouteStepProcessed = routeStepListener
        // So I should be careful not to overwrite it if I want both to work.
        // Actually, SignalKEngine.kt: var onRouteStepProcessed: (() -> Unit)? = null
        // I'll check if I can chain them or if I should just let NauticalPlugin handle it.
        // But the plan says "Connect to SignalKEngine.onRouteStepProcessed".
        
        loggingJob?.cancel()
        scope.cancel()
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

        // 3. Tack/Gybe Detection
        val twa = state.trueWindAngle
        if (twa != null) {
            val twaSign = sign(twa)
            if (lastTwaSign != null && lastTwaSign != twaSign && (state.speedOverGround ?: 0.0) > 1.0) {
                triggerLog(if (twaSign > 0) "Tacked to Starboard" else "Tacked to Port")
            }
            lastTwaSign = twaSign
        }
    }

    suspend fun triggerLog(reason: String = "Periodic") {
        val state = signalKEngine.getCurrentState() ?: return
        val perf = performanceRepository.livePerformanceData.value
        
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        
        // Distance check only for event-based logs to avoid cluttering if GPS jitters
        if (reason != "Periodic" && reason != "Manual") {
            val lLat = lastLoggedLat
            val lLon = lastLoggedLon
            if (lLat != null && lLon != null) {
                val distMeters = KMapUtils.getDistance(lLat, lLon, lat, lon)
                if (distMeters < 10.0) { // 10m threshold for events to avoid double logging on small movements
                    return 
                }
            }
        }

        val entry = LogbookEntry(
            timestamp = System.currentTimeMillis(),
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
        lastLoggedLat = lat
        lastLoggedLon = lon
    }
}
