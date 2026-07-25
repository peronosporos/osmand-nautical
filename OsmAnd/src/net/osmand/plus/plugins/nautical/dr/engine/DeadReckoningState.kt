package net.osmand.plus.plugins.nautical.dr.engine

/**
 * Source of the position fix.
 */
enum class FixSource {
    GPS,
    DEAD_RECKONING
}

/**
 * Represents a position fix in the Dead Reckoning system.
 *
 * @param latitude Latitude in degrees.
 * @param longitude Longitude in degrees.
 * @param timestamp Epoch timestamp in milliseconds.
 * @param source The source of this fix.
 */
data class DrFix(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val source: FixSource
)

/**
 * Represents the motion vector of the vessel.
 *
 * @param speedThroughWater Speed of the vessel through the water in meters per second.
 * @param headingDegrees Compass heading in degrees (0-360).
 * @param leewayDegrees Leeway angle in degrees (positive for leeway to starboard, negative to port).
 */
data class DrVector(
    val speedThroughWater: Double,
    val headingDegrees: Double,
    val leewayDegrees: Double = 0.0
)
