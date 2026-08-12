package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.network.SignalKTideExtreme
import net.osmand.plus.plugins.nautical.network.SignalKTidePrediction
import net.osmand.plus.plugins.nautical.network.SignalKTideStation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages tide data from Signal K 'signalk-tides' plugin.
 */
class SignalKTideManager(
    private val app: OsmandApplication,
    private val scope: CoroutineScope,
) {
    private val log = PlatformUtil.getLog(SignalKTideManager::class.java)

    private val _stations = MutableStateFlow<Map<String, SignalKTideStation>>(emptyMap())
    val stations = _stations.asStateFlow()

    private val _vesselTide = MutableStateFlow<TideState?>(null)
    val vesselTide = _vesselTide.asStateFlow()

    private var cachedRestService: SignalKRestService? = null
    private var lastRestUrl: String? = null

    init {
        scope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collect { state ->
                _vesselTide.value = state.tide
            }
        }
    }

    fun findNearestStation(lat: Double, lon: Double): SignalKTideStation? {
        val allStations = _stations.value.values
        if (allStations.isEmpty()) return null

        return allStations.minByOrNull { station ->
            calculateDistance(lat, lon, station.position.coordinates[1], station.position.coordinates[0])
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (sin(dLat / 2) * sin(dLat / 2)) +
                (cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                        (sin(dLon / 2) * sin(dLon / 2)))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun start() {
        scope.launch {
            var attempt = 0
            while (isActive && _stations.value.isEmpty()) {
                val success = fetchStations()
                if (success) break
                
                attempt++
                val delayMs = Math.min(2000L * Math.pow(2.0, attempt.toDouble()).toLong(), 60000L)
                log.info("Nautical: Tide station fetch failed, retrying in ${delayMs / 1000}s...")
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    suspend fun fetchStations(): Boolean = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext false
        try {
            val response = service.getTideStations()
            if (response.isSuccessful) {
                _stations.value = response.body() ?: emptyMap()
                log.info("Nautical: Fetched ${_stations.value.size} tide stations from Signal K")
                return@withContext true
            }
        } catch (e: Exception) {
            log.error("Nautical: Failed to fetch tide stations: ${e.message}")
        }
        false
    }

    suspend fun getExtremes(stationId: String): List<SignalKTideExtreme> = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext emptyList<SignalKTideExtreme>()
        try {
            val response = service.getTideExtremes(stationId)
            if (response.isSuccessful) {
                return@withContext response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            log.error("Nautical: Failed to fetch tide extremes for $stationId: ${e.message}")
        }
        emptyList()
    }

    suspend fun getTimeline(stationId: String): List<SignalKTidePrediction> = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext emptyList<SignalKTidePrediction>()
        try {
            val response = service.getTideTimeline(stationId)
            if (response.isSuccessful) {
                return@withContext response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            log.error("Nautical: Failed to fetch tide timeline for $stationId: ${e.message}")
        }
        emptyList()
    }

    private fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        if (ip.isEmpty()) return null
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val url = "$protocol://$ip:$port"

        if (url == lastRestUrl && cachedRestService != null) {
            return cachedRestService
        }

        lastRestUrl = url
        cachedRestService = SignalKRestService.create(url, client)
        return cachedRestService
    }
}
