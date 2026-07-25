package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages SignalK data streams with throttling and threshold-based filtering.
 */
class SignalKDataBroker {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _headingTrue = MutableStateFlow<Double?>(null)
    private val _windAngleApparent = MutableStateFlow<Double?>(null)
    private val _autopilotState = MutableStateFlow<String>("standby")
    private val _autopilotTargetHeadingMag = MutableStateFlow<Double?>(null)
    private val _cpa = MutableStateFlow<Double?>(null) // Closest Point of Approach in nautical miles
    private val _tcpa = MutableStateFlow<Double?>(null) // Time to Closest Point of Approach in seconds
    private val _threatName = MutableStateFlow<String?>(null)

    val headingTrue: StateFlow<Double?> = _headingTrue.asStateFlow()
    val windAngleApparent: StateFlow<Double?> = _windAngleApparent.asStateFlow()
    val autopilotState: StateFlow<String> = _autopilotState.asStateFlow()
    val autopilotTargetHeadingMag: StateFlow<Double?> = _autopilotTargetHeadingMag.asStateFlow()
    val cpa: StateFlow<Double?> = _cpa.asStateFlow()
    val tcpa: StateFlow<Double?> = _tcpa.asStateFlow()
    val threatName: StateFlow<String?> = _threatName.asStateFlow()

    // Throttling and Threshold settings
    private val throttleInterval = 500.milliseconds
    private val angleThreshold = Math.toRadians(2.0)

    private var lastHeadingTime = 0L
    private var lastHeadingValue: Double? = null

    private var lastWindTime = 0L
    private var lastWindValue: Double? = null

    fun processHeadingUpdate(value: Double) {
        val now = System.currentTimeMillis()
        if (shouldUpdate(value, lastHeadingValue, now, lastHeadingTime)) {
            _headingTrue.value = value
            lastHeadingValue = value
            lastHeadingTime = now
        }
    }

    fun processWindAngleUpdate(value: Double) {
        val now = System.currentTimeMillis()
        if (shouldUpdate(value, lastWindValue, now, lastWindTime)) {
            _windAngleApparent.value = value
            lastWindValue = value
            lastWindTime = now
        }
    }

    fun updateAutopilotState(state: String) {
        _autopilotState.value = state
    }

    fun updateAutopilotTargetHeadingMag(value: Double) {
        _autopilotTargetHeadingMag.value = value
    }

    fun updateClosestApproach(cpaVal: Double?, tcpaVal: Double?, name: String? = null) {
        _cpa.value = cpaVal
        _tcpa.value = tcpaVal
        if (name != null) {
            _threatName.value = name
        }
    }

    private fun shouldUpdate(newValue: Double, lastValue: Double?, now: Long, lastTime: Long): Boolean {
        if (lastValue == null) return true
        if (now - lastTime < throttleInterval.inWholeMilliseconds) return false
        return abs(newValue - lastValue) > angleThreshold
    }
}
