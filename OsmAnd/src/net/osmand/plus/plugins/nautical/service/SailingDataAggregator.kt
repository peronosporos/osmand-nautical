package net.osmand.plus.plugins.nautical.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import kotlin.time.Duration.Companion.milliseconds

class SailingDataAggregator {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val environmentalFilterService: net.osmand.plus.plugins.nautical.engine.EnvironmentalFilterService?
        get() = SailingDependencyContainer.environmentalFilterService

    private val _aggregatedData = MutableStateFlow(LivePerformanceData())
    val aggregatedData: StateFlow<LivePerformanceData> = _aggregatedData.asStateFlow()

    private var lastUpdateTime = System.currentTimeMillis()
    private var watchdogJob: Job? = null

    companion object {
        private const val SOURCE_DIRECT_NMEA = "direct-nmea"
        private const val SOURCE_SIGNALK_WS = "signalk-ws"
        private const val SOURCE_INTERNAL = "internal"

        private val PRIORITY_MAP = mapOf(
            SOURCE_DIRECT_NMEA to 3,
            SOURCE_SIGNALK_WS to 2,
            SOURCE_INTERNAL to 1,
        )
        
        private const val STALE_THRESHOLD_MS = 5000L
    }

    init {
        startWatchdog()
    }

    fun handleDelta(delta: DeltaMessage) {
        lastUpdateTime = System.currentTimeMillis()
        val updates = delta.updates ?: return
        
        _aggregatedData.update { current ->
            var updated = current
            val newTimestamps = updated.timestamps.toMutableMap()
            val newSources = updated.sources.toMutableMap()
            var changed = false

            for (update in updates) {
                val sourceLabel = update.source?.get("label")?.toString() ?: SOURCE_SIGNALK_WS
                val sourcePriority = PRIORITY_MAP[sourceLabel] ?: 0
                
                val values = update.values ?: continue
                for (v in values) {
                    val path = v.path ?: continue
                    val num = (v.value as? Number)?.toDouble() ?: continue
                    
                    // Check if current data for this path is fresh and from a higher priority source
                    val lastTimestamp = newTimestamps[path] ?: 0L
                    val lastSource = newSources[path] ?: ""
                    val lastPriority = PRIORITY_MAP[lastSource] ?: 0
                    
                    val isFresh = (lastUpdateTime - lastTimestamp) < STALE_THRESHOLD_MS
                    if (isFresh && (lastPriority > sourcePriority)) {
                        continue // Ignore lower priority fresh data
                    }

                    changed = true
                    newTimestamps[path] = lastUpdateTime
                    newSources[path] = sourceLabel
                    
                    updated = applyValue(updated, path, num)
                }
            }

            if (changed) {
                updated.copy(
                    timestamp = lastUpdateTime, 
                    timestamps = newTimestamps,
                    sources = newSources,
                )
            } else {
                current
            }
        }
    }

    private fun applyValue(current: LivePerformanceData, path: String, num: Double): LivePerformanceData {
        return when (path) {
            LivePerformanceData.PATH_STW -> {
                val leeway = calculateLeeway(num, current.roll ?: 0.0)
                current.copy(speedThroughWater = num, leeway = leeway)
            }
            LivePerformanceData.PATH_TWS -> current.copy(windSpeedTrue = num)
            LivePerformanceData.PATH_TWA -> current.copy(windAngleTrueWater = num)
            LivePerformanceData.PATH_SOG -> current.copy(speedOverGround = num)
            LivePerformanceData.PATH_COG -> current.copy(courseOverGround = num)
            LivePerformanceData.PATH_DEPTH -> current.copy(depthBelowTransducer = num)
            LivePerformanceData.PATH_POLAR_SPEED -> current.copy(polarSpeed = num)
            LivePerformanceData.PATH_TARGET_ANGLE -> current.copy(targetAngle = num)
            LivePerformanceData.PATH_POLAR_SPEED_RATIO -> current.copy(polarSpeedRatio = num)
            LivePerformanceData.PATH_ROLL -> {
                val leeway = calculateLeeway(current.speedThroughWater ?: 0.0, num)
                current.copy(roll = num, leeway = leeway)
            }
            LivePerformanceData.PATH_PITCH -> current.copy(pitch = num)
            LivePerformanceData.PATH_LEEWAY -> current.copy(leeway = num)
            LivePerformanceData.PATH_AWA -> {
                val corrected = environmentalFilterService?.correctWindAngle(num, current.roll ?: 0.0, current.pitch ?: 0.0) ?: num
                current.copy(windAngleApparent = corrected)
            }
            LivePerformanceData.PATH_AWS -> {
                val corrected = environmentalFilterService?.correctWindSpeed(num, current.pitch ?: 0.0) ?: num
                current.copy(windSpeedApparent = corrected)
            }
            else -> current
        }
    }

    /**
     * Integrates pre-processed performance data (e.g. from SignalKEngine).
     * Bypasses internal Priority arbitration as it's considered authoritative for SignalK source.
     */
    fun handleLivePerformanceData(data: LivePerformanceData) {
        _aggregatedData.update { current ->
            // For now, simple merge. Authorized SignalK data takes precedence over existing if fresh.
            val now = System.currentTimeMillis()
            data.copy(
                timestamp = now,
                sources = current.sources + data.timestamps.mapValues { SOURCE_SIGNALK_WS },
            )
        }
    }

    private fun calculateLeeway(stwMs: Double, rollRad: Double): Double {
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance() ?: return 0.0
        val k = plugin.getSettings().NAUTICAL_LEEWAY_COEFFICIENT.get()
        return net.osmand.plus.plugins.nautical.utils.LeewayCalculator.calculateLeewayRadians(rollRad, stwMs, k)
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                val now = System.currentTimeMillis()
                
                _aggregatedData.update { current ->
                    val newTimestamps = current.timestamps.toMutableMap()
                    var updated = false
                    
                    var stw = current.speedThroughWater
                    var tws = current.windSpeedTrue
                    var twa = current.windAngleTrueWater
                    var sog = current.speedOverGround
                    var cog = current.courseOverGround
                    var leeway = current.leeway
                    var depth = current.depthBelowTransducer
                    var polarSpeed = current.polarSpeed
                    var targetAngle = current.targetAngle
                    var polarRatio = current.polarSpeedRatio

                    fun checkStale(path: String, currentVal: Double?): Double? {
                        val last = newTimestamps[path] ?: 0L
                        return if ((now - last) > 5000) {
                            if (currentVal != null) updated = true
                            null
                        } else {
                            currentVal
                        }
                    }

                    stw = checkStale(LivePerformanceData.PATH_STW, stw)
                    tws = checkStale(LivePerformanceData.PATH_TWS, tws)
                    twa = checkStale(LivePerformanceData.PATH_TWA, twa)
                    sog = checkStale(LivePerformanceData.PATH_SOG, sog)
                    cog = checkStale(LivePerformanceData.PATH_COG, cog)
                    leeway = checkStale(LivePerformanceData.PATH_LEEWAY, leeway)
                    depth = checkStale(LivePerformanceData.PATH_DEPTH, depth)
                    polarSpeed = checkStale(LivePerformanceData.PATH_POLAR_SPEED, polarSpeed)
                    targetAngle = checkStale(LivePerformanceData.PATH_TARGET_ANGLE, targetAngle)
                    polarRatio = checkStale(LivePerformanceData.PATH_POLAR_SPEED_RATIO, polarRatio)

                    if (updated) {
                        current.copy(
                            speedThroughWater = stw,
                            windSpeedTrue = tws,
                            windAngleTrueWater = twa,
                            speedOverGround = sog,
                            courseOverGround = cog,
                            leeway = leeway,
                            depthBelowTransducer = depth,
                            polarSpeed = polarSpeed,
                            targetAngle = targetAngle,
                            polarSpeedRatio = polarRatio,
                            timestamp = now,
                            timestamps = newTimestamps
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        scope.cancel()
    }
}
