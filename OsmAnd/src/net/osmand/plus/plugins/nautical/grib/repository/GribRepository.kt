package net.osmand.plus.plugins.nautical.grib.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.grib.parser.*
import java.io.InputStream

enum class GribStatus {
    IDLE,
    LOADING,
    READY,
    ERROR
}

class GribRepository {
    private val log = PlatformUtil.getLog(GribRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(GribStatus.IDLE)
    val status: StateFlow<GribStatus> = _status.asStateFlow()

    private var interpolationEngine: GribInterpolationEngine? = null

    fun loadGrib(inputStream: InputStream) {
        _status.value = GribStatus.LOADING
        scope.launch {
            try {
                val parser = GribParser()
                val gridData = parser.parse(inputStream)
                if (gridData != null) {
                    interpolationEngine = GribInterpolationEngine(gridData)
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

    fun getWindVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        return interpolationEngine?.getWindVector(lat, lon, timestamp)
    }

    fun getPressure(lat: Double, lon: Double, timestamp: Long): Double? {
        return interpolationEngine?.getPressure(lat, lon, timestamp)
    }

    fun getWaveData(lat: Double, lon: Double, timestamp: Long): WaveVector? {
        return interpolationEngine?.getWaveData(lat, lon, timestamp)
    }
}
