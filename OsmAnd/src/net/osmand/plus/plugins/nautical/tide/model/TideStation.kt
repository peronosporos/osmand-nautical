package net.osmand.plus.plugins.nautical.tide.model

/**
 * Represents a tide station with its geographic location and harmonic constituents.
 *
 * @property id Unique identifier for the station.
 * @property name Display name of the station.
 * @property latitude Latitude in decimal degrees.
 * @property longitude Longitude in decimal degrees.
 * @property timezoneOffset Timezone offset from UTC in seconds.
 * @property constituents List of harmonic constituents for this station.
 */
data class TideStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneOffset: Int,
    val constituents: List<HarmonicConstituent>
)
