package net.osmand.plus.plugins.nautical.dr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.dr.engine.*
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository

/**
 * UI State for the Dead Reckoning system.
 */
data class DrUiState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: FixSource = FixSource.GPS,
    val drDurationSeconds: Long = 0,
    val statusResId: Int = R.string.dr_status_gps,
    val lastValidGpsLat: Double? = null,
    val lastValidGpsLon: Double? = null,
)

/**
 * ViewModel for the Dead Reckoning fallback system.
 * Monitors GPS health and transitions to estimated projections if signal is lost.
 */
class DeadReckoningViewModel(
    private val app: net.osmand.plus.OsmandApplication,
    private val repository: SailingPerformanceRepository
) : ViewModel() {

    private val settings = app.settings
    private val _uiState = MutableStateFlow(DrUiState())
    val uiState: StateFlow<DrUiState> = _uiState.asStateFlow()

    private var lastValidGpsFix: DrFix? = null
    private var drStartTime: Long = 0
    private var watchdogJob: Job? = null
    private var projectionJob: Job? = null

    init {
        restoreState()
        startWatchdog()
    }

    private fun restoreState() {
        val startTime = settings.NAUTICAL_DR_START_TIME.get()
        if (startTime != 0L && (System.currentTimeMillis() - startTime < 300000)) { // 5 mins
            val lat = settings.NAUTICAL_DR_LAST_LAT.get()
            val lon = settings.NAUTICAL_DR_LAST_LON.get()
            if (lat != 0.0 && lon != 0.0) {
                drStartTime = startTime
                lastValidGpsFix = DrFix(lat, lon, startTime, FixSource.DEAD_RECKONING)
                startDeadReckoning(resuming = true)
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob = repository.livePerformanceData
            .onEach { data ->
                val now = System.currentTimeMillis()
                val lat = data.latitude
                val lon = data.longitude
                
                if ((lat != null) && (lon != null)) {
                    lastValidGpsFix = DrFix(
                        latitude = lat,
                        longitude = lon,
                        timestamp = data.timestamp,
                        source = FixSource.GPS
                    )
                    
                    if (_uiState.value.source == FixSource.DEAD_RECKONING) {
                        stopDeadReckoning()
                    }
                    
                    _uiState.update { 
                        it.copy(
                            latitude = data.latitude,
                            longitude = data.longitude,
                            source = FixSource.GPS,
                            drDurationSeconds = 0,
                            statusResId = R.string.dr_status_gps,
                            lastValidGpsLat = null,
                            lastValidGpsLon = null
                        )
                    }
                } else {
                    // Check for staleness if GPS was active
                    if (_uiState.value.source == FixSource.GPS && 
                        lastValidGpsFix != null && 
                        (now - lastValidGpsFix!!.timestamp > 3000)) {
                        startDeadReckoning()
                    }
                }
            }
            .launchIn(viewModelScope)
            
        // Periodic check in case no data flows at all
        viewModelScope.launch {
            while (isActive) {
                delay(1.seconds)
                val now = System.currentTimeMillis()
                val lastFix = lastValidGpsFix
                if (_uiState.value.source == FixSource.GPS && 
                    lastFix != null && 
                    (now - lastFix.timestamp > 3000)) {
                    startDeadReckoning()
                }
            }
        }
    }

    private fun startDeadReckoning(resuming: Boolean = false) {
        if (projectionJob?.isActive == true && !resuming) return
        
        val initialFix = lastValidGpsFix
        if (!resuming) {
            drStartTime = System.currentTimeMillis()
            settings.NAUTICAL_DR_START_TIME.set(drStartTime)
        }
        
        projectionJob?.cancel()
        projectionJob = viewModelScope.launch {
            while (isActive) {
                val lastFix = lastValidGpsFix ?: break
                val telemetry = repository.livePerformanceData.value
                
                val vector = DrVector(
                    speedThroughWater = telemetry.speedThroughWater ?: 0.0,
                    headingDegrees = telemetry.headingTrue ?: 0.0,
                    leewayDegrees = settings.NAUTICAL_LEEWAY_COEFFICIENT.get().toDouble()
                )
                
                val now = System.currentTimeMillis()
                
                val estimatedFix = DrProjectionEngine.projectPosition(
                    lastFix = lastFix,
                    vector = vector,
                    elapsedTimeSeconds = 1 // Project 1 second forward from the current "lastFix"
                )
                
                // Update our working fix for the next iteration
                lastValidGpsFix = estimatedFix.copy(timestamp = now)
                
                // Persist latest estimated position
                settings.NAUTICAL_DR_LAST_LAT.set(estimatedFix.latitude)
                settings.NAUTICAL_DR_LAST_LON.set(estimatedFix.longitude)
                
                _uiState.update { 
                    it.copy(
                        latitude = estimatedFix.latitude,
                        longitude = estimatedFix.longitude,
                        source = FixSource.DEAD_RECKONING,
                        drDurationSeconds = (now - drStartTime) / 1000,
                        statusResId = R.string.dr_status_fallback,
                        lastValidGpsLat = initialFix?.latitude,
                        lastValidGpsLon = initialFix?.longitude
                    )
                }
                
                delay(1.seconds)
            }
        }
    }

    private fun stopDeadReckoning() {
        projectionJob?.cancel()
        projectionJob = null
        settings.NAUTICAL_DR_START_TIME.set(0L)
        settings.NAUTICAL_DR_LAST_LAT.set(0.0)
        settings.NAUTICAL_DR_LAST_LON.set(0.0)
    }

    override fun onCleared() {
        super.onCleared()
        watchdogJob?.cancel()
        stopDeadReckoning()
    }
}
