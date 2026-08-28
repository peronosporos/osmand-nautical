package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.util.MapUtils
import java.util.concurrent.ConcurrentHashMap

class NauticalSpeedLimitSentry(private val app: OsmandApplication) {

    data class SpeedLimitZone(
        val id: String,
        val name: String,
        val maxSpeedKn: Double,
        val polygon: List<LatLon> = emptyList(),
        val center: LatLon? = null
    )

    private val registeredZones = ConcurrentHashMap<String, SpeedLimitZone>()

    private val _activeViolatedZone = MutableStateFlow<SpeedLimitZone?>(null)
    val activeViolatedZone: StateFlow<SpeedLimitZone?> = _activeViolatedZone.asStateFlow()

    private var lastBannerTime = 0L

    fun registerSpeedLimitZone(id: String, name: String, maxSpeedKn: Double, polygon: List<LatLon>) {
        if (maxSpeedKn <= 0.0) return
        val center = if (polygon.isNotEmpty()) {
            val lat = polygon.map { it.latitude }.average()
            val lon = polygon.map { it.longitude }.average()
            LatLon(lat, lon)
        } else null
        registeredZones[id] = SpeedLimitZone(id, name, maxSpeedKn, polygon, center)
    }

    fun evaluateSpeedLimit(lat: Double?, lon: Double?, sogMps: Double?) {
        if (lat == null || lon == null || sogMps == null) {
            _activeViolatedZone.value = null
            return
        }

        val sogKn = sogMps * 1.94384
        val proximityThresholdM = 0.2 * 1852.0 // 0.2 NM

        var violated: SpeedLimitZone? = null
        for ((_, zone) in registeredZones) {
            val c = zone.center
            val dist = if (c != null) MapUtils.getDistance(lat, lon, c.latitude, c.longitude) else 0.0
            if (dist <= proximityThresholdM && sogKn > zone.maxSpeedKn) {
                violated = zone
                break
            }
        }

        _activeViolatedZone.value = violated
        if (violated != null) {
            val now = System.currentTimeMillis()
            if (now - lastBannerTime > 30000L) { // Debounce 30s
                lastBannerTime = now
                try {
                    NauticalPlugin.hudManager?.get()?.showBanner(
                        "SPEED LIMIT ZONE: Max ${violated.maxSpeedKn.toInt()} kn (SOG: ${sogKn.toInt()} kn)",
                        8000L,
                        isWarning = true
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun isZoneViolated(zoneId: String): Boolean {
        return _activeViolatedZone.value?.id == zoneId
    }

    companion object {
        private var instance: NauticalSpeedLimitSentry? = null

        fun getInstance(app: OsmandApplication): NauticalSpeedLimitSentry {
            return instance ?: synchronized(this) {
                instance ?: NauticalSpeedLimitSentry(app).also { instance = it }
            }
        }
    }
}
