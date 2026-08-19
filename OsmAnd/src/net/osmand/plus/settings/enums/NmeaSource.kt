package net.osmand.plus.settings.enums

import net.osmand.plus.R

enum class NmeaSource(private val titleId: Int) : EnumWithTitleId {
    INTERNAL(R.string.nautical_nmea_source_internal),
    SIGNALK(R.string.nautical_nmea_source_signalk),
    BLUETOOTH(R.string.nautical_nmea_source_bluetooth),
    USB(R.string.nautical_nmea_source_usb),
    TCP(R.string.nautical_nmea_source_tcp);

    override fun getTitleId(): Int = titleId

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String?): NmeaSource {
            return entries.find { it.name.lowercase() == value?.lowercase() } ?: SIGNALK
        }
    }
}
