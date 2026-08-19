package net.osmand.plus.plugins.nautical.telemetry

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

data class FilteredMetricState(
    val key: String,
    val value: Double?,
    val secondaryValue: Double? = null,
    val formatted: MetricValue,
    val isStale: Boolean,
    val lastUpdateTimeMs: Long,
    val alpha: Float = if (isStale) 0.4f else 1.0f
)

class TelemetryFilterEngine(
    private val app: OsmandApplication,
    private val scope: CoroutineScope
) {
    private val _filteredMetrics = MutableStateFlow<Map<String, FilteredMetricState>>(emptyMap())
    val filteredMetrics: StateFlow<Map<String, FilteredMetricState>> = _filteredMetrics.asStateFlow()

    private val lastValues = ConcurrentHashMap<String, Double>()
    private val lastSecondaryValues = ConcurrentHashMap<String, Double>()
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    private val depthOutlierCounters = ConcurrentHashMap<String, Int>()

    private var watchdogJob: Job? = null

    companion object {
        const val WATCHDOG_TIMEOUT_MS = 3500L
        const val TAU_WIND_SEC = 2.0
        const val TAU_SPEED_COURSE_SEC = 1.5
        const val TAU_DEPTH_SEC = 0.5
        const val TAU_BATTERY_TANK_SEC = 5.0
    }

    init {
        startWatchdog()
    }

    fun processState(state: MarineState, now: Long = System.currentTimeMillis()) {
        val allDefs = TelemetryRegistry.getAllMetrics()
        val currentStates = HashMap<String, FilteredMetricState>(_filteredMetrics.value)

        for (def in allDefs) {
            val rawPrimary = def.extractor(state)
            val rawSecondary = def.secondaryExtractor?.invoke(state)

            if (rawPrimary != null && !rawPrimary.isNaN() && !rawPrimary.isInfinite()) {
                val filteredPrimary = applyFilter(def.key, rawPrimary, def.category, def.isDepth, now)
                lastValues[def.key] = filteredPrimary
                lastUpdateTimes[def.key] = now
                def.ringBuffer.addSample(now, filteredPrimary)

                val filteredSecondary = if (rawSecondary != null && !rawSecondary.isNaN() && !rawSecondary.isInfinite()) {
                    val filteredSec = applyFilter("${def.key}_sec", rawSecondary, def.category, false, now)
                    lastSecondaryValues[def.key] = filteredSec
                    filteredSec
                } else {
                    lastSecondaryValues[def.key]
                }

                val formatted = def.formatter(app, app.settings, filteredPrimary, filteredSecondary)
                currentStates[def.key] = FilteredMetricState(
                    key = def.key,
                    value = filteredPrimary,
                    secondaryValue = filteredSecondary,
                    formatted = formatted,
                    isStale = false,
                    lastUpdateTimeMs = now,
                    alpha = 1.0f
                )
            } else {
                val lastTime = lastUpdateTimes[def.key] ?: 0L
                val isStale = (now - lastTime) > WATCHDOG_TIMEOUT_MS
                val lastVal = lastValues[def.key]
                val lastSecVal = lastSecondaryValues[def.key]

                val formatted = if (isStale || lastVal == null) {
                    MetricValue(primaryText = "---", isValid = false)
                } else {
                    def.formatter(app, app.settings, lastVal, lastSecVal)
                }

                currentStates[def.key] = FilteredMetricState(
                    key = def.key,
                    value = if (isStale) null else lastVal,
                    secondaryValue = if (isStale) null else lastSecVal,
                    formatted = formatted,
                    isStale = isStale,
                    lastUpdateTimeMs = lastTime,
                    alpha = if (isStale) 0.4f else 1.0f
                )
            }
        }

        _filteredMetrics.value = currentStates
    }

    private fun applyFilter(
        key: String,
        raw: Double,
        category: MetricCategory,
        isDepth: Boolean,
        now: Long
    ): Double {
        val prevVal = lastValues[key] ?: return raw
        val prevTime = lastUpdateTimes[key] ?: (now - 1000L)
        val dtSec = ((now - prevTime).coerceIn(10L, 5000L)) / 1000.0

        if (isDepth) {
            // Depth outlier rejection (minimum-hold with spike rejection)
            val diff = abs(raw - prevVal)
            if (diff > 25.0 || raw < 0.0 || raw > 11000.0) {
                val counter = (depthOutlierCounters[key] ?: 0) + 1
                depthOutlierCounters[key] = counter
                if (counter < 3) {
                    return prevVal
                }
            }
            depthOutlierCounters[key] = 0
            val alpha = 1.0 - exp(-dtSec / TAU_DEPTH_SEC)
            return prevVal + alpha * (raw - prevVal)
        }

        val tau = when (category) {
            MetricCategory.WIND -> TAU_WIND_SEC
            MetricCategory.NAVIGATION -> TAU_SPEED_COURSE_SEC
            MetricCategory.POWER, MetricCategory.VESSEL -> TAU_BATTERY_TANK_SEC
            MetricCategory.ENVIRONMENT -> TAU_SPEED_COURSE_SEC
        }

        val alpha = (1.0 - exp(-dtSec / tau)).coerceIn(0.01, 1.0)

        val isAngle = key.contains("angle", ignoreCase = true) ||
                key.contains("heading", ignoreCase = true) ||
                key.contains("course", ignoreCase = true) ||
                key.contains("direction", ignoreCase = true) ||
                key.contains("roll") || key.contains("pitch")

        return if (isAngle) {
            var diff = raw - prevVal
            while (diff < -PI) diff += 2 * PI
            while (diff > PI) diff -= 2 * PI
            prevVal + alpha * diff
        } else {
            prevVal + alpha * (raw - prevVal)
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val current = _filteredMetrics.value
                var changed = false
                val updated = HashMap<String, FilteredMetricState>(current)

                for ((k, state) in current) {
                    if (!state.isStale && (now - state.lastUpdateTimeMs > WATCHDOG_TIMEOUT_MS)) {
                        updated[k] = state.copy(
                            isStale = true,
                            alpha = 0.4f,
                            formatted = MetricValue(primaryText = "---", isValid = false)
                        )
                        changed = true
                    }
                }

                if (changed) {
                    _filteredMetrics.value = updated
                }
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}
