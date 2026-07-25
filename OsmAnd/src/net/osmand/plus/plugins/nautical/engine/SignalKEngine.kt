package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import net.osmand.shared.util.KMapUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import android.content.Context
import java.io.File
import java.io.ObjectInputStream
import java.io.Serializable

class SignalKEngine {
    private val log = PlatformUtil.getLog(SignalKEngine::class.java)
    val dataBroker = SignalKDataBroker()

    @Volatile
    private var _currentState: MarineState? = null
    private val aisCache = ConcurrentHashMap<Int, AisTarget>()

    private val stateLock = Any()

    var onConnectionLost: (() -> Unit)? = null
    var onConnectionError: (() -> Unit)? = null
    var onConnectionRestored: (() -> Unit)? = null
    var onRouteStepProcessed: (() -> Unit)? = null
    var deltaSender: ((String) -> Unit)? = null

    private val stateListeners = java.util.concurrent.CopyOnWriteArraySet<(MarineState) -> Unit>()
    private var aisListener: ((AisTarget) -> Unit)? = null

    private var trueSelfContext: String = "vessels.self"
    private var watchdogJob: Job? = null
    private var cleanupJob: Job? = null
    private var lastUpdateTimestamp: Long = 0

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val depthBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val windBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val windDirectionBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val vmgBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val cogBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val sogBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val stwBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val rpmBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val tempEngineBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val voltBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val socBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val xteBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val waterTempBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val outsideTempBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val pressureBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val driftBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val setTrueBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val rollBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val pitchBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val awaBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val awsBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val twaBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val rotBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val ttwBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val dtwBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val polarRatioBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val magHdgBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val logBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val tripLogBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val depthKeelBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val fuelBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val freshWaterBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val wasteBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val oilPressureBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val engineLoadBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val coolantTempBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val batteryCurrentBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val solarCurrentBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val twdBuffer = CircularBuffer<Pair<Double, Long>>(3600)
    private val trajectoryBuffer = CircularBuffer<Pair<Double, Double>>(1000)
    private val routeQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Double, Double>>()
    var isFollowingRoute: Boolean = false
        private set

    var xteThresholdNm: Double = 0.1
    var vesselDraft: Double = 0.0

    fun getCurrentState(): MarineState? = _currentState

    @Synchronized
    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        onConnectionLost = null
        onConnectionError = null
        onConnectionRestored = null
        onRouteStepProcessed = null
        dataBroker.stop()
        engineScope.cancel()
        stateListeners.clear()
        aisListener = null
        aisCache.clear()
        routeQueue.clear()
        _currentState = null
        isFollowingRoute = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun saveBuffersToDisk(context: Context, sync: Boolean = false) {
        val saveAction = suspend {
            val root = JSONObject()
            
            fun bufferToJson(buffer: CircularBuffer<Pair<Double, Long>>): JSONArray {
                val array = JSONArray()
                buffer.getAll().forEach { (value, time) ->
                    val obj = JSONObject()
                    obj.put("v", value)
                    obj.put("t", time)
                    array.put(obj)
                }
                return array
            }

            root.put("depth", bufferToJson(depthBuffer))
            root.put("wind", bufferToJson(windBuffer))
            root.put("wind_dir", bufferToJson(windDirectionBuffer))
            root.put("vmg", bufferToJson(vmgBuffer))
            root.put("cog", bufferToJson(cogBuffer))
            root.put("sog", bufferToJson(sogBuffer))
            root.put("stw", bufferToJson(stwBuffer))
            root.put("rpm", bufferToJson(rpmBuffer))
            root.put("temp_eng", bufferToJson(tempEngineBuffer))
            root.put("volt", bufferToJson(voltBuffer))
            root.put("soc", bufferToJson(socBuffer))
            root.put("xte", bufferToJson(xteBuffer))
            root.put("water_temp", bufferToJson(waterTempBuffer))
            root.put("outside_temp", bufferToJson(outsideTempBuffer))
            root.put("pressure", bufferToJson(pressureBuffer))
            root.put("drift", bufferToJson(driftBuffer))
            root.put("set_true", bufferToJson(setTrueBuffer))
            root.put("roll", bufferToJson(rollBuffer))
            root.put("pitch", bufferToJson(pitchBuffer))
            root.put("awa", bufferToJson(awaBuffer))
            root.put("aws", bufferToJson(awsBuffer))
            root.put("twa", bufferToJson(twaBuffer))
            root.put("rot", bufferToJson(rotBuffer))
            root.put("ttw", bufferToJson(ttwBuffer))
            root.put("dtw", bufferToJson(dtwBuffer))
            root.put("polar_ratio", bufferToJson(polarRatioBuffer))
            root.put("mag_hdg", bufferToJson(magHdgBuffer))
            root.put("log", bufferToJson(logBuffer))
            root.put("trip_log", bufferToJson(tripLogBuffer))
            root.put("depth_keel", bufferToJson(depthKeelBuffer))
            root.put("fuel", bufferToJson(fuelBuffer))
            root.put("fresh_water", bufferToJson(freshWaterBuffer))
            root.put("waste", bufferToJson(wasteBuffer))
            root.put("oil_pressure", bufferToJson(oilPressureBuffer))
            root.put("engine_load", bufferToJson(engineLoadBuffer))
            root.put("coolant_temp", bufferToJson(coolantTempBuffer))
            root.put("battery_current", bufferToJson(batteryCurrentBuffer))
            root.put("solar_current", bufferToJson(solarCurrentBuffer))
            root.put("twd", bufferToJson(twdBuffer))

            val trajectoryArray = JSONArray()
            trajectoryBuffer.getAll().forEach { (lat, lon) ->
                val obj = JSONObject()
                obj.put("lat", lat)
                obj.put("lon", lon)
                trajectoryArray.put(obj)
            }
            root.put("trajectory", trajectoryArray)

            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, "nautical_history.json")
                    file.writeText(root.toString())
                    File(context.filesDir, "nautical_history.dat").delete()
                } catch (e: Exception) {
                    log.error("Failed to save history: ${e.message}")
                }
            }
        }

        if (sync) {
            runBlocking { saveAction() }
        } else {
            engineScope.launch(Dispatchers.IO) { saveAction() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadBuffersFromDisk(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "nautical_history.json")
        if (!file.exists()) {
            loadLegacyBuffers(context)
            return@withContext
        }
        try {
            val root = JSONObject(file.readText())
            
            fun jsonToBuffer(key: String, buffer: CircularBuffer<Pair<Double, Long>>) {
                val array = root.optJSONArray(key) ?: return
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    buffer.add(Pair(obj.getDouble("v"), obj.getLong("t")))
                }
            }

            jsonToBuffer("depth", depthBuffer)
            jsonToBuffer("wind", windBuffer)
            jsonToBuffer("wind_dir", windDirectionBuffer)
            jsonToBuffer("vmg", vmgBuffer)
            jsonToBuffer("cog", cogBuffer)
            jsonToBuffer("sog", sogBuffer)
            jsonToBuffer("stw", stwBuffer)
            jsonToBuffer("rpm", rpmBuffer)
            jsonToBuffer("temp_eng", tempEngineBuffer)
            jsonToBuffer("volt", voltBuffer)
            jsonToBuffer("soc", socBuffer)
            jsonToBuffer("xte", xteBuffer)
            jsonToBuffer("water_temp", waterTempBuffer)
            jsonToBuffer("outside_temp", outsideTempBuffer)
            jsonToBuffer("pressure", pressureBuffer)
            jsonToBuffer("drift", driftBuffer)
            jsonToBuffer("set_true", setTrueBuffer)
            jsonToBuffer("roll", rollBuffer)
            jsonToBuffer("pitch", pitchBuffer)
            jsonToBuffer("awa", awaBuffer)
            jsonToBuffer("aws", awsBuffer)
            jsonToBuffer("twa", twaBuffer)
            jsonToBuffer("rot", rotBuffer)
            jsonToBuffer("ttw", ttwBuffer)
            jsonToBuffer("dtw", dtwBuffer)
            jsonToBuffer("polar_ratio", polarRatioBuffer)
            jsonToBuffer("mag_hdg", magHdgBuffer)
            jsonToBuffer("log", logBuffer)
            jsonToBuffer("trip_log", tripLogBuffer)
            jsonToBuffer("depth_keel", depthKeelBuffer)
            jsonToBuffer("fuel", fuelBuffer)
            jsonToBuffer("fresh_water", freshWaterBuffer)
            jsonToBuffer("waste", wasteBuffer)
            jsonToBuffer("oil_pressure", oilPressureBuffer)
            jsonToBuffer("engine_load", engineLoadBuffer)
            jsonToBuffer("coolant_temp", coolantTempBuffer)
            jsonToBuffer("battery_current", batteryCurrentBuffer)
            jsonToBuffer("solar_current", solarCurrentBuffer)
            jsonToBuffer("twd", twdBuffer)

            val trajectoryArray = root.optJSONArray("trajectory")
            if (trajectoryArray != null) {
                for (i in 0 until trajectoryArray.length()) {
                    val obj = trajectoryArray.getJSONObject(i)
                    trajectoryBuffer.add(Pair(obj.getDouble("lat"), obj.getDouble("lon")))
                }
            }
        } catch (e: Exception) {
            log.error("Failed to load history: ${e.message}")
        }
    }

    private fun loadLegacyBuffers(context: Context) {
        fun <T> load(fileName: String, action: (T) -> Unit) {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return
            try {
                ObjectInputStream(file.inputStream()).use { ois ->
                    val data = ois.readObject()
                    if (data is Collection<*>) {
                        data.forEach { item ->
                            @Suppress("UNCHECKED_CAST")
                            when {
                                fileName == "trajectory_buffer.dat" && item is Pair<*, *> -> action(item as T)
                                item is Pair<*, *> -> action(item as T)
                                item is Double -> action(Pair(item, System.currentTimeMillis()) as T)
                            }
                        }
                    }
                }
                file.delete() // Clean up legacy file
            } catch (e: Exception) {
                log.error("Failed to load $fileName: ${e.message}")
            }
        }

        load<Pair<Double, Long>>("depth_buffer.dat") { depthBuffer.add(it) }
        load<Pair<Double, Long>>("wind_buffer.dat") { windBuffer.add(it) }
        load<Pair<Double, Long>>("wind_direction_buffer.dat") { windDirectionBuffer.add(it) }
        load<Pair<Double, Long>>("vmg_buffer.dat") { vmgBuffer.add(it) }
        load<Pair<Double, Long>>("cog_buffer.dat") { cogBuffer.add(it) }
        load<Pair<Double, Long>>("sog_buffer.dat") { sogBuffer.add(it) }
        load<Pair<Double, Long>>("stw_buffer.dat") { stwBuffer.add(it) }
        load<Pair<Double, Long>>("rpm_buffer.dat") { rpmBuffer.add(it) }
        load<Pair<Double, Long>>("temp_engine_buffer.dat") { tempEngineBuffer.add(it) }
        load<Pair<Double, Long>>("volt_buffer.dat") { voltBuffer.add(it) }
        load<Pair<Double, Long>>("soc_buffer.dat") { socBuffer.add(it) }
        load<Pair<Double, Long>>("xte_buffer.dat") { xteBuffer.add(it) }
        load<Pair<Double, Long>>("water_temp_buffer.dat") { waterTempBuffer.add(it) }
        load<Pair<Double, Long>>("outside_temp_buffer.dat") { outsideTempBuffer.add(it) }
        load<Pair<Double, Long>>("pressure_buffer.dat") { pressureBuffer.add(it) }
        load<Pair<Double, Long>>("drift_buffer.dat") { driftBuffer.add(it) }
        load<Pair<Double, Long>>("set_true_buffer.dat") { setTrueBuffer.add(it) }
        load<Pair<Double, Long>>("roll_buffer.dat") { rollBuffer.add(it) }
        load<Pair<Double, Long>>("pitch_buffer.dat") { pitchBuffer.add(it) }
        load<Pair<Double, Long>>("awa_buffer.dat") { awaBuffer.add(it) }
        load<Pair<Double, Long>>("aws_buffer.dat") { awsBuffer.add(it) }
        load<Pair<Double, Long>>("twa_buffer.dat") { twaBuffer.add(it) }
        load<Pair<Double, Long>>("rot_buffer.dat") { rotBuffer.add(it) }
        load<Pair<Double, Long>>("ttw_buffer.dat") { ttwBuffer.add(it) }
        load<Pair<Double, Long>>("dtw_buffer.dat") { dtwBuffer.add(it) }
        load<Pair<Double, Long>>("polar_ratio_buffer.dat") { polarRatioBuffer.add(it) }
        load<Pair<Double, Long>>("mag_hdg_buffer.dat") { magHdgBuffer.add(it) }
        load<Pair<Double, Long>>("log_buffer.dat") { logBuffer.add(it) }
        load<Pair<Double, Long>>("trip_log_buffer.dat") { tripLogBuffer.add(it) }
        load<Pair<Double, Long>>("depth_keel_buffer.dat") { depthKeelBuffer.add(it) }
        load<Pair<Double, Long>>("fuel_buffer.dat") { fuelBuffer.add(it) }
        load<Pair<Double, Long>>("fresh_water_buffer.dat") { freshWaterBuffer.add(it) }
        load<Pair<Double, Long>>("waste_buffer.dat") { wasteBuffer.add(it) }
        load<Pair<Double, Long>>("oil_pressure_buffer.dat") { oilPressureBuffer.add(it) }
        load<Pair<Double, Long>>("engine_load_buffer.dat") { engineLoadBuffer.add(it) }
        load<Pair<Double, Long>>("battery_current_buffer.dat") { batteryCurrentBuffer.add(it) }
        load<Pair<Double, Long>>("solar_current_buffer.dat") { solarCurrentBuffer.add(it) }
        load<Pair<Double, Long>>("twd_buffer.dat") { twdBuffer.add(it) }
        load<Pair<Double, Double>>("trajectory_buffer.dat") { trajectoryBuffer.add(it) }
    }

    fun clearRoute() {
        routeQueue.clear()
        isFollowingRoute = false
        log.info("Route cleared. Manual control engaged.")
    }

    fun dispatchCommand(command: String) {
        log.debug("Dispatching: $command")
        val parts = command.split(":", limit = 2)
        if (parts.size < 2) return
        
        val path = when (parts[0]) {
            "CALIBRATE_COMPASS" -> "steering.autopilot.actions.calibrateCompass"
            else -> return
        }
        
        val value = parts[1]
        val payload = """
            {
                "updates": [
                    {
                        "values": [
                            {
                                "path": "$path",
                                "value": "$value"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        
        deltaSender?.invoke(payload)
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
            var previouslyDisconnected = false
            while (isActive) {
                try {
                    val delayTime = if (powerSaveMode) 5000L else 1000L
                    delay(delayTime.milliseconds)
                    val elapsed = System.currentTimeMillis() - lastUpdateTimestamp
                    if (elapsed > 10000) {
                        if (!previouslyDisconnected) {
                            previouslyDisconnected = true
                            _currentState = null
                            isFollowingRoute = false
                            notifyListeners(MarineState(connectionStatus = ConnectionStatus.DISCONNECTED))
                            log.error("Data timeout!")
                            onConnectionLost?.invoke()
                        }
                    } else if (elapsed > 5000) {
                        previouslyDisconnected = false
                        if (_currentState?.connectionStatus != ConnectionStatus.STALE) {
                            _currentState = _currentState?.copy(connectionStatus = ConnectionStatus.STALE)
                            _currentState?.let { notifyListeners(it) }
                        }
                    } else {
                        if (previouslyDisconnected) {
                            previouslyDisconnected = false
                            onConnectionRestored?.invoke()
                        }
                        if (_currentState?.connectionStatus != ConnectionStatus.CONNECTED) {
                            _currentState = _currentState?.copy(connectionStatus = ConnectionStatus.CONNECTED)
                        }

                        // Per-field selective staleness check
                        _currentState?.let { current ->
                            val now = System.currentTimeMillis()
                            var modified = false
                            var nextState = current

                            val staleThreshold = 5000L
                            val timestamps = current.timestamps

                            fun isStale(path: String) = (now - (timestamps[path] ?: 0L)) > staleThreshold

                            if (current.speedOverGround != null && isStale("navigation.speedOverGround")) {
                                nextState = nextState.copy(speedOverGround = null)
                                modified = true
                            }
                            if (current.courseOverGroundTrue != null && isStale("navigation.courseOverGroundTrue")) {
                                nextState = nextState.copy(courseOverGroundTrue = null)
                                modified = true
                            }
                            if (current.headingTrue != null && isStale("navigation.headingTrue")) {
                                nextState = nextState.copy(headingTrue = null)
                                modified = true
                            }
                            if (current.speedThroughWater != null && isStale("navigation.speedThroughWater")) {
                                nextState = nextState.copy(speedThroughWater = null)
                                modified = true
                            }
                            if (current.depthBelowTransducer != null && isStale("environment.depth.belowTransducer")) {
                                nextState = nextState.copy(depthBelowTransducer = null)
                                modified = true
                            }
                            if (current.windDirectionApparent != null && isStale("environment.wind.angleApparent")) {
                                nextState = nextState.copy(windDirectionApparent = null)
                                modified = true
                            }

                            if (modified) {
                                _currentState = nextState
                                notifyListeners(nextState)
                            } else if (current.connectionStatus != ConnectionStatus.CONNECTED) {
                                notifyListeners(current.copy(connectionStatus = ConnectionStatus.CONNECTED))
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Watchdog loop error: ${e.message}")
                    if (e is CancellationException) throw e
                }
            }
        }

        cleanupJob?.cancel()
        cleanupJob = engineScope.launch {
            while (isActive) {
                try {
                    delay(60000.milliseconds) // Cleanup every minute
                    val now = System.currentTimeMillis()
                    val it = aisCache.entries.iterator()
                    while (it.hasNext()) {
                        val entry = it.next()
                        if (now - entry.value.lastUpdate > 600000) { // 10 minutes TTL
                            it.remove()
                            log.debug("AIS target ${entry.key} expired and removed from cache.")
                        }
                    }
                } catch (e: Exception) {
                    log.error("Cleanup loop error: ${e.message}")
                    if (e is CancellationException) throw e
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

    fun getDepthHistory(): List<Pair<Double, Long>> = depthBuffer.getAll()
    fun getWindHistory(): List<Pair<Double, Long>> = windBuffer.getAll()
    fun getWindDirectionHistory(): List<Pair<Double, Long>> = windDirectionBuffer.getAll()
    fun getVmgHistory(): List<Pair<Double, Long>> = vmgBuffer.getAll()
    fun getCogHistory(): List<Pair<Double, Long>> = cogBuffer.getAll()
    fun getSogHistory(): List<Pair<Double, Long>> = sogBuffer.getAll()
    fun getStwHistory(): List<Pair<Double, Long>> = stwBuffer.getAll()
    fun getRpmHistory(): List<Pair<Double, Long>> = rpmBuffer.getAll()
    fun getTempEngineHistory(): List<Pair<Double, Long>> = tempEngineBuffer.getAll()
    fun getVoltHistory(): List<Pair<Double, Long>> = voltBuffer.getAll()
    fun getSocHistory(): List<Pair<Double, Long>> = socBuffer.getAll()
    fun getXteHistory(): List<Pair<Double, Long>> = xteBuffer.getAll()
    fun getWaterTempHistory(): List<Pair<Double, Long>> = waterTempBuffer.getAll()
    fun getOutsideTempHistory(): List<Pair<Double, Long>> = outsideTempBuffer.getAll()
    fun getPressureHistory(): List<Pair<Double, Long>> = pressureBuffer.getAll()
    fun getDriftHistory(): List<Pair<Double, Long>> = driftBuffer.getAll()
    fun getRollHistory(): List<Pair<Double, Long>> = rollBuffer.getAll()
    fun getPitchHistory(): List<Pair<Double, Long>> = pitchBuffer.getAll()
    fun getAwaHistory(): List<Pair<Double, Long>> = awaBuffer.getAll()
    fun getAwsHistory(): List<Pair<Double, Long>> = awsBuffer.getAll()
    fun getTwaHistory(): List<Pair<Double, Long>> = twaBuffer.getAll()
    fun getRotHistory(): List<Pair<Double, Long>> = rotBuffer.getAll()
    fun getTtwHistory(): List<Pair<Double, Long>> = ttwBuffer.getAll()
    fun getDtwHistory(): List<Pair<Double, Long>> = dtwBuffer.getAll()
    fun getPolarRatioHistory(): List<Pair<Double, Long>> = polarRatioBuffer.getAll()
    fun getMagHdgHistory(): List<Pair<Double, Long>> = magHdgBuffer.getAll()
    fun getLogHistory(): List<Pair<Double, Long>> = logBuffer.getAll()
    fun getTripLogHistory(): List<Pair<Double, Long>> = tripLogBuffer.getAll()
    fun getDepthKeelHistory(): List<Pair<Double, Long>> = depthKeelBuffer.getAll()
    fun getFuelHistory(): List<Pair<Double, Long>> = fuelBuffer.getAll()
    fun getFreshWaterHistory(): List<Pair<Double, Long>> = freshWaterBuffer.getAll()
    fun getWasteHistory(): List<Pair<Double, Long>> = wasteBuffer.getAll()
    fun getOilPressureHistory(): List<Pair<Double, Long>> = oilPressureBuffer.getAll()
    fun getEngineLoadHistory(): List<Pair<Double, Long>> = engineLoadBuffer.getAll()
    fun getCoolantTempHistory(): List<Pair<Double, Long>> = coolantTempBuffer.getAll()
    fun getBatteryCurrentHistory(): List<Pair<Double, Long>> = batteryCurrentBuffer.getAll()
    fun getSolarCurrentHistory(): List<Pair<Double, Long>> = solarCurrentBuffer.getAll()
    fun getTwdHistory(): List<Pair<Double, Long>> = twdBuffer.getAll()

    private var lastTrajectoryTimestamp: Long = 0
    private var lastFollowingUpdateTimestamp: Long = 0
    private var lastSetDriftTimestamp: Long = 0
    var arrivalRadiusMeters: Double = 35.0
    
    @Volatile
    private var powerSaveMode: Boolean = false

    fun setPowerSaveMode(enabled: Boolean) {
        powerSaveMode = enabled
        log.info("SignalK Engine Power Save Mode: $enabled")
    }

    fun addTrajectoryPoint(lat: Double, lon: Double) {
        val history = trajectoryBuffer.getAll()
        val last = history.lastOrNull()
        val now = System.currentTimeMillis()

        if (now - lastTrajectoryTimestamp < 5000) return // Throttle trajectory to 0.2Hz (save battery/disk)

        if (last != null) {
            val dist = KMapUtils.getDistance(last.first, last.second, lat, lon)
            val timeGap = now - lastTrajectoryTimestamp
            if (dist > 500.0 && timeGap < 30000) { // 500 meters is a reasonable "jump" threshold for a boat unless it's a long time gap
                log.warn("Jump detected ($dist m)! Discarding point: $lat, $lon")
                return
            }
        }
        trajectoryBuffer.add(Pair(lat, lon))
        lastTrajectoryTimestamp = now
    }

    fun copyTrajectoryTo(target: MutableList<Pair<Double, Double>>) {
        trajectoryBuffer.copyTo(target)
    }

    fun handleIncomingMessage(jsonMessage: String) {
        lastUpdateTimestamp = System.currentTimeMillis()
        resetWatchdog()
        engineScope.launch(Dispatchers.Default) {
            try {
                val json = JSONObject(jsonMessage)
                if (json.has("self")) {
                    trueSelfContext = json.getString("self")
                    // Extract MMSI from context string: "vessels.urn:mrn:imo:mmsi:235084430"
                    if (trueSelfContext.startsWith("vessels.urn:mrn:imo:mmsi:")) {
                        val mmsiStr = trueSelfContext.substringAfterLast(":")
                        val mmsi = mmsiStr.toIntOrNull()
                        if (mmsi != null) {
                            synchronized(stateLock) {
                                _currentState = (_currentState ?: MarineState()).copy(vesselMmsi = mmsi)
                            }
                        }
                    }
                    return@launch
                }

                if (!json.has("updates")) return@launch

                val context = json.optString("context", "vessels.self")
                val updates = json.getJSONArray("updates")
                
                val currentMmsi = synchronized(stateLock) { _currentState?.vesselMmsi }
                val isSelf = (context == "vessels.self") || (context == "") || (context == trueSelfContext) ||
                             (currentMmsi != null && context == "vessels.urn:mrn:imo:mmsi:$currentMmsi")

                var numericMmsi = 0
                if (!isSelf) {
                    val rawId = context.substringAfterLast(":", "")
                    if (rawId.isEmpty()) return@launch
                    numericMmsi = rawId.toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
                }

                val aisTarget = if (!isSelf) aisCache.getOrPut(numericMmsi) { AisTarget(numericMmsi) } else null
                var stateUpdated = false
                var currentBatchState: MarineState? = null

                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    
                    // Process metadata if present in the update
                    if (isSelf && update.has("meta")) {
                        val metaArray = update.optJSONArray("meta")
                        if (metaArray != null) {
                            synchronized(stateLock) {
                                val currentMeta = _currentState?.pathMeta?.toMutableMap() ?: mutableMapOf()
                                for (m in 0 until metaArray.length()) {
                                    val mItem = metaArray.getJSONObject(m)
                                    val mPath = mItem.optString("path")
                                    val mValue = mItem.optJSONObject("value")
                                    if (mPath.isNotEmpty() && mValue != null) {
                                        val metaMap = mutableMapOf<String, Any>()
                                        val keys = mValue.keys()
                                        while (keys.hasNext()) {
                                            val key = keys.next()
                                            metaMap[key] = mValue.get(key)
                                        }
                                        currentMeta[mPath] = metaMap
                                    }
                                }
                                _currentState = (_currentState ?: MarineState()).copy(pathMeta = currentMeta)
                            }
                        }
                    }

                    if (!update.has("values")) continue

                    val values = update.getJSONArray("values")
                    for (j in 0 until values.length()) {
                        val valueItem = values.getJSONObject(j)
                        val path = valueItem.optString("path")
                        val valueObj = valueItem.opt("value")

                        if (isSelf) {
                            if (currentBatchState == null) {
                                synchronized(stateLock) {
                                    currentBatchState = _currentState ?: MarineState()
                                }
                            }
                            val res = parseSelfValue(currentBatchState!!, path, valueItem, valueObj)
                            currentBatchState = res.first
                            if (res.second) stateUpdated = true
                        } else if (aisTarget != null) {
                            updateAisTarget(aisTarget, path, valueItem, valueObj)
                        }
                    }
                }

                if (isSelf && stateUpdated && currentBatchState != null) {
                    val finalState = calculateDepths(calculateSetAndDrift(currentBatchState)).let { s ->
                        val xteNm = abs(s.crossTrackError ?: 0.0) / 1852.0
                        val isOff = xteNm > xteThresholdNm && (s.autopilotState.uppercase(Locale.US) == "TRACK")
                        s.copy(isOffCourse = isOff, connectionStatus = ConnectionStatus.CONNECTED)
                    }
                    synchronized(stateLock) {
                        _currentState = finalState
                    }
                    withContext(Dispatchers.Main) {
                        notifyListeners(finalState)
                    }
                } else if ((aisTarget != null) && (aisTarget.latitude != null) && (aisTarget.longitude != null)) {
                    val copy = aisTarget.copy()
                    aisListener?.invoke(copy)
                }
            } catch (e: Exception) {
                log.error("JSON parsing error: ${e.message}")
            }
        }
    }

    private fun updateAisTarget(aisTarget: AisTarget, path: String, valueItem: JSONObject, valueObj: Any?) {
        aisTarget.lastUpdate = System.currentTimeMillis()
        when (path) {
            "" -> {
                val name = valueItem.optString("name", "")
                if (name.isNotEmpty()) {
                    aisTarget.name = name
                } else {
                    val vName = valueItem.optString("vesselName", "")
                    if (vName.isNotEmpty()) aisTarget.name = vName
                }
                val type = valueItem.optInt("vesselType", -1)
                if (type != -1) aisTarget.vesselType = type
            }
            "name", "vesselName" -> aisTarget.name = valueObj?.toString()
            "design.type" -> {
                if (valueObj is JSONObject) {
                    aisTarget.vesselType = valueObj.optInt("id", -1)
                } else if (valueObj is Number) {
                    aisTarget.vesselType = valueObj.toInt()
                }
            }
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

    private fun parseSelfValue(s: MarineState, path: String, valueItem: JSONObject, valueObj: Any?): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = valueItem.optDouble("value", Double.NaN)
        val now = System.currentTimeMillis()
        val newTimestamps = state.timestamps.toMutableMap()
        newTimestamps[path] = now

        when (path) {
            "navigation.position" -> {
                if (valueObj is JSONObject) {
                    val lat = valueObj.optDouble("latitude", Double.NaN)
                    val lon = valueObj.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        state = state.copy(latitude = lat, longitude = lon, timestamps = newTimestamps)
                        updated = true
                        updateFollowingState(lat, lon)
                        addTrajectoryPoint(lat, lon)
                    }
                }
            }
            "navigation.headingTrue" -> {
                val heading = valueItem.optDouble("value", Double.NaN)
                if (!heading.isNaN()) {
                    state = state.copy(headingTrue = heading, timestamps = newTimestamps)
                    dataBroker.processHeadingUpdate(heading)
                    updated = true
                }
            }
            "navigation.headingMagnetic" -> {
                val heading = valueItem.optDouble("value", Double.NaN)
                if (!heading.isNaN()) {
                    state = state.copy(headingMagnetic = heading, timestamps = newTimestamps)
                    magHdgBuffer.add(Pair(heading, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.magneticVariation" -> {
                val variation = valueItem.optDouble("value", Double.NaN)
                if (!variation.isNaN()) {
                    state = state.copy(magneticVariation = variation, timestamps = newTimestamps)
                    updated = true
                }
            }
            "navigation.log" -> {
                val logVal = valueItem.optDouble("value", Double.NaN)
                if (!logVal.isNaN()) {
                    state = state.copy(log = logVal, timestamps = newTimestamps)
                    logBuffer.add(Pair(logVal, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.trip.log" -> {
                val logVal = valueItem.optDouble("value", Double.NaN)
                if (!logVal.isNaN()) {
                    state = state.copy(tripLog = logVal, timestamps = newTimestamps)
                    tripLogBuffer.add(Pair(logVal, lastUpdateTimestamp))
                    updated = true
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
                        yaw = if (yaw.isNaN()) state.yaw else yaw,
                        timestamps = newTimestamps
                    )
                    if (!roll.isNaN()) rollBuffer.add(Pair(roll, lastUpdateTimestamp))
                    if (!pitch.isNaN()) pitchBuffer.add(Pair(pitch, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.speedOverGround" -> {
                val sog = valueItem.optDouble("value", Double.NaN)
                if (!sog.isNaN()) {
                    state = state.copy(speedOverGround = sog, timestamps = newTimestamps)
                    sogBuffer.add(Pair(sog, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.speedThroughWater" -> {
                val stw = valueItem.optDouble("value", Double.NaN)
                if (!stw.isNaN()) {
                    state = state.copy(speedThroughWater = stw, timestamps = newTimestamps)
                    stwBuffer.add(Pair(stw, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.rateOfTurn" -> {
                val rot = valueItem.optDouble("value", Double.NaN)
                if (!rot.isNaN()) {
                    state = state.copy(rateOfTurn = rot, timestamps = newTimestamps)
                    rotBuffer.add(Pair(rot, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.drift" -> {
                val drift = valueItem.optDouble("value", Double.NaN)
                if (!drift.isNaN()) {
                    state = state.copy(drift = drift, timestamps = newTimestamps)
                    driftBuffer.add(Pair(drift, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.closestApproach" -> {
                if (valueObj is JSONObject) {
                    val cpa = valueObj.optDouble("cpa", Double.NaN)
                    val tcpa = valueObj.optDouble("tcpa", Double.NaN)
                    val name = valueObj.optString("name", "Unknown Vessel")
                    if (!cpa.isNaN() && !tcpa.isNaN()) {
                        dataBroker.updateClosestApproach(cpa, tcpa, name)
                        state = state.copy(timestamps = newTimestamps)
                        updated = true
                    }
                }
            }
            "navigation.setTrue" -> {
                val set = valueItem.optDouble("value", Double.NaN)
                if (!set.isNaN()) {
                    state = state.copy(setTrue = set, timestamps = newTimestamps)
                    setTrueBuffer.add(Pair(set, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.courseOverGroundTrue" -> {
                val cog = valueItem.optDouble("value", Double.NaN)
                if (!cog.isNaN()) {
                    state = state.copy(courseOverGroundTrue = cog, timestamps = newTimestamps)
                    cogBuffer.add(Pair(cog, lastUpdateTimestamp))
                    updated = true
                }
            }
            "performance.velocityMadeGood" -> {
                val vmg = valueItem.optDouble("value", Double.NaN)
                if (!vmg.isNaN()) {
                    state = state.copy(velocityMadeGood = vmg, timestamps = newTimestamps)
                    vmgBuffer.add(Pair(vmg, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.crossTrackError" -> {
                if (!value.isNaN()) {
                    if (state.crossTrackError == null) {
                        state = state.copy(crossTrackError = value, timestamps = newTimestamps)
                        xteBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                }
            }
            "navigation.courseRhumbline.crossTrackError" -> {
                if (!value.isNaN()) {
                    state = state.copy(crossTrackError = value, timestamps = newTimestamps)
                    xteBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            "navigation.courseGreatCircle.crossTrackError" -> {
                if (!value.isNaN()) {
                    if (state.crossTrackError == null) {
                        state = state.copy(crossTrackError = value, timestamps = newTimestamps)
                        xteBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                }
            }
            "steering.autopilot.state" -> {
                val raw = valueItem.optString("value", "standby").uppercase(Locale.US)
                val normalized = when (raw) {
                    "ROUTE", "TRACK" -> "track"
                    else -> raw.lowercase(Locale.US)
                }
                var nextPendingMode = state.pendingAutopilotState
                if (normalized == nextPendingMode?.lowercase(Locale.US)) {
                    nextPendingMode = null
                }
                state = state.copy(autopilotState = normalized, pendingAutopilotState = nextPendingMode, timestamps = newTimestamps)
                dataBroker.updateAutopilotState(normalized)
                updated = true
            }
            "steering.rudderAngle" -> {
                val rudder = valueItem.optDouble("value", Double.NaN)
                if (!rudder.isNaN()) {
                    state = state.copy(rudderAngle = rudder, timestamps = newTimestamps)
                    updated = true
                }
            }
            "steering.autopilot.target.headingTrue" -> {
                val target = valueItem.optDouble("value", Double.NaN)
                if (!target.isNaN()) {
                    var nextPendingHeading = state.pendingTargetHeading
                    if (nextPendingHeading != null && kotlin.math.abs(target - nextPendingHeading) < 0.02) { // ~1 degree
                        nextPendingHeading = null
                    }
                    state = state.copy(targetHeading = target, pendingTargetHeading = nextPendingHeading, timestamps = newTimestamps)
                    // Push to dataBroker if needed, requirements specifically asked for headingMagnetic
                    // but we can push headingTrue if that's what's available as a fallback.
                    updated = true
                }
            }
            "steering.autopilot.target.headingMagnetic" -> {
                val target = valueItem.optDouble("value", Double.NaN)
                if (!target.isNaN()) {
                    dataBroker.updateAutopilotTargetHeadingMag(target)
                    state = state.copy(timestamps = newTimestamps)
                    updated = true
                }
            }
            "steering.autopilot.target.windAngleApparent" -> {
                val target = valueItem.optDouble("value", Double.NaN)
                if (!target.isNaN()) {
                    state = state.copy(targetWindAngleApparent = target, timestamps = newTimestamps)
                    updated = true
                }
            }
            "steering.autopilot.seaState" -> {
                val level = valueItem.optInt("value", -1)
                if (level != -1) {
                    state = state.copy(seaState = level, timestamps = newTimestamps)
                    updated = true
                }
            }
            "name" -> {
                val name = valueItem.optString("value", "")
                if (name.isNotEmpty()) {
                    state = state.copy(vesselName = name, timestamps = newTimestamps)
                    updated = true
                }
            }
            "design.type" -> {
                val type = if (valueObj is JSONObject) valueObj.optInt("id", -1) else (valueObj as? Number)?.toInt() ?: -1
                if (type != -1) {
                    state = state.copy(vesselType = type, timestamps = newTimestamps)
                    updated = true
                }
            }
            "design.length.overall" -> {
                val value = valueItem.optDouble("value", Double.NaN)
                if (!value.isNaN()) {
                    state = state.copy(vesselLength = value, timestamps = newTimestamps)
                    updated = true
                }
            }
            "design.beam" -> {
                val value = valueItem.optDouble("value", Double.NaN)
                if (!value.isNaN()) {
                    state = state.copy(vesselBeam = value, timestamps = newTimestamps)
                    updated = true
                }
            }
            "navigation.callsign" -> {
                val callSign = valueItem.optString("value", "")
                if (callSign.isNotEmpty()) {
                    state = state.copy(vesselCallSign = callSign, timestamps = newTimestamps)
                    updated = true
                }
            }
            "propulsion.*.coolantTemperature" -> {
                if (!value.isNaN()) {
                    state = state.copy(engineCoolantTemperature = value, timestamps = newTimestamps)
                    updated = true
                }
            }
            "propulsion.*.state" -> {
                val v = valueItem.optString("value", "")
                if (v.isNotEmpty()) {
                    state = state.copy(engineState = v, timestamps = newTimestamps)
                    updated = true
                }
            }
            else -> {
                val res = parseTelemetryValue(state, path, valueItem, valueObj)
                state = res.first.copy(timestamps = newTimestamps)
                updated = res.second
                
                // FALLBACK: If not handled by hardcoded paths, store in customValues for dynamic widgets
                if (!updated && valueObj is Number) {
                    val custom = state.customValues.toMutableMap()
                    custom[path] = valueObj.toDouble()
                    state = state.copy(customValues = custom)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseTelemetryValue(s: MarineState, path: String, valueItem: JSONObject, valueObj: Any?): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = valueItem.optDouble("value", Double.NaN)

        when {
            path == "environment.depth.belowTransducer" -> {
                if (!value.isNaN()) {
                    state = state.copy(depthBelowTransducer = value)
                    depthBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.depth.surfaceToTransducer" -> {
                if (!value.isNaN()) {
                    state = state.copy(depthSurfaceToTransducer = value)
                    updated = true
                }
            }
            path == "environment.depth.belowKeel" -> {
                if (!value.isNaN()) {
                    state = state.copy(depthBelowKeel = value)
                    depthKeelBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.water.temperature" -> {
                if (!value.isNaN()) {
                    state = state.copy(waterTemperature = value)
                    waterTempBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.outside.temperature" -> {
                if (!value.isNaN()) {
                    state = state.copy(outsideTemperature = value)
                    outsideTempBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.outside.pressure" -> {
                if (!value.isNaN()) {
                    state = state.copy(outsidePressure = value)
                    pressureBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.wind.speedTrue" -> {
                if (!value.isNaN()) {
                    state = state.copy(windSpeedTrue = value)
                    windBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.wind.directionTrue" -> {
                if (!value.isNaN()) {
                    state = state.copy(windDirectionTrue = value)
                    windDirectionBuffer.add(Pair(value, lastUpdateTimestamp))
                    twdBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.wind.angleApparent" -> {
                if (!value.isNaN()) {
                    state = state.copy(windDirectionApparent = value)
                    dataBroker.processWindAngleUpdate(value)
                    awaBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.wind.speedApparent" -> {
                if (!value.isNaN()) {
                    state = state.copy(windSpeedApparent = value)
                    awsBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "environment.wind.angleTrue" -> {
                if (!value.isNaN()) {
                    state = state.copy(trueWindAngle = value)
                    twaBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path == "performance.targetSpeed" -> {
                if (!value.isNaN()) {
                    state = state.copy(polarTargetSpeed = value)
                    updated = true
                }
            }
            path == "performance.polarSpeedRatio" -> {
                if (!value.isNaN()) {
                    state = state.copy(polarSpeedRatio = value)
                    polarRatioBuffer.add(Pair(value, lastUpdateTimestamp))
                    updated = true
                }
            }
            path.startsWith("propulsion.") -> {
                val instance = path.substringAfter("propulsion.").substringBefore(".")
                if (state.engineInstance == null || state.engineInstance == instance) {
                    state = state.copy(engineInstance = instance)
                } else {
                    // For now we only track one engine in the summary state. 
                    // If multiple exist, we prefer 'started' ones or just the first one seen.
                }

                if (path.endsWith(".revolutions")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineRpm = value * 60.0)
                        rpmBuffer.add(Pair(value * 60.0, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".temperature")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineTemperature = value)
                        tempEngineBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".oilPressure")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineOilPressure = value)
                        oilPressureBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".engineLoad")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineLoad = value)
                        engineLoadBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".coolantTemperature")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineCoolantTemperature = value)
                        coolantTempBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".state")) {
                    val v = valueItem.optString("value", "")
                    if (v.isNotEmpty()) {
                        state = state.copy(engineState = v)
                        updated = true
                    }
                } else if (path.endsWith(".runTime")) {
                    if (!value.isNaN()) {
                        state = state.copy(engineRunTime = value)
                        updated = true
                    }
                }
            }
            path.startsWith("electrical.") -> {
                if (path.endsWith(".state")) {
                    val v = when (valueObj) {
                        is Boolean -> valueObj
                        is Number -> valueObj.toInt() != 0
                        is String -> valueObj.lowercase(Locale.US) == "on" || valueObj == "1"
                        else -> false
                    }
                    val switchPath = path.removeSuffix(".state")
                    val updatedSwitches = state.switches.toMutableMap()
                    updatedSwitches[switchPath] = v
                    state = state.copy(switches = updatedSwitches)
                    updated = true
                } else if (path.endsWith(".voltage")) {
                    if (!value.isNaN()) {
                        state = state.copy(batteryVoltage = value)
                        voltBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                } else if (path.endsWith(".current")) {
                    if (path.contains(".solar.")) {
                        if (!value.isNaN()) {
                            state = state.copy(solarCurrent = value)
                            solarCurrentBuffer.add(Pair(value, lastUpdateTimestamp))
                            updated = true
                        }
                    } else {
                        if (!value.isNaN()) {
                            state = state.copy(batteryCurrent = value)
                            batteryCurrentBuffer.add(Pair(value, lastUpdateTimestamp))
                            updated = true
                        }
                    }
                } else if (path.endsWith(".capacity.stateOfCharge")) {
                    if (!value.isNaN()) {
                        state = state.copy(batterySoc = value)
                        socBuffer.add(Pair(value, lastUpdateTimestamp))
                        updated = true
                    }
                }
            }
            path.startsWith("notifications.") -> {
                if (valueObj is JSONObject) {
                    val message = valueObj.optString("message", "")
                    val stateStr = valueObj.optString("state", "normal").lowercase(Locale.US)
                    val methodArray = valueObj.optJSONArray("method")
                    val methods = mutableListOf<String>()
                    if (methodArray != null) {
                        for (m in 0 until methodArray.length()) {
                            methods.add(methodArray.getString(m))
                        }
                    }

                    val notificationState = when (stateStr) {
                        "alert" -> NotificationState.ALERT
                        "warn" -> NotificationState.WARN
                        "alarm" -> NotificationState.ALARM
                        "emergency" -> NotificationState.EMERGENCY
                        else -> NotificationState.NORMAL
                    }

                    val updatedNotifications = state.notifications.toMutableMap()
                    if (notificationState == NotificationState.NORMAL) {
                        updatedNotifications.remove(path)
                    } else {
                        updatedNotifications[path] = SignalKNotification(message, notificationState, methods)
                    }
                    state = state.copy(notifications = updatedNotifications)
                    updated = true
                } else if (valueObj == null || (valueObj is JSONObject && valueObj.length() == 0)) {
                    // Notification cleared
                    if (state.notifications.containsKey(path)) {
                        val updatedNotifications = state.notifications.toMutableMap()
                        updatedNotifications.remove(path)
                        state = state.copy(notifications = updatedNotifications)
                        updated = true
                    }
                }
            }
            path.startsWith("tanks.") -> {
                if (path.endsWith(".currentLevel")) {
                    if (path.contains(".fuel.")) {
                        if (!value.isNaN()) {
                            state = state.copy(fuelLevel = value)
                            fuelBuffer.add(Pair(value, lastUpdateTimestamp))
                            updated = true
                        }
                    } else if (path.contains(".freshWater.")) {
                        if (!value.isNaN()) {
                            state = state.copy(freshWaterLevel = value)
                            freshWaterBuffer.add(Pair(value, lastUpdateTimestamp))
                            updated = true
                        }
                    } else if (path.contains(".wasteWater.")) {
                        if (!value.isNaN()) {
                            state = state.copy(wasteWaterLevel = value)
                            wasteBuffer.add(Pair(value, lastUpdateTimestamp))
                            updated = true
                        }
                    }
                }
            }
        }
        return Pair(state, updated)
    }

    /**
     * Calculates tidal set and drift by vector subtraction: Current = COG/SOG - HDG/STW.
     * Also derives True Wind Direction (TWD) if True Wind Angle (TWA) is available but TWD is not.
     */
    private fun calculateSetAndDrift(state: MarineState): MarineState {
        var updatedState = state
        
        // 1. Heading True Fallback (if Variation is known)
        if (updatedState.headingTrue == null) {
            val hdgMag = updatedState.headingMagnetic
            val variation = updatedState.magneticVariation
            if (hdgMag != null && variation != null) {
                updatedState = updatedState.copy(headingTrue = (hdgMag + variation + 2 * PI) % (2 * PI))
            }
        }

        val sog = updatedState.speedOverGround ?: return updatedState
        val cog = updatedState.courseOverGroundTrue ?: return updatedState
        val stw = updatedState.speedThroughWater ?: return updatedState
        val hdg = updatedState.headingTrue ?: return updatedState

        // Vector B (COG/SOG): Vessel track over ground
        // 0 rad = North, increasing clockwise
        val bx = sog * sin(cog)
        val by = sog * cos(cog)

        // Vector A (HDG/STW): Vessel movement through water
        val ax = stw * sin(hdg)
        val ay = stw * cos(hdg)

        // Vector C = B - A (Current vector: Set and Drift)
        val cx = bx - ax
        val cy = by - ay

        val drift = sqrt(cx * cx + cy * cy)
        val set = (atan2(cx, cy) + 2 * PI) % (2 * PI)

        val now = System.currentTimeMillis()
        if (now - lastSetDriftTimestamp > 5000) {
            driftBuffer.add(Pair(drift, lastUpdateTimestamp))
            setTrueBuffer.add(Pair(set, lastUpdateTimestamp))
            lastSetDriftTimestamp = now
        }

        var finalState = updatedState.copy(drift = drift, setTrue = set)
        
        // 2. True Wind Direction (TWD) Fallback
        // TWD = HDG (True) + TWA (True Wind Angle relative to boat heading)
        if (finalState.windDirectionTrue == null) {
            val hdgTrue = finalState.headingTrue
            val twa = finalState.trueWindAngle
            if (hdgTrue != null && twa != null) {
                val twd = (hdgTrue + twa + 2 * PI) % (2 * PI)
                finalState = finalState.copy(windDirectionTrue = twd)
                twdBuffer.add(Pair(twd, lastUpdateTimestamp))
            }
        }

        return finalState
    }

    /**
     * Ensures mandatory depth fields (Keel/Surface) are derived if only Transducer is available.
     * Uses vesselDraft as fallback for transducer offset if not provided by Signal K metadata.
     */
    private fun calculateDepths(state: MarineState): MarineState {
        var updated = state
        val draft = vesselDraft
        
        // If we have belowTransducer but missing belowKeel, we can estimate if we know transducer position.
        // Signal K usually provides transducer offset via metadata or specific paths.
        // If not, we use the user-configured vessel draft as the "worst case" offset.
        
        if (updated.depthBelowKeel == null && updated.depthBelowTransducer != null) {
            // Assume transducer is at waterline if no other info (fallback)
            // Or if we know the draft, and assume transducer is somewhat deep... 
            // Better: If user provided NAUTICAL_VESSEL_DRAFT, and Signal K is only giving depthBelowTransducer
            // without offset, depthBelowKeel = depthBelowTransducer - (draft - transducer_depth_below_waterline).
            // Simplification for safety: depthBelowKeel = depthBelowTransducer - draft (if transducer is at surface)
            // but transducer is usually below surface.
            
            // Re-evaluating: depthBelowKeel = depthBelowTransducer - (keel_offset_from_transducer).
            // If Signal K doesn't give us the offset, we can't be sure.
            // But we can surface a "Shallow Water" alert based on depthBelowTransducer vs a threshold.
            
            // For now, if belowKeel is null, we try to derive it from metadata (transducer offset)
            val meta = updated.pathMeta["environment.depth.belowTransducer"]
            val offset = (meta?.get("offset") as? Number)?.toDouble() ?: 0.0
            
            // Signal K Convention: 
            // offset > 0 -> from transducer to surface
            // offset < 0 -> from transducer to keel
            if (offset < 0) {
                 updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer!! + offset)
            } else if (draft > 0) {
                // If no meta offset, use safety margin: assume transducer is at surface and subtract draft
                updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer!! - draft)
            }
        }
        
        return updated
    }

    fun loadRoute(route: List<Pair<Double, Double>>) {
        routeQueue.clear()
        routeQueue.addAll(route)
        isFollowingRoute = true
        onRouteStepProcessed?.invoke()
        log.info("Route loaded: ${route.size} points. Following enabled.")
    }

    fun getNextWaypoint(): Pair<Double, Double>? = routeQueue.peek()

    fun getRoutePoints(): List<Pair<Double, Double>> = routeQueue.toList()

    fun setAutoSeaStateEnabled(enabled: Boolean) {
        _currentState = _currentState?.copy(isAutoSeaStateEnabled = enabled)
        _currentState?.let { notifyListeners(it) }
    }

    fun updatePendingCommand(targetHeading: Double? = null, mode: String? = null) {
        synchronized(stateLock) {
            val current = _currentState ?: MarineState()
            _currentState = current.copy(
                pendingTargetHeading = targetHeading,
                pendingAutopilotState = mode,
                commandSentTimestamp = if (targetHeading != null || mode != null) System.currentTimeMillis() else 0
            )
        }
        _currentState?.let { notifyListeners(it) }
    }

    fun updateFollowingState(currentLat: Double, currentLon: Double) {
        if (!isFollowingRoute || routeQueue.isEmpty()) return
        
        val now = System.currentTimeMillis()
        if (now - lastFollowingUpdateTimestamp < 1000) return // Throttle to 1Hz
        lastFollowingUpdateTimestamp = now

        val target = routeQueue.peek() ?: return
        val distance = KMapUtils.getDistance(currentLat, currentLon, target.first, target.second)

        // Arrival Radius Check
        if (distance < arrivalRadiusMeters) {
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
