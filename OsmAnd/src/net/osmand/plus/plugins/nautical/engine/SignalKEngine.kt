package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import kotlinx.coroutines.*
import net.osmand.plus.plugins.nautical.NauticalPlugin
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds
import kotlin.math.hypot

data class AisTarget(
    val mmsi: Int,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedOverGround: Float? = null,
    var courseOverGround: Float? = null,
    var headingTrue: Float? = null
)

class SignalKEngine {


    private var _currentState: MarineState? = null
    private val aisCache = ConcurrentHashMap<Int, AisTarget>()

    var onConnectionLost: (() -> Unit)? = null

    private val stateListeners = java.util.concurrent.CopyOnWriteArraySet<(MarineState) -> Unit>()
    private var aisListener: ((AisTarget) -> Unit)? = null

    private var trueSelfContext: String = "vessels.self"
    private var watchdogJob: Job? = null
    private var lastUpdateTimestamp: Long = 0

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val depthBuffer = CircularBuffer<Double>(360)
    private val windBuffer = CircularBuffer<Double>(360)
    private val trajectoryBuffer = CircularBuffer<Pair<Double, Double>>(100)
    private val routeQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Double, Double>>()
    var isFollowingRoute: Boolean = false
        private set

    fun getCurrentState(): MarineState? = _currentState
    fun isDataStale(): Boolean = (System.currentTimeMillis() - lastUpdateTimestamp) > 5000

    fun stop() {
        watchdogJob?.cancel()
        engineScope.cancel()
    }

    fun getRouteQueueSize(): Int = routeQueue.size

    fun clearRoute() {
        routeQueue.clear()
        isFollowingRoute = false
        Log.i("SignalKEngine", "Route cleared. Manual control engaged.")
    }

    fun dispatchCommand(command: String) {
        Log.d("SignalKEngine", "Dispatching: $command")
    }

    private fun resetWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = engineScope.launch {
            delay(10.seconds)
            _currentState = null
            notifyListeners(MarineState())
            Log.e("NauticalEngine", "Data timeout!")
            onConnectionLost?.invoke()
        }
    }

    fun registerListener(listener: (MarineState) -> Unit) { stateListeners.add(listener) }
    fun unregisterListener(listener: (MarineState) -> Unit) { stateListeners.remove(listener) }
    fun registerAisListener(listener: (AisTarget) -> Unit) { this.aisListener = listener }

    private fun notifyListeners(state: MarineState) {
        synchronized(stateListeners) {
            stateListeners.forEach { it.invoke(state) }
        }
    }

    fun getDepthHistory(): List<Double> = depthBuffer.getAll()
    fun getWindHistory(): List<Double> = windBuffer.getAll()

    fun addWaypoint(lat: Double, lon: Double) {
        val history = trajectoryBuffer.getAll()
        val last = history.lastOrNull()

        if (last != null) {
            val delta = hypot(lat - last.first, lon - last.second)
            if (delta > 0.1) {
                Log.w("SignalKEngine", "Jump detected! Discarding point: $lat, $lon")
                return
            }
        }
        trajectoryBuffer.add(Pair(lat, lon))
    }

    fun getTrajectory(): List<Pair<Double, Double>> {
        return trajectoryBuffer.getAll()
    }

    fun handleIncomingMessage(jsonMessage: String) {
        lastUpdateTimestamp = System.currentTimeMillis()
        resetWatchdog()
        try {
            val json = JSONObject(jsonMessage)
            if (json.has("self")) {
                trueSelfContext = json.getString("self")
                return
            }

            if (!json.has("updates")) return

            // Pattern: Use a local variable for the state during parsing, then commit once at the end
            var state = _currentState ?: MarineState()

            val context = json.optString("context", "vessels.self")
            val updates = json.getJSONArray("updates")
            val isSelf = context == "vessels.self" || context == "" || context == trueSelfContext

            var numericMmsi = 0
            if (!isSelf) {
                val rawId = context.substringAfterLast(":", "")
                if (rawId.isEmpty()) return
                numericMmsi = rawId.toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
            }

            val aisTarget = if (!isSelf) aisCache.getOrPut(numericMmsi) { AisTarget(numericMmsi) } else null
            var stateUpdated = false

            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                if (!update.has("values")) continue

                val values = update.getJSONArray("values")
                for (j in 0 until values.length()) {
                    val valueItem = values.getJSONObject(j)
                    val path = valueItem.optString("path")
                    val valueObj = valueItem.opt("value")

                    if (isSelf) {
                        when (path) {
                            "navigation.position" -> {
                                if (valueObj is JSONObject) {
                                    val lat = valueObj.optDouble("latitude", Double.NaN)
                                    val lon = valueObj.optDouble("longitude", Double.NaN)
                                    if (!lat.isNaN() && !lon.isNaN()) {
                                        state = state.copy(latitude = lat, longitude = lon)
                                        stateUpdated = true
                                        updateFollowingState(lat, lon)
                                        NauticalPlugin.autopilot?.processRouteStep()
                                    }
                                }
                            }
                            "navigation.headingTrue" -> {
                                val heading = valueItem.optDouble("value", Double.NaN)
                                if (!heading.isNaN()) {
                                    state = state.copy(headingTrue = heading)
                                    stateUpdated = true
                                }
                            }
                            "navigation.speedOverGround" -> {
                                val sog = valueItem.optDouble("value", Double.NaN)
                                if (!sog.isNaN()) {
                                    state = state.copy(speedOverGround = sog)
                                    stateUpdated = true
                                }
                            }
                            "steering.autopilot.state" -> {
                                state = state.copy(autopilotState = valueItem.optString("value", "standby"))
                                stateUpdated = true
                            }
                            "environment.depth.belowTransducer" -> {
                                val depth = valueItem.optDouble("value", Double.NaN)
                                if (!depth.isNaN()) {
                                    state = state.copy(depthBelowTransducer = depth)
                                    depthBuffer.add(depth)
                                    stateUpdated = true
                                }
                            }
                            "environment.wind.speedTrue" -> {
                                val wind = valueItem.optDouble("value", Double.NaN)
                                if (!wind.isNaN()) {
                                    state = state.copy(windSpeedTrue = wind)
                                    windBuffer.add(wind)
                                    stateUpdated = true
                                }
                            }
                        }
                    } else if (aisTarget != null) {
                        when (path) {
                            "navigation.position" -> {
                                if (valueObj is JSONObject) {
                                    aisTarget.latitude = valueObj.optDouble("latitude", Double.NaN).takeUnless { it.isNaN() }
                                    aisTarget.longitude = valueObj.optDouble("longitude", Double.NaN).takeUnless { it.isNaN() }
                                }
                            }
                            "navigation.speedOverGround" -> aisTarget.speedOverGround = valueItem.optDouble("value", Double.NaN).takeUnless { it.isNaN() }?.toFloat()
                            "navigation.courseOverGroundTrue" -> aisTarget.courseOverGround = valueItem.optDouble("value", Double.NaN).takeUnless { it.isNaN() }?.toFloat()
                            "navigation.headingTrue" -> aisTarget.headingTrue = valueItem.optDouble("value", Double.NaN).takeUnless { it.isNaN() }?.toFloat()
                        }
                    }
                }
            }

            if (isSelf && stateUpdated) {
                _currentState = state
                notifyListeners(state)
            } else if (aisTarget != null && aisTarget.latitude != null && aisTarget.longitude != null) {
                engineScope.launch { aisListener?.invoke(aisTarget) }
            }
        } catch (e: Exception) {
            Log.e("SignalKEngine", "JSON parsing error: ${e.message}")
        }
    }

    fun loadRoute(route: List<Pair<Double, Double>>) {
        routeQueue.clear()
        routeQueue.addAll(route)
        isFollowingRoute = true
        pushNextWaypointsToAutopilot()
        Log.i("SignalKEngine", "Route loaded: ${route.size} points. Following enabled.")
    }

    fun updateFollowingState(currentLat: Double, currentLon: Double) {
        if (!isFollowingRoute || routeQueue.isEmpty()) return

        val target = routeQueue.peek() ?: return
        // Using Haversine-lite distance (0.02 NM is ~37 meters)
        val distance = hypot(currentLat - target.first, currentLon - target.second) * 60.0

        if (distance < 0.02) {
            routeQueue.poll() // Arrived! Remove this point
            Log.i("SignalKEngine", "Waypoint reached. Next in queue: ${routeQueue.size}")
        }

        // If route finished
        if (routeQueue.isEmpty()) {
            isFollowingRoute = false
            Log.i("SignalKEngine", "Route complete.")
        }
    }

    fun pushNextWaypointsToAutopilot() {
        repeat(5) {
            routeQueue.poll()?.let { point ->
                dispatchCommand("WAYPOINT:${point.first},${point.second}")
            }
        }
    }


}