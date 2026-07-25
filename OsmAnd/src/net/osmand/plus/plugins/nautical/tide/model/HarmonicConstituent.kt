package net.osmand.plus.plugins.nautical.tide.model

/**
 * Represents a single harmonic constituent of a tide.
 *
 * @property name The name of the constituent (e.g., M2, S2, N2).
 * @property amplitude The amplitude of the constituent in meters.
 * @property epoch The phase offset (g) in degrees.
 * @property speed The speed of the constituent in degrees per hour.
 */
data class HarmonicConstituent(
    val name: String,
    val amplitude: Double,
    val epoch: Double,
    val speed: Double
)
