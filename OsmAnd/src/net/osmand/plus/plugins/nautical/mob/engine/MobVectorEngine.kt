package net.osmand.plus.plugins.nautical.mob.engine

import net.osmand.data.LatLon
import net.osmand.shared.util.KMapUtils
import kotlin.math.*

object MobVectorEngine {

    /**
     * Calculates the return vector from the boat's current position back to the MOB coordinates.
     * Accounts for casualty drift based on tidal current and wind leeway.
     *
     * @param currentLocation The boat's live position.
     * @param mobEvent The recorded MOB event data.
     * @param sog Current Speed Over Ground in meters per second (m/s).
     * @param driftMps Current tidal drift speed (m/s) if available.
     * @param driftDeg Current tidal drift direction (degrees) if available.
     * @param twsMps True Wind Speed in m/s (or default 10 kn).
     * @param twdDeg True Wind Direction in degrees (meteorological).
     * @return A [MobReturnVector] containing distance, bearing, and ETA.
     */
    fun calculateReturnVector(
        currentLocation: LatLon,
        mobEvent: MobEvent,
        sog: Double,
        driftMps: Double = 0.0,
        driftDeg: Double = 0.0,
        twsMps: Double? = null,
        twdDeg: Double? = null
    ): MobReturnVector {
        val dropLocation = mobEvent.dropLocation
        val timeElapsedSec = (System.currentTimeMillis() - mobEvent.dropTimestamp) / 1000.0
        
        // 1. Tidal current vector
        val tideDx = driftMps * sin(Math.toRadians(driftDeg))
        val tideDy = driftMps * cos(Math.toRadians(driftDeg))

        // 2. Wind leeway vector (casualty leeway = 0.03 * trueWindSpeed downwind)
        val defaultTwsMps = 10.0 * 0.514444 // 10 knots in m/s
        val effectiveTws = twsMps ?: defaultTwsMps
        val leewaySpeedMps = 0.03 * effectiveTws
        // Wind leeway direction is downwind (direction wind is blowing TO = TWD + 180 or driftDeg fallback)
        val downwindDeg = twdDeg?.let { (it + 180.0) % 360.0 } ?: driftDeg
        val leewayDx = leewaySpeedMps * sin(Math.toRadians(downwindDeg))
        val leewayDy = leewaySpeedMps * cos(Math.toRadians(downwindDeg))

        // 3. Compound displacement vector
        val totalVx = tideDx + leewayDx
        val totalVy = tideDy + leewayDy
        val totalDriftSpeed = sqrt(totalVx * totalVx + totalVy * totalVy)
        val totalDriftBearing = (Math.toDegrees(atan2(totalVx, totalVy)) + 360.0) % 360.0

        // Account for casualty drift over timeElapsedSec using rhumb line projection
        val estimatedCasualtyLoc = if (totalDriftSpeed > 0.001 && timeElapsedSec > 0.0) {
            val driftDist = totalDriftSpeed * timeElapsedSec
            val p = KMapUtils.rhumbDestinationPoint(dropLocation.latitude, dropLocation.longitude, driftDist, totalDriftBearing)
            LatLon(p.latitude, p.longitude)
        } else {
            dropLocation
        }

        val distanceMeters = KMapUtils.getDistance(
            currentLocation.latitude, currentLocation.longitude,
            estimatedCasualtyLoc.latitude, estimatedCasualtyLoc.longitude
        )

        val bearingDegrees = calculateBearingDegrees(currentLocation, estimatedCasualtyLoc)

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
     * Generates an IAMSAR Expanding Square Search (SS) pattern starting at the datum.
     * Track spacing S with leg progression 1S, 1S, 2S, 2S, 3S, 3S, 4S, 4S... and 90-degree turns to starboard.
     *
     * @param datum Estimated casualty location / search datum.
     * @param trackSpacingMeters Track spacing S in meters.
     * @param legs Number of legs to generate (default 8).
     * @return Ordered list of waypoints defining the search track starting from datum.
     */
    fun generateExpandingSquarePattern(
        datum: LatLon,
        trackSpacingMeters: Double,
        legs: Int = 8
    ): List<LatLon> {
        val pattern = mutableListOf<LatLon>()
        pattern.add(datum)

        var current = datum
        for (i in 1..legs) {
            val multiplier = (i + 1) / 2
            val distance = multiplier * trackSpacingMeters
            val bearing = ((i - 1) * 90.0) % 360.0

            val next = KMapUtils.rhumbDestinationPoint(
                current.latitude, current.longitude,
                distance, bearing
            )
            val nextLatLon = LatLon(next.latitude, next.longitude)
            pattern.add(nextLatLon)
            current = nextLatLon
        }

        return pattern
    }

    /**
     * Generates an IAMSAR Sector Search (VS) pattern starting at the datum.
     * 3 equilateral triangles with 120-degree turns to starboard, each pass crossing through the datum.
     *
     * @param datum Estimated casualty location / search datum.
     * @param radiusMeters Search radius / sector leg length in meters.
     * @return Ordered list of waypoints defining the sector search track.
     */
    fun generateSectorSearchPattern(
        datum: LatLon,
        radiusMeters: Double
    ): List<LatLon> {
        val pattern = mutableListOf<LatLon>()
        pattern.add(datum)

        val sectorAngles = listOf(0.0, 120.0, 240.0)
        for (startAngle in sectorAngles) {
            // Outbound leg to outer waypoint 1
            val v1 = KMapUtils.rhumbDestinationPoint(
                datum.latitude, datum.longitude,
                radiusMeters, startAngle
            )
            pattern.add(LatLon(v1.latitude, v1.longitude))

            // Cross leg to outer waypoint 2 (60 degrees clockwise, chord length = radius)
            val crossAngle = (startAngle + 60.0) % 360.0
            val v2 = KMapUtils.rhumbDestinationPoint(
                datum.latitude, datum.longitude,
                radiusMeters, crossAngle
            )
            pattern.add(LatLon(v2.latitude, v2.longitude))

            // Inbound leg passing back through datum
            pattern.add(datum)
        }

        return pattern
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

    private var lastAudioGuidanceTimeMs = 0L

    fun checkAndTriggerAudioGuidance(
        app: net.osmand.plus.OsmandApplication,
        returnVector: MobReturnVector
    ) {
        if (!app.settings.NAUTICAL_MOB_AUDIO_GUIDANCE.get()) return
        val intervalSec = app.settings.NAUTICAL_MOB_AUDIO_INTERVAL.get().coerceAtLeast(5)
        val now = System.currentTimeMillis()
        if (now - lastAudioGuidanceTimeMs < intervalSec * 1000L) return

        lastAudioGuidanceTimeMs = now
        val dist = returnVector.distanceMeters
        val bearing = returnVector.bearingDegrees
        val msg = app.getString(net.osmand.plus.R.string.nautical_mob_target_bearing, dist.toInt(), bearing.toInt())
        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app)
            .dispatchTts(msg, net.osmand.plus.plugins.nautical.audio.AlarmType.TTS_INSTRUCTION)
    }

    fun resetAudioGuidance() {
        lastAudioGuidanceTimeMs = 0L
    }
}
