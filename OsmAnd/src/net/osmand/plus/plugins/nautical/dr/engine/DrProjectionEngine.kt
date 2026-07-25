package net.osmand.plus.plugins.nautical.dr.engine

import kotlin.math.*

/**
 * Pure Kotlin utility for projecting position based on speed and heading.
 */
object DrProjectionEngine {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Projects a new position based on the last fix and motion vector.
     *
     * @param lastFix The last known position fix.
     * @param vector The current motion vector (STW, Heading, Leeway).
     * @param elapsedTimeSeconds Time elapsed since last fix in seconds.
     * @return A new estimated position fix.
     */
    fun projectPosition(
        lastFix: DrFix,
        vector: DrVector,
        elapsedTimeSeconds: Long
    ): DrFix {
        if (elapsedTimeSeconds <= 0 || vector.speedThroughWater <= 0.0) {
            return lastFix.copy(
                timestamp = lastFix.timestamp + (elapsedTimeSeconds * 1000),
                source = FixSource.DEAD_RECKONING
            )
        }

        val distanceMeters = vector.speedThroughWater * elapsedTimeSeconds
        val totalBearingDegrees = normalizeDegrees(vector.headingDegrees + vector.leewayDegrees)

        val startLatRad = Math.toRadians(lastFix.latitude)
        val startLonRad = Math.toRadians(lastFix.longitude)
        val bearingRad = Math.toRadians(totalBearingDegrees)
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        // Great Circle Destination Formula
        val destLatRad = asin(
            sin(startLatRad) * cos(angularDistance) +
                    cos(startLatRad) * sin(angularDistance) * cos(bearingRad)
        )

        val y = sin(bearingRad) * sin(angularDistance) * cos(startLatRad)
        val x = cos(angularDistance) - sin(startLatRad) * sin(destLatRad)
        val destLonRad = startLonRad + atan2(y, x)

        val destLat = Math.toDegrees(destLatRad)
        val destLon = normalizeLongitude(Math.toDegrees(destLonRad))

        return DrFix(
            latitude = destLat,
            longitude = destLon,
            timestamp = lastFix.timestamp + (elapsedTimeSeconds * 1000),
            source = FixSource.DEAD_RECKONING
        )
    }

    private fun normalizeDegrees(degrees: Double): Double {
        var normalized = degrees % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var normalized = (longitude + 180.0) % 360.0
        if (normalized <= 0) normalized += 360.0
        return normalized - 180.0
    }
}
