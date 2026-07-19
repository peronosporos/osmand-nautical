package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import net.osmand.shared.util.KMapUtils
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds
import android.content.Context
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class SignalKEngine {
    private val log = PlatformUtil.getLog(SignalKEngine::class.java)

    private var _currentState: MarineState? = null
    private val aisCache = ConcurrentHashMap<Int, AisTarget>()

    var onConnectionLost: (() -> Unit)? = null
    var onRouteStepProcessed: (() -> Unit)? = null

    private val stateListeners = java.util.concurrent.CopyOnWriteArraySet<(MarineState) -> Unit>()
    private var aisListener: ((AisTarget) -> Unit)? = null

    private var trueSelfContext: String = "vessels.self"
    private var watchdogJob: Job? = null
    private var lastUpdateTimestamp: Long = 0

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val depthBuffer = CircularBuffer<Double>(3600)
    private val windBuffer = CircularBuffer<Double>(3600)
    private val windDirectionBuffer = CircularBuffer<Double>(3600)
    private val vmgBuffer = CircularBuffer<Double>(3600)
    private val cogBuffer = CircularBuffer<Double>(3600)
    private val sogBuffer = CircularBuffer<Double>(3600)
    private val stwBuffer = CircularBuffer<Double>(3600)
    private val trajectoryBuffer = CircularBuffer<Pair<Double, Double>>(100)
    private val routeQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Double, Double>>()
    var isFollowingRoute: Boolean = false
        private set

    fun getCurrentState(): MarineState? = _currentState

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        onConnectionLost = null
        onRouteStepProcessed = null
        engineScope.cancel()
        stateListeners.clear()
        aisListener = null
        aisCache.clear()
        routeQueue.clear()
        _currentState = null
        isFollowingRoute = false
    }

    fun saveBuffersToDisk(context: Context) {
        engineScope.launch(Dispatchers.IO) {
            saveToFile(
                File(context.filesDir, "depth_buffer.dat"),
                depthBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "wind_buffer.dat"),
                windBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "wind_direction_buffer.dat"),
                windDirectionBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "vmg_buffer.dat"),
                vmgBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "cog_buffer.dat"),
                cogBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "sog_buffer.dat"),
                sogBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "stw_buffer.dat"),
                stwBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "trajectory_buffer.dat"),
                trajectoryBuffer.getAll() as Serializable,
            )
        }
    }

    private fun saveToFile(file: File, data: Serializable) {
        try {
            ObjectOutputStream(file.outputStream()).use { it.writeObject(data) }
        } catch (e: Exception) {
            log.error("Failed to save ${file.name}: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadBuffersFromDisk(context: Context) {
        engineScope.launch(Dispatchers.IO) {
            fun <T> load(fileName: String, action: (T) -> Unit) {
                val file = File(context.filesDir, fileName)
                if (!file.exists()) return
                try {
                    ObjectInputStream(file.inputStream()).use { ois ->
                        val data = ois.readObject() as Collection<T>
                        data.forEach { action(it) }
                    }
                } catch (e: Exception) {
                    log.error("Failed to load $fileName: ${e.message}")
                }
            }

            load<Double>("depth_buffer.dat") { depthBuffer.add(it) }
            load<Double>("wind_buffer.dat") { windBuffer.add(it) }
            load<Double>("wind_direction_buffer.dat") { windDirectionBuffer.add(it) }
            load<Double>("vmg_buffer.dat") { vmgBuffer.add(it) }
            load<Double>("cog_buffer.dat") { cogBuffer.add(it) }
            load<Double>("sog_buffer.dat") { sogBuffer.add(it) }
            load<Double>("stw_buffer.dat") { stwBuffer.add(it) }
            load<Pair<Double, Double>>("trajectory_buffer.dat") { trajectoryBuffer.add(it) }
        }
    }

    fun clearRoute() {
        routeQueue.clear()
        isFollowingRoute = false
        log.info("Route cleared. Manual control engaged.")
    }

    fun addWaypointToRoute(lat: Double, lon: Double) {
        routeQueue.add(Pair(lat, lon))
        isFollowingRoute = true
        log.info("New waypoint added: $lat, $lon")
    }

    fun dispatchCommand(command: String) {
        log.debug("Dispatching: $command")
    }

    private fun resetWatchdog() {
        lastUpdateTimestamp = System.currentTimeMillis()
        if (watchdogJob?.isActive != true) {
            startWatchdog()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = engineScope.launch {
            while (isActive) {
                delay(1.seconds)
                val elapsed = System.currentTimeMillis() - lastUpdateTimestamp
                if (elapsed > 10000) {
                    _currentState = null
                    notifyListeners(MarineState(connectionStatus = ConnectionStatus.DISCONNECTED))
                    log.error("Data timeout!")
                    onConnectionLost?.invoke()
                    break
                } else if (elapsed > 5000) {
                    if (_currentState?.connectionStatus != ConnectionStatus.STALE) {
                        _currentState = _currentState?.copy(connectionStatus = ConnectionStatus.STALE)
                        _currentState?.let { notifyListeners(it) }
                    }
                }
            }
        }
    }

    fun registerListener(listener: (MarineState) -> Unit) { stateListeners.add(listener) }
    fun unregisterListener(listener: (MarineState) -> Unit) { stateListeners.remove(listener) }
    fun registerAisListener(listener: ((AisTarget) -> Unit)?) { this.aisListener = listener }

    private fun notifyListeners(state: MarineState) {
        stateListeners.forEach { it.invoke(state) }
    }

    fun getDepthHistory(): List<Double> = depthBuffer.getAll()
    fun getWindHistory(): List<Double> = windBuffer.getAll()
    fun getWindDirectionHistory(): List<Double> = windDirectionBuffer.getAll()
    fun getVmgHistory(): List<Double> = vmgBuffer.getAll()
    fun getCogHistory(): List<Double> = cogBuffer.getAll()
    fun getSogHistory(): List<Double> = sogBuffer.getAll()
    fun getStwHistory(): List<Double> = stwBuffer.getAll()

    fun addTrajectoryPoint(lat: Double, lon: Double) {
        val history = trajectoryBuffer.getAll()
        val last = history.lastOrNull()

        if (last != null) {
            val dist = KMapUtils.getDistance(last.first, last.second, lat, lon)
            if (dist > 500.0) { // 500 meters is a reasonable "jump" threshold for a boat
                log.warn("Jump detected ($dist m)! Discarding point: $lat, $lon")
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
            var state = (_currentState ?: MarineState()).copy(connectionStatus = ConnectionStatus.CONNECTED)

            val context = json.optString("context", "vessels.self")
            val updates = json.getJSONArray("updates")
            val isSelf = (context == "vessels.self") || (context == "") || (context == trueSelfContext)

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

                    when {
                        isSelf -> {
                            when (path) {
                                "navigation.position" -> {
                                    if (valueObj is JSONObject) {
                                        val lat = valueObj.optDouble("latitude", Double.NaN)
                                        val lon = valueObj.optDouble("longitude", Double.NaN)
                                        if (!lat.isNaN() && !lon.isNaN()) {
                                            state = state.copy(latitude = lat, longitude = lon)
                                            stateUpdated = true
                                            updateFollowingState(lat, lon)
                                            addTrajectoryPoint(lat, lon)
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
                                        sogBuffer.add(sog)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.speedThroughWater" -> {
                                    val stw = valueItem.optDouble("value", Double.NaN)
                                    if (!stw.isNaN()) {
                                        state = state.copy(speedThroughWater = stw)
                                        stwBuffer.add(stw)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.rateOfTurn" -> {
                                    val rot = valueItem.optDouble("value", Double.NaN)
                                    if (!rot.isNaN()) {
                                        state = state.copy(rateOfTurn = rot)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.drift" -> {
                                    val drift = valueItem.optDouble("value", Double.NaN)
                                    if (!drift.isNaN()) {
                                        state = state.copy(drift = drift)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.setTrue" -> {
                                    val set = valueItem.optDouble("value", Double.NaN)
                                    if (!set.isNaN()) {
                                        state = state.copy(setTrue = set)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.courseOverGroundTrue" -> {
                                    val cog = valueItem.optDouble("value", Double.NaN)
                                    if (!cog.isNaN()) {
                                        state = state.copy(courseOverGroundTrue = cog)
                                        cogBuffer.add(cog)
                                        stateUpdated = true
                                    }
                                }
                                "performance.velocityMadeGood" -> {
                                    val vmg = valueItem.optDouble("value", Double.NaN)
                                    if (!vmg.isNaN()) {
                                        state = state.copy(velocityMadeGood = vmg)
                                        vmgBuffer.add(vmg)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.crossTrackError",
                                "navigation.courseRhumbline.crossTrackError",
                                "navigation.courseGreatCircle.crossTrackError" -> {
                                    val xte = valueItem.optDouble("value", Double.NaN)
                                    if (!xte.isNaN()) {
                                        state = state.copy(crossTrackError = xte)
                                        stateUpdated = true
                                    }
                                }
                                "steering.autopilot.state" -> {
                                    state = state.copy(autopilotState = valueItem.optString("value", "standby"))
                                    stateUpdated = true
                                }
                                "steering.rudderAngle" -> {
                                    val rudder = valueItem.optDouble("value", Double.NaN)
                                    if (!rudder.isNaN()) {
                                        state = state.copy(rudderAngle = rudder)
                                        stateUpdated = true
                                    }
                                }
                                "steering.autopilot.target.headingTrue" -> {
                                    val target = valueItem.optDouble("value", Double.NaN)
                                    if (!target.isNaN()) {
                                        state = state.copy(targetHeading = target)
                                        stateUpdated = true
                                    }
                                }
                                "steering.autopilot.target.windAngleApparent" -> {
                                    val target = valueItem.optDouble("value", Double.NaN)
                                    if (!target.isNaN()) {
                                        state = state.copy(targetWindAngleApparent = target)
                                        stateUpdated = true
                                    }
                                }
                                "steering.autopilot.seaState" -> {
                                    val level = valueItem.optInt("value", -1)
                                    if (level != -1) {
                                        state = state.copy(seaState = level)
                                        stateUpdated = true
                                    }
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
                                "environment.wind.directionTrue" -> {
                                    val dir = valueItem.optDouble("value", Double.NaN)
                                    if (!dir.isNaN()) {
                                        state = state.copy(windDirectionTrue = dir)
                                        windDirectionBuffer.add(dir)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.angleApparent" -> {
                                    val dir = valueItem.optDouble("value", Double.NaN)
                                    if (!dir.isNaN()) {
                                        state = state.copy(windDirectionApparent = dir)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.speedApparent" -> {
                                    val speed = valueItem.optDouble("value", Double.NaN)
                                    if (!speed.isNaN()) {
                                        state = state.copy(windSpeedApparent = speed)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.angleTrue" -> {
                                    val dir = valueItem.optDouble("value", Double.NaN)
                                    if (!dir.isNaN()) {
                                        state = state.copy(trueWindAngle = dir)
                                        stateUpdated = true
                                    }
                                }
                                "performance.targetSpeed" -> {
                                    val speed = valueItem.optDouble("value", Double.NaN)
                                    if (!speed.isNaN()) {
                                        state = state.copy(polarTargetSpeed = speed)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.courseRhumbline.timeToWaypoint",
                                "navigation.courseGreatCircle.timeToWaypoint" -> {
                                    val ttw = valueItem.optDouble("value", Double.NaN)
                                    if (!ttw.isNaN()) {
                                        state = state.copy(timeToWaypoint = ttw)
                                        stateUpdated = true
                                    }
                                }
                            }
                        }
                        aisTarget != null -> {
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
            }

            if (isSelf && stateUpdated) {
                _currentState = state
                notifyListeners(state)
            } else if ((aisTarget != null) && (aisTarget.latitude != null) && (aisTarget.longitude != null)) {
                engineScope.launch { aisListener?.invoke(aisTarget) }
            }
        } catch (e: Exception) {
            log.error("JSON parsing error: ${e.message}")
        }
    }

    fun loadRoute(route: List<Pair<Double, Double>>) {
        routeQueue.clear()
        routeQueue.addAll(route)
        isFollowingRoute = true
        onRouteStepProcessed?.invoke()
        log.info("Route loaded: ${route.size} points. Following enabled.")
    }

    fun getNextWaypoint(): Pair<Double, Double>? = routeQueue.peek()

    fun updateFollowingState(currentLat: Double, currentLon: Double) {
        if (!isFollowingRoute || routeQueue.isEmpty()) return

        val target = routeQueue.peek() ?: return
        val distance = KMapUtils.getDistance(currentLat, currentLon, target.first, target.second)

        // 0.02 NM is ~37 meters
        if (distance < 37.0) {
            routeQueue.poll() // Arrived! Remove this point
            log.info("Waypoint reached. Next in queue: ${routeQueue.size}")
            onRouteStepProcessed?.invoke()
        }

        // If route finished
        if (routeQueue.isEmpty()) {
            isFollowingRoute = false
            log.info("Route complete.")
        }
    }
}
