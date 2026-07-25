package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import java.io.InputStream
import kotlin.math.*

class TacticalProcessor(private val app: OsmandApplication) {

    val polarDiagram = PolarDiagram()

    // Layline endpoints (lat, lon) for rendering and intersection checks
    var portLaylineEnd: Pair<Double, Double>? = null
        private set
    var starboardLaylineEnd: Pair<Double, Double>? = null
        private set

    private var lastLaylineIntersectionState = false
    private var targetWaypoint: Pair<Double, Double>? = null // (lat, lon)

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
        val tws = state.windSpeedTrue ?: 5.14 // approx 10 knots default (m/s)
        val twd = state.windDirectionTrue ?: 0.0 // radians true

        val optimalTwa = polarDiagram.getOptimalUpwindTwaRad(tws)

        // Calculate Port and Starboard Laylines in Radians
        // Port tack heading = TWD + optimalTwa
        // Starboard tack heading = TWD - optimalTwa
        // NOTE: verify consistency with LaylineMathEngine (which uses TWD +/- TWA)
        val portHeading = (twd + optimalTwa + 2 * PI) % (2 * PI)
        val starboardHeading = (twd - optimalTwa + 2 * PI) % (2 * PI)

        // Project laylines for e.g. 2 nautical miles (approx 3.7 km)
        val distanceKm = 3.7
        portLaylineEnd = calculateDestination(lat, lon, distanceKm, Math.toDegrees(portHeading))
        starboardLaylineEnd = calculateDestination(lat, lon, distanceKm, Math.toDegrees(starboardHeading))

        // Check intersection with target waypoint if set
        targetWaypoint?.let { target ->
            val intersected = checkLaylineIntersection(lat, lon, target, portLaylineEnd, starboardLaylineEnd)
            if (intersected && !lastLaylineIntersectionState) {
                triggerLaylineReached()
            }
            lastLaylineIntersectionState = intersected
        }
    }

    private fun triggerLaylineReached() {
        // Announce TTS prompt
        app.player?.let { player ->
            player.playCommands(player.newCommandBuilder().attention("Layline reached. Ready to tack."))
        }

        // Arm Tacking Maneuver via ManeuverManager if available
        val maneuverManager = NauticalPlugin.getInstance()?.maneuverManager
        maneuverManager?.let { manager ->
            manager.setActiveManeuver("tacking")
        }
    }

    private fun calculateDestination(latDeg: Double, lonDeg: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val earthRadiusKm = 6371.0
        val brngRad = Math.toRadians(bearingDeg)
        val lat1Rad = Math.toRadians(latDeg)
        val lon1Rad = Math.toRadians(lonDeg)

        val angularDistance = distanceKm / earthRadiusKm

        val lat2Rad = asin(sin(lat1Rad) * cos(angularDistance) + cos(lat1Rad) * sin(angularDistance) * cos(brngRad))
        val lon2Rad = lon1Rad + atan2(sin(brngRad) * sin(angularDistance) * cos(lat1Rad), cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad))

        return Pair(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
    }

    private fun checkLaylineIntersection(
        currLat: Double, currLon: Double,
        target: Pair<Double, Double>,
        portEnd: Pair<Double, Double>?,
        starboardEnd: Pair<Double, Double>?
    ): Boolean {
        if (portEnd == null || starboardEnd == null) return false

        val distToPort = distanceToSegment(currLat, currLon, portEnd.first, portEnd.second, target.first, target.second)
        val distToStbd = distanceToSegment(currLat, currLon, starboardEnd.first, starboardEnd.second, target.first, target.second)

        return distToPort < 0.05 || distToStbd < 0.05
    }

    private fun distanceToSegment(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val A = x - x1
        val B = y - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
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
            xx = x1 + param * C
            yy = y1 + param * D
        }

        val dx = x - xx
        val dy = y - yy
        return sqrt(dx * dx + dy * dy) * 111.0
    }
}
