package net.osmand.plus.plugins.nautical.engine

import net.osmand.shared.util.KMapUtils
import kotlin.math.*

/**
 * Automated pattern generator for SAR and specialized nautical operations.
 */
object PatternSteeringEngine {

    const val NM_TO_METERS = 1852.0

    /**
     * Generates an Expanding Square Search pattern.
     * @param startLat Initial latitude (center)
     * @param startLon Initial longitude (center)
     * @param spacingNm Distance between legs in Nautical Miles
     * @param iterations Number of square expansions
     * @param initialHeading True heading for the first leg (degrees)
     * @param turnsRight Whether to turn right (starboard) or left (port)
     * @param driftMps Drift speed in meters per second
     * @param driftDeg Drift direction in degrees
     * @param avgSpeedMps Average boat speed in meters per second
     */
    fun generateExpandingSquare(
        startLat: Double,
        startLon: Double,
        spacingNm: Double,
        iterations: Int,
        initialHeading: Double = 0.0,
        turnsRight: Boolean = true,
        driftMps: Double = 0.0,
        driftDeg: Double = 0.0,
        avgSpeedMps: Double = 3.0
    ): List<Pair<Double, Double>> {
        val waypoints = mutableListOf<Pair<Double, Double>>()
        val spacingM = spacingNm * NM_TO_METERS
        
        var currentLat = startLat
        var currentLon = startLon
        var currentHeading = initialHeading
        val turnAngle = if (turnsRight) 90.0 else -90.0
        
        var elapsedTime = 0.0

        for (i in 1..iterations) {
            val legLength = i * spacingM
            
            // First leg of this iteration
            val p1 = KMapUtils.rhumbDestinationPoint(currentLat, currentLon, legLength, currentHeading)
            val legDuration = legLength / avgSpeedMps
            elapsedTime += legDuration
            
            val shiftedP1 = shiftByDrift(p1.latitude, p1.longitude, driftMps, driftDeg, elapsedTime)
            waypoints.add(shiftedP1)
            
            currentLat = p1.latitude
            currentLon = p1.longitude
            currentHeading = (currentHeading + turnAngle + 360) % 360
            
            // Second leg of this iteration
            val p2 = KMapUtils.rhumbDestinationPoint(currentLat, currentLon, legLength, currentHeading)
            elapsedTime += legDuration // assuming same length/speed
            
            val shiftedP2 = shiftByDrift(p2.latitude, p2.longitude, driftMps, driftDeg, elapsedTime)
            waypoints.add(shiftedP2)
            
            currentLat = p2.latitude
            currentLon = p2.longitude
            currentHeading = (currentHeading + turnAngle + 360) % 360
        }
        
        return waypoints
    }

    private fun shiftByDrift(lat: Double, lon: Double, driftMps: Double, driftDeg: Double, timeSec: Double): Pair<Double, Double> {
        if (driftMps <= 0.0) return lat to lon
        val dist = driftMps * timeSec
        val p = KMapUtils.rhumbDestinationPoint(lat, lon, dist, driftDeg)
        return p.latitude to p.longitude
    }

    /**
     * Generates a Sector Search (Williamson-style) pattern.
     * Standard IAMSAR: 3 sectors, 9 legs total, 120° turns.
     */
    fun generateSectorSearch(
        centerLat: Double,
        centerLon: Double,
        radiusNm: Double,
        initialHeading: Double = 0.0,
        turnsRight: Boolean = true
    ): List<Pair<Double, Double>> {
        val waypoints = mutableListOf<Pair<Double, Double>>()
        val radiusM = radiusNm * NM_TO_METERS
        
        var currentHeading = initialHeading
        val turnAngle = if (turnsRight) 120.0 else -120.0

        // 3 sectors, each with 3 legs
        repeat(3) {
            // Leg 1: Outward from center
            val p1 = KMapUtils.rhumbDestinationPoint(centerLat, centerLon, radiusM, currentHeading)
            waypoints.add(p1.latitude to p1.longitude)
            
            // Turn 120 degrees
            val heading2 = (currentHeading + turnAngle + 360) % 360
            
            // Leg 2: Cross leg
            val p2 = KMapUtils.rhumbDestinationPoint(p1.latitude, p1.longitude, radiusM, heading2)
            waypoints.add(p2.latitude to p2.longitude)
            
            // Leg 3: Return to center
            waypoints.add(centerLat to centerLon)
            
            // Next sector starts with a 30 degree offset from previous sector's entry leg
            currentHeading = (currentHeading + turnAngle + 30.0 + 360) % 360
        }
        
        return waypoints
    }

    /**
     * Generates a Creeping Line Search pattern.
     * Perpendicular legs to the major axis (creep direction).
     */
    fun generateCreepingLine(
        startLat: Double,
        startLon: Double,
        creepHeading: Double,
        lengthNm: Double,
        widthNm: Double,
        spacingNm: Double,
        turnsRight: Boolean = true
    ): List<Pair<Double, Double>> {
        val waypoints = mutableListOf<Pair<Double, Double>>()
        val lengthM = lengthNm * NM_TO_METERS
        val widthM = widthNm * NM_TO_METERS
        val spacingM = spacingNm * NM_TO_METERS
        
        val numLegs = (lengthM / spacingM).toInt()
        val searchHeading = if (turnsRight) (creepHeading + 90) % 360 else (creepHeading - 90 + 360) % 360
        
        var currentDatumLat = startLat
        var currentDatumLon = startLon
        
        for (i in 0..numLegs) {
            // First point of leg (offset by half width from datum)
            val pStart = KMapUtils.rhumbDestinationPoint(currentDatumLat, currentDatumLon, widthM / 2.0, (searchHeading + 180) % 360)
            val pEnd = KMapUtils.rhumbDestinationPoint(currentDatumLat, currentDatumLon, widthM / 2.0, searchHeading)
            
            if (i % 2 == 0) {
                waypoints.add(pStart.latitude to pStart.longitude)
                waypoints.add(pEnd.latitude to pEnd.longitude)
            } else {
                waypoints.add(pEnd.latitude to pEnd.longitude)
                waypoints.add(pStart.latitude to pStart.longitude)
            }
            
            // Move datum forward by spacing along creep heading
            val nextDatum = KMapUtils.rhumbDestinationPoint(currentDatumLat, currentDatumLon, spacingM, creepHeading)
            currentDatumLat = nextDatum.latitude
            currentDatumLon = nextDatum.longitude
        }
        
        return waypoints
    }

    /**
     * Generates a Parallel Sweep Search pattern.
     * Legs parallel to the major axis.
     */
    fun generateParallelSweep(
        startLat: Double,
        startLon: Double,
        majorHeading: Double,
        lengthNm: Double,
        widthNm: Double,
        spacingNm: Double
    ): List<Pair<Double, Double>> {
        val waypoints = mutableListOf<Pair<Double, Double>>()
        val lengthM = lengthNm * NM_TO_METERS
        val widthM = widthNm * NM_TO_METERS
        val spacingM = spacingNm * NM_TO_METERS
        
        val numLegs = (widthM / spacingM).toInt()
        val crossHeading = (majorHeading + 90) % 360
        
        var currentStartLat = startLat
        var currentStartLon = startLon
        
        for (i in 0..numLegs) {
            val pEnd = KMapUtils.rhumbDestinationPoint(currentStartLat, currentStartLon, lengthM, majorHeading)
            
            if (i % 2 == 0) {
                waypoints.add(currentStartLat to currentStartLon)
                waypoints.add(pEnd.latitude to pEnd.longitude)
            } else {
                waypoints.add(pEnd.latitude to pEnd.longitude)
                waypoints.add(currentStartLat to currentStartLon)
            }
            
            // Move start point of next leg sideways
            val nextStart = KMapUtils.rhumbDestinationPoint(currentStartLat, currentStartLon, spacingM, crossHeading)
            currentStartLat = nextStart.latitude
            currentStartLon = nextStart.longitude
        }
        
        return waypoints
    }

    /**
     * Generates a Circular Expanding Spiral (approximated by segments).
     */
    fun generateSpiral(
        centerLat: Double,
        centerLon: Double,
        maxRadiusNm: Double,
        legSpacingNm: Double
    ): List<Pair<Double, Double>> {
        val waypoints = mutableListOf<Pair<Double, Double>>()
        val maxRadiusM = maxRadiusNm * NM_TO_METERS
        val spacingM = legSpacingNm * NM_TO_METERS
        
        // Use Archimedian spiral: r = a * theta
        // Spacing between arms = 2 * PI * a = spacingM => a = spacingM / (2 * PI)
        val a = spacingM / (2 * PI)
        
        var theta = 0.0
        val step = PI / 4 // 45 degree segments
        
        while (true) {
            val r = a * theta
            if (r > maxRadiusM) break
            
            val bearing = Math.toDegrees(theta)
            val p = KMapUtils.rhumbDestinationPoint(centerLat, centerLon, r, bearing)
            waypoints.add(p.latitude to p.longitude)
            
            theta += step
        }
        
        return waypoints
    }
}
