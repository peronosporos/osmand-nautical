package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import java.util.Locale
import kotlin.math.*

/**
 * EnvironmentalFilterService provides:
 * 1. 3D Motion Correction: Subscribes to roll/pitch/yaw (attitude) and apparent wind data,
 *    mathematically subtracting masthead swing to create a stable "Motion-Corrected Wind" data flow.
 * 2. Gust Response Logic: Monitors corrected wind speed/angle for sudden spikes (>15% increase in <3s).
 *    When a gust is detected while autopilot is in 'wind' mode, temporarily instructs AutopilotController
 *    to bear away 5-10 degrees to keep the boat flat, returning to course once the gust passes.
 */
class EnvironmentalFilterService(
    private val dataBroker: SignalKDataBroker,
    private val autopilotController: AutopilotController,
) {
    private val log = PlatformUtil.getLog(EnvironmentalFilterService::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _correctedWindAngleApparent = MutableStateFlow<Double?>(null)
    val correctedWindAngleApparent: StateFlow<Double?> = _correctedWindAngleApparent.asStateFlow()

    private val _correctedWindSpeedApparent = MutableStateFlow<Double?>(null)
    val correctedWindSpeedApparent: StateFlow<Double?> = _correctedWindSpeedApparent.asStateFlow()

    private val _isGustActive = MutableStateFlow(value = false)
    val isGustActive: StateFlow<Boolean> = _isGustActive.asStateFlow()

    // History buffer for gust detection (timestamp, wind speed)
    private data class WindSample(val timestamp: Long, val speed: Double)
    private val windSamples = ArrayDeque<WindSample>()
    private val gustWindowMillis = 3000L // 3 seconds
    private val gustThresholdPercent = 0.15 // 15% increase

    // Bear away tracking
    private var bearAwayDegrees: Double = 0.0
    private var isBearAwayApplied = false

    init {
        startListening()
    }

    private fun startListening() {
        scope.launch {
            // Combine or collect flows from dataBroker
            launch {
                dataBroker.windAngleApparent.collect { rawAngle ->
                    try {
                        val state = dataBroker.marineState.value
                        var aws = state.windSpeedApparent

                        // GRIB Fallback if live data is missing
                        if ((aws == null) || (aws == 0.0)) {
                            val lat = state.latitude
                            val lon = state.longitude
                            if ((lat != null) && (lon != null)) {
                                val gribWind = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.gribRepository?.getWindVector(lat, lon, System.currentTimeMillis())
                                if (gribWind != null) {
                                    aws = gribWind.speed
                                }
                            }
                        }

                        processWindUpdate(rawAngle, aws, state.roll ?: 0.0, state.pitch ?: 0.0)
                    } catch (e: Exception) {
                        log?.error("Error processing wind angle update: ${e.message}", e)
                    }
                }
            }
            launch {
                dataBroker.windSpeedApparent.collect { rawSpeed ->
                    try {
                        val state = dataBroker.marineState.value
                        processWindUpdate(state.windDirectionApparent, rawSpeed, state.roll ?: 0.0, state.pitch ?: 0.0)
                    } catch (e: Exception) {
                        log?.error("Error processing wind speed update: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Process raw wind and attitude updates (roll in radians or degrees, pitch, yaw).
     */
    fun processWindUpdate(rawAngleApparent: Double?, rawSpeedApparent: Double?, roll: Double = 0.0, pitch: Double = 0.0) {
        if (rawAngleApparent == null) return

        val correctedAngle = correctWindAngle(rawAngleApparent, roll, pitch)
        _correctedWindAngleApparent.value = correctedAngle

        if (rawSpeedApparent != null) {
            val correctedSpeed = correctWindSpeed(rawSpeedApparent, pitch)
            _correctedWindSpeedApparent.value = correctedSpeed

            // 2. Gust Response Logic
            checkGustAndManageAutopilot(correctedSpeed)
        }
    }

    /**
     * Corrects wind angle for masthead swing based on roll and pitch.
     * All angles are in Radians.
     */
    fun correctWindAngle(rawAngle: Double, roll: Double, pitch: Double): Double {
        val correction = (-roll * cos(rawAngle)) - (pitch * sin(rawAngle))
        return rawAngle + correction
    }

    /**
     * Corrects wind speed for masthead bobbing based on pitch.
     */
    fun correctWindSpeed(rawSpeed: Double, pitch: Double): Double {
        return rawSpeed * (1.0 - 0.05 * abs(pitch))
    }

    private fun checkGustAndManageAutopilot(currentSpeed: Double) {
        val now = System.currentTimeMillis()
        val currentSample = WindSample(now, currentSpeed)
        
        synchronized(windSamples) {
            windSamples.addLast(currentSample)

            // Prune history older than 3 seconds
            val iterator = windSamples.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().timestamp > gustWindowMillis) {
                    iterator.remove()
                } else {
                    break // Remaining samples are within window
                }
            }

            if (windSamples.isEmpty()) return

            val baselineSample = windSamples.firstOrNull() ?: return
            val baselineSpeed = baselineSample.speed
            
            if (baselineSpeed > 0.0) {
                val increaseRatio = (currentSpeed - baselineSpeed) / baselineSpeed
                val currentState = dataBroker.autopilotState.value.lowercase(Locale.US)
                val isWindMode = currentState == "wind"

                if (increaseRatio >= gustThresholdPercent) {
                    if (!_isGustActive.value) {
                        _isGustActive.value = true
                        log?.warn("Wind gust detected! Speed increased by ${(increaseRatio * 100).toInt()}% in <3s.")

                        if (isWindMode && !isBearAwayApplied) {
                            // Bear away 5-10 degrees (e.g. 7 degrees) to keep boat flat
                            bearAwayDegrees = 7.0
                            isBearAwayApplied = true
                            log?.info("Autopilot in WIND mode: bearing away by $bearAwayDegrees degrees due to gust.")
                            autopilotController.adjustHeading(bearAwayDegrees)
                        }
                    }
                } else if (increaseRatio < 0.05 && _isGustActive.value) {
                    // Gust has passed
                    _isGustActive.value = false
                    log?.info("Wind gust subsided.")

                    if (isWindMode && isBearAwayApplied) {
                        log?.info("Returning to original course by reversing bear away of -$bearAwayDegrees degrees.")
                        autopilotController.adjustHeading(-bearAwayDegrees)
                        isBearAwayApplied = false
                        bearAwayDegrees = 0.0
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
