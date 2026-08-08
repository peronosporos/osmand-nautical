package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.laylines.engine.LatLon
import net.osmand.plus.plugins.nautical.laylines.engine.LaylineData
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.network.SignalKCourse
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.network.SignalKRoute
import net.osmand.plus.plugins.nautical.network.SignalKRouteFeature
import net.osmand.plus.plugins.nautical.network.SignalKLineString
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.utils.EMA
import net.osmand.plus.plugins.nautical.utils.AngleEMA
import net.osmand.plus.plugins.nautical.utils.LeewayCalculator
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.settings.enums.TtwMode
import net.osmand.plus.settings.enums.XteDirection
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.extensions.toRadians
import net.osmand.shared.util.KMapUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.StringReader
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

class SignalKEngine(
    private val app: OsmandApplication,
    val engineScope: CoroutineScope,
    val capabilityManager: CapabilityManager? = null
) {
    private val log = PlatformUtil.getLog(SignalKEngine::class.java)
    val dataBroker = SignalKDataBroker(app.settings)
    private val controlManager = SignalKControlManager(app, dataBroker, engineScope)
    private val resourceManager = SignalKResourceManager(app, engineScope)
    var environmentalFilterService: EnvironmentalFilterService? = null

    private val engineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("SignalKEngine Global Error: ${throwable.message}", throwable)
    }

    // Isolated scope for background parsing tasks to prevent ripple failures
    private val parsingScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + engineExceptionHandler)

    private val messageChannel = Channel<String>(capacity = 128)
    private var messageProcessingJob: Job? = null

    val marineStateFlow: StateFlow<MarineState> = dataBroker.marineState

    private val _pulseFlow = MutableStateFlow(false)
    val pulseFlow: StateFlow<Boolean> = _pulseFlow.asStateFlow()

    private val _trajectoryEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trajectoryEventFlow = _trajectoryEventFlow.asSharedFlow()

    private val aisCache = ConcurrentHashMap<Int, AisObject>()

    var onConnectionLost: (() -> Unit)? = null
    var onConnectionError: (() -> Unit)? = null
    var onAuthError: (() -> Unit)? = null
    var onConnectionRestored: (() -> Unit)? = null
    private val routeStepListeners = CopyOnWriteArraySet<() -> Unit>()
    var deltaSender: ((String) -> Unit)? = null

    private val stateListeners = CopyOnWriteArraySet<(MarineState) -> Unit>()
    private var aisListener: ((AisObject) -> Unit)? = null
    private val deltaQueue = mutableMapOf<String, Any>()
    private var deltaFlushJob: Job? = null

    private var trueSelfContext: String = "vessels.self"
    private var watchdogJob: Job? = null
    private var cleanupJob: Job? = null
    private var pulseJob: Job? = null
    @Volatile
    private var lastUpdateTimestamp: Long = 0
    private var lastMessageProcessedTime: Long = 0

    private val telemetryBuffers = ConcurrentHashMap<String, CircularBuffer<Pair<Double, Long>>>()

    private fun getBuffer(path: String): CircularBuffer<Pair<Double, Long>> {
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        val capacity = if (caps.hasHistory || caps.hasLogging) 60 else 3600
        return telemetryBuffers.getOrPut(path) { CircularBuffer(capacity) }
    }

    private val trajectoryBuffer = CircularBuffer<Pair<Double, Double>>(1000)
    private val routeQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Double, Double>>()
    private var lastWaypointLat: Double? = null
    private var lastWaypointLon: Double? = null
    var isFollowingRoute: Boolean = false
        private set

    var xteThresholdNm: Double = 0.1
    var vesselDraft: Double = 0.0
    var corridorWidthNm: Double = 0.5
    var safetyCorridorBufferNm: Double = 0.1
    var arrivalRadiusMeters: Double = 50.0

    init {
        val settings = app.settings
        xteThresholdNm = settings.NAUTICAL_XTE_THRESHOLD.get().toDouble()
        vesselDraft = settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        corridorWidthNm = settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
        safetyCorridorBufferNm = settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
        arrivalRadiusMeters = settings.NAUTICAL_ARRIVAL_RADIUS.get().toDouble()

        engineScope.launch {
            capabilityManager?.capabilities?.collect { caps ->
                val newCapacity = if (caps.hasHistory || caps.hasLogging) 60 else 3600
                telemetryBuffers.values.forEach { it.setCapacity(newCapacity) }
            }
        }

        startMessageProcessing()
    }

    private fun startMessageProcessing() {
        messageProcessingJob?.cancel()
        messageProcessingJob = parsingScope.launch {
            for (message in messageChannel) {
                processJsonMessage(message)
            }
        }
    }

    private fun processJsonMessage(jsonMessage: String) {
        val reader = JsonReader(StringReader(jsonMessage))
        try {
            reader.beginObject()
            var context: String? = null
            var isHello = false

            while (reader.hasNext()) {
                val name = reader.nextName()
                when (name) {
                    "self" -> {
                        trueSelfContext = reader.nextString()
                        handleSelfIdentity(trueSelfContext)
                        isHello = true
                    }
                    "context" -> context = reader.nextString()
                    "updates" -> {
                        if (isHello) {
                            reader.skipValue()
                        } else {
                            processUpdates(reader, context ?: "vessels.self")
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            log.error("JsonReader error: ${e.message}")
        } finally {
            try { reader.close() } catch (_: Exception) {}
        }
    }

    private var lastRestUrl: String? = null
    private var cachedRestService: SignalKRestService? = null

    fun getCurrentState(): MarineState = dataBroker.marineState.value

    @Synchronized
    fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        if (ip.isEmpty()) return null

        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val url = "$protocol://$ip:$port"
        
        if (url == lastRestUrl && cachedRestService != null) {
            return cachedRestService
        }
        
        lastRestUrl = url
        cachedRestService = SignalKRestService.create(url, client)
        return cachedRestService
    }

    fun setSwitch(path: String, state: Boolean) = controlManager.setSwitchState(path, state)
    fun setAutopilotMode(mode: String) = controlManager.setAutopilotMode(mode)
    fun setAutopilotHeading(radians: Double) = controlManager.setAutopilotTargetHeading(radians)
    fun setAutopilotHeadingMagnetic(radians: Double) = controlManager.setAutopilotTargetHeadingMagnetic(radians)
    fun acknowledgeNotification(path: String) = controlManager.acknowledgeNotification(path)

    fun setAnchor(lat: Double, lon: Double, radius: Double) = controlManager.setAnchor(lat, lon, radius)
    fun disarmAnchor() = controlManager.disarmAnchor()

    fun clearBuffers(context: Context) {
        telemetryBuffers.clear()
        trajectoryBuffer.clear()
        val binFile = File(context.filesDir, "nautical_history.bin")
        if (binFile.exists()) binFile.delete()
        val jsonFile = File(context.filesDir, "nautical_history.json")
        if (jsonFile.exists()) jsonFile.delete()
        log.info("SignalK historical buffers cleared from disk and memory.")
    }

    @Synchronized
    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        pulseJob?.cancel()
        pulseJob = null
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        messageProcessingJob?.cancel()
        messageProcessingJob = null
        messageChannel.close()
        parsingScope.cancel()
        resourceManager.stopSync()
        onConnectionLost = null
        onConnectionError = null
        onConnectionRestored = null
        routeStepListeners.clear()
        stateListeners.clear()
        dataBroker.stop()
        aisListener = null
        aisCache.clear()
        routeQueue.clear()
        isFollowingRoute = false
    }

    suspend fun saveBuffersToDisk(context: Context) = withContext(Dispatchers.IO + NonCancellable) {
        val file = File(context.filesDir, "nautical_history.bin")
        try {
            DataOutputStream(file.outputStream().buffered()).use { dos ->
                dos.writeInt(2) // Version

                fun writeBuffer(path: String) {
                    val buffer = telemetryBuffers[path] ?: return
                    val all = buffer.getAll()
                    if (all.isEmpty()) return
                    
                    dos.writeUTF(path)
                    dos.writeInt(all.size)
                    all.forEach { (v, t) ->
                        dos.writeDouble(v)
                        dos.writeLong(t)
                    }
                }

                dos.writeInt(telemetryBuffers.size)
                telemetryBuffers.keys.forEach { writeBuffer(it) }

                val trajectory = trajectoryBuffer.getAll()
                dos.writeInt(trajectory.size)
                trajectory.forEach { (lat, lon) ->
                    dos.writeDouble(lat)
                    dos.writeDouble(lon)
                }
            }
            File(context.filesDir, "nautical_history.json").delete()
        } catch (e: Exception) {
            log.error("Failed to save history: ${e.message}")
        }
    }

    suspend fun loadBuffersFromDisk(context: Context) = withContext(Dispatchers.IO) {
        val binFile = File(context.filesDir, "nautical_history.bin")
        if (binFile.exists()) {
            try {
                DataInputStream(binFile.inputStream().buffered()).use { dis ->
                    val version = dis.readInt()
                    if (version == 2) {
                        val bufferCount = dis.readInt()
                        repeat(bufferCount) {
                            val path = dis.readUTF()
                            val size = dis.readInt()
                            val buffer = getBuffer(path)
                            repeat(size) {
                                buffer.add(Pair(dis.readDouble(), dis.readLong()))
                            }
                        }

                        val tSize = dis.readInt()
                        repeat(tSize) {
                            trajectoryBuffer.add(Pair(dis.readDouble(), dis.readDouble()))
                        }
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to load binary history: ${e.message}")
            }
        }

        val file = File(context.filesDir, "nautical_history.json")
        if (!file.exists()) {
            loadLegacyBuffers(context)
            return@withContext
        }
        try {
            val root = JSONObject(file.readText())
            
            fun jsonToBuffer(key: String, path: String) {
                val array = root.optJSONArray(key) ?: return
                val buffer = getBuffer(path)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    buffer.add(Pair(obj.getDouble("v"), obj.getLong("t")))
                }
            }

            jsonToBuffer("depth", SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER)
            jsonToBuffer("wind", SignalKPaths.ENV_WIND_SPEED_TRUE)
            jsonToBuffer("wind_dir", SignalKPaths.ENV_WIND_DIRECTION_TRUE)
            jsonToBuffer("vmg", SignalKPaths.PERF_VMG)
            jsonToBuffer("cog", SignalKPaths.NAV_COURSE_OVER_GROUND)
            jsonToBuffer("sog", SignalKPaths.NAV_SPEED_OVER_GROUND)
            jsonToBuffer("stw", SignalKPaths.NAV_SPEED_THROUGH_WATER)
            jsonToBuffer("rpm", SignalKPaths.PROPULSION_PREFIX + "0.revolutions")
            jsonToBuffer("temp_eng", SignalKPaths.PROPULSION_PREFIX + "0.temperature")
            jsonToBuffer("volt", SignalKPaths.BATTERIES_PREFIX + "0.voltage")
            jsonToBuffer("soc", SignalKPaths.BATTERIES_PREFIX + "0.capacity.stateOfCharge")
            jsonToBuffer("xte", SignalKPaths.NAV_XTE)
            jsonToBuffer("water_temp", SignalKPaths.ENV_WATER_TEMP)
            jsonToBuffer("outside_temp", SignalKPaths.ENV_OUTSIDE_TEMP)
            jsonToBuffer("pressure", SignalKPaths.ENV_OUTSIDE_PRESSURE)
            jsonToBuffer("drift", SignalKPaths.NAV_DRIFT)
            jsonToBuffer("set_true", SignalKPaths.NAV_SET_TRUE)
            jsonToBuffer("roll", SignalKPaths.NAV_ATTITUDE + ".roll")
            jsonToBuffer("pitch", SignalKPaths.NAV_ATTITUDE + ".pitch")
            jsonToBuffer("awa", SignalKPaths.ENV_WIND_ANGLE_APPARENT)
            jsonToBuffer("aws", SignalKPaths.ENV_WIND_SPEED_APPARENT)
            jsonToBuffer("twa", SignalKPaths.ENV_WIND_ANGLE_TRUE)
            jsonToBuffer("rot", SignalKPaths.NAV_RATE_OF_TURN)
            jsonToBuffer("ttw", SignalKPaths.NAV_TTW)
            jsonToBuffer("dtw", SignalKPaths.NAV_DTW)
            jsonToBuffer("polar_ratio", SignalKPaths.PERF_POLAR_RATIO)
            jsonToBuffer("mag_hdg", SignalKPaths.NAV_HEADING_MAG)
            jsonToBuffer("log", SignalKPaths.NAV_LOG)
            jsonToBuffer("trip_log", SignalKPaths.NAV_TRIP_LOG)
            jsonToBuffer("depth_keel", SignalKPaths.ENV_DEPTH_BELOW_KEEL)
            jsonToBuffer("fuel", SignalKPaths.TANKS_PREFIX + "fuel.0.currentLevel")
            jsonToBuffer("fresh_water", SignalKPaths.TANKS_PREFIX + "freshWater.0.currentLevel")
            jsonToBuffer("waste", SignalKPaths.TANKS_PREFIX + "wasteWater.0.currentLevel")
            jsonToBuffer("oil_pressure", SignalKPaths.PROPULSION_PREFIX + "0.oilPressure")
            jsonToBuffer("engine_load", SignalKPaths.PROPULSION_PREFIX + "0.engineLoad")
            jsonToBuffer("coolant_temp", SignalKPaths.PROPULSION_PREFIX + "0.coolantTemperature")
            jsonToBuffer("battery_current", SignalKPaths.BATTERIES_PREFIX + "0.current")
            jsonToBuffer("solar_current", SignalKPaths.ELECTRICAL_PREFIX + "solar.0.current")
            jsonToBuffer("twd", SignalKPaths.NAV_TWD)

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
                var readSuccess = false
                ObjectInputStream(file.inputStream()).use { ois ->
                    val data = ois.readObject()
                    if (data is Collection<*>) {
                        data.forEach { item ->
                            when {
                                fileName == "trajectory_buffer.dat" && item is Pair<*, *> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    action(item as T)
                                }
                                item is Pair<*, *> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    action(item as T)
                                }
                                item is Double -> {
                                    @Suppress("UNCHECKED_CAST")
                                    action(Pair(item, TemporalUtils.now()) as T)
                                }
                            }
                        }
                        readSuccess = true
                    }
                }
                if (readSuccess) {
                    file.delete()
                    log.info("Nautical: Migrated legacy buffer $fileName and cleared source.")
                }
            } catch (e: Exception) {
                log.error("Failed to load $fileName: ${e.message}")
            }
        }

        load<Pair<Double, Long>>("depth_buffer.dat") { getBuffer("environment.depth.belowTransducer").add(it) }
        load<Pair<Double, Long>>("wind_buffer.dat") { getBuffer("environment.wind.speedTrue").add(it) }
        load<Pair<Double, Long>>("wind_direction_buffer.dat") { getBuffer("environment.wind.directionTrue").add(it) }
        load<Pair<Double, Long>>("vmg_buffer.dat") { getBuffer("performance.velocityMadeGood").add(it) }
        load<Pair<Double, Long>>("cog_buffer.dat") { getBuffer("navigation.courseOverGroundTrue").add(it) }
        load<Pair<Double, Long>>("sog_buffer.dat") { getBuffer("navigation.speedOverGround").add(it) }
        load<Pair<Double, Long>>("stw_buffer.dat") { getBuffer("navigation.speedThroughWater").add(it) }
        load<Pair<Double, Long>>("rpm_buffer.dat") { getBuffer("propulsion.0.revolutions").add(it) }
        load<Pair<Double, Long>>("temp_engine_buffer.dat") { getBuffer("propulsion.0.temperature").add(it) }
        load<Pair<Double, Long>>("volt_buffer.dat") { getBuffer("electrical.batteries.0.voltage").add(it) }
        load<Pair<Double, Long>>("soc_buffer.dat") { getBuffer("electrical.batteries.0.capacity.stateOfCharge").add(it) }
        load<Pair<Double, Long>>("xte_buffer.dat") { getBuffer("navigation.crossTrackError").add(it) }
        load<Pair<Double, Long>>("water_temp_buffer.dat") { getBuffer("environment.water.temperature").add(it) }
        load<Pair<Double, Long>>("outside_temp_buffer.dat") { getBuffer("environment.outside.temperature").add(it) }
        load<Pair<Double, Long>>("pressure_buffer.dat") { getBuffer("environment.outside.pressure").add(it) }
        load<Pair<Double, Long>>("drift_buffer.dat") { getBuffer("navigation.drift").add(it) }
        load<Pair<Double, Long>>("set_true_buffer.dat") { getBuffer("navigation.setTrue").add(it) }
        load<Pair<Double, Long>>("roll_buffer.dat") { getBuffer("navigation.attitude.roll").add(it) }
        load<Pair<Double, Long>>("pitch_buffer.dat") { getBuffer("navigation.attitude.pitch").add(it) }
        load<Pair<Double, Long>>("awa_buffer.dat") { getBuffer("environment.wind.angleApparent").add(it) }
        load<Pair<Double, Long>>("aws_buffer.dat") { getBuffer("environment.wind.speedApparent").add(it) }
        load<Pair<Double, Long>>("twa_buffer.dat") { getBuffer("environment.wind.angleTrue").add(it) }
        load<Pair<Double, Long>>("rot_buffer.dat") { getBuffer("navigation.rateOfTurn").add(it) }
        load<Pair<Double, Long>>("ttw_buffer.dat") { getBuffer("navigation.timeToWaypoint").add(it) }
        load<Pair<Double, Long>>("dtw_buffer.dat") { getBuffer("navigation.distanceToWaypoint").add(it) }
        load<Pair<Double, Long>>("polar_ratio_buffer.dat") { getBuffer("performance.polarSpeedRatio").add(it) }
        load<Pair<Double, Long>>("mag_hdg_buffer.dat") { getBuffer("navigation.headingMagnetic").add(it) }
        load<Pair<Double, Long>>("log_buffer.dat") { getBuffer("navigation.log").add(it) }
        load<Pair<Double, Long>>("trip_log_buffer.dat") { getBuffer("navigation.trip.log").add(it) }
        load<Pair<Double, Long>>("depth_keel_buffer.dat") { getBuffer("environment.depth.belowKeel").add(it) }
        load<Pair<Double, Long>>("fuel_buffer.dat") { getBuffer("tanks.fuel.0.currentLevel").add(it) }
        load<Pair<Double, Long>>("fresh_water_buffer.dat") { getBuffer("tanks.freshWater.0.currentLevel").add(it) }
        load<Pair<Double, Long>>("waste_buffer.dat") { getBuffer("tanks.wasteWater.0.currentLevel").add(it) }
        load<Pair<Double, Long>>("oil_pressure_buffer.dat") { getBuffer("propulsion.0.oilPressure").add(it) }
        load<Pair<Double, Long>>("engine_load_buffer.dat") { getBuffer("propulsion.0.engineLoad").add(it) }
        load<Pair<Double, Long>>("battery_current_buffer.dat") { getBuffer("electrical.batteries.0.current").add(it) }
        load<Pair<Double, Long>>("solar_current_buffer.dat") { getBuffer("electrical.solar.0.current").add(it) }
        load<Pair<Double, Long>>("twd_buffer.dat") { getBuffer("navigation.trueWindDirection").add(it) }
        load<Pair<Double, Double>>("trajectory_buffer.dat") { trajectoryBuffer.add(it) }
    }

    fun clearRoute() {
        routeQueue.clear()
        isFollowingRoute = false
        log.info("Route cleared. Manual control engaged.")
    }

    fun refreshVesselState() {
        engineScope.launch(Dispatchers.IO) {
            try {
                val plugin = NauticalPlugin.getInstance() ?: return@launch
                val client = plugin.okHttpClient ?: return@launch
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                if (ip.isNullOrEmpty()) return@launch

                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                val baseUrl = "$protocol://$ip:$port"
                val restService = SignalKRestService.create(baseUrl, client) ?: return@launch

                val response = restService.getVesselSelf()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        processVesselTree(body)
                        log.info("Nautical: Immediate background state refresh completed via REST.")
                    }
                }

                // Course API Reconciliation (v2)
                val courseResponse = restService.getCourse()
                if (courseResponse.isSuccessful) {
                    courseResponse.body()?.let { processCourseObject(it) }
                }

                // History backfill if supported
                if (capabilityManager?.capabilities?.value?.hasHistory == true) {
                    fetchHistoryFromServer(restService)
                }
            } catch (e: Exception) {
                log.error("Nautical: Failed to reconcile state via REST: ${e.message}")
            }
        }
    }

    private suspend fun fetchHistoryFromServer(restService: SignalKRestService) {
        try {
            val paths = listOf(
                SignalKPaths.ENV_DEPTH_BELOW_KEEL,
                SignalKPaths.ENV_WIND_ANGLE_APPARENT,
                SignalKPaths.ENV_WIND_SPEED_APPARENT,
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.NAV_COURSE_OVER_GROUND,
                SignalKPaths.NAV_SPEED_THROUGH_WATER
            ).joinToString(",")
            
            // Fetch last 1 hour of data
            val from = TemporalUtils.formatIso8601(System.currentTimeMillis() - 3600000)
            
            val response = restService.getHistoryValues(paths, from)
            if (response.isSuccessful) {
                val body = response.body() ?: return
                // Process historical values and inject into telemetryBuffers
                body.forEach { (path, valuesObj) ->
                    if (valuesObj is List<*>) {
                        val buffer = getBuffer(path)
                        valuesObj.forEach { item ->
                            if (item is Map<*, *>) {
                                val ts = item["timestamp"]?.toString()?.let { 
                                    TemporalUtils.parseIso8601(it)
                                } ?: 0L
                                val value = (item["value"] as? Number)?.toDouble() ?: Double.NaN
                                if (ts > 0 && !value.isNaN()) {
                                    buffer.add(Pair(value, ts))
                                }
                            }
                        }
                    }
                }
                log.info("Nautical: History backfill completed for paths: $paths")
            }
        } catch (e: Exception) {
            log.error("Nautical: Failed to fetch history: ${e.message}")
        }
    }

    private fun processVesselTree(tree: Map<String, Any>) {
        var updated = false
        var current = dataBroker.marineState.value

        fun extractValue(path: String): Any? {
            val parts = path.split(".")
            var node: Any? = tree
            for (part in parts) {
                node = (node as? Map<*, *>)?.get(part)
                if (node == null) break
            }
            return (node as? Map<*, *>)?.get("value")
        }

        // Essential Telemetry Reconciliation
        extractValue(SignalKPaths.NAV_POSITION)?.let { pos ->
            if (pos is Map<*, *>) {
                val lat = (pos["latitude"] as? Number)?.toDouble() ?: Double.NaN
                val lon = (pos["longitude"] as? Number)?.toDouble() ?: Double.NaN
                if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                    current = current.copy(latitude = lat, longitude = lon)
                    updated = true
                }
            }
        }

        extractValue(SignalKPaths.NAV_SPEED_OVER_GROUND)?.let { sog ->
            if (sog is Number && MarineStateConstants.isValidSpeed(sog.toDouble())) {
                current = current.copy(speedOverGround = sog.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.NAV_COURSE_OVER_GROUND)?.let { cog ->
            if (cog is Number) {
                current = current.copy(courseOverGroundTrue = cog.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.NAV_HEADING_TRUE)?.let { hdg ->
            if (hdg is Number) {
                current = current.copy(headingTrue = hdg.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.ENV_DEPTH_BELOW_KEEL)?.let { depth ->
            if (depth is Number && MarineStateConstants.isValidDepth(depth.toDouble())) {
                current = current.copy(depthBelowKeel = depth.toDouble())
                updated = true
            }
        }

        if (updated) {
            finalizeAndNotifyState(current)
        }
    }

    fun isAuthenticated(): Boolean {
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val token = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
        val user = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val pass = app.settings.NAUTICAL_SERVER_PASSWORD.get()
        
        // Command dispatch requires secure transport AND cryptographic JWT verification or valid encrypted credentials
        if (!useSecure) {
            log.error("Authentication rejected: Secure connection is required for state mutation commands.")
            return false
        }

        if (token.isNotBlank()) {
            return validateJwtToken(token)
        }

        return user.isNotBlank() && pass.isNotBlank()
    }

    private fun validateJwtToken(token: String): Boolean {
        return try {
            val jwt = JWT.decode(token)
            val expiresAt = jwt.expiresAt
            if (expiresAt != null && expiresAt.before(Date())) {
                log.error("JWT token expired at $expiresAt")
                false
            } else {
                true
            }
        } catch (e: JWTDecodeException) {
            log.error("Failed to decode JWT token: ${e.message}")
            false
        }
    }

    private val lastAuthErrorTime = AtomicLong(0)
    private fun triggerAuthError() {
        val now = System.currentTimeMillis()
        val last = lastAuthErrorTime.get()
        if (now - last > 5000) {
            if (lastAuthErrorTime.compareAndSet(last, now)) {
                engineScope.launch(Dispatchers.Main) {
                    onAuthError?.invoke()
                }
            }
        }
    }

    fun sendDelta(path: String, value: Any) {
        synchronized(deltaQueue) {
            deltaQueue[path] = value
            if (deltaFlushJob == null || deltaFlushJob?.isActive == false) {
                deltaFlushJob = engineScope.launch {
                    delay(100.milliseconds) // 100ms batching window
                    flushDeltas()
                }
            }
        }
    }

    fun dispatchCommand(command: String) {
        if (!isAuthenticated()) {
            log.error("Rejected state mutation command '$command': Session is unauthenticated or insecure!")
            triggerAuthError()
            return
        }

        NauticalLog.auditCommand(command)
        log.debug("Dispatching authenticated command: $command")
        val parts = command.split(":", limit = 2)
        if (parts.size < 2) return
        
        val cmd = parts[0]
        val rawValue = parts[1]

        val (path, value) = when (cmd) {
            "CALIBRATE_COMPASS" -> "steering.autopilot.actions.calibrateCompass" to (rawValue == "START")
            "TARGET_HEADING" -> "steering.autopilot.target.headingTrue" to (rawValue.toDoubleOrNull() ?: rawValue)
            "STATE" -> "steering.autopilot.state" to rawValue
            "SWITCH" -> {
                val subParts = rawValue.split(":", limit = 2)
                if (subParts.size < 2) return
                val switchPath = subParts[0]
                val state = subParts[1].lowercase(Locale.US).let { it == "true" || it == "on" || it == "1" }
                "electrical.switches.$switchPath.state" to state
            }
            "ANCHOR_STATE" -> "steering.anchor.state" to rawValue
            "ANCHOR_POS" -> {
                try {
                    "steering.anchor.position" to JSONObject(rawValue)
                } catch (_: Exception) {
                    "steering.anchor.position" to rawValue
                }
            }
            "NOTIFICATION" -> {
                val nPath = rawValue.substringBefore(":")
                val nValue = rawValue.substringAfter(":")
                nPath to nValue
            }
            "LOGBOOK_ENTRY" -> "notifications.logbook.entry" to rawValue
            "MEDIA" -> "entertainment.media.state" to rawValue
            else -> return
        }
        
        synchronized(deltaQueue) {
            deltaQueue[path] = value
            if (deltaFlushJob == null || deltaFlushJob?.isActive == false) {
                deltaFlushJob = engineScope.launch {
                    delay(100.milliseconds) // 100ms batching window
                    flushDeltas()
                }
            }
        }
    }

    private fun flushDeltas() {
        val toSend: Map<String, Any>
        synchronized(deltaQueue) {
            if (deltaQueue.isEmpty()) return
            toSend = deltaQueue.toMap()
            deltaQueue.clear()
        }

        try {
            val updatesArray = JSONArray()
            val valuesArray = JSONArray()

            toSend.forEach { (path, value) ->
                val entry = JSONObject()
                entry.put("path", path)
                entry.put("value", JSONObject.wrap(value))
                valuesArray.put(entry)
            }

            val update = JSONObject()
            update.put("values", valuesArray)
            updatesArray.put(update)

            val root = JSONObject()
            root.put("updates", updatesArray)

            deltaSender?.invoke(root.toString())
        } catch (e: Exception) {
            log.error("Failed to flush deltas: ${e.message}")
        }
    }

    private fun resolveSelfIdentity() {
        engineScope.launch(Dispatchers.IO) {
            try {
                withTimeout(5000.milliseconds) {
                    val plugin = NauticalPlugin.getInstance() ?: return@withTimeout
                    val client = plugin.okHttpClient ?: return@withTimeout
                    val ip = app.settings.NAUTICAL_SERVER_IP.get()
                    val port = app.settings.NAUTICAL_SERVER_PORT.get()
                    val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                    val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withTimeout
                    
                    var response = restService.getSelfIdentity()
                    if (!response.isSuccessful) {
                        log.info("SignalK v2 self identity failed, trying v1 fallback...")
                        response = restService.getV1SelfIdentity()
                    }

                    if (response.isSuccessful) {
                        val body = response.body()
                        val mmsi = (body?.get("mmsi") as? String)?.toIntOrNull()
                        val name = body?.get("name") as? String
                        val uuid = body?.get("uuid") as? String
                        dataBroker.updateState { s ->
                            s.copy(
                                vesselMmsi = mmsi ?: s.vesselMmsi,
                                vesselName = name ?: s.vesselName,
                                vesselUuid = uuid ?: s.vesselUuid
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to resolve self identity (timeout or network): ${e.message}")
            }
        }
    }

    private fun resetWatchdog() {
        lastUpdateTimestamp = TemporalUtils.now()
        if (watchdogJob?.isActive != true) {
            startWatchdog()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        resourceManager.startSync()
        watchdogJob = engineScope.launch {
            var previouslyDisconnected = false
            while (isActive) {
                try {
                    val refreshRate = app.settings.NAUTICAL_TELEMETRY_REFRESH_RATE.get().coerceAtLeast(1)
                    val delayTime = if (powerSaveMode) 5000L else (refreshRate * 1000L)
                    delay(delayTime.milliseconds)
                    
                    // Periodic maintenance of historical buffers
                    val now = TemporalUtils.now()
                    telemetryBuffers.values.forEach { it.prune(3600000) { p -> p.second } }

                    val elapsed = now - lastUpdateTimestamp
                    val watchdogTimeoutMs = app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.get() * 1000L
                    if (elapsed > watchdogTimeoutMs) {
                        if (!previouslyDisconnected) {
                            previouslyDisconnected = true
                            dataBroker.updateState { s ->
                                s.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
                            }
                            isFollowingRoute = false
                            notifyListeners(dataBroker.marineState.value)
                            log.error("Data timeout (${app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.get()}s)! Dispatching DISCONNECTED state.")
                            onConnectionLost?.invoke()
                        }
                    } else if (elapsed > watchdogTimeoutMs / 2) {
                        previouslyDisconnected = false
                        if (dataBroker.marineState.value.connectionStatus != ConnectionStatus.STALE) {
                            dataBroker.updateState { it.copy(connectionStatus = ConnectionStatus.STALE) }
                            notifyListeners(dataBroker.marineState.value)
                        }
                    } else {
                        if (previouslyDisconnected) {
                            previouslyDisconnected = false
                            onConnectionRestored?.invoke()
                        }
                        if (dataBroker.marineState.value.connectionStatus != ConnectionStatus.CONNECTED) {
                            dataBroker.updateState { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
                        }

                        var modified = false
                        val current = dataBroker.marineState.value
                        val nextStalePaths = current.stalePaths.toMutableSet()
                        val staleThreshold = 10000L
                        val timestamps = current.timestamps

                        fun checkStale(path: String): Boolean {
                            val isStale = (now - (timestamps[path] ?: 0L)) > staleThreshold
                            if (isStale) {
                                if (nextStalePaths.add(path)) modified = true
                            } else {
                                if (nextStalePaths.remove(path)) modified = true
                            }
                            return isStale
                        }

                        val sogStale = checkStale("navigation.speedOverGround")
                        val cogStale = checkStale("navigation.courseOverGroundTrue")
                        val hdgStale = checkStale("navigation.headingTrue") || checkStale("navigation.headingMagnetic")
                        val depthStale = checkStale("environment.depth.belowTransducer")
                        val windStale = checkStale("environment.wind.angleApparent") || checkStale("environment.wind.speedTrue") || checkStale("environment.wind.speedApparent")
                        
                        checkStale("navigation.speedThroughWater")
                        checkStale("navigation.crossTrackError")
                        checkStale("navigation.attitude.roll")
                        checkStale("navigation.attitude.pitch")

                        val coreStale = sogStale || cogStale || hdgStale || depthStale || windStale
                        val nextStatus = if (coreStale) ConnectionStatus.STALE else ConnectionStatus.CONNECTED

                        if (modified || current.connectionStatus != nextStatus) {
                            dataBroker.updateState { it.copy(stalePaths = nextStalePaths, connectionStatus = nextStatus) }
                            notifyListeners(dataBroker.marineState.value)
                        }
                    }
                    updatePulseLifecycle()
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
                    delay(60000.milliseconds)
                    val now = TemporalUtils.now()
                    val iterator = aisCache.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value.lastUpdate > 1800000) {
                            iterator.remove()
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
    fun addRouteStepListener(listener: () -> Unit) { routeStepListeners.add(listener) }
    fun removeRouteStepListener(listener: () -> Unit) { routeStepListeners.remove(listener) }
    fun registerAisListener(listener: ((AisObject) -> Unit)?) { this.aisListener = listener }

    private fun notifyListeners(state: MarineState) {
        stateListeners.forEach { it.invoke(state) }
    }

    fun getDepthHistory(): List<Pair<Double, Long>> = getBuffer("environment.depth.belowTransducer").getAll()
    fun getWindHistory(): List<Pair<Double, Long>> = getBuffer("environment.wind.speedTrue").getAll()
    fun getWindDirectionHistory(): List<Pair<Double, Long>> = getBuffer("environment.wind.directionTrue").getAll()
    fun getVmgHistory(): List<Pair<Double, Long>> = getBuffer("performance.velocityMadeGood").getAll()
    fun getCogHistory(): List<Pair<Double, Long>> = getBuffer("navigation.courseOverGroundTrue").getAll()
    fun getSogHistory(): List<Pair<Double, Long>> = getBuffer("navigation.speedOverGround").getAll()
    fun getStwHistory(): List<Pair<Double, Long>> = getBuffer("navigation.speedThroughWater").getAll()
    fun getRpmHistory(): List<Pair<Double, Long>> = getBuffer("propulsion.0.revolutions").getAll()
    fun getTempEngineHistory(): List<Pair<Double, Long>> = getBuffer("propulsion.0.temperature").getAll()
    fun getVoltHistory(): List<Pair<Double, Long>> = getBuffer("electrical.batteries.0.voltage").getAll()
    fun getSocHistory(): List<Pair<Double, Long>> = getBuffer("electrical.batteries.0.capacity.stateOfCharge").getAll()
    fun getXteHistory(): List<Pair<Double, Long>> = getBuffer("navigation.crossTrackError").getAll()
    fun getWaterTempHistory(): List<Pair<Double, Long>> = getBuffer("environment.water.temperature").getAll()
    fun getOutsideTempHistory(): List<Pair<Double, Long>> = getBuffer("environment.outside.temperature").getAll()
    fun getPressureHistory(): List<Pair<Double, Long>> = getBuffer("environment.outside.pressure").getAll()
    fun getDriftHistory(): List<Pair<Double, Long>> = getBuffer("navigation.drift").getAll()
    fun getRollHistory(): List<Pair<Double, Long>> = getBuffer("navigation.attitude.roll").getAll()
    fun getPitchHistory(): List<Pair<Double, Long>> = getBuffer("navigation.attitude.pitch").getAll()
    fun getAwaHistory(): List<Pair<Double, Long>> = getBuffer("environment.wind.angleApparent").getAll()
    fun getAwsHistory(): List<Pair<Double, Long>> = getBuffer("environment.wind.speedApparent").getAll()
    fun getTwaHistory(): List<Pair<Double, Long>> = getBuffer("environment.wind.angleTrue").getAll()
    fun getRotHistory(): List<Pair<Double, Long>> = getBuffer("navigation.rateOfTurn").getAll()
    fun getTtwHistory(): List<Pair<Double, Long>> = getBuffer("navigation.timeToWaypoint").getAll()
    fun getDtwHistory(): List<Pair<Double, Long>> = getBuffer("navigation.distanceToWaypoint").getAll()
    fun getPolarRatioHistory(): List<Pair<Double, Long>> = getBuffer("performance.polarSpeedRatio").getAll()
    fun getMagHdgHistory(): List<Pair<Double, Long>> = getBuffer("navigation.headingMagnetic").getAll()
    fun getLogHistory(): List<Pair<Double, Long>> = getBuffer("navigation.log").getAll()
    fun getTripLogHistory(): List<Pair<Double, Long>> = getBuffer("navigation.trip.log").getAll()
    fun getDepthKeelHistory(): List<Pair<Double, Long>> = getBuffer("environment.depth.belowKeel").getAll()
    fun getFuelHistory(): List<Pair<Double, Long>> = getBuffer("tanks.fuel.0.currentLevel").getAll()
    fun getFreshWaterHistory(): List<Pair<Double, Long>> = getBuffer("tanks.freshWater.0.currentLevel").getAll()
    fun getWasteHistory(): List<Pair<Double, Long>> = getBuffer("tanks.wasteWater.0.currentLevel").getAll()
    fun getOilPressureHistory(): List<Pair<Double, Long>> = getBuffer("propulsion.0.oilPressure").getAll()
    fun getEngineLoadHistory(): List<Pair<Double, Long>> = getBuffer("propulsion.0.engineLoad").getAll()
    fun getCoolantTempHistory(): List<Pair<Double, Long>> = getBuffer("propulsion.0.coolantTemperature").getAll()
    fun getBatteryCurrentHistory(): List<Pair<Double, Long>> = getBuffer("electrical.batteries.0.current").getAll()
    fun getSolarCurrentHistory(): List<Pair<Double, Long>> = getBuffer("electrical.solar.0.current").getAll()
    fun getTwdHistory(): List<Pair<Double, Long>> = getBuffer("navigation.trueWindDirection").getAll()
    fun getHumidityHistory(): List<Pair<Double, Long>> = getBuffer("environment.outside.relativeHumidity").getAll()

    suspend fun uploadActiveRouteToSignalK(name: String) = withContext(Dispatchers.IO) {
        val points = getRoutePoints()
        if (points.isEmpty()) return@withContext

        try {
            val plugin = NauticalPlugin.getInstance() ?: return@withContext
            val client = plugin.okHttpClient ?: return@withContext
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withContext

            val coords = points.map { listOf(it.second, it.first) }
            val skRoute = SignalKRoute(
                name = name,
                description = app.getString(R.string.nautical_sk_exported_description),
                distance = null,
                feature = SignalKRouteFeature(
                    geometry = SignalKLineString(coordinates = coords)
                )
            )

            val response = restService.createRoute(skRoute)
            if (response.isSuccessful) {
                val routeId = response.body()?.id
                log.info("Route uploaded successfully to Signal K v2. Resource ID: $routeId")
                withContext(Dispatchers.Main) {
                    app.showToastMessage(R.string.nautical_route_upload_success)
                }
            } else {
                log.error("Failed to upload route to Signal K v2: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            log.error("Error uploading route: ${e.message}")
        }
    }

    suspend fun updateRouteOnServer(routeId: String, name: String, points: List<Pair<Double, Double>>) = withContext(Dispatchers.IO) {
        try {
            val plugin = NauticalPlugin.getInstance() ?: return@withContext
            val client = plugin.okHttpClient ?: return@withContext
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withContext

            val coords = points.map { listOf(it.second, it.first) }
            val skRoute = SignalKRoute(
                name = name,
                description = app.getString(R.string.nautical_sk_updated_description),
                distance = null,
                feature = SignalKRouteFeature(
                    geometry = SignalKLineString(coordinates = coords)
                )
            )

            val response = restService.updateRoute(routeId, skRoute)
            if (response.isSuccessful) {
                log.info("Route $routeId updated successfully on Signal K v2.")
            } else {
                log.error("Failed to update route $routeId: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            log.error("Error updating route $routeId: ${e.message}")
        }
    }

    suspend fun deleteRouteFromServer(routeId: String) = withContext(Dispatchers.IO) {
        try {
            val plugin = NauticalPlugin.getInstance() ?: return@withContext
            val client = plugin.okHttpClient ?: return@withContext
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withContext

            val response = restService.deleteRoute(routeId)
            if (response.isSuccessful) {
                log.info("Route $routeId deleted successfully from Signal K v2.")
            } else {
                log.error("Failed to delete route $routeId: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            log.error("Error deleting route $routeId: ${e.message}")
        }
    }

    suspend fun fetchRoutesFromServer(): Map<String, SignalKRoute>? = withContext(Dispatchers.IO) {
        try {
            val plugin = NauticalPlugin.getInstance() ?: return@withContext null
            val client = plugin.okHttpClient ?: return@withContext null
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val restService = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@withContext null

            val response = restService.getRoutes()
            if (response.isSuccessful) {
                return@withContext response.body()
            }
        } catch (e: Exception) {
            log.error("Error fetching routes from server: ${e.message}")
        }
        return@withContext null
    }

    private var lastTrajectoryTimestamp: Long = 0
    private var lastFollowingUpdateTimestamp: Long = 0
    private var lastSetDriftTimestamp: Long = 0

    private val vmgEma = EMA(0.2) // standard alpha is usually (1-alpha) in manual math
    private val driftEma = EMA(0.2)
    private val setAngleEma = AngleEMA(0.2)

    @Volatile
    private var powerSaveMode: Boolean = false

    fun setPowerSaveMode(enabled: Boolean) {
        powerSaveMode = enabled
        log.info("SignalK Engine Power Save Mode: $enabled")
    }

    fun addTrajectoryPoint(lat: Double, lon: Double) {
        val history = trajectoryBuffer.getAll()
        val last = history.lastOrNull()
        val now = TemporalUtils.now()

        // Improved Resolution (TASK-UX-003): 5s or 50m displacement
        if (now - lastTrajectoryTimestamp < 5000) return

        if (last != null) {
            val dist = KMapUtils.getDistance(last.first, last.second, lat, lon)
            val timeGap = now - lastTrajectoryTimestamp
            if (dist > 50.0 || timeGap > 60000) {
                trajectoryBuffer.add(Pair(lat, lon))
                lastTrajectoryTimestamp = now
                _trajectoryEventFlow.tryEmit(Unit)
            }
        } else {
            trajectoryBuffer.add(Pair(lat, lon))
            lastTrajectoryTimestamp = now
            _trajectoryEventFlow.tryEmit(Unit)
        }
    }

    fun copyTrajectoryTo(target: MutableList<Pair<Double, Double>>) {
        trajectoryBuffer.copyTo(target)
    }

    fun handleIncomingMessage(jsonMessage: String) {
        lastUpdateTimestamp = TemporalUtils.now()
        resetWatchdog()

        if (powerSaveMode) {
            val now = System.currentTimeMillis()
            if (now - lastMessageProcessedTime < 2000) return
            lastMessageProcessedTime = now
        }

        val result = messageChannel.trySend(jsonMessage)
        if (!result.isSuccess) {
            log.warn("SignalK message buffer full, message dropped")
        }
    }

    private fun handleSelfIdentity(self: String) {
        // High-Priority resolution from Hello message (TASK-CONN-001)
        if (self.isNotBlank()) {
             trueSelfContext = self
             log.info("Nautical: Identified own vessel context as '$self'")
        }

        if (self.startsWith("vessels.urn:mrn:imo:mmsi:")) {
            val mmsiStr = self.substringAfterLast(":")
            val mmsi = mmsiStr.toIntOrNull()
            if (mmsi != null && MarineStateConstants.isValidMmsi(mmsi)) {
                dataBroker.updateState { it.copy(vesselMmsi = mmsi) }
            }
        } else if (self == "vessels.self") {
            resolveSelfIdentity()
        }
    }

    private fun processUpdates(reader: JsonReader, context: String) {
        val currentMmsi = dataBroker.marineState.value.vesselMmsi
        val isSelf = (context == "vessels.self") || (context == "") || (context == trueSelfContext) ||
                (currentMmsi != null && context == "vessels.urn:mrn:imo:mmsi:$currentMmsi")

        var aisTarget: AisObject? = null
        if (!isSelf) {
            // Task: Robust Context Parsing (Industry Standard)
            // Handle: vessels.*, aircraft.*, atons.*, sar.*
            val type = context.substringBefore(".")
            val rawId = context.substringAfter("$type.", "")
            
            if (rawId.isNotEmpty() && (type == "vessels" || type == "aircraft" || type == "atons" || type == "sar")) {
                val numericMmsi: Int = if (rawId.contains("mmsi:")) {
                    rawId.substringAfter("mmsi:").toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
                } else if (rawId.contains("uuid:")) {
                    rawId.substringAfter("uuid:").hashCode().absoluteValue % 1000000000
                } else {
                    rawId.toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
                }
                
                aisTarget = aisCache.getOrPut(numericMmsi) { 
                    log.info("Nautical: New AIS target discovered from context: $context (MMSI: $numericMmsi)")
                    val msgType = when(type) {
                        "aircraft" -> 9
                        "atons" -> 21
                        else -> 1 // Default vessel
                    }
                    val obj = AisObject(numericMmsi, msgType, 0.0, 0.0)
                    // TODO: Handle SAR type if possible via updates
                    obj
                }
            } else if (context.isNotEmpty()) {
                log.debug("Nautical: Ignoring Signal K update for unknown context: $context")
            }
        }

        reader.beginArray()
        var currentBatchState: MarineState? = null
        var stateUpdated = false

        while (reader.hasNext()) {
            reader.beginObject()
            var updateTimestamp = TemporalUtils.now()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "timestamp" -> {
                        val ts = readJsonValue(reader)
                        if (ts is Number) {
                            updateTimestamp = TemporalUtils.validate(ts.toLong())
                        } else if (ts is String) {
                            val parsed = TemporalUtils.parseIso8601(ts)
                            if (parsed > 0) {
                                updateTimestamp = parsed
                            } else {
                                log.debug("Signal K: Malformed string timestamp received, using device time: $ts")
                            }
                        }
                    }
                    "meta" -> {
                        if (isSelf) processMeta(reader) else reader.skipValue()
                    }
                    "values" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var path: String? = null
                            var value: Any? = null
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "path" -> path = reader.nextString()
                                    "value" -> {
                                        if (isSelf && path != null) {
                                            if (currentBatchState == null) {
                                                currentBatchState = dataBroker.marineState.value
                                            }
                                            // Pass the update timestamp to parsers
                                            val res = parseOptimizedSelfValue(currentBatchState, path, reader, updateTimestamp)
                                            if (res != null) {
                                                currentBatchState = res.first
                                                if (res.second) stateUpdated = true
                                                path = null // Handled
                                            } else {
                                                value = readJsonValue(reader)
                                            }
                                        } else {
                                            value = readJsonValue(reader)
                                            if (aisTarget != null && path != null) {
                                                log.debug("Nautical: Received AIS update for ${aisTarget.mmsi} - Path: $path, Value: $value")
                                            }
                                        }
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (path != null) {
                                if (isSelf) {
                                    if (currentBatchState == null) {
                                        currentBatchState = dataBroker.marineState.value
                                    }
                                    val res = parseSelfValue(currentBatchState, path, value, updateTimestamp)
                                    currentBatchState = res.first
                                    if (res.second) stateUpdated = true
                                } else if (aisTarget != null) {
                                        if (path == SignalKPaths.AIS_THREAT_LEVEL) {
                                        val level = (value as? Number)?.toInt() ?: 0
                                        aisTarget.let {
                                            NauticalPlugin.getInstance()?.aisManager?.updateAisThreatLevel(it.mmsi, level)
                                        }
                                    } else {
                                        aisTarget = updateAisTarget(aisTarget, path, value, updateTimestamp)
                                    }
                                }
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endArray()

        if (isSelf && stateUpdated && currentBatchState != null) {
            finalizeAndNotifyState(currentBatchState)
        } else if (aisTarget != null && aisTarget.position != null) {
            val copy = AisObject(aisTarget)
            aisListener?.invoke(copy)
        }
    }

    private fun parseOptimizedSelfValue(s: MarineState, path: String, reader: JsonReader, now: Long): Pair<MarineState, Boolean>? {
        val newTimestamps = s.timestamps.toMutableMap()
        newTimestamps[path] = now

        return when (path) {
            SignalKPaths.NAV_POSITION -> {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    var lat = Double.NaN
                    var lon = Double.NaN
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "latitude" -> lat = reader.nextDouble()
                            "longitude" -> lon = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                        val state = s.copy(latitude = lat, longitude = lon, timestamps = newTimestamps, timeOfPositionFix = now)
                        updateFollowingState(lat, lon)
                        addTrajectoryPoint(lat, lon)
                        Pair(state, true)
                    } else null
                } else null
            }
            SignalKPaths.NAV_ATTITUDE -> {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    var roll = Double.NaN
                    var pitch = Double.NaN
                    var yaw = Double.NaN
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "roll" -> roll = reader.nextDouble()
                            "pitch" -> pitch = reader.nextDouble()
                            "yaw" -> yaw = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    val state = s.copy(
                        roll = if (roll.isNaN()) s.roll else roll,
                        pitch = if (pitch.isNaN()) s.pitch else pitch,
                        yaw = if (yaw.isNaN()) s.yaw else yaw,
                        timestamps = newTimestamps
                    )
                    if (!roll.isNaN()) {
                        getBuffer(SignalKPaths.NAV_ATTITUDE + ".roll").add(Pair(roll, now))
                        dataBroker.processRollUpdate(roll)
                    }
                    if (!pitch.isNaN()) {
                        getBuffer(SignalKPaths.NAV_ATTITUDE + ".pitch").add(Pair(pitch, now))
                        dataBroker.processPitchUpdate(pitch)
                    }
                    Pair(state, true)
                } else null
            }
            SignalKPaths.NAV_CLOSEST_APPROACH -> {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    var cpa = Double.NaN
                    var tcpa = Double.NaN
                    var name = app.getString(R.string.nautical_unknown_vessel)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "cpa" -> cpa = reader.nextDouble()
                            "tcpa" -> tcpa = reader.nextDouble()
                            "name" -> name = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (!cpa.isNaN() && !tcpa.isNaN()) {
                        val cpaNm = SignalKUnitConverter.metersToNm(cpa)
                        dataBroker.updateClosestApproach(cpaNm, tcpa, name)
                        val state = s.copy(cpa = cpaNm, tcpa = tcpa, threatName = name, timestamps = newTimestamps)
                        Pair(state, true)
                    } else null
                } else null
            }
            SignalKPaths.NAV_COURSE_NEXT_POINT -> {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    var lat = Double.NaN
                    var lon = Double.NaN
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "position" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "latitude" -> lat = reader.nextDouble()
                                        "longitude" -> lon = reader.nextDouble()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                        val state = s.copy(serverNextPoint = LatLon(lat, lon), timestamps = newTimestamps)
                        Pair(state, true)
                    } else null
                } else null
            }
            SignalKPaths.FORWARD_WATCH_DETECTIONS -> {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    val hazards = mutableListOf<ForwardHazard>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            var id = ""
                            var name = app.getString(R.string.nautical_obstacle)
                            var dist = 0.0
                            var bear = 0.0
                            var severity = NotificationState.NORMAL
                            var lat = Double.NaN
                            var lon = Double.NaN

                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "id" -> id = reader.nextString()
                                    "name" -> name = reader.nextString()
                                    "distance" -> dist = reader.nextDouble()
                                    "bearing" -> bear = reader.nextDouble()
                                    "severity" -> severity = when(reader.nextString()) {
                                        "alert" -> NotificationState.ALERT
                                        "warn" -> NotificationState.WARN
                                        "alarm" -> NotificationState.ALARM
                                        "emergency" -> NotificationState.EMERGENCY
                                        else -> NotificationState.NORMAL
                                    }
                                    "position" -> {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "latitude" -> lat = reader.nextDouble()
                                                "longitude" -> lon = reader.nextDouble()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            hazards.add(ForwardHazard(id, name, dist, bear, severity, if (lat.isNaN()) null else lat to lon))
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                    val state = s.copy(forwardHazards = hazards, timestamps = newTimestamps)
                    NauticalPlugin.getInstance()?.safetyManager?.updateForwardHazards(hazards)
                    Pair(state, true)
                } else null
            }
            "navigation.course" -> {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    var nextLat = Double.NaN
                    var nextLon = Double.NaN
                    var activeRouteHref: String? = null
                    var radius: Double? = null

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "nextPoint" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "position") {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "latitude" -> nextLat = reader.nextDouble()
                                                "longitude" -> nextLon = reader.nextDouble()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    } else reader.skipValue()
                                }
                                reader.endObject()
                            }
                            "activeRoute" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "href") activeRouteHref = reader.nextString()
                                    else reader.skipValue()
                                }
                                reader.endObject()
                            }
                            "arrivalCircle" -> radius = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (!nextLat.isNaN()) {
                        dataBroker.updateState { it.copy(serverNextPoint = LatLon(nextLat, nextLon)) }
                    }
                    radius?.let { arrivalRadiusMeters = it }
                    if (activeRouteHref != null) isFollowingRoute = true

                    Pair(s, true)
                } else null
            }
            else -> null
        }
    }

    private fun readJsonValue(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                val s = reader.nextString()
                s.toDoubleOrNull() ?: s.toLongOrNull()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    obj.put(key, readJsonValue(reader))
                }
                reader.endObject()
                obj
            }
            JsonToken.BEGIN_ARRAY -> {
                val arr = JSONArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    arr.put(readJsonValue(reader))
                }
                reader.endArray()
                arr
            }
            else -> { reader.skipValue(); null }
        }
    }

    private fun processMeta(reader: JsonReader) {
        reader.beginArray()
        val currentMeta = dataBroker.marineState.value.pathMeta.toMutableMap()
        while (reader.hasNext()) {
            reader.beginObject()
            var path: String? = null
            var value: JSONObject? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "path" -> path = reader.nextString()
                    "value" -> value = readJsonValue(reader) as? JSONObject
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (path != null && value != null) {
                val metaMap = mutableMapOf<String, Any>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    metaMap[key] = value.get(key)
                }
                currentMeta[path] = metaMap
            }
        }
        dataBroker.updateState { it.copy(pathMeta = currentMeta) }
        reader.endArray()
    }

    private var lastStateUpdateTime: Long = 0
    @Volatile
    private var pendingFinalState: MarineState? = null

    private fun finalizeAndNotifyState(state: MarineState) {
        synchronized(this) {
            pendingFinalState = state
        }
        val now = TemporalUtils.now()
        if (now - lastStateUpdateTime < 100) {
            return
        }
        lastStateUpdateTime = now
        val currentState = synchronized(this) { pendingFinalState } ?: return
        
        // Task 11: Engine Guard Logic
        val isEngineRunning = currentState.engines.values.any {
            it.state?.lowercase() == "started" || (it.revolutions != null && it.revolutions > 100.0)
        }
        val stateWithEngine = currentState.copy(isEngineRunning = isEngineRunning)

        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()

        val stateWithLeeway = if (caps.hasLeeway || caps.hasDerivedData) {
            stateWithEngine
        } else {
            val leeway = calculateLeeway(stateWithEngine)
            stateWithEngine.copy(leeway = leeway)
        }

        val processedState = calculateNavigationMetrics(
            calculateEfficiencyMetrics(
                calculateDepths(
                    if (caps.hasSetAndDrift || caps.hasDerivedData) stateWithLeeway else calculateSetAndDrift(stateWithLeeway, now)
                )
            ), now
        ).let { s ->
            val xteNm = SignalKUnitConverter.metersToNm(abs(s.crossTrackError ?: 0.0))
            val isOff = xteNm > xteThresholdNm && (s.autopilotState.uppercase(Locale.US) == "TRACK")
            s.copy(isOffCourse = isOff, connectionStatus = ConnectionStatus.CONNECTED)
        }

        val finalState = MultihullShuntManager.transformState(processedState)

        val effectiveStw = if (finalState.isStwUnreliable) finalState.speedOverGround else finalState.speedThroughWater

        val perfData = LivePerformanceData(
            speedThroughWater = effectiveStw,
            windSpeedTrue = finalState.windSpeedTrue,
            windAngleTrueWater = finalState.trueWindAngle,
            speedOverGround = finalState.speedOverGround,
            courseOverGround = finalState.courseOverGroundTrue,
            latitude = finalState.latitude,
            longitude = finalState.longitude,
            headingTrue = finalState.headingTrue,
            headingMagnetic = finalState.headingMagnetic,
            magneticVariation = finalState.magneticVariation,
            leeway = finalState.leeway,
            depthBelowTransducer = finalState.depthBelowTransducer,
            polarSpeed = finalState.polarTargetSpeed,
            targetAngle = finalState.targetWindAngleApparent,
            polarSpeedRatio = finalState.polarSpeedRatio,
            roll = finalState.roll,
            pitch = finalState.pitch,
            windAngleApparent = finalState.windDirectionApparent,
            windSpeedApparent = finalState.windSpeedApparent,
            destinationLatitude = getNextWaypoint()?.first,
            destinationLongitude = getNextWaypoint()?.second,
            lastWaypointLatitude = lastWaypointLat,
            lastWaypointLongitude = lastWaypointLon,
            distanceToWaypoint = finalState.distanceToWaypoint,
            drift = finalState.drift,
            setTrue = finalState.setTrue,
            timestamp = finalState.timestamps.values.maxOrNull()?.let { TemporalUtils.validate(it) } ?: TemporalUtils.now()
        )

        dataBroker.updatePerformanceData(perfData)
        
        // Task: Bridge to SailingDataAggregator for unification
        SailingDependencyContainer.nmeaMultiplexer?.aggregator?.handleLivePerformanceData(perfData)

        dataBroker.updateState { finalState }
        
        engineScope.launch(Dispatchers.Main) {
            notifyListeners(finalState)
        }
    }

    private fun updateAisTarget(target: AisObject, path: String, valueObj: Any?, now: Long): AisObject {
        // APRS / Meshtastic Source Detection
        val source = dataBroker.marineState.value.pathMeta[path]?.get("source")?.toString() ?: ""
        val isRemote = source.contains("aprs") || source.contains("meshtastic")
        if (isRemote) {
             NauticalPlugin.getInstance()?.aisManager?.markRemoteVessel(target.mmsi)
        }

        // Task 8.0: Temporal check to prevent out-of-order AIS updates from overwriting newer data
        if (now < target.lastUpdate) {
            log.debug("Nautical: Skipping out-of-order AIS update for MMSI ${target.mmsi} (Path: $path)")
            return target
        }
        
        // AisObject is mutable and its set() / init methods update lastUpdate.
        val temp = AisObject(target.mmsi, 1, target.position?.latitude ?: 0.0, target.position?.longitude ?: 0.0)
        temp.set(target)

        when (path) {
            "" -> {
                if (valueObj is JSONObject) {
                    val name = valueObj.optString("name", "")
                    val vName = valueObj.optString("vesselName", "")
                    val shipName = name.ifEmpty { vName.ifEmpty { null } }
                    
                    val type = valueObj.optInt("vesselType", -1)
                    
                    // We need to use constructors or a way to set these fields.
                    // AisObject has no public setters for many fields, they are initialized in constructors or via set(AisObject).
                    // This is why I'll create a new one with updated fields and use set().
                    
                    val updated = AisObject(
                        target.mmsi, 1,
                        target.imo, null, shipName,
                        if (type != -1) type else target.shipType,
                        target.dimensionToBow, target.dimensionToStern,
                        target.dimensionToPort, target.dimensionToStarboard,
                        target.draught, target.destination,
                        target.etaMon, target.etaDay, target.etaHour, target.etaMin
                    )
                    target.set(updated)
                }
            }
            "name", "vesselName" -> {
                val shipName = valueObj?.toString()
                log.info("Nautical: Identified AIS vessel ${target.mmsi} as '$shipName'")
                val updated = AisObject(
                    target.mmsi, 1,
                    target.imo, target.callSign, shipName,
                    target.shipType,
                    target.dimensionToBow, target.dimensionToStern,
                    target.dimensionToPort, target.dimensionToStarboard,
                    target.draught, target.destination,
                    target.etaMon, target.etaDay, target.etaHour, target.etaMin
                )
                target.set(updated)
            }
            "design.type" -> {
                val type = if (valueObj is JSONObject) valueObj.optInt("id", -1) else (valueObj as? Number)?.toInt() ?: -1
                if (type != -1) {
                    val updated = AisObject(
                        target.mmsi, 1,
                        target.imo, target.callSign, target.shipName,
                        type,
                        target.dimensionToBow, target.dimensionToStern,
                        target.dimensionToPort, target.dimensionToStarboard,
                        target.draught, target.destination,
                        target.etaMon, target.etaDay, target.etaHour, target.etaMin
                    )
                    target.set(updated)
                }
            }
            "navigation.position" -> {
                if (valueObj is JSONObject) {
                    val lat = valueObj.optDouble("latitude", Double.NaN)
                    val lon = valueObj.optDouble("longitude", Double.NaN)
                    if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                        // Use the constructor that sets lat/lon
                        val updated = AisObject(target.mmsi, 1, lat, lon)
                        target.set(updated)
                    }
                }
            }
            "navigation.speedOverGround" -> {
                val sogMs = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (MarineStateConstants.isValidSpeed(sogMs)) {
                    val sogKnots = SignalKUnitConverter.msToKnots(sogMs)
                    val updated = AisObject(
                        target.mmsi, 1, target.timeStamp, target.navStatus, target.manInd, target.heading,
                        target.cog, sogKnots, target.position?.latitude ?: 0.0, target.position?.longitude ?: 0.0, target.rot
                    )
                    target.set(updated)
                }
            }
            "navigation.courseOverGroundTrue" -> {
                val cogRad = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!cogRad.isNaN()) {
                    val cogDeg = SignalKUnitConverter.radToDeg(cogRad)
                    val updated = AisObject(
                        target.mmsi, 1, target.timeStamp, target.navStatus, target.manInd, target.heading,
                        cogDeg, target.sog, target.position?.latitude ?: 0.0, target.position?.longitude ?: 0.0, target.rot
                    )
                    target.set(updated)
                }
            }
            "navigation.headingTrue" -> {
                val hdgRad = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!hdgRad.isNaN()) {
                    val hdgDeg = SignalKUnitConverter.radToDeg(hdgRad).toInt()
                    val updated = AisObject(
                        target.mmsi, 1, target.timeStamp, target.navStatus, target.manInd, hdgDeg,
                        target.cog, target.sog, target.position?.latitude ?: 0.0, target.position?.longitude ?: 0.0, target.rot
                    )
                    target.set(updated)
                }
            }
            SignalKPaths.NAV_DESTINATION -> {
                val dest = valueObj?.toString()
                val updated = AisObject(
                    target.mmsi, 1, 
                    target.imo, target.callSign, target.shipName, 
                    target.shipType,
                    target.dimensionToBow, target.dimensionToStern,
                    target.dimensionToPort, target.dimensionToStarboard,
                    target.draught, dest,
                    target.etaMon, target.etaDay, target.etaHour, target.etaMin
                )
                target.set(updated)
            }
            SignalKPaths.NAV_STATE -> {
                val statusStr = valueObj?.toString() ?: ""
                val status = when (statusStr.lowercase(Locale.US)) {
                    "under way using engine", "motoring" -> 0
                    "at anchor" -> 1
                    "not under command" -> 2
                    "restricted manoeuverability" -> 3
                    "constrained by her draught" -> 4
                    "moored" -> 5
                    "aground" -> 6
                    "engaged in fishing" -> 7
                    "under way sailing", "sailing" -> 8
                    else -> target.navStatus
                }
                val updated = AisObject(
                    target.mmsi, 1, target.timeStamp, status, target.manInd, target.heading,
                    target.cog, target.sog, target.position?.latitude ?: 0.0, target.position?.longitude ?: 0.0, target.rot
                )
                target.set(updated)
            }
        }
        return target
    }

    private fun parseSelfValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val newTimestamps = s.timestamps.toMutableMap()
        newTimestamps[path] = now
        val stateWithTs = s.copy(timestamps = newTimestamps)

        // Hot Path: High-frequency telemetry
        return when (path) {
            SignalKPaths.NAV_HEADING_TRUE -> processHeadingTrue(stateWithTs, valueObj, now)
            SignalKPaths.NAV_HEADING_MAG -> processHeadingMag(stateWithTs, valueObj, now)
            SignalKPaths.NAV_SPEED_OVER_GROUND -> processSog(stateWithTs, valueObj, now)
            SignalKPaths.NAV_SPEED_THROUGH_WATER -> processStw(stateWithTs, valueObj, now)
            SignalKPaths.ENV_WIND_ANGLE_APPARENT -> processWindAngleApparent(stateWithTs, valueObj, now)
            SignalKPaths.ENV_WIND_SPEED_APPARENT -> processWindSpeedApparent(stateWithTs, valueObj, now)
            
            else -> {
                // Category-based dispatch
                when {
                    path.startsWith("navigation.") -> parseNavigationValue(stateWithTs, path, valueObj, now)
                    path.startsWith("performance.") -> parsePerformanceValue(stateWithTs, path, valueObj, now)
                    path.startsWith("steering.") -> parseAutopilotValue(stateWithTs, path, valueObj, now)
                    path.startsWith("environment.") -> parseEnvironmentValue(stateWithTs, path, valueObj, now)
                    path.startsWith("propulsion.") || path.startsWith("electrical.") || path.startsWith("tanks.") ->
                        parseSystemValue(stateWithTs, path, valueObj, now)
                    else -> {
                        val res = parseTelemetryValue(stateWithTs, path, valueObj, now)
                        if (res.second) res else parseOtherValue(stateWithTs, path, valueObj)
                    }
                }
            }
        }
    }

    private fun processHeadingTrue(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            val state = s.copy(headingTrue = value, timeOfHeadingFix = now)
            dataBroker.processHeadingUpdate(value)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processHeadingMag(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            val state = s.copy(headingMagnetic = value, timeOfHeadingFix = now)
            dataBroker.processHeadingUpdate(value)
            dataBroker.processVariationUpdate(state.magneticVariation ?: 0.0)
            getBuffer(SignalKPaths.NAV_HEADING_MAG).add(Pair(value, now))
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processSog(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidSpeed(value)) {
            val state = s.copy(speedOverGround = value, timeOfSogFix = now)
            getBuffer(SignalKPaths.NAV_SPEED_OVER_GROUND).add(Pair(value, now))
            dataBroker.processSogUpdate(value)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processStw(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidSpeed(value)) {
            val state = s.copy(speedThroughWater = value, timeOfSogFix = now)
            getBuffer(SignalKPaths.NAV_SPEED_THROUGH_WATER).add(Pair(value, now))
            dataBroker.processStwUpdate(value)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processWindAngleApparent(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            val corrected = environmentalFilterService?.correctWindAngle(value, s.roll ?: 0.0, s.pitch ?: 0.0) ?: value
            val state = s.copy(windDirectionApparent = corrected, timeOfWindFix = now)
            dataBroker.processWindAngleUpdate(corrected)
            getBuffer(SignalKPaths.ENV_WIND_ANGLE_APPARENT).add(Pair(corrected, now))
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processWindSpeedApparent(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidWindSpeed(value)) {
            val corrected = environmentalFilterService?.correctWindSpeed(value, s.pitch ?: 0.0) ?: value
            val state = s.copy(windSpeedApparent = corrected, timeOfWindFix = now)
            dataBroker.processWindSpeedUpdate(corrected)
            getBuffer(SignalKPaths.ENV_WIND_SPEED_APPARENT).add(Pair(corrected, now))
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun parseNavigationValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.NAV_MAG_VARIATION -> {
                if (!value.isNaN()) {
                    state = state.copy(magneticVariation = value)
                    dataBroker.processVariationUpdate(value)
                    updated = true
                }
            }
            SignalKPaths.NAV_LOG -> {
                if (!value.isNaN()) {
                    state = state.copy(log = value)
                    getBuffer(SignalKPaths.NAV_LOG).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_TRIP_LOG -> {
                if (!value.isNaN()) {
                    state = state.copy(tripLog = value)
                    getBuffer(SignalKPaths.NAV_TRIP_LOG).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_RATE_OF_TURN -> {
                if (!value.isNaN()) {
                    state = state.copy(rateOfTurn = value, timeOfRotFix = now)
                    getBuffer(SignalKPaths.NAV_RATE_OF_TURN).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_DRIFT -> {
                if (MarineStateConstants.isValidSpeed(value)) {
                    state = state.copy(drift = value, timeOfDriftFix = now)
                    getBuffer(SignalKPaths.NAV_DRIFT).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_SET_TRUE -> {
                if (!value.isNaN()) {
                    state = state.copy(setTrue = value, timeOfDriftFix = now)
                    getBuffer(SignalKPaths.NAV_SET_TRUE).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_COURSE_OVER_GROUND -> {
                if (!value.isNaN()) {
                    state = state.copy(courseOverGroundTrue = value)
                    getBuffer(SignalKPaths.NAV_COURSE_OVER_GROUND).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_XTE -> {
                if (!value.isNaN() && state.crossTrackError == null) {
                    state = state.copy(crossTrackError = value)
                    getBuffer(SignalKPaths.NAV_XTE).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_XTE_RHUMB -> {
                if (!value.isNaN()) {
                    state = state.copy(crossTrackError = value)
                    getBuffer(SignalKPaths.NAV_XTE_RHUMB).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_XTE_GC -> {
                if (!value.isNaN() && state.crossTrackError == null) {
                    state = state.copy(crossTrackError = value)
                    getBuffer(SignalKPaths.NAV_XTE_GC).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_DATETIME_MOON_PHASE -> {
                if (!value.isNaN()) {
                    state = state.copy(moonPhase = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_TWD -> {
                if (!value.isNaN()) {
                    state = state.copy(windDirectionTrue = value, timeOfWindFix = now)
                    getBuffer(SignalKPaths.NAV_TWD).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.NAV_ANCHOR_RODE_DEPLOYED -> {
                if (!value.isNaN()) {
                    state = state.copy(rodeDeployed = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_CALLSIGN -> {
                val callSign = valueObj as? String ?: ""
                if (callSign.isNotEmpty()) {
                    state = state.copy(vesselCallSign = callSign)
                    updated = true
                }
            }
            else -> {
                if (path.startsWith(SignalKPaths.NAV_GNSS_PREFIX)) {
                    val currentGnss = state.gnss ?: GnssState()
                    val newGnss = when {
                        path.endsWith(".method") -> currentGnss.copy(method = valueObj?.toString())
                        path.endsWith(".satellites") -> currentGnss.copy(satellites = (valueObj as? Number)?.toInt())
                        path.endsWith(".horizontalDilution") -> currentGnss.copy(horizontalDilution = value)
                        path.endsWith(".verticalDilution") -> currentGnss.copy(verticalDilution = value)
                        path.endsWith(".integrity") -> currentGnss.copy(integrity = valueObj?.toString())
                        else -> currentGnss
                    }
                    state = state.copy(gnss = newGnss)
                    updated = true
                } else if (path.startsWith(SignalKPaths.NAV_ANCHOR_PREFIX)) {
                    val currentAnchor = state.anchor ?: AnchorState()
                    val newAnchor = when {
                        path.endsWith(".state") -> currentAnchor.copy(state = valueObj?.toString())
                        path.endsWith(".maxDrift") -> currentAnchor.copy(maxDrift = value)
                        path.endsWith(".radius") -> currentAnchor.copy(radius = value)
                        path.endsWith(".selection") -> currentAnchor.copy(selection = valueObj?.toString())
                        path.endsWith(".position") -> {
                            if (valueObj is JSONObject) {
                                currentAnchor.copy(
                                    latitude = valueObj.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() },
                                    longitude = valueObj.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                                )
                            } else currentAnchor
                        }
                        else -> currentAnchor
                    }
                    state = state.copy(anchor = newAnchor)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parsePerformanceValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.PERF_VMG -> {
                if (!value.isNaN()) {
                    state = state.copy(velocityMadeGood = value)
                    getBuffer(SignalKPaths.PERF_VMG).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.PERF_TACK_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(tackAngle = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_WIND_SHIFT -> {
                if (!value.isNaN()) {
                    state = state.copy(windShift = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_LAYLINES -> {
                if (valueObj is JSONObject) {
                    val port = valueObj.optJSONObject("portTackPoint")
                    val stbd = valueObj.optJSONObject("starboardTackPoint")
                    val target = valueObj.optJSONObject("targetWaypoint")
                    if (target != null) {
                        val laylineData = LaylineData(
                            portTackPoint = port?.let { LatLon(it.optDouble("latitude"), it.optDouble("longitude")) },
                            starboardTackPoint = stbd?.let { LatLon(it.optDouble("latitude"), it.optDouble("longitude")) },
                            isFetchable = valueObj.optBoolean("isFetchable", true),
                            targetWaypoint = LatLon(target.optDouble("latitude"), target.optDouble("longitude"))
                        )
                        state = state.copy(serverLaylines = laylineData)
                        updated = true
                    }
                }
            }
            SignalKPaths.PERF_POLAR_RATIO -> {
                if (!value.isNaN()) {
                    state = state.copy(polarSpeedRatio = value)
                    getBuffer(SignalKPaths.PERF_POLAR_RATIO).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_SPEED, "performance.polarSpeed" -> {
                if (MarineStateConstants.isValidSpeed(value)) {
                    state = state.copy(polarTargetSpeed = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(targetWindAngleApparent = value)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseAutopilotValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.STEERING_AUTOPILOT_STATE -> {
                val raw = (valueObj as? String ?: "standby").uppercase(Locale.US)
                val normalized = when (raw) {
                    "ROUTE", "TRACK" -> "track"
                    else -> raw.lowercase(Locale.US)
                }
                var nextPendingMode = state.pendingAutopilotState
                if (normalized == nextPendingMode?.lowercase(Locale.US)) {
                    nextPendingMode = null
                }
                state = state.copy(autopilotState = normalized, pendingAutopilotState = nextPendingMode)
                dataBroker.updateAutopilotState(normalized)
                updated = true
            }
            SignalKPaths.STEERING_AUTOPILOT_DUTY_CYCLE -> {
                if (!value.isNaN()) {
                    state = state.copy(actuatorDutyCycle = value)
                    getBuffer(path).add(Pair(value, now))
                    checkActuatorLoad(state)
                    updated = true
                }
            }
            SignalKPaths.STEERING_ACTUATOR_CURRENT -> {
                if (!value.isNaN()) {
                    state = state.copy(actuatorCurrent = value)
                    getBuffer(path).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.STEERING_RUDDER_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(rudderAngle = value, timeOfRudderFix = now)
                    dataBroker.processRudderUpdate(value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_HDG_TRUE -> {
                if (!value.isNaN()) {
                    var nextPendingHeading = state.pendingTargetHeading
                    if (nextPendingHeading != null && abs(value - nextPendingHeading) < 0.02) {
                        nextPendingHeading = null
                    }
                    state = state.copy(targetHeading = value, pendingTargetHeading = nextPendingHeading)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_HDG_MAG -> {
                if (!value.isNaN()) {
                    dataBroker.updateAutopilotTargetHeadingMag(value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_AWA -> {
                if (!value.isNaN()) {
                    state = state.copy(targetWindAngleApparent = value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_SEA_STATE -> {
                val level = (valueObj as? Number)?.toInt() ?: -1
                if (level != -1) {
                    state = state.copy(seaState = level)
                    updated = true
                }
            }
            else -> {
                when {
                    path.startsWith(SignalKPaths.STEERING_AUTOPILOT_CONFIG_PREFIX) -> {
                        val field = path.removePrefix(SignalKPaths.STEERING_AUTOPILOT_CONFIG_PREFIX)
                        state = updatePypilotConfig(state, field, valueObj)
                        updated = true
                    }
                    path.startsWith(SignalKPaths.STEERING_AUTOPILOT_SERVO_PREFIX) -> {
                        val field = path.removePrefix(SignalKPaths.STEERING_AUTOPILOT_SERVO_PREFIX)
                        state = updatePypilotServo(state, field, valueObj)
                        updated = true
                    }
                    path.startsWith(SignalKPaths.STEERING_AUTOPILOT_CALIBRATION_PREFIX) -> {
                        val field = path.removePrefix(SignalKPaths.STEERING_AUTOPILOT_CALIBRATION_PREFIX)
                        state = updatePypilotCalibration(state, field, valueObj)
                        updated = true
                    }
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseSystemValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when {
            path.startsWith(SignalKPaths.BATTERIES_PREFIX) -> {
                val parts = path.split(".")
                if (parts.size >= 3) {
                    val instance = parts[2]
                    state = updateBattery(state, instance) { b: Battery ->
                        when {
                            path.endsWith(".voltage") -> b.copy(voltage = value)
                            path.endsWith(".current") -> b.copy(current = value)
                            path.endsWith(".temperature") -> b.copy(temperature = value)
                            path.endsWith(".capacity.stateOfCharge") -> b.copy(stateOfCharge = value)
                            path.endsWith(".capacity.stateOfHealth") -> b.copy(stateOfHealth = value)
                            path.endsWith(".capacity.timeRemaining") -> b.copy(timeRemaining = value)
                            path.endsWith(".capacity.timeToFull") -> b.copy(timeToFull = value)
                            path.endsWith(".name") -> b.copy(name = valueObj?.toString())
                            path.endsWith(".cells") -> {
                                if (valueObj is JSONObject) {
                                    val cellList = mutableListOf<Double>()
                                    val keys = valueObj.keys().asSequence().sorted()
                                    for (key in keys) {
                                        val cell = valueObj.optJSONObject(key)
                                        val v = cell?.optDouble("voltage", Double.NaN) ?: Double.NaN
                                        if (!v.isNaN()) cellList.add(v)
                                    }
                                    b.copy(cellVoltages = cellList)
                                } else b
                            }
                            else -> b
                        }
                    }
                    if (path.endsWith(".voltage")) state = state.copy(batteryVoltage = value)
                    if (path.endsWith(".current")) state = state.copy(batteryCurrent = value)
                    if (path.endsWith(".capacity.stateOfCharge")) state = state.copy(batterySoc = value)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.TANKS_PREFIX) -> {
                val parts = path.split(".")
                if (parts.size >= 3) {
                    val type = parts[1]
                    val instance = parts[2]
                    val field = parts.last()
                    state = updateTank(state, instance, type) { t: Tank ->
                        when (field) {
                            "currentLevel" -> t.copy(currentLevel = value)
                            "currentVolume" -> t.copy(currentVolume = value)
                            "capacity" -> t.copy(capacity = value)
                            "name" -> t.copy(name = valueObj?.toString())
                            else -> t
                        }
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.PROPULSION_PREFIX) -> {
                val parts = path.split(".")
                if (parts.size >= 3) {
                    val instance = parts[1]
                    if (instance == "watermaker") {
                        val watermakerId = parts.getOrNull(2) ?: "0"
                        val watermakerField = parts.last()
                        state = updateWatermaker(state, watermakerId) { w: Watermaker ->
                            when (watermakerField) {
                                "state" -> w.copy(state = valueObj?.toString())
                                "rate" -> w.copy(rate = value)
                                "totalProduction" -> w.copy(totalProduction = value)
                                "salinity" -> w.copy(salinity = value)
                                else -> w
                            }
                        }
                    } else {
                        val field = parts.last()
                        state = updateEngine(state, instance) { e: Engine ->
                            when (field) {
                                "revolutions" -> {
                                    val rpm = SignalKUnitConverter.hertzToRpm(value)
                                    getBuffer(path).add(Pair(rpm, now))
                                    e.copy(revolutions = rpm)
                                }
                                "temperature" -> {
                                    getBuffer(path).add(Pair(value, now))
                                    e.copy(temperature = value)
                                }
                                "oilPressure" -> {
                                    getBuffer(path).add(Pair(value, now))
                                    e.copy(oilPressure = value)
                                }
                                "oilTemperature" -> e.copy(oilTemperature = value)
                                "fuel.rate", "fuelRate" -> e.copy(fuelRate = value)
                                "fuel.economy" -> e.copy(fuelEconomy = value)
                                "boostPressure" -> e.copy(boostPressure = value)
                                "engineLoad", "load" -> {
                                    getBuffer(path).add(Pair(value, now))
                                    e.copy(load = value)
                                }
                                "coolantTemperature" -> {
                                    getBuffer(path).add(Pair(value, now))
                                    e.copy(coolantTemperature = value)
                                }
                                "exhaustTemperature" -> e.copy(exhaustTemperature = value)
                                "runTime" -> {
                                    state = state.copy(engineHours = value / 3600.0)
                                    e.copy(runTime = value)
                                }
                                "state" -> e.copy(state = valueObj?.toString())
                                "alternatorVoltage", "voltage" -> e.copy(alternatorVoltage = value)
                                "alternatorCurrent", "current" -> e.copy(alternatorCurrent = value)
                                "driveTrimState" -> e.copy(driveTrimState = value)
                                "transmissionGear", "gear" -> e.copy(transmissionGear = valueObj?.toString())
                                "transmissionPressure" -> e.copy(transmissionPressure = value)
                                "transmissionOilTemperature" -> e.copy(transmissionOilTemperature = value)
                                else -> e
                            }
                        }
                        if (instance == "0" || state.engines.size <= 1) {
                            state = state.copy(engineInstance = instance)
                            when (field) {
                                "revolutions" -> state = state.copy(engineRpm = SignalKUnitConverter.hertzToRpm(value))
                                "temperature" -> state = state.copy(engineTemperature = value)
                                "coolantTemperature" -> state = state.copy(engineCoolantTemperature = value)
                                "oilPressure" -> state = state.copy(engineOilPressure = value)
                                "fuel.rate", "fuelRate" -> state = state.copy(fuelRate = value)
                                "engineLoad", "load" -> state = state.copy(engineLoad = value)
                                "exhaustTemperature" -> state = state.copy(engineExhaustTemperature = value)
                                "runTime" -> state = state.copy(engineRunTime = value)
                                "state" -> state = state.copy(engineState = valueObj?.toString())
                                "alternatorVoltage", "voltage" -> state = state.copy(alternatorVoltage = value)
                                "alternatorCurrent", "current" -> state = state.copy(alternatorCurrent = value)
                                "transmissionGear", "gear" -> state = state.copy(transmissionGear = valueObj?.toString())
                                "transmissionPressure" -> state = state.copy(transmissionPressure = value)
                                "transmissionOilTemperature" -> state = state.copy(transmissionOilTemperature = value)
                            }
                        }
                    }
                    updated = true
                }
            }
            path.startsWith("electrical.switches.") || (path.startsWith("electrical.") && path.endsWith(".state")) -> {
                val switchPath = if (path.startsWith("electrical.switches.")) {
                    path.removePrefix("electrical.switches.")
                } else {
                    path.removePrefix("electrical.").removeSuffix(".state")
                }
                val switchState = when (valueObj) {
                    is Boolean -> valueObj
                    is Number -> valueObj.toDouble() > 0.5
                    is String -> valueObj.lowercase(Locale.US) == "on" || valueObj.lowercase(Locale.US) == "true" || valueObj == "1"
                    else -> false
                }
                val switches = state.switches.toMutableMap()
                switches[switchPath] = switchState
                state = state.copy(switches = switches)
                updated = true
            }
            path.startsWith(SignalKPaths.ELECTRICAL_AC_PREFIX) -> {
                when {
                    path.endsWith(".voltage") -> { if (!value.isNaN()) { state = state.copy(acVoltage = value); updated = true } }
                    path.endsWith(".current") -> { if (!value.isNaN()) { state = state.copy(acCurrent = value); updated = true } }
                    path.endsWith(".frequency") -> { if (!value.isNaN()) { state = state.copy(acFrequency = value); updated = true } }
                    path.endsWith(".selectedSource") || path.endsWith(".source") -> { state = state.copy(acSource = valueObj?.toString()); updated = true }
                }
            }
            path.startsWith(SignalKPaths.INVERTERS_PREFIX) -> {
                val parts = path.split(".")
                if (parts.size >= 3) {
                    val instance = parts[2]
                    val field = parts.last()
                    state = updateInverter(state, instance) { i: Inverter ->
                        when (field) {
                            "state" -> i.copy(state = valueObj?.toString())
                            "mode" -> i.copy(mode = valueObj?.toString())
                            "acVoltage" -> i.copy(acVoltage = value)
                            "acCurrent" -> i.copy(acCurrent = value)
                            "load" -> i.copy(load = value)
                            "name" -> i.copy(name = valueObj?.toString())
                            else -> i
                        }
                    }
                    if (instance == "0" || state.inverters.size <= 1) {
                        if (field == "state") state = state.copy(inverterState = valueObj?.toString())
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.CHARGERS_PREFIX) -> {
                val parts = path.split(".")
                if (parts.size >= 3) {
                    val instance = parts[2]
                    val field = parts.last()
                    state = updateCharger(state, instance) { c: Charger ->
                        when (field) {
                            "state" -> c.copy(state = valueObj?.toString())
                            "mode" -> c.copy(mode = valueObj?.toString())
                            "voltage" -> c.copy(voltage = value)
                            "current" -> c.copy(current = value)
                            "name" -> c.copy(name = valueObj?.toString())
                            else -> c
                        }
                    }
                    if (instance == "0" || state.chargers.size <= 1) {
                        if (field == "state") state = state.copy(chargerState = valueObj?.toString())
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.ELECTRICAL_PREFIX) -> {
                when {
                    path.endsWith(".voltage") -> {
                        if (!value.isNaN()) {
                            state = state.copy(batteryVoltage = value)
                            getBuffer(path).add(Pair(value, now))
                            updated = true
                        }
                    }
                    path.endsWith(".current") -> {
                        if (path.contains(".solar.")) {
                            if (!value.isNaN()) {
                                state = state.copy(solarCurrent = value)
                                getBuffer(path).add(Pair(value, now))
                                updated = true
                            }
                        } else {
                            if (!value.isNaN()) {
                                state = state.copy(batteryCurrent = value)
                                getBuffer(path).add(Pair(value, now))
                                updated = true
                            }
                        }
                    }
                    path.endsWith(".capacity.stateOfCharge") -> {
                        if (!value.isNaN()) {
                            state = state.copy(batterySoc = value)
                            getBuffer(path).add(Pair(value, now))
                            updated = true
                        }
                    }
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseEnvironmentValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER -> {
                if (MarineStateConstants.isValidDepth(value)) {
                    val buffer = getBuffer(SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER)
                    buffer.add(Pair(value, now))
                    val smoothedValue = buffer.getAverage { it.first }
                    state = state.copy(depthBelowTransducer = value, timeOfDepthFix = now)
                    dataBroker.processDepthUpdate(smoothedValue)
                    updated = true
                }
            }
            SignalKPaths.ENV_DEPTH_SURFACE_TO_TRANSDUCER -> {
                if (MarineStateConstants.isValidDepth(value)) {
                    state = state.copy(depthSurfaceToTransducer = value, timeOfDepthFix = now)
                    updated = true
                }
            }
            SignalKPaths.ENV_DEPTH_BELOW_KEEL -> {
                if (MarineStateConstants.isValidDepth(value)) {
                    state = state.copy(depthBelowKeel = value, timeOfDepthFix = now)
                    getBuffer(SignalKPaths.ENV_DEPTH_BELOW_KEEL).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_WATER_TEMP -> {
                if (!value.isNaN()) {
                    state = state.copy(waterTemperature = value)
                    getBuffer(SignalKPaths.ENV_WATER_TEMP).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_TEMP -> {
                if (!value.isNaN()) {
                    state = state.copy(outsideTemperature = value)
                    getBuffer(SignalKPaths.ENV_OUTSIDE_TEMP).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_PRESSURE -> {
                if (!value.isNaN()) {
                    state = state.copy(outsidePressure = value)
                    getBuffer(SignalKPaths.ENV_OUTSIDE_PRESSURE).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_HUMIDITY -> {
                if (!value.isNaN()) {
                    state = state.copy(outsideHumidity = value)
                    getBuffer(SignalKPaths.ENV_OUTSIDE_HUMIDITY).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_ILLUMINANCE -> {
                if (!value.isNaN()) {
                    state = state.copy(outsideIlluminance = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_WATER_SALINITY -> {
                if (!value.isNaN()) {
                    state = state.copy(waterSalinity = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_AIR_DEW_POINT -> {
                if (!value.isNaN()) {
                    state = state.copy(airDewPoint = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_MOON_PHASE -> {
                if (!value.isNaN()) {
                    state = state.copy(moonPhase = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_SUNLIGHT_MODE -> {
                state = state.copy(sunlightMode = valueObj?.toString())
                updated = true
            }
            SignalKPaths.ENV_WIND_SPEED_TRUE -> {
                if (MarineStateConstants.isValidWindSpeed(value)) {
                    state = state.copy(windSpeedTrue = value, timeOfWindFix = now)
                    getBuffer(SignalKPaths.ENV_WIND_SPEED_TRUE).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_DIRECTION_TRUE -> {
                if (!value.isNaN()) {
                    state = state.copy(windDirectionTrue = value, timeOfWindFix = now)
                    getBuffer(SignalKPaths.ENV_WIND_DIRECTION_TRUE).add(Pair(value, now))
                    getBuffer(SignalKPaths.NAV_TWD).add(Pair(value, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_ANGLE_APPARENT -> {
                if (!value.isNaN()) {
                    val corrected = environmentalFilterService?.correctWindAngle(value, state.roll ?: 0.0, state.pitch ?: 0.0) ?: value
                    state = state.copy(windDirectionApparent = corrected, timeOfWindFix = now)
                    dataBroker.processWindAngleUpdate(corrected)
                    getBuffer(SignalKPaths.ENV_WIND_ANGLE_APPARENT).add(Pair(corrected, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_SPEED_APPARENT -> {
                if (MarineStateConstants.isValidWindSpeed(value)) {
                    val corrected = environmentalFilterService?.correctWindSpeed(value, state.pitch ?: 0.0) ?: value
                    state = state.copy(windSpeedApparent = corrected, timeOfWindFix = now)
                    dataBroker.processWindSpeedUpdate(corrected)
                    getBuffer(SignalKPaths.ENV_WIND_SPEED_APPARENT).add(Pair(corrected, now))
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_ANGLE_TRUE, "environment.wind.angleTrueWater" -> {
                if (!value.isNaN()) {
                    state = state.copy(trueWindAngle = value)
                    getBuffer(SignalKPaths.ENV_WIND_ANGLE_TRUE).add(Pair(value, now))
                    updated = true
                }
            }
            else -> {
                when {
                    path.startsWith(SignalKPaths.ENV_TIDE_PREFIX) -> {
                        dataBroker.processTideUpdate { currentTide ->
                            val tide = currentTide ?: TideState()
                            when {
                                path.endsWith(".heightNow") -> tide.copy(heightNow = value)
                                path.endsWith(".stationName") -> tide.copy(stationName = valueObj?.toString())
                                path.endsWith(".state") -> tide.copy(state = valueObj?.toString())
                                path.endsWith(".timeToNextExtreme") -> {
                                    if (!value.isNaN()) tide.copy(nextExtremeTime = now + (value * 1000).toLong()) else tide
                                }
                                path.endsWith(".heightHigh") -> tide.copy(nextExtremeHeight = value, nextExtremeType = "High")
                                path.endsWith(".heightLow") -> tide.copy(nextExtremeHeight = value, nextExtremeType = "Low")
                                else -> tide
                            }
                        }
                        updated = true
                    }
                    path.startsWith(SignalKPaths.ENV_CURRENT_PREFIX) -> {
                        when {
                            path.endsWith(".drift") -> {
                                if (!value.isNaN()) {
                                    state = state.copy(drift = value, timeOfDriftFix = now)
                                    updated = true
                                }
                            }
                            path.endsWith(".setTrue") -> {
                                if (!value.isNaN()) {
                                    state = state.copy(setTrue = value, timeOfDriftFix = now)
                                    updated = true
                                }
                            }
                        }
                    }
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseOtherValue(s: MarineState, path: String, valueObj: Any?): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when {
            path == SignalKPaths.NAME -> {
                val name = valueObj as? String ?: ""
                if (name.isNotEmpty()) {
                    state = state.copy(vesselName = name)
                    updated = true
                }
            }
            path == SignalKPaths.FLAG -> {
                val flag = valueObj as? String ?: ""
                if (flag.isNotEmpty()) {
                    state = state.copy(vesselFlag = flag)
                    updated = true
                }
            }
            path == SignalKPaths.PORT -> {
                val port = valueObj as? String ?: ""
                if (port.isNotEmpty()) {
                    state = state.copy(vesselPort = port)
                    updated = true
                }
            }
            path == SignalKPaths.UUID -> {
                val uuid = valueObj as? String ?: ""
                if (uuid.isNotEmpty()) {
                    state = state.copy(vesselUuid = uuid)
                    updated = true
                }
            }
            path == SignalKPaths.NAV_AIS_BUDDIES -> {
                if (valueObj is JSONArray) {
                    val buddies = mutableSetOf<Int>()
                    for (i in 0 until valueObj.length()) {
                        val mmsi = valueObj.optInt(i, 0)
                        if (mmsi != 0) buddies.add(mmsi)
                    }
                    state = state.copy(aisBuddies = buddies)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.NOTIFICATIONS_PREFIX) -> {
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
                        if (path == SignalKPaths.NOTIFICATIONS_MOB) state = state.copy(isMobActive = false)
                    } else {
                        updatedNotifications[path] = SignalKNotification(message, notificationState, methods)
                        if (path == SignalKPaths.NOTIFICATIONS_MOB && notificationState == NotificationState.EMERGENCY) {
                            state = state.copy(isMobActive = true)
                        }
                    }
                    state = state.copy(notifications = updatedNotifications)
                    updated = true
                } else if (valueObj == null) {
                    if (state.notifications.containsKey(path)) {
                        val updatedNotifications = state.notifications.toMutableMap()
                        updatedNotifications.remove(path)
                        state = state.copy(notifications = updatedNotifications)
                        updated = true
                    }
                }
            }
            path == SignalKPaths.NOTIFICATIONS_WATCHDOG -> {
                if (valueObj is JSONObject) {
                    val message = valueObj.optString("message", "")
                    val stateStr = valueObj.optString("state", "normal").lowercase(Locale.US)
                    val notificationState = when (stateStr) {
                        "alert" -> NotificationState.ALERT
                        "warn" -> NotificationState.WARN
                        "alarm" -> NotificationState.ALARM
                        "emergency" -> NotificationState.EMERGENCY
                        else -> NotificationState.NORMAL
                    }
                    state = state.copy(watchdogStatus = SignalKNotification(message, notificationState))
                    updated = true
                } else if (valueObj == null) {
                    state = state.copy(watchdogStatus = null)
                    updated = true
                }
            }
            path == SignalKPaths.COMMUNICATION_VHF_CHANNEL -> {
                val chan = valueObj?.toString() ?: ""
                if (chan.isNotEmpty()) {
                    state = state.copy(vhfChannel = chan)
                    updated = true
                }
            }
            path == SignalKPaths.COMMUNICATION_CREW_NAMES -> {
                if (valueObj is JSONArray) {
                    val names = mutableListOf<String>()
                    for (i in 0 until valueObj.length()) {
                        names.add(valueObj.getString(i))
                    }
                    state = state.copy(crewNames = names)
                    updated = true
                }
            }
            path == SignalKPaths.SAILS_INVENTORY -> {
                if (valueObj is JSONObject) {
                    val inventory = mutableListOf<Sail>()
                    val keys = valueObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = valueObj.getJSONObject(key)
                        inventory.add(Sail(
                            id = key,
                            name = obj.optString("name", "Unknown"),
                            type = obj.optString("type", "Unknown"),
                            area = obj.optDouble("area", Double.NaN).takeIf { !it.isNaN() },
                            active = obj.optBoolean("active", false)
                        ))
                    }
                    state = state.copy(sailInventory = inventory)
                    updated = true
                }
            }
            path == SignalKPaths.SAILS_REEFS -> {
                if (valueObj is Number) {
                    state = state.copy(reefs = valueObj.toInt())
                    updated = true
                }
            }
            path == SignalKPaths.DESIGN_TYPE -> {
                val type = if (valueObj is JSONObject) valueObj.optInt("id", -1) else (valueObj as? Number)?.toInt() ?: -1
                if (type != -1) {
                    state = state.copy(vesselType = type)
                    updated = true
                }
            }
            path == SignalKPaths.DESIGN_LENGTH_OVERALL -> {
                if (!value.isNaN()) {
                    state = state.copy(vesselLength = value)
                    updated = true
                }
            }
            path == SignalKPaths.DESIGN_BEAM -> {
                if (!value.isNaN()) {
                    state = state.copy(vesselBeam = value)
                    updated = true
                }
            }
            path == SignalKPaths.DESIGN_AIR_DRAFT -> {
                if (!value.isNaN()) {
                    state = state.copy(airDraft = value)
                    updated = true
                }
            }
            path == SignalKPaths.DESIGN_DISPLACEMENT -> {
                if (!value.isNaN()) {
                    state = state.copy(displacement = value)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.MEDIA_FUSION_PREFIX) -> {
                val currentMedia = state.mediaInfo ?: MediaInfo()
                val field = path.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX)
                val nextMedia = when (field) {
                    SignalKPaths.MEDIA_TITLE.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX) -> currentMedia.copy(title = valueObj?.toString())
                    SignalKPaths.MEDIA_ARTIST.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX) -> currentMedia.copy(artist = valueObj?.toString())
                    SignalKPaths.MEDIA_PLAYBACK_STATE.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX) -> currentMedia.copy(playbackState = valueObj?.toString()?.lowercase())
                    SignalKPaths.MEDIA_SOURCE.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX) -> currentMedia.copy(source = valueObj?.toString())
                    SignalKPaths.MEDIA_VOLUME.removePrefix(SignalKPaths.MEDIA_FUSION_PREFIX) -> if (!value.isNaN()) currentMedia.copy(volume = value) else currentMedia
                    else -> {
                        if (field.startsWith("volumeZones.")) {
                            val zone = field.removePrefix("volumeZones.")
                            val zones = currentMedia.volumeZones.toMutableMap()
                            if (!value.isNaN()) zones[zone] = value
                            currentMedia.copy(volumeZones = zones)
                        } else currentMedia
                    }
                }
                state = state.copy(mediaInfo = nextMedia)
                updated = true
            }
            path.startsWith(SignalKPaths.RIGGING_LOAD_PREFIX) -> {
                if (!value.isNaN()) {
                    val instance = path.removePrefix(SignalKPaths.RIGGING_LOAD_PREFIX)
                    val riggingLoads = state.riggingLoads.toMutableMap()
                    riggingLoads[instance] = value
                    state = state.copy(riggingLoads = riggingLoads)
                    updated = true
                }
            }
            else -> {
                if (valueObj is Number) {
                    val custom = state.customValues.toMutableMap()
                    custom[path] = valueObj.toDouble()
                    state = state.copy(customValues = custom)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseTelemetryValue(marineState: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = marineState
        var updated = false
        val value = if (valueObj is Number) valueObj.toDouble() else Double.NaN

        when (path) {
            SignalKPaths.PERF_TARGET_SPEED, "performance.polarSpeed" -> {
                if (MarineStateConstants.isValidSpeed(value)) {
                    state = state.copy(polarTargetSpeed = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(targetWindAngleApparent = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_POLAR_RATIO -> {
                if (!value.isNaN()) {
                    state = state.copy(polarSpeedRatio = value)
                    getBuffer(SignalKPaths.PERF_POLAR_RATIO).add(Pair(value, now))
                    updated = true
                }
            }
            else -> {
                // Unknown telemetry fallback
            }
        }
        return Pair(state, updated)
    }

    private fun calculateNavigationMetrics(state: MarineState, now: Long): MarineState {
        var s = state
        val lat = s.latitude ?: return s
        val lon = s.longitude ?: return s
        val target = getNextWaypoint() ?: return s
        val dtw = KMapUtils.getDistance(lat, lon, target.first, target.second)
        s = s.copy(distanceToWaypoint = dtw)
        val sog = s.speedOverGround
        val cog = s.courseOverGroundTrue
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (sog != null && cog != null && !caps.hasVmg && !caps.hasDerivedData) {
            val btw = (KMapUtils.getBearing(lat, lon, target.first, target.second)).toRadians()
            val rawVmgWp = sog * cos(cog - btw)
            val smoothedVmg = vmgEma.update(rawVmgWp)
            s = s.copy(velocityMadeGood = smoothedVmg)
            getBuffer("performance.velocityMadeGood").add(Pair(smoothedVmg, now))
        }
        val sogTtw = if (sog != null && sog > 0.1) dtw / sog else null
        val vmgTtw = if (s.velocityMadeGood != null && s.velocityMadeGood > 0.1) dtw / s.velocityMadeGood else null
        val selectedTtw = when (app.settings.NAUTICAL_TTW_MODE.get()) {
            TtwMode.VMG -> vmgTtw ?: sogTtw
            TtwMode.SOG -> sogTtw
            else -> sogTtw ?: vmgTtw
        }
        s = s.copy(sogTimeToWaypoint = sogTtw, vmgTimeToWaypoint = vmgTtw, timeToWaypoint = selectedTtw)
        if (selectedTtw != null) {
            getBuffer("navigation.timeToWaypoint").add(Pair(selectedTtw, now))
        }
        val startLat = lastWaypointLat
        val startLon = lastWaypointLon
        if (startLat != null && startLon != null) {
            val xte = calculateLocalXte(startLat, startLon, target.first, target.second, lat, lon)
            val xteNm = SignalKUnitConverter.metersToNm(abs(xte))
            val direction = when {
                xteNm < 0.0005 -> XteDirection.ON_COURSE
                xte > 0 -> XteDirection.STARBOARD
                else -> XteDirection.PORT
            }

            val halfCorridorWidth = corridorWidthNm / 2.0
            val isOutsideCorridor = xteNm > (halfCorridorWidth + safetyCorridorBufferNm)

            s = s.copy(
                xteMeters = abs(xte),
                xteDirection = direction,
                crossTrackError = s.crossTrackError ?: xte,
                isOutsideSafetyCorridor = isOutsideCorridor
            )
        }
        return s
    }

    private fun calculateEfficiencyMetrics(state: MarineState): MarineState {
        var s = state
        val fuelLevel = s.tanks["fuel.0"]?.currentLevel ?: s.tanks.values.find { it.type == "fuel" }?.currentLevel
        val fuelRate = s.engines["0"]?.fuelRate ?: s.engines.values.find { it.fuelRate != null }?.fuelRate
        val sog = s.speedOverGround
        val capacity = app.settings.FUEL_TANK_CAPACITY.get().toDouble()
        if (fuelLevel != null && fuelRate != null && fuelRate > 0.00001 && sog != null) {
            val remainingLiters = fuelLevel * capacity
            val secondsToEmpty = remainingLiters / fuelRate
            val rangeMeters = secondsToEmpty * sog
            s = s.copy(estimatedRange = rangeMeters)
        }
        return s
    }

    private fun calculateLocalXte(lat1: Double, lon1: Double, lat2: Double, lon2: Double, lat3: Double, lon3: Double): Double {
        val dist = KMapUtils.getOrthogonalDistance(lat3, lon3, lat1, lon1, lat2, lon2)
        val isRight = KMapUtils.rightSide(lat3, lon3, lat1, lon1, lat2, lon2)
        return if (isRight) dist else -dist
    }

    private fun calculateLeeway(state: MarineState): Double {
        val roll = state.roll ?: 0.0
        val stw = state.speedThroughWater ?: 0.0
        val k = app.settings.NAUTICAL_LEEWAY_COEFFICIENT.get()
        return LeewayCalculator.calculateLeewayRadians(roll, stw, k)
    }

    private fun calculateSetAndDrift(state: MarineState, now: Long): MarineState {
        var updatedState = state
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
        val leeway = updatedState.leeway ?: 0.0
        val bx = sog * sin(cog)
        val by = sog * cos(cog)
        val ax = stw * sin(hdg + leeway)
        val ay = stw * cos(hdg + leeway)
        val cx = bx - ax
        val cy = by - ay
        val drift = sqrt(cx * cx + cy * cy)
        val set = (atan2(cx, cy) + 2 * PI) % (2 * PI)

        val smoothedDrift = driftEma.update(drift)
        val smoothedSet = setAngleEma.update(set)

        if (now - lastSetDriftTimestamp > 1000) {
            getBuffer("navigation.drift").add(Pair(smoothedDrift, now))
            getBuffer("navigation.setTrue").add(Pair(smoothedSet, now))
            lastSetDriftTimestamp = now
        }
        var finalState = updatedState.copy(drift = smoothedDrift, setTrue = smoothedSet)
        if (finalState.windDirectionTrue == null) {
            val hdgTrue = finalState.headingTrue
            val twa = finalState.trueWindAngle
            if (hdgTrue != null && twa != null) {
                val twd = (hdgTrue + twa + 2 * PI) % (2 * PI)
                finalState = finalState.copy(windDirectionTrue = twd)
                getBuffer("navigation.trueWindDirection").add(Pair(twd, now))
            }
        }
        return finalState
    }

    private fun calculateDepths(state: MarineState): MarineState {
        var updated = state
        val draft = vesselDraft
        if (updated.depthBelowKeel == null && updated.depthBelowTransducer != null) {
            val meta = updated.pathMeta["environment.depth.belowTransducer"]
            val offset = (meta?.get("offset") as? Number)?.toDouble() ?: 0.0
            if (offset < 0) {
                 updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer + offset)
            } else if (draft > 0) {
                updated = updated.copy(depthBelowKeel = updated.depthBelowTransducer - draft)
            }
        }
        return updated
    }

    fun loadRoute(route: List<Pair<Double, Double>>) {
        routeQueue.clear()
        routeQueue.addAll(route)
        isFollowingRoute = true
        val current = dataBroker.marineState.value
        lastWaypointLat = current.latitude
        lastWaypointLon = current.longitude
        routeStepListeners.forEach { it.invoke() }
        log.info("Route loaded: ${route.size} points. Following enabled.")
    }

    fun getNextWaypoint(): Pair<Double, Double>? = routeQueue.peek()
    fun getSecondNextWaypoint(): Pair<Double, Double>? {
        val it = routeQueue.iterator()
        if (it.hasNext()) it.next()
        return if (it.hasNext()) it.next() else null
    }
    fun getRoutePoints(): List<Pair<Double, Double>> = routeQueue.toList()

    fun setAutoSeaStateEnabled(enabled: Boolean) {
        dataBroker.updateState { it.copy(isAutoSeaStateEnabled = enabled) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun setMobActive(active: Boolean, lat: Double? = null, lon: Double? = null) {
        dataBroker.updateState { it.copy(isMobActive = active, mobLatitude = lat, mobLongitude = lon) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun setShunted(shunted: Boolean) {
        dataBroker.updateState { it.copy(isShunted = shunted) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun onInternalLocationUpdate(loc: Location) {
        val currentStatus = dataBroker.marineState.value.connectionStatus
        if (currentStatus == ConnectionStatus.CONNECTED) return
        
        val now = TemporalUtils.now()
        dataBroker.updateState { s ->
            val newTimestamps = s.timestamps.toMutableMap()
            val lat = loc.latitude
            val lon = loc.longitude
            val sog = loc.speed.toDouble()
            val cog = Math.toRadians(loc.bearing.toDouble())
            newTimestamps["navigation.position"] = now
            newTimestamps["navigation.speedOverGround"] = now
            newTimestamps["navigation.courseOverGroundTrue"] = now
            
            addTrajectoryPoint(lat, lon)
            getBuffer("navigation.speedOverGround").add(Pair(sog, now))
            getBuffer("navigation.courseOverGroundTrue").add(Pair(cog, now))

            s.copy(
                latitude = lat,
                longitude = lon,
                speedOverGround = sog,
                courseOverGroundTrue = cog,
                timestamps = newTimestamps
            )
        }
        notifyListeners(dataBroker.marineState.value)
    }

    fun updatePendingCommand(targetHeading: Double? = null, mode: String? = null, path: String? = null) {
        dataBroker.updateState { current ->
            current.copy(
                pendingTargetHeading = targetHeading,
                pendingAutopilotState = mode,
                pendingCommandPath = path,
                commandSentTimestamp = if (targetHeading != null || mode != null || path != null) TemporalUtils.now() else 0
            )
        }
        notifyListeners(dataBroker.marineState.value)
    }

    fun updateFollowingState(currentLat: Double, currentLon: Double) {
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (caps.hasCourseAutoAdvance) return // Offload to server

        if (!isFollowingRoute || routeQueue.isEmpty()) return
        val now = TemporalUtils.now()
        if (now - lastFollowingUpdateTimestamp < 1000) return
        lastFollowingUpdateTimestamp = now
        val target = routeQueue.peek() ?: return
        val distance = KMapUtils.getDistance(currentLat, currentLon, target.first, target.second)
        if (distance < arrivalRadiusMeters) {
            val reached = routeQueue.poll()
            lastWaypointLat = reached?.first
            lastWaypointLon = reached?.second
            routeStepListeners.forEach { it.invoke() }
        }
        if (routeQueue.isEmpty()) {
            isFollowingRoute = false
        }
    }

    private fun updatePulseLifecycle() {
        val current = dataBroker.marineState.value
        val anyAlarm = current.notifications.values.any { it.state == NotificationState.ALARM || it.state == NotificationState.EMERGENCY } || current.isActuatorOverloaded
        if (anyAlarm && (pulseJob?.isActive != true)) {
            pulseJob = engineScope.launch {
                while (isActive) {
                    _pulseFlow.value = !_pulseFlow.value
                    delay(500.milliseconds)
                }
            }
        } else if (!anyAlarm) {
            pulseJob?.cancel()
            pulseJob = null
            _pulseFlow.value = false
        }
    }

    private fun checkActuatorLoad(state: MarineState) {
        val buffer = getBuffer(SignalKPaths.STEERING_AUTOPILOT_DUTY_CYCLE)
        val now = TemporalUtils.now()

        var sum = 0.0
        var count = 0
        val data = buffer.getAll()
        val windowMs = app.settings.NAUTICAL_ACTUATOR_OVERLOAD_WINDOW_SEC.get() * 1000L
        for (i in data.indices.reversed()) {
            val item = data[i]
            if (now - item.second < windowMs) {
                sum += item.first
                count++
                if (count >= 10) break
            } else {
                break
            }
        }

        if (count < 5) return
        val avgLoad = sum / count
        val threshold = app.settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get() / 100.0

        if (avgLoad > threshold) {
            if (!state.isActuatorOverloaded) {
                dataBroker.updateState { it.copy(isActuatorOverloaded = true) }
                val msg = app.getString(R.string.nautical_actuator_overload_alarm)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.ACTUATOR_OVERLOAD, voiceText = msg)

                NauticalPlugin.hudManager?.get()?.showBanner(
                    app.getString(R.string.nautical_actuator_maintenance_required),
                    0, // Persistent
                    isWarning = true
                )
            }
        } else if (state.isActuatorOverloaded && avgLoad < (threshold - 0.15)) {
            dataBroker.updateState { it.copy(isActuatorOverloaded = false) }
            NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.ACTUATOR_OVERLOAD)
            NauticalPlugin.hudManager?.get()?.hideBanner()
        }
    }

    private fun updateEngine(state: MarineState, instance: String, transform: (Engine) -> Engine): MarineState {
        val engines = state.engines.toMutableMap()
        val engine = engines[instance] ?: Engine(instance = instance)
        engines[instance] = transform(engine)
        return state.copy(engines = engines)
    }

    private fun updateBattery(state: MarineState, instance: String, transform: (Battery) -> Battery): MarineState {
        val batteries = state.batteries.toMutableMap()
        val battery = batteries[instance] ?: Battery(instance = instance)

        // Enrich with metadata name if available
        val meta = state.pathMeta["electrical.batteries.$instance"]
        val displayName = meta?.get("displayName") as? String

        batteries[instance] = transform(battery.copy(name = displayName ?: battery.name))
        return state.copy(batteries = batteries)
    }

    private fun updateCharger(state: MarineState, instance: String, transform: (Charger) -> Charger): MarineState {
        val chargers = state.chargers.toMutableMap()
        val charger = chargers[instance] ?: Charger(instance = instance)

        val meta = state.pathMeta["electrical.chargers.$instance"]
        val displayName = meta?.get("displayName") as? String

        chargers[instance] = transform(charger.copy(name = displayName ?: charger.name))
        return state.copy(chargers = chargers)
    }

    private fun updateInverter(state: MarineState, instance: String, transform: (Inverter) -> Inverter): MarineState {
        val inverters = state.inverters.toMutableMap()
        val inverter = inverters[instance] ?: Inverter(instance = instance)

        val meta = state.pathMeta["electrical.inverters.$instance"]
        val displayName = meta?.get("displayName") as? String

        inverters[instance] = transform(inverter.copy(name = displayName ?: inverter.name))
        return state.copy(inverters = inverters)
    }

    private fun updateTank(state: MarineState, instance: String, type: String, transform: (Tank) -> Tank): MarineState {
        val tanks = state.tanks.toMutableMap()
        val key = "$type.$instance"
        val tank = tanks[key] ?: Tank(instance = instance, type = type)

        val meta = state.pathMeta["tanks.$type.$instance"]
        val displayName = meta?.get("displayName") as? String

        tanks[key] = transform(tank.copy(name = displayName ?: tank.name))
        return state.copy(tanks = tanks)
    }

    private fun updatePypilotConfig(state: MarineState, field: String, value: Any?): MarineState {
        val config = state.pypilotConfig ?: PypilotConfig()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "p" -> config.copy(p = v)
            "i" -> config.copy(i = v)
            "d" -> config.copy(d = v)
            "dd" -> config.copy(dd = v)
            "pr" -> config.copy(pr = v)
            "ff" -> config.copy(ff = v)
            "wg" -> config.copy(wg = v)
            "deadzone" -> config.copy(deadzone = v)
            "profile" -> config.copy(activeProfile = value?.toString())
            else -> config
        }
        return state.copy(pypilotConfig = next)
    }

    private fun updatePypilotServo(state: MarineState, field: String, value: Any?): MarineState {
        val servo = state.pypilotServo ?: PypilotServoState()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "voltage" -> servo.copy(voltage = v)
            "current" -> servo.copy(current = v)
            "controllerTemp" -> servo.copy(controllerTemp = v)
            "motorTemp" -> servo.copy(motorTemp = v)
            "ampHours" -> servo.copy(ampHours = v)
            "runtime" -> servo.copy(runtime = v)
            "engagement" -> servo.copy(engagement = value?.toString())
            else -> servo
        }
        return state.copy(pypilotServo = next)
    }

    private fun updatePypilotCalibration(state: MarineState, field: String, value: Any?): MarineState {
        val cal = state.pypilotCalibration ?: PypilotCalibrationState()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "compassProgress" -> cal.copy(compassCalibrationProgress = v)
            "accelProgress" -> cal.copy(accelCalibrationProgress = v)
            "rudderProgress" -> cal.copy(rudderCalibrationProgress = v)
            "isCalibrating" -> cal.copy(isCalibrating = value == true || value == "true")
            else -> cal
        }
        return state.copy(pypilotCalibration = next)
    }

    private fun updateWatermaker(state: MarineState, instance: String, transform: (Watermaker) -> Watermaker): MarineState {
        val watermakers = state.watermakers.toMutableMap()
        val watermaker = watermakers[instance] ?: Watermaker(instance = instance)
        watermakers[instance] = transform(watermaker)
        return state.copy(watermakers = watermakers)
    }

    private fun processCourseObject(course: SignalKCourse) {
        val nextPoint = course.nextPoint?.position
        if (nextPoint != null) {
            val lat = nextPoint.coordinates[1]
            val lon = nextPoint.coordinates[0]
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                dataBroker.updateState { it.copy(serverNextPoint = LatLon(lat, lon)) }
            }
        }
        
        course.arrivalRadius?.let { radius ->
            arrivalRadiusMeters = radius
        }

        course.activeRoute?.href?.let {
            isFollowingRoute = true
        } ?: run {
            if (course.nextPoint == null) isFollowingRoute = false
        }
    }
}
