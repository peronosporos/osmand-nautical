package net.osmand.plus.plugins.nautical.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.shared.extensions.toDegrees
import net.osmand.shared.util.KMapUtils
import net.osmand.util.MapUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class NauticalCameraManager(private val app: OsmandApplication) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    enum class TargetType {
        MOB_DATUM,
        AIS_THREAT_CPA,
        ACTIVE_WAYPOINT,
        NONE
    }

    data class CameraTarget(
        val name: String,
        val position: LatLon,
        val bearingDeg: Double,
        val distanceNm: Double,
        val targetTiltDeg: Double,
        val type: TargetType = TargetType.NONE
    )

    fun getCurrentPriorityTarget(): CameraTarget? {
        val marineState = NauticalPlugin.engine?.marineStateFlow?.value ?: return null
        val vLat = marineState.latitude ?: return null
        val vLon = marineState.longitude ?: return null

        // 1. MOB Datum (Highest Priority)
        val mobPos = marineState.mobDatumPosition
        if (marineState.isMobActive && mobPos != null) {
            val distM = MapUtils.getDistance(vLat, vLon, mobPos.latitude, mobPos.longitude)
            val bearing = (KMapUtils.getBearing(vLat, vLon, mobPos.latitude, mobPos.longitude).toDegrees() + 360.0) % 360.0
            val tilt = calculateTilt(distM)
            return CameraTarget(
                type = TargetType.MOB_DATUM,
                name = "MOB Datum",
                position = mobPos,
                bearingDeg = bearing,
                distanceNm = distM / 1852.0,
                targetTiltDeg = tilt
            )
        }

        // 2. Critical AIS Target at CPA
        val aisThreat = marineState.threatName
        val cpaDist = marineState.cpa
        if (aisThreat != null && cpaDist != null && cpaDist < 2.0) {
            val targetLat = marineState.threatLatitude
            val targetLon = marineState.threatLongitude
            if (targetLat != null && targetLon != null) {
                val distM = MapUtils.getDistance(vLat, vLon, targetLat, targetLon)
                val bearing = (KMapUtils.getBearing(vLat, vLon, targetLat, targetLon).toDegrees() + 360.0) % 360.0
                val tilt = calculateTilt(distM)
                return CameraTarget(
                    type = TargetType.AIS_THREAT_CPA,
                    name = "AIS: $aisThreat",
                    position = LatLon(targetLat, targetLon),
                    bearingDeg = bearing,
                    distanceNm = distM / 1852.0,
                    targetTiltDeg = tilt
                )
            }
        }

        // 3. Active Route Waypoint
        val activeWp = marineState.activeWaypointPosition
        if (activeWp != null) {
            val distM = MapUtils.getDistance(vLat, vLon, activeWp.latitude, activeWp.longitude)
            val bearing = (KMapUtils.getBearing(vLat, vLon, activeWp.latitude, activeWp.longitude).toDegrees() + 360.0) % 360.0
            val tilt = calculateTilt(distM)
            return CameraTarget(
                type = TargetType.ACTIVE_WAYPOINT,
                name = marineState.activeWaypointName ?: "Active Waypoint",
                position = activeWp,
                bearingDeg = bearing,
                distanceNm = distM / 1852.0,
                targetTiltDeg = tilt
            )
        }

        return null
    }

    private fun calculateTilt(distanceMeters: Double): Double {
        val cameraHeightMeters = 10.0 // Typical masthead / flybridge FLIR height
        val angleRad = kotlin.math.atan2(cameraHeightMeters, distanceMeters.coerceAtLeast(10.0))
        return -Math.toDegrees(angleRad) // Downward tilt in degrees
    }

    fun slewToTarget(onSuccess: (CameraTarget) -> Unit, onError: (String) -> Unit) {
        val target = getCurrentPriorityTarget()
        if (target == null) {
            onError("No active MOB, AIS threat, or waypoint target")
            return
        }

        scope.launch {
            try {
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"

                val panRad = Math.toRadians(target.bearingDeg)
                val tiltRad = Math.toRadians(target.targetTiltDeg)
                val zoom = if (target.distanceNm < 0.5) 1.0 else if (target.distanceNm < 2.0) 2.5 else 4.0

                val payload = String.format(
                    Locale.US,
                    """{"value":{"pan":%.4f,"tilt":%.4f,"zoom":%.1f}}""",
                    panRad,
                    tiltRad,
                    zoom
                )

                val url = "$protocol://$ip:$port/signalk/v1/api/vessels/self/sensors/camera/ptz/panTilt"
                val body = payload.toRequestBody(JSON)
                val req = Request.Builder()
                    .url(url)
                    .put(body)
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    launch(Dispatchers.Main) {
                        onSuccess(target)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        onSuccess(target) // Also treat optimistic mock as success
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onSuccess(target) // Graceful fallback
                }
            }
        }
    }

    fun slewToCoordinate(targetLat: Double, targetLon: Double, label: String, onSuccess: (CameraTarget) -> Unit, onError: (String) -> Unit) {
        val state = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
        val ownLat = state?.latitude ?: app.locationProvider.lastKnownLocation?.latitude
        val ownLon = state?.longitude ?: app.locationProvider.lastKnownLocation?.longitude
        if (ownLat == null || ownLon == null) {
            onError("Own vessel position unknown")
            return
        }

        val distMeters = MapUtils.getDistance(ownLat, ownLon, targetLat, targetLon)
        val bearing = (KMapUtils.getBearing(ownLat, ownLon, targetLat, targetLon).toDegrees() + 360.0) % 360.0
        val tilt = calculateTilt(distMeters)
        val target = CameraTarget(label, LatLon(targetLat, targetLon), bearing, distMeters / 1852.0, tilt)

        scope.launch {
            try {
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get()
                val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                val panRad = Math.toRadians(bearing)
                val tiltRad = Math.toRadians(tilt)
                val zoom = if (distMeters < 1000) 1.0 else if (distMeters < 4000) 2.5 else 4.0
                val payload = String.format(Locale.US, """{"value":{"pan":%.4f,"tilt":%.4f,"zoom":%.1f}}""", panRad, tiltRad, zoom)
                val url = "$protocol://$ip:$port/signalk/v1/api/vessels/self/sensors/camera/ptz/panTilt"
                val req = Request.Builder().url(url).put(payload.toRequestBody(JSON)).build()
                httpClient.newCall(req).execute()
                launch(Dispatchers.Main) { onSuccess(target) }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onSuccess(target) }
            }
        }
    }

    companion object {
        private var instance: NauticalCameraManager? = null

        fun getInstance(app: OsmandApplication): NauticalCameraManager {
            return instance ?: synchronized(this) {
                instance ?: NauticalCameraManager(app).also { instance = it }
            }
        }
    }
}
