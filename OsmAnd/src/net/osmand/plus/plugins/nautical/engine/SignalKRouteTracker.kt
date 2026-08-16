package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.plugins.nautical.laylines.engine.LatLon
import net.osmand.plus.plugins.nautical.network.SignalKCourse
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.shared.util.KMapUtils
import java.util.concurrent.ConcurrentLinkedQueue

class SignalKRouteTracker {

    val routeQueue = ConcurrentLinkedQueue<Pair<Double, Double>>()
    var lastWaypointLat: Double? = null
    var lastWaypointLon: Double? = null

    var isFollowingRoute: Boolean = false
        internal set

    var arrivalRadiusMeters: Double = 50.0
    var xteThresholdNm: Double = 0.1
    var vesselDraft: Double = 0.0
    var corridorWidthNm: Double = 0.5
    var safetyCorridorBufferNm: Double = 0.1

    private var lastFollowingUpdateTimestamp: Long = 0

    fun loadRoute(route: List<Pair<Double, Double>>, startLat: Double? = null, startLon: Double? = null) {
        routeQueue.clear()
        routeQueue.addAll(route)
        isFollowingRoute = true
        lastWaypointLat = startLat
        lastWaypointLon = startLon
    }

    fun clearRoute() {
        routeQueue.clear()
        isFollowingRoute = false
        lastWaypointLat = null
        lastWaypointLon = null
    }

    fun getNextWaypoint(): Pair<Double, Double>? = routeQueue.peek()

    fun getSecondNextWaypoint(): Pair<Double, Double>? {
        val iterator = routeQueue.iterator()
        if (iterator.hasNext()) {
            iterator.next() // Skip first
            if (iterator.hasNext()) return iterator.next()
        }
        return null
    }

    fun getRoutePoints(): List<Pair<Double, Double>> = routeQueue.toList()

    fun updateFollowingState(
        currentLat: Double,
        currentLon: Double,
        capabilityManager: CapabilityManager?,
        onStepReached: () -> Unit
    ) {
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (caps.hasCourseAutoAdvance) return // Offload to server

        if (!isFollowingRoute || routeQueue.isEmpty()) return
        val now = TemporalUtils.now()
        if (now - lastFollowingUpdateTimestamp < 1000) return
        lastFollowingUpdateTimestamp = now
        val target = routeQueue.peek() ?: return
        val distance = KMapUtils.getDistance(currentLat, currentLon, target.first, target.second)
        if (distance < arrivalRadiusMeters) {
            val reached = routeQueue.poll()
            lastWaypointLat = reached?.first
            lastWaypointLon = reached?.second
            onStepReached()
        }
        if (routeQueue.isEmpty()) {
            isFollowingRoute = false
        }
    }

    fun processCourseObject(course: SignalKCourse, onNextPointFound: (LatLon) -> Unit) {
        val nextPoint = course.nextPoint?.position
        if (nextPoint != null) {
            val lat = nextPoint.coordinates[1]
            val lon = nextPoint.coordinates[0]
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                onNextPointFound(LatLon(lat, lon))
            }
        }

        course.arrivalRadius?.let { radius ->
            arrivalRadiusMeters = radius
        }

        course.activeRoute?.href?.let {
            isFollowingRoute = true
        } ?: run {
            if (course.nextPoint == null) isFollowingRoute = false
        }
    }
}
