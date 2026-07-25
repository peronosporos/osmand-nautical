package net.osmand.plus.plugins.nautical.mob.engine

import net.osmand.data.LatLon

/**
 * Data model for a Man Overboard event.
 */
data class MobEvent(
    val id: String,
    val dropLocation: LatLon,
    val dropTimestamp: Long,
    val initialSog: Double, // in m/s
    val initialCog: Double  // in radians
)

/**
 * Calculated vector back to the MOB location.
 */
data class MobReturnVector(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val estimatedTimeToMarkerSeconds: Double
)

/**
 * Current status of the MOB system.
 */
data class MobStatus(
    val state: MobState,
    val event: MobEvent? = null,
    val returnVector: MobReturnVector? = null
)

/**
 * States for the MOB state machine.
 */
enum class MobState {
    INACTIVE,
    ACTIVE_EMERGENCY,
    RESOLVED
}
