package net.osmand.plus.plugins.nautical.tide.model

/**
 * Represents a predicted tide height at a specific time.
 *
 * @property timestamp Unix timestamp in milliseconds.
 * @property heightMeters Predicted water height in meters relative to datum.
 * @property heightAboveDatum Predicted water height in meters relative to datum (usually chart datum).
 * @property meanSeaLevel The mean sea level offset used for this prediction.
 * @property isHighTide True if this prediction represents a local maximum (high tide).
 */
data class TidePrediction(
    val timestamp: Long,
    val heightMeters: Double,
    val heightAboveDatum: Double = heightMeters,
    val meanSeaLevel: Double = 0.0,
    val isHighTide: Boolean? = null,
    val velocity: Double? = null, // Current speed in m/s
    val direction: Double? = null // Current direction in Radians
)
