package net.osmand.plus.plugins.nautical.laylines.engine

/**
 * Pure Kotlin data classes for Tactical Layline calculations.
 */

@kotlinx.serialization.Serializable
data class LatLon(val latitude: Double, val longitude: Double)

@kotlinx.serialization.Serializable
data class Vector2D(val x: Double, val y: Double) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Vector2D(x * scalar, y * scalar)

    fun magnitude() = kotlin.math.sqrt(x * x + y * y)
}

@kotlinx.serialization.Serializable
data class TidalCurrentVector(val speedMs: Double, val directionRadians: Double)

@kotlinx.serialization.Serializable
data class LaylineData(
    val portTackPoint: LatLon?,
    val starboardTackPoint: LatLon?,
    val isFetchable: Boolean,
    val targetWaypoint: LatLon,
    val portShiftCone: Pair<LatLon, LatLon>? = null,
    val stbdShiftCone: Pair<LatLon, LatLon>? = null
)
