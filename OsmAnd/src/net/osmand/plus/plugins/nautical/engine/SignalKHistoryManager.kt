package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.WearOsNauticalManager
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.shared.util.KMapUtils
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.util.concurrent.ConcurrentHashMap

class SignalKHistoryManager(
    private val app: OsmandApplication,
    private val capabilityManager: CapabilityManager? = null
) {
    private val log = PlatformUtil.getLog(SignalKHistoryManager::class.java)
    private val wearOsManager = WearOsNauticalManager(app)

    private val telemetryBuffers = ConcurrentHashMap<String, CircularBuffer<Pair<Double, Long>>>()
    private val trajectoryBuffer = CircularBuffer<TrajectoryPoint>(10000)

    private val _trajectoryEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trajectoryEventFlow = _trajectoryEventFlow.asSharedFlow()

    @Volatile
    var lastTrajectoryTimestamp: Long = 0
        private set

    @Volatile
    var powerSaveMode: Boolean = false
        private set

    fun getBuffer(path: String): CircularBuffer<Pair<Double, Long>> {
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        val isWatch = wearOsManager.isWatchMode()
        val capacity = if (isWatch) {
            15 // Deeply throttled for watches to save RAM
        } else if (caps.hasHistory || caps.hasLogging) {
            60
        } else {
            600 // Reduced from 3600 to 600 (10 mins at 1Hz) for efficiency
        }
        return telemetryBuffers.getOrPut(path) { CircularBuffer(capacity) }
    }

    fun setCapacity(newCapacity: Int) {
        telemetryBuffers.values.forEach { it.setCapacity(newCapacity) }
    }

    fun getHistory(path: String): List<Pair<Double, Long>> = getBuffer(path).getAll()

    fun setPowerSaveMode(enabled: Boolean) {
        val plugin = NauticalPlugin.getInstance()
        val isBackground = plugin?.isAppInBackground ?: false
        val actual = enabled && isBackground
        powerSaveMode = actual
        log.info("SignalK History Manager Power Save Mode: $actual (requested: $enabled, isBackground: $isBackground)")
    }

    fun addTrajectoryPoint(lat: Double, lon: Double) {
        val now = TemporalUtils.now()

        // Improved Resolution (TASK-UX-003): 5s minimum interval
        if (now - lastTrajectoryTimestamp < 5000) return

        val history = trajectoryBuffer.getAll()
        val last = history.lastOrNull()

        if (last != null) {
            val dist = KMapUtils.getDistance(last.lat, last.lon, lat, lon)
            val timeGap = now - lastTrajectoryTimestamp
            // Record if: 10m displacement (tactical resolution) OR 60s time gap
            if (dist > 10.0 || timeGap > 60000) {
                trajectoryBuffer.add(TrajectoryPoint(lat, lon, now))
                lastTrajectoryTimestamp = now
                _trajectoryEventFlow.tryEmit(Unit)
            }
        } else {
            trajectoryBuffer.add(TrajectoryPoint(lat, lon, now))
            lastTrajectoryTimestamp = now
            _trajectoryEventFlow.tryEmit(Unit)
        }
    }

    fun clearTrajectory() {
        trajectoryBuffer.clear()
        lastTrajectoryTimestamp = 0
        _trajectoryEventFlow.tryEmit(Unit)
        log.info("Nautical trajectory breadcrumbs cleared.")
    }

    fun copyTrajectoryTo(target: MutableList<TrajectoryPoint>) {
        trajectoryBuffer.copyTo(target)
    }

    fun clearBuffers(context: Context) {
        telemetryBuffers.clear()
        clearTrajectory()
        val binFile = File(context.filesDir, "nautical_history.bin")
        if (binFile.exists()) binFile.delete()
        val jsonFile = File(context.filesDir, "nautical_history.json")
        if (jsonFile.exists()) jsonFile.delete()
        log.info("SignalK historical buffers cleared from disk and memory.")
    }

    suspend fun saveBuffersToDisk(context: Context) = withContext(Dispatchers.IO + NonCancellable) {
        if (powerSaveMode && lastTrajectoryTimestamp != 0L) {
            log.debug("Nautical: Skipping background buffer save (Power Save active).")
            return@withContext
        }

        val file = File(context.filesDir, "nautical_history.bin")
        try {
            DataOutputStream(file.outputStream().buffered()).use { dos ->
                dos.writeInt(3) // Version

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
                trajectory.forEach { pt ->
                    dos.writeDouble(pt.lat)
                    dos.writeDouble(pt.lon)
                    dos.writeLong(pt.time)
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
                    if (version == 3) {
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
                            trajectoryBuffer.add(TrajectoryPoint(dis.readDouble(), dis.readDouble(), dis.readLong()))
                        }
                        return@withContext
                    } else if (version == 2) {
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
                            trajectoryBuffer.add(TrajectoryPoint(dis.readDouble(), dis.readDouble(), TemporalUtils.now()))
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
                    trajectoryBuffer.add(TrajectoryPoint(
                        obj.getDouble("lat"),
                        obj.getDouble("lon"),
                        obj.optLong("t", TemporalUtils.now())
                    ))
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
        load<Pair<Double, Double>>("trajectory_buffer.dat") { 
            trajectoryBuffer.add(TrajectoryPoint(it.first, it.second, TemporalUtils.now())) 
        }
    }
}
