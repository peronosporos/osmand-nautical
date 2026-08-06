package net.osmand.plus.plugins.nautical.anchor

import net.osmand.Location
import net.osmand.data.LatLon
import net.osmand.shared.util.KMapUtils

/**
 * Data point for the anchor snail trail.
 */
data class TrackPoint(val latLon: LatLon, val timestamp: Long)

/**
 * Thread-safe circular buffer for storing historical anchor positions.
 * Enforces a fixed size to prevent memory growth during long anchorages.
 */
class AnchorTrackBuffer(private val maxCapacity: Int = 720) {
    private val buffer = ArrayDeque<TrackPoint>()
    private val lock = Any()

    companion object {
        private const val MIN_TIME_DELTA_MS = 30_000L // 30 seconds per point
        private const val MIN_DISTANCE_DELTA_M = 4.0  // 4 meters minimum movement to filter jitter
    }

    /**
     * Adds a new location to the buffer if it meets the filtering criteria.
     * @return true if point was added, false otherwise.
     */
    fun addPosition(location: Location): Boolean {
        synchronized(lock) {
            val lastPoint = buffer.lastOrNull()
            if (lastPoint != null) {
                val timeDelta = location.time - lastPoint.timestamp
                val distanceDelta = KMapUtils.getDistance(
                    lastPoint.latLon.latitude, lastPoint.latLon.longitude,
                    location.latitude, location.longitude,
                )
                
                // Filtering: avoid overlapping static points
                if (timeDelta < MIN_TIME_DELTA_MS && (distanceDelta < MIN_DISTANCE_DELTA_M)) {
                    return false
                }
            }

            if (buffer.size >= maxCapacity) {
                buffer.removeFirst()
            }
            buffer.addLast(TrackPoint(LatLon(location.latitude, location.longitude), location.time))
            return true
        }
    }

    /**
     * Returns a snapshot of the current points in the buffer.
     */
    fun getPoints(): List<TrackPoint> {
        synchronized(lock) {
            return buffer.toList()
        }
    }
}
