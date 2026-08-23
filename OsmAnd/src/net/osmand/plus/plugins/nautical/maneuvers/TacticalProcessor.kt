package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.laylines.engine.LatLon
import net.osmand.plus.plugins.nautical.laylines.engine.LaylineMathEngine
import net.osmand.plus.plugins.nautical.laylines.engine.TidalCurrentVector
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.util.MapUtils
import java.io.InputStream
import kotlin.math.*

class TacticalProcessor(private val app: OsmandApplication) {

    val polarDiagram = PolarDiagram()

    init {
        // TASK-002: Connect to PerformanceRepository to keep polar data in sync
        SailingDependencyContainer.performanceRepository?.activePolarProfile?.let { profileFlow ->
            app.runInUIThread {
                NauticalPlugin.getInstance()?.pluginScope?.launch {
                    profileFlow.collectLatest { profile ->
                        profile?.let {
                            polarDiagram.loadFromProfile(it)
                        }
                    }
                }
            }
        }
    }

    // Layline endpoints (lat, lon) for rendering and intersection checks
    var portLaylineEnd: Pair<Double, Double>? = null
        private set
    var starboardLaylineEnd: Pair<Double, Double>? = null
        private set

    private var lastLaylineIntersectionState = false
    var targetWaypoint: Pair<Double, Double>? = null
        private set // (lat, lon)

    fun loadPolarFromStream(stream: InputStream): Boolean {
        return polarDiagram.loadFromCsv(stream)
    }

    fun setTargetWaypoint(lat: Double, lon: Double) {
        targetWaypoint = Pair(lat, lon)
    }

    fun clearTargetWaypoint() {
        targetWaypoint = null
        portLaylineEnd = null
        starboardLaylineEnd = null
    }

    fun update(state: MarineState) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val tws = state.windSpeedTrue ?: 5.14
        val twd = state.windDirectionTrue ?: 0.0
        
        val target = targetWaypoint ?: return
        
        // TASK-001: Unified Layline Logic using LaylineMathEngine
        val fallbackTwa = Math.toRadians(app.settings.NAUTICAL_LAYLINES_TACK_ANGLE.get().toDouble() / 2.0)
        val polarTwa = polarDiagram.getOptimalUpwindTwaRad(tws)
        val optimalTwa = if (polarTwa > 0.0) polarTwa else fallbackTwa

        val manualLeeway = Math.toRadians(app.settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.get().toDouble())
        val leeway = state.leeway ?: if (manualLeeway > 0.0) manualLeeway else 0.0
        val isInfinite = app.settings.NAUTICAL_SHOW_INFINITE_LAYLINES.get()

        val current = TidalCurrentVector(
            (state.drift ?: 0.0) * sin(state.setTrue ?: 0.0),
            (state.drift ?: 0.0) * cos(state.setTrue ?: 0.0)
        )

        val result = LaylineMathEngine.calculateApparentLaylines(
            boatPosition = LatLon(lat, lon),
            targetWaypoint = LatLon(target.first, target.second),
            optimalTwa = optimalTwa,
            trueWindDirection = twd,
            boatSpeed = state.speedThroughWater ?: state.speedOverGround ?: 5.0,
            current = current,
            leewayRadians = leeway,
            isInfinite = isInfinite
        )

        portLaylineEnd = result.portTackPoint?.let { it.latitude to it.longitude }
        starboardLaylineEnd = result.starboardTackPoint?.let { it.latitude to it.longitude }

        val isDownwind = abs((Math.toRadians(calculateBearing(lat, lon, target.first, target.second)) - twd + 3 * PI) % (2 * PI) - PI) > (PI / 2.0)

        // Check intersection with target waypoint
        val intersected = checkLaylineIntersection(lat, lon, target, portLaylineEnd, starboardLaylineEnd)
        if (intersected && !lastLaylineIntersectionState) {
            triggerLaylineReached(isDownwind)
        }
        lastLaylineIntersectionState = intersected
    }

    private fun triggerLaylineReached(isDownwind: Boolean) {
        // Announce TTS prompt
        val msg = if (isDownwind) app.getString(net.osmand.plus.R.string.nautical_layline_reached_gybe) else app.getString(net.osmand.plus.R.string.nautical_layline_reached_tack)
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention(msg))
        }

        // Arm Maneuver via ManeuverManager if available
        val maneuverManager = NauticalPlugin.getInstance()?.maneuverManager
        maneuverManager?.setActiveManeuver(if (isDownwind) "gybing" else "tacking")
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        val y = sin(lon2Rad - lon1Rad) * cos(lat2Rad)
        val x = (cos(lat1Rad) * sin(lat2Rad)) - (sin(lat1Rad) * cos(lat2Rad) * cos(lon2Rad - lon1Rad))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun checkLaylineIntersection(
        currLat: Double, currLon: Double,
        target: Pair<Double, Double>,
        portEnd: Pair<Double, Double>?,
        starboardEnd: Pair<Double, Double>?,
    ): Boolean {
        if (portEnd == null || starboardEnd == null) return false

        val distToPort = distanceToSegment(currLat, currLon, portEnd.first, portEnd.second, target.first, target.second)
        val distToStbd = distanceToSegment(currLat, currLon, starboardEnd.first, starboardEnd.second, target.first, target.second)

        return distToPort < 0.05 || distToStbd < 0.05
    }

    fun distanceToSegment(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1

        val dot = (a * c) + (b * d)
        val lenSq = (c * c) + (d * d)
        var param = -1.0
        if (lenSq != 0.0) {
            param = dot / lenSq
        }

        val xx: Double
        val yy: Double

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + (param * c)
            yy = y1 + (param * d)
        }

        // TASK-004: Replace inaccurate 111.0 multiplier with proper Geodesic distance
        return MapUtils.getDistance(x, y, xx, yy) / 1000.0 // Convert meters to km
    }
}
