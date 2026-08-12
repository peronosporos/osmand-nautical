package net.osmand.plus.plugins.nautical.hazard.engine

import net.osmand.data.LatLon
import java.io.Serializable

/**
 * Subject indicators for NAVTEX messages as per IMO GMDSS standards.
 */
enum class NavtexSubject(val code: Char) : Serializable {
    NAVTEX_WARNING('A'),
    METEOROLOGICAL_WARNING('B'),
    ICE_REPORT('C'),
    SEARCH_AND_RESCUE('D'),
    METEOROLOGICAL_FORECAST('E'),
    PILOT_SERVICE('F'),
    NAVIGATIONAL_WARNING_L('L'),
    UNKNOWN('Z');

    companion object {
        fun fromCode(code: Char): NavtexSubject {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * Data model for a Navtex message.
 *
 * @property id Unique ID (Station + Subject + Sequence)
 * @property stationLetter Transmitter station identity (A-Z)
 * @property subject Categorized subject indicator
 * @property sequenceNumber Message serial number (00-99)
 * @property timestamp UTC timestamp of message reception
 * @property body Raw text body of the message
 * @property points Extracted geographic coordinates, if found in body (can be multiple for polygons)
 * @property isUrgent True if the subject represents a high-priority warning
 */
data class NavtexMessage(
    val id: String,
    val stationLetter: Char,
    val subject: NavtexSubject,
    val sequenceNumber: Int,
    val timestamp: Long,
    val body: String,
    val points: List<LatLon> = emptyList(),
    val isUrgent: Boolean = false,
) : Serializable {
    val isPolygon: Boolean get() = points.size > 2
}
