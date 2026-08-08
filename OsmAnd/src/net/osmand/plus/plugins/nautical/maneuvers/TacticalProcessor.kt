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
        
        val target = targetWaypoint
        val isDownwind = if (target != null) {
            val bearingToTarget = Math.toRadians(calculateBearing(lat, lon, target.first, target.second))
            val relWind = (bearingToTarget - twd + 3 * PI) % (2 * PI) - PI
            abs(relWind) > PI / 2
        } else false

        val optimalTwa = if (isDownwind) {
            polarDiagram.getOptimalDownwindTwaRad(tws)
        } else {
            polarDiagram.getOptimalUpwindTwaRad(tws)
        }

        // Calculate Port and Starboard Laylines in Radians (Relative to Water)
        var portHeading = (twd + optimalTwa + (2 * PI)) % (2 * PI)
        var starboardHeading = (twd - optimalTwa + (2 * PI)) % (2 * PI)
        
        // Environmental Correction: Compensate for Current (Set/Drift)
        val drift = state.drift
        val set = state.setTrue
        if (drift != null && set != null && drift > 0.05) {
            val boatSpeed = polarDiagram.getTargetSpeedRad(tws, optimalTwa)
            portHeading = compensateForCurrent(portHeading, boatSpeed, set, drift)
            starboardHeading = compensateForCurrent(starboardHeading, boatSpeed, set, drift)
        }

        // Project laylines for e.g. 2 nautical miles (approx 3.7 km)
        val distanceKm = 3.7
        portLaylineEnd = calculateDestination(lat, lon, distanceKm, Math.toDegrees(portHeading))
        starboardLaylineEnd = calculateDestination(lat, lon, distanceKm, Math.toDegrees(starboardHeading))

        // Check intersection with target waypoint if set
        target?.let { t ->
            val intersected = checkLaylineIntersection(lat, lon, t, portLaylineEnd, starboardLaylineEnd)
            if (intersected && !lastLaylineIntersectionState) {
                triggerLaylineReached(isDownwind)
            }
            lastLaylineIntersectionState = intersected
        }
    }

    private fun compensateForCurrent(heading: Double, boatSpeed: Double, set: Double, drift: Double): Double {
        // Vector addition: Boat Velocity + Current Velocity = COG Vector
        val boatX = boatSpeed * sin(heading)
        val boatY = boatSpeed * cos(heading)
        val currentX = drift * sin(set)
        val currentY = drift * cos(set)
        
        return (atan2(boatX + currentX, boatY + currentY) + 2 * PI) % (2 * PI)
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

    private fun calculateDestination(latDeg: Double, lonDeg: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val earthRadiusKm = 6371.0
        val brngRad = Math.toRadians(bearingDeg)
        val lat1Rad = Math.toRadians(latDeg)
        val lon1Rad = Math.toRadians(lonDeg)

        val angularDistance = distanceKm / earthRadiusKm

        val lat2Rad = asin((sin(lat1Rad) * cos(angularDistance)) + (cos(lat1Rad) * sin(angularDistance) * cos(brngRad)))
        val lon2Rad = lon1Rad + atan2(sin(brngRad) * sin(angularDistance) * cos(lat1Rad), cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad))

        return Pair(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
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

    private fun distanceToSegment(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
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

        val dx = x - xx
        val dy = y - yy
        return sqrt(dx * dx + dy * dy) * 111.0
    }
}
