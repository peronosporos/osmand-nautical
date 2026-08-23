package net.osmand.plus.plugins.nautical.grib.repository

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.grib.parser.GribGridData
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.WaveVector
import net.osmand.plus.plugins.nautical.grib.parser.WindVector
import java.io.InputStream
import java.util.Collections

enum class GribStatus {
    IDLE,
    LOADING,
    READY,
    ERROR,
    UNSUPPORTED_EDITION
}

class GribRepository {
    private val log = PlatformUtil.getLog(GribRepository::class.java)
    
    companion object {
        const val GRIB_DIR = "nautical/grib"
    }
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("GribRepository Scope Error: ${throwable.message}", throwable)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    var isThrottled: Boolean = false
        set(value) {
            field = value
            if (value) {
                lastThrottledValueMap.clear()
            }
        }

    private val lastThrottledValueMap: MutableMap<Long, Any> = Collections.synchronizedMap(
        object : LinkedHashMap<Long, Any>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Any>?): Boolean {
                return size > 512
            }
        }
    )

    private val _status = MutableStateFlow(GribStatus.IDLE)
    val status: StateFlow<GribStatus> = _status.asStateFlow()

    private var interpolationEngine: GribInterpolationEngine? = null
    val engine: GribInterpolationEngine?
        get() = interpolationEngine
    var gridData: GribGridData? = null
        private set

    private var parsingJob: kotlinx.coroutines.Job? = null

    fun cleanup() {
        parsingJob?.cancel()
        interpolationEngine = null
        gridData = null
        _status.value = GribStatus.IDLE
        lastThrottledValueMap.clear()
        log.info("GRIB repository state cleared.")
    }

    fun loadGrib(bytes: ByteArray) {
        parsingJob?.cancel()
        _status.value = GribStatus.LOADING
        
        parsingJob = scope.launch {
            try {
                // Robust edition detection: find "GRIB" magic
                var offset = -1
                for (i in 0 until bytes.size - 8) {
                    if (bytes[i] == 'G'.code.toByte() && bytes[i+1] == 'R'.code.toByte() && 
                        bytes[i+2] == 'I'.code.toByte() && bytes[i+3] == 'B'.code.toByte()) {
                        offset = i
                        break
                    }
                }

                if (offset == -1) {
                    _status.value = GribStatus.ERROR
                    log.error("GRIB: Magic marker not found")
                    return@launch
                }
                
                val edition = bytes[offset + 7].toInt()
                val data = if (edition == 2) {
                    net.osmand.plus.plugins.nautical.grib.parser.GribParser().parse(bytes)
                } else if (edition == 1) {
                    net.osmand.plus.plugins.nautical.grib.parser.Grib1Parser().parse(bytes)
                } else {
                    null
                }

                if (data != null) {
                    this@GribRepository.gridData = data
                    interpolationEngine = GribInterpolationEngine(data)
                    lastThrottledValueMap.clear()
                    _status.value = GribStatus.READY
                    log.debug("GRIB (v$edition) successfully loaded and parsed")
                } else {
                    _status.value = GribStatus.ERROR
                    log.error("Failed to parse GRIB file")
                }
            } catch (e: net.osmand.plus.plugins.nautical.grib.parser.UnsupportedGribException) {
                _status.value = GribStatus.UNSUPPORTED_EDITION
                log.error("Unsupported GRIB: ${e.message}")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _status.value = GribStatus.ERROR
                    log.error("Error loading GRIB: ${e.message}")
                }
            }
        }
    }

    fun fetchFromSignalK(restService: net.osmand.plus.plugins.nautical.network.SignalKRestService, pluginId: String) {
        _status.value = GribStatus.LOADING
        scope.launch(Dispatchers.IO) {
            try {
                val response = restService.getGribData(pluginId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        loadGrib(body.bytes())
                    } else {
                        _status.value = GribStatus.ERROR
                    }
                } else {
                    _status.value = GribStatus.ERROR
                }
            } catch (e: Exception) {
                log.error("Failed to fetch GRIB from Signal K: ${e.message}")
                _status.value = GribStatus.ERROR
            }
        }
    }

    private fun getSpatialKey(lat: Double, lon: Double, timestamp: Long, prefix: Int): Long {
        // Spatial hashing: 0.01 degree precision (~1km)
        val latKey = (lat * 100).toLong()
        val lonKey = (lon * 100).toLong()
        val timeKey = timestamp / 600000 // 10 min
        
        // Combine into 64-bit Long: [4 bits prefix][20 bits time][20 bits lat][20 bits lon]
        return (prefix.toLong() and 0xF shl 60) or
               ((timeKey and 0xFFFFF) shl 40) or
               ((latKey + 9000 and 0xFFFFF) shl 20) or
               ((lonKey + 18000 and 0xFFFFF))
    }

    fun getWindVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        if (isThrottled) {
            val key = getSpatialKey(lat, lon, timestamp, 1)
            synchronized(lastThrottledValueMap) {
                lastThrottledValueMap[key]?.let { return it as? WindVector }
                val vector = interpolationEngine?.getWindVector(lat, lon, timestamp) ?: return null
                lastThrottledValueMap[key] = vector
                return vector
            }
        }
        return interpolationEngine?.getWindVector(lat, lon, timestamp)
    }

    fun getCurrentVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        if (isThrottled) {
            val key = getSpatialKey(lat, lon, timestamp, 2)
            synchronized(lastThrottledValueMap) {
                lastThrottledValueMap[key]?.let { return it as? WindVector }
                val vector = interpolationEngine?.getCurrentVector(lat, lon, timestamp) ?: return null
                lastThrottledValueMap[key] = vector
                return vector
            }
        }
        return interpolationEngine?.getCurrentVector(lat, lon, timestamp)
    }

    fun getPressure(lat: Double, lon: Double, timestamp: Long): Double? {
        if (isThrottled) {
            val key = getSpatialKey(lat, lon, timestamp, 3)
            synchronized(lastThrottledValueMap) {
                lastThrottledValueMap[key]?.let { return it as? Double }
                val value = interpolationEngine?.getPressure(lat, lon, timestamp) ?: return null
                lastThrottledValueMap[key] = value
                return value
            }
        }
        return interpolationEngine?.getPressure(lat, lon, timestamp)
    }

    fun getWaveData(lat: Double, lon: Double, timestamp: Long): WaveVector? {
        if (isThrottled) {
            val key = getSpatialKey(lat, lon, timestamp, 4)
            synchronized(lastThrottledValueMap) {
                lastThrottledValueMap[key]?.let { return it as? WaveVector }
                val data = interpolationEngine?.getWaveData(lat, lon, timestamp) ?: return null
                lastThrottledValueMap[key] = data
                return data
            }
        }
        return interpolationEngine?.getWaveData(lat, lon, timestamp)
    }
}
