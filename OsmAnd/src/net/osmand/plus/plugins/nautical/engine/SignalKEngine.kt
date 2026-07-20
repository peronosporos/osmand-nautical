package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import net.osmand.shared.util.KMapUtils
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
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
    private val rpmBuffer = CircularBuffer<Double>(3600)
    private val tempEngineBuffer = CircularBuffer<Double>(3600)
    private val voltBuffer = CircularBuffer<Double>(3600)
    private val socBuffer = CircularBuffer<Double>(3600)
    private val xteBuffer = CircularBuffer<Double>(3600)
    private val waterTempBuffer = CircularBuffer<Double>(3600)
    private val outsideTempBuffer = CircularBuffer<Double>(3600)
    private val pressureBuffer = CircularBuffer<Double>(3600)
    private val rollBuffer = CircularBuffer<Double>(3600)
    private val pitchBuffer = CircularBuffer<Double>(3600)
    private val awaBuffer = CircularBuffer<Double>(3600)
    private val awsBuffer = CircularBuffer<Double>(3600)
    private val twaBuffer = CircularBuffer<Double>(3600)
    private val rotBuffer = CircularBuffer<Double>(3600)
    private val ttwBuffer = CircularBuffer<Double>(3600)
    private val dtwBuffer = CircularBuffer<Double>(3600)
    private val polarRatioBuffer = CircularBuffer<Double>(3600)
    private val magHdgBuffer = CircularBuffer<Double>(3600)
    private val logBuffer = CircularBuffer<Double>(3600)
    private val tripLogBuffer = CircularBuffer<Double>(3600)
    private val depthKeelBuffer = CircularBuffer<Double>(3600)
    private val fuelBuffer = CircularBuffer<Double>(3600)
    private val freshWaterBuffer = CircularBuffer<Double>(3600)
    private val wasteBuffer = CircularBuffer<Double>(3600)
    private val oilPressureBuffer = CircularBuffer<Double>(3600)
    private val engineLoadBuffer = CircularBuffer<Double>(3600)
    private val batteryCurrentBuffer = CircularBuffer<Double>(3600)
    private val solarCurrentBuffer = CircularBuffer<Double>(3600)
    private val twdBuffer = CircularBuffer<Double>(3600)
    private val trajectoryBuffer = CircularBuffer<Pair<Double, Double>>(1000)
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
                File(context.filesDir, "rpm_buffer.dat"),
                rpmBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "temp_engine_buffer.dat"),
                tempEngineBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "volt_buffer.dat"),
                voltBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "soc_buffer.dat"),
                socBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "xte_buffer.dat"),
                xteBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "water_temp_buffer.dat"),
                waterTempBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "outside_temp_buffer.dat"),
                outsideTempBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "pressure_buffer.dat"),
                pressureBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "roll_buffer.dat"),
                rollBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "pitch_buffer.dat"),
                pitchBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "awa_buffer.dat"),
                awaBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "aws_buffer.dat"),
                awsBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "twa_buffer.dat"),
                twaBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "rot_buffer.dat"),
                rotBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "ttw_buffer.dat"),
                ttwBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "dtw_buffer.dat"),
                dtwBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "polar_ratio_buffer.dat"),
                polarRatioBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "mag_hdg_buffer.dat"),
                magHdgBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "log_buffer.dat"),
                logBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "trip_log_buffer.dat"),
                tripLogBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "depth_keel_buffer.dat"),
                depthKeelBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "fuel_buffer.dat"),
                fuelBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "fresh_water_buffer.dat"),
                freshWaterBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "waste_buffer.dat"),
                wasteBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "oil_pressure_buffer.dat"),
                oilPressureBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "engine_load_buffer.dat"),
                engineLoadBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "battery_current_buffer.dat"),
                batteryCurrentBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "solar_current_buffer.dat"),
                solarCurrentBuffer.getAll() as Serializable,
            )
            saveToFile(
                File(context.filesDir, "twd_buffer.dat"),
                twdBuffer.getAll() as Serializable,
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
            load<Double>("rpm_buffer.dat") { rpmBuffer.add(it) }
            load<Double>("temp_engine_buffer.dat") { tempEngineBuffer.add(it) }
            load<Double>("volt_buffer.dat") { voltBuffer.add(it) }
            load<Double>("soc_buffer.dat") { socBuffer.add(it) }
            load<Double>("xte_buffer.dat") { xteBuffer.add(it) }
            load<Double>("water_temp_buffer.dat") { waterTempBuffer.add(it) }
            load<Double>("outside_temp_buffer.dat") { outsideTempBuffer.add(it) }
            load<Double>("pressure_buffer.dat") { pressureBuffer.add(it) }
            load<Double>("roll_buffer.dat") { rollBuffer.add(it) }
            load<Double>("pitch_buffer.dat") { pitchBuffer.add(it) }
            load<Double>("awa_buffer.dat") { awaBuffer.add(it) }
            load<Double>("aws_buffer.dat") { awsBuffer.add(it) }
            load<Double>("twa_buffer.dat") { twaBuffer.add(it) }
            load<Double>("rot_buffer.dat") { rotBuffer.add(it) }
            load<Double>("ttw_buffer.dat") { ttwBuffer.add(it) }
            load<Double>("dtw_buffer.dat") { dtwBuffer.add(it) }
            load<Double>("polar_ratio_buffer.dat") { polarRatioBuffer.add(it) }
            load<Double>("mag_hdg_buffer.dat") { magHdgBuffer.add(it) }
            load<Double>("log_buffer.dat") { logBuffer.add(it) }
            load<Double>("trip_log_buffer.dat") { tripLogBuffer.add(it) }
            load<Double>("depth_keel_buffer.dat") { depthKeelBuffer.add(it) }
            load<Double>("fuel_buffer.dat") { fuelBuffer.add(it) }
            load<Double>("fresh_water_buffer.dat") { freshWaterBuffer.add(it) }
            load<Double>("waste_buffer.dat") { wasteBuffer.add(it) }
            load<Double>("oil_pressure_buffer.dat") { oilPressureBuffer.add(it) }
            load<Double>("engine_load_buffer.dat") { engineLoadBuffer.add(it) }
            load<Double>("battery_current_buffer.dat") { batteryCurrentBuffer.add(it) }
            load<Double>("solar_current_buffer.dat") { solarCurrentBuffer.add(it) }
            load<Double>("twd_buffer.dat") { twdBuffer.add(it) }
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
                delay(1000L)
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
    fun getRpmHistory(): List<Double> = rpmBuffer.getAll()
    fun getTempEngineHistory(): List<Double> = tempEngineBuffer.getAll()
    fun getVoltHistory(): List<Double> = voltBuffer.getAll()
    fun getSocHistory(): List<Double> = socBuffer.getAll()
    fun getXteHistory(): List<Double> = xteBuffer.getAll()
    fun getWaterTempHistory(): List<Double> = waterTempBuffer.getAll()
    fun getOutsideTempHistory(): List<Double> = outsideTempBuffer.getAll()
    fun getPressureHistory(): List<Double> = pressureBuffer.getAll()
    fun getRollHistory(): List<Double> = rollBuffer.getAll()
    fun getPitchHistory(): List<Double> = pitchBuffer.getAll()
    fun getAwaHistory(): List<Double> = awaBuffer.getAll()
    fun getAwsHistory(): List<Double> = awsBuffer.getAll()
    fun getTwaHistory(): List<Double> = twaBuffer.getAll()
    fun getRotHistory(): List<Double> = rotBuffer.getAll()
    fun getTtwHistory(): List<Double> = ttwBuffer.getAll()
    fun getDtwHistory(): List<Double> = dtwBuffer.getAll()
    fun getPolarRatioHistory(): List<Double> = polarRatioBuffer.getAll()
    fun getMagHdgHistory(): List<Double> = magHdgBuffer.getAll()
    fun getLogHistory(): List<Double> = logBuffer.getAll()
    fun getTripLogHistory(): List<Double> = tripLogBuffer.getAll()
    fun getDepthKeelHistory(): List<Double> = depthKeelBuffer.getAll()
    fun getFuelHistory(): List<Double> = fuelBuffer.getAll()
    fun getFreshWaterHistory(): List<Double> = freshWaterBuffer.getAll()
    fun getWasteHistory(): List<Double> = wasteBuffer.getAll()
    fun getOilPressureHistory(): List<Double> = oilPressureBuffer.getAll()
    fun getEngineLoadHistory(): List<Double> = engineLoadBuffer.getAll()
    fun getBatteryCurrentHistory(): List<Double> = batteryCurrentBuffer.getAll()
    fun getSolarCurrentHistory(): List<Double> = solarCurrentBuffer.getAll()
    fun getTwdHistory(): List<Double> = twdBuffer.getAll()

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
                                "navigation.headingMagnetic" -> {
                                    val heading = valueItem.optDouble("value", Double.NaN)
                                    if (!heading.isNaN()) {
                                        state = state.copy(headingMagnetic = heading)
                                        magHdgBuffer.add(heading)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.log" -> {
                                    val logVal = valueItem.optDouble("value", Double.NaN)
                                    if (!logVal.isNaN()) {
                                        state = state.copy(log = logVal)
                                        logBuffer.add(logVal)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.trip.log" -> {
                                    val logVal = valueItem.optDouble("value", Double.NaN)
                                    if (!logVal.isNaN()) {
                                        state = state.copy(tripLog = logVal)
                                        tripLogBuffer.add(logVal)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.attitude" -> {
                                    if (valueObj is JSONObject) {
                                        val roll = valueObj.optDouble("roll", Double.NaN)
                                        val pitch = valueObj.optDouble("pitch", Double.NaN)
                                        val yaw = valueObj.optDouble("yaw", Double.NaN)
                                        state = state.copy(
                                            roll = if (roll.isNaN()) state.roll else roll,
                                            pitch = if (pitch.isNaN()) state.pitch else pitch,
                                            yaw = if (yaw.isNaN()) state.yaw else yaw
                                        )
                                        if (!roll.isNaN()) rollBuffer.add(roll)
                                        if (!pitch.isNaN()) pitchBuffer.add(pitch)
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
                                        rotBuffer.add(rot)
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
                                        xteBuffer.add(xte)
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
                                "environment.depth.belowKeel" -> {
                                    val depth = valueItem.optDouble("value", Double.NaN)
                                    if (!depth.isNaN()) {
                                        state = state.copy(depthBelowKeel = depth)
                                        depthKeelBuffer.add(depth)
                                        stateUpdated = true
                                    }
                                }
                                "environment.water.temperature" -> {
                                    val temp = valueItem.optDouble("value", Double.NaN)
                                    if (!temp.isNaN()) {
                                        state = state.copy(waterTemperature = temp)
                                        waterTempBuffer.add(temp)
                                        stateUpdated = true
                                    }
                                }
                                "environment.outside.temperature" -> {
                                    val temp = valueItem.optDouble("value", Double.NaN)
                                    if (!temp.isNaN()) {
                                        state = state.copy(outsideTemperature = temp)
                                        outsideTempBuffer.add(temp)
                                        stateUpdated = true
                                    }
                                }
                                "environment.outside.pressure" -> {
                                    val press = valueItem.optDouble("value", Double.NaN)
                                    if (!press.isNaN()) {
                                        state = state.copy(outsidePressure = press)
                                        pressureBuffer.add(press)
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
                                        twdBuffer.add(dir)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.angleApparent" -> {
                                    val dir = valueItem.optDouble("value", Double.NaN)
                                    if (!dir.isNaN()) {
                                        state = state.copy(windDirectionApparent = dir)
                                        awaBuffer.add(dir)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.speedApparent" -> {
                                    val speed = valueItem.optDouble("value", Double.NaN)
                                    if (!speed.isNaN()) {
                                        state = state.copy(windSpeedApparent = speed)
                                        awsBuffer.add(speed)
                                        stateUpdated = true
                                    }
                                }
                                "environment.wind.angleTrue" -> {
                                    val dir = valueItem.optDouble("value", Double.NaN)
                                    if (!dir.isNaN()) {
                                        state = state.copy(trueWindAngle = dir)
                                        twaBuffer.add(dir)
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
                                "performance.polarSpeedRatio" -> {
                                    val ratio = valueItem.optDouble("value", Double.NaN)
                                    if (!ratio.isNaN()) {
                                        state = state.copy(polarSpeedRatio = ratio)
                                        polarRatioBuffer.add(ratio)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.courseRhumbline.timeToWaypoint",
                                "navigation.courseGreatCircle.timeToWaypoint" -> {
                                    val ttw = valueItem.optDouble("value", Double.NaN)
                                    if (!ttw.isNaN()) {
                                        state = state.copy(timeToWaypoint = ttw)
                                        ttwBuffer.add(ttw)
                                        stateUpdated = true
                                    }
                                }
                                "navigation.courseRhumbline.distanceToWaypoint",
                                "navigation.courseGreatCircle.distanceToWaypoint" -> {
                                    val dtw = valueItem.optDouble("value", Double.NaN)
                                    if (!dtw.isNaN()) {
                                        state = state.copy(distanceToWaypoint = dtw)
                                        dtwBuffer.add(dtw)
                                        stateUpdated = true
                                    }
                                }
                                else -> {
                                    val value = valueItem.optDouble("value", Double.NaN)
                                    if (path.startsWith("propulsion.") && path.endsWith(".revolutions")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(engineRpm = value * 60.0) // Hz to RPM
                                            rpmBuffer.add(value * 60.0)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("propulsion.") && path.endsWith(".temperature")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(engineTemperature = value)
                                            tempEngineBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("propulsion.") && path.endsWith(".oilPressure")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(engineOilPressure = value)
                                            oilPressureBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("propulsion.") && path.endsWith(".engineLoad")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(engineLoad = value)
                                            engineLoadBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("propulsion.") && path.endsWith(".runTime")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(engineRunTime = value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("electrical.batteries.") && path.endsWith(".voltage")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(batteryVoltage = value)
                                            voltBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("electrical.batteries.") && path.endsWith(".current")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(batteryCurrent = value)
                                            batteryCurrentBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("electrical.batteries.") && path.endsWith(".capacity.stateOfCharge")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(batterySoc = value)
                                            socBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("electrical.solar.") && path.endsWith(".current")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(solarCurrent = value)
                                            solarCurrentBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("tanks.fuel.") && path.endsWith(".currentLevel")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(fuelLevel = value)
                                            fuelBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("tanks.freshWater.") && path.endsWith(".currentLevel")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(freshWaterLevel = value)
                                            freshWaterBuffer.add(value)
                                            stateUpdated = true
                                        }
                                    } else if (path.startsWith("tanks.wasteWater.") && path.endsWith(".currentLevel")) {
                                        if (!value.isNaN()) {
                                            state = state.copy(wasteWaterLevel = value)
                                            wasteBuffer.add(value)
                                            stateUpdated = true
                                        }
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
                val finalState = calculateSetAndDrift(state)
                _currentState = finalState
                notifyListeners(finalState)
            } else if ((aisTarget != null) && (aisTarget.latitude != null) && (aisTarget.longitude != null)) {
                engineScope.launch { aisListener?.invoke(aisTarget) }
            }
        } catch (e: Exception) {
            log.error("JSON parsing error: ${e.message}")
        }
    }

    private fun calculateSetAndDrift(state: MarineState): MarineState {
        if (state.drift != null && state.setTrue != null) return state

        val sog = state.speedOverGround ?: return state
        val cog = state.courseOverGroundTrue ?: return state
        val stw = state.speedThroughWater ?: return state
        val hdg = state.headingTrue ?: return state

        // Vector B (COG/SOG): Track over ground
        val bx = sog * sin(cog)
        val by = sog * cos(cog)

        // Vector A (HDG/STW): Movement through water
        val ax = stw * sin(hdg)
        val ay = stw * cos(hdg)

        // Vector C = B - A (Current vector)
        val cx = bx - ax
        val cy = by - ay

        val drift = sqrt(cx * cx + cy * cy)
        val set = (atan2(cx, cy) + 2 * PI) % (2 * PI)

        return state.copy(drift = drift, setTrue = set)
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
