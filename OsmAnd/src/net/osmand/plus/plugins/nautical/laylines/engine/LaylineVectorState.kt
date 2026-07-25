package net.osmand.plus.plugins.nautical.laylines.engine

/**
 * Pure Kotlin data classes for Tactical Layline calculations.
 */

data class LatLon(val latitude: Double, val longitude: Double)

data class Vector2D(val x: Double, val y: Double) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Vector2D(x * scalar, y * scalar)

    fun magnitude() = kotlin.math.sqrt(x * x + y * y)
}

data class TidalCurrentVector(val speedMs: Double, val directionRadians: Double)

data class LaylineData(
    val portTackPoint: LatLon?,
    val starboardTackPoint: LatLon?,
    val isFetchable: Boolean,
    val targetWaypoint: LatLon
)
