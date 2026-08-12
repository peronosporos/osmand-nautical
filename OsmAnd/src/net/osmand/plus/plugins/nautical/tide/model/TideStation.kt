package net.osmand.plus.plugins.nautical.tide.model

/**
 * Represents a tide station with its geographic location and harmonic constituents.
 *
 * @property id Unique identifier for the station.
 * @property name Display name of the station.
 * @property latitude Latitude in decimal degrees.
 * @property longitude Longitude in decimal degrees.
 * @property timezoneOffset Timezone offset from UTC in seconds.
 * @property datum Mean sea level or vertical datum offset in meters.
 * @property constituents List of harmonic constituents for this station.
 */
data class TideStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneOffset: Int,
    val datum: Double = 0.0,
    val constituents: List<HarmonicConstituent>,
    val orientationDeg: Double? = null // For current stations: direction of flood
)
