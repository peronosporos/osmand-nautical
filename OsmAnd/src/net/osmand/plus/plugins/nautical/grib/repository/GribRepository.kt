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
    ERROR
}

class GribRepository {
    private val log = PlatformUtil.getLog(GribRepository::class.java)
    
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

    private val lastThrottledValueMap: MutableMap<String, Any> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Any>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean {
                return size > 256
            }
        }
    )

    private val _status = MutableStateFlow(GribStatus.IDLE)
    val status: StateFlow<GribStatus> = _status.asStateFlow()

    private var interpolationEngine: GribInterpolationEngine? = null
    var gridData: GribGridData? = null
        private set

    fun cleanup() {
        interpolationEngine = null
        gridData = null
        _status.value = GribStatus.IDLE
        log.info("GRIB repository state cleared.")
    }

    fun loadGrib(inputStream: InputStream) {
        _status.value = GribStatus.LOADING
        scope.launch {
            try {
                val parser = net.osmand.plus.plugins.nautical.grib.parser.GribParser()
                val data = parser.parse(inputStream)
                if (data != null) {
                    this@GribRepository.gridData = data
                    interpolationEngine = GribInterpolationEngine(data)
                    _status.value = GribStatus.READY
                    log.debug("GRIB successfully loaded and parsed")
                } else {
                    _status.value = GribStatus.ERROR
                    log.error("Failed to parse GRIB file")
                }
            } catch (e: Exception) {
                _status.value = GribStatus.ERROR
                log.error("Error loading GRIB: ${e.message}")
            }
        }
    }

    fun fetchFromSignalK(restService: net.osmand.plus.plugins.nautical.network.SignalKRestService) {
        _status.value = GribStatus.LOADING
        scope.launch(Dispatchers.IO) {
            try {
                val response = restService.getGribData()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        loadGrib(body.byteStream())
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

    fun getWindVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        if (isThrottled) {
            val key = "wind_${lat}_${lon}_${timestamp / 600000}" // 10 min cache
            return lastThrottledValueMap.getOrPut(key) { 
                interpolationEngine?.getWindVector(lat, lon, timestamp) ?: return null
            } as? WindVector
        }
        return interpolationEngine?.getWindVector(lat, lon, timestamp)
    }

    fun getCurrentVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        if (isThrottled) {
            val key = "cur_${lat}_${lon}_${timestamp / 600000}"
            return lastThrottledValueMap.getOrPut(key) { 
                interpolationEngine?.getCurrentVector(lat, lon, timestamp) ?: return null
            } as? WindVector
        }
        return interpolationEngine?.getCurrentVector(lat, lon, timestamp)
    }

    fun getPressure(lat: Double, lon: Double, timestamp: Long): Double? {
        if (isThrottled) {
            val key = "press_${lat}_${lon}_${timestamp / 600000}"
            return lastThrottledValueMap.getOrPut(key) {
                interpolationEngine?.getPressure(lat, lon, timestamp) ?: return null
            } as? Double
        }
        return interpolationEngine?.getPressure(lat, lon, timestamp)
    }

    fun getWaveData(lat: Double, lon: Double, timestamp: Long): WaveVector? {
        if (isThrottled) {
            val key = "wave_${lat}_${lon}_${timestamp / 600000}"
            return lastThrottledValueMap.getOrPut(key) {
                interpolationEngine?.getWaveData(lat, lon, timestamp) ?: return null
            } as? WaveVector
        }
        return interpolationEngine?.getWaveData(lat, lon, timestamp)
    }
}
