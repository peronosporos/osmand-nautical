package net.osmand.plus.plugins.nautical.mob.engine

import net.osmand.data.LatLon
import net.osmand.shared.util.KMapUtils
import kotlin.math.*

object MobVectorEngine {

    /**
     * Calculates the return vector from the boat's current position back to the MOB coordinates.
     *
     * @param currentLocation The boat's live position.
     * @param mobEvent The recorded MOB event data.
     * @param sog Current Speed Over Ground in meters per second (m/s).
     * @return A [MobReturnVector] containing distance, bearing, and ETA.
     */
    fun calculateReturnVector(
        currentLocation: LatLon,
        mobEvent: MobEvent,
        sog: Double
    ): MobReturnVector {
        val dropLocation = mobEvent.dropLocation

        val distanceMeters = KMapUtils.getDistance(
            currentLocation.latitude, currentLocation.longitude,
            dropLocation.latitude, dropLocation.longitude
        )

        val bearingDegrees = calculateBearingDegrees(currentLocation, dropLocation)

        val etaSeconds = if (sog > 0.1) {
            distanceMeters / sog
        } else {
            Double.POSITIVE_INFINITY
        }

        return MobReturnVector(
            distanceMeters = distanceMeters,
            bearingDegrees = bearingDegrees,
            estimatedTimeToMarkerSeconds = etaSeconds
        )
    }

    /**
     * Calculates the initial bearing (forward azimuth) from point A to point B.
     * Result is in degrees [0, 360).
     */
    private fun calculateBearingDegrees(from: LatLon, to: LatLon): Double {
        val lat1 = from.latitude * PI / 180.0
        val lon1 = from.longitude * PI / 180.0
        val lat2 = to.latitude * PI / 180.0
        val lon2 = to.longitude * PI / 180.0

        val deltaLon = lon2 - lon1

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        
        val bearingRadians = atan2(y, x)
        return (bearingRadians * 180.0 / PI + 360.0) % 360.0
    }
}
