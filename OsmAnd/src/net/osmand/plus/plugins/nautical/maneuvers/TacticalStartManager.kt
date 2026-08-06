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
     */
    fun getDistanceToLine(lat: Double, lon: Double): Double? {
        val p1 = portPin ?: return null
        val p2 = starboardPin ?: return null
        
        return distancePointToSegment(lat, lon, p1.first, p1.second, p2.first, p2.second)
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
        
        val lineBearing = Math.toDegrees(KMapUtils.getBearing(p1.first, p1.second, p2.first, p2.second))
        val perpendicular = (lineBearing + 90 + 360) % 360
        
        var bias = perpendicular - twd
        if (bias > 180) bias -= 360
        if (bias < -180) bias += 360
        
        return bias
    }

    /**
     * Calculates Time to Burn in seconds.
     * Distance to Line / Speed Over Ground.
     */
    fun getTimeToBurn(lat: Double, lon: Double): Double? {
        val dist = getDistanceToLine(lat, lon) ?: return null
        val state = NauticalPlugin.engine?.getCurrentState() ?: return null
        val sog = state.speedOverGround ?: return null
        
        if (sog < 0.2) return Double.MAX_VALUE // Effectively stationary
        
        return dist / sog
    }

    private fun distancePointToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return calculateDistance(px, py, x1, y1)

        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        return when {
            t < 0 -> calculateDistance(px, py, x1, y1)
            t > 1 -> calculateDistance(px, py, x2, y2)
            else -> calculateDistance(px, py, x1 + t * dx, y1 + t * dy)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return KMapUtils.getDistance(lat1, lon1, lat2, lon2)
    }
}
