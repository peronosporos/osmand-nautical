package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.shared.util.KMapUtils

/**
 * Manages the Tactical Start Line (Port and Starboard pins).
 * Calculates Distance to Line, Line Bias, and Time to Burn.
 */
class TacticalStartManager(private val app: OsmandApplication) {

    var portPin: Pair<Double, Double>? = null
        private set
    var starboardPin: Pair<Double, Double>? = null
        private set

    fun setPortPin(lat: Double, lon: Double) {
        portPin = Pair(lat, lon)
    }

    fun setStarboardPin(lat: Double, lon: Double) {
        starboardPin = Pair(lat, lon)
    }

    fun clear() {
        portPin = null
        starboardPin = null
    }

    fun isLineSet(): Boolean = portPin != null && starboardPin != null

    /**
     * Calculates perpendicular distance from boat to the start line segment in meters.
     * Item 3: Uses spherical geometry projection.
     */
    fun getDistanceToLine(lat: Double, lon: Double): Double? {
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        
        // 1. Get distance and bearing of the line
        val d12 = KMapUtils.getDistance(p1.first, p1.second, p2.first, p2.second)
        if (d12 < 1.0) return KMapUtils.getDistance(lat, lon, p1.first, p1.second)
        
        val b12 = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second)
        val b1p = KMapUtils.getBearing(p1.first, p1.second, lat, lon)
        val d1p = KMapUtils.getDistance(p1.first, p1.second, lat, lon)
        
        // 2. Spherical projection using cross-track distance formula
        val angle = Math.toRadians(b1p - b12)
        val xtd = d1p * Math.sin(angle)
        val atd = d1p * Math.cos(angle)
        
        return when {
            atd < 0 -> KMapUtils.getDistance(lat, lon, p1.first, p1.second)
            atd > d12 -> KMapUtils.getDistance(lat, lon, p2.first, p2.second)
            else -> Math.abs(xtd)
        }
    }

    /**
     * Calculates line bias in degrees. Positive favors Starboard, negative favors Port.
     * Calculated as: (Line Bearing + 90) - Wind Direction.
     */
    fun getLineBias(): Double? {
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        val state = NauticalPlugin.engine?.getCurrentState() ?: return null
        val twd = state.windDirectionTrue?.let { Math.toDegrees(it) } ?: return null
        
        val lineBearing = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second)
        val perpendicular = (lineBearing + 90 + 360) % 360
        
        var bias = perpendicular - twd
        if (bias > 180) bias -= 360
        if (bias < -180) bias += 360
        
        return bias
    }

    /**
     * Calculates Time to Burn in seconds.
     * Item 4 & 5: (Race Countdown) - (Distance to Line / Component of Velocity Perpendicular to Line).
     */
    fun getTimeToBurn(lat: Double, lon: Double): Double? {
        val dist = getDistanceToLine(lat, lon) ?: return null
        val state = NauticalPlugin.engine?.getCurrentState() ?: return null
        val sog = state.speedOverGround ?: return null
        val cog = state.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: return null
        val timer = state.racingTimer ?: 0.0
        
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        val lineBearing = KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second)
        val linePerp = (lineBearing + 90 + 360) % 360
        
        // Calculate velocity component towards the line
        val vPerp = sog * Math.cos(Math.toRadians(cog - linePerp))
        
        if (vPerp < 0.1) return Double.MAX_VALUE 
        
        val ttl = dist / vPerp
        return timer - ttl
    }
}
