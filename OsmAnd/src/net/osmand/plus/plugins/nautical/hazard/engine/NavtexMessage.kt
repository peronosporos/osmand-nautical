package net.osmand.plus.plugins.nautical.hazard.engine

import net.osmand.data.LatLon
import net.osmand.plus.R
import java.io.Serializable

/**
 * Subject indicator (B2) for NAVTEX messages, per ITU-R M.625 / GMDSS:
 * A navigational warnings, B meteorological warnings, C ice reports,
 * D search & rescue and anti-piracy, E meteorological forecasts,
 * F pilot service, G AIS (formerly Decca), H LORAN, I not used
 * (formerly OMEGA), J SATNAV (GPS/GLONASS), K other electronic navaids,
 * L navigational warnings additional to A, T test transmissions (UK),
 * V notice to fishermen (US, unused), W environmental (US, unused),
 * X/Y special services allocated by the IMO NAVTEX Panel,
 * Z no message on hand.
 * A, B, D and L must not be rejected by a receiving station.
 */
enum class NavtexSubject(val code: Char) : Serializable {
    NAVIGATIONAL_WARNING('A'),
    METEOROLOGICAL_WARNING('B'),
    ICE_REPORT('C'),
    SEARCH_AND_RESCUE('D'),
    METEOROLOGICAL_FORECAST('E'),
    PILOT_SERVICE('F'),
    AIS_MESSAGES('G'),
    LORAN_MESSAGES('H'),
    NOT_USED_FORMER_OMEGA('I'),
    SATNAV_MESSAGES('J'),
    OTHER_ELECTRONIC_NAVAIDS('K'),
    NAVIGATIONAL_WARNING_ADDITIONAL('L'),
    TEST_TRANSMISSIONS('T'),
    NOTICE_TO_FISHERMEN('V'),
    ENVIRONMENTAL_MESSAGES('W'),
    SPECIAL_SERVICES_X('X'),
    SPECIAL_SERVICES_Y('Y'),
    NO_MESSAGE('Z'),
    UNKNOWN('\u0000');

    /**
     * Localized display label for this subject (user-visible in the HUD
     * ticker, the message list and the map context menu). Standard names
     * such as AIS, LORAN, OMEGA, SATNAV stay untranslated.
     */
    fun label(context: android.content.Context): String {
        return when (this) {
            NAVIGATIONAL_WARNING -> context.getString(R.string.navtex_subject_a)
            METEOROLOGICAL_WARNING -> context.getString(R.string.navtex_subject_b)
            ICE_REPORT -> context.getString(R.string.navtex_subject_c)
            SEARCH_AND_RESCUE -> context.getString(R.string.navtex_subject_d)
            METEOROLOGICAL_FORECAST -> context.getString(R.string.navtex_subject_e)
            PILOT_SERVICE -> context.getString(R.string.navtex_subject_f)
            AIS_MESSAGES -> context.getString(R.string.navtex_subject_g)
            LORAN_MESSAGES -> context.getString(R.string.navtex_subject_h)
            NOT_USED_FORMER_OMEGA -> context.getString(R.string.navtex_subject_i)
            SATNAV_MESSAGES -> context.getString(R.string.navtex_subject_j)
            OTHER_ELECTRONIC_NAVAIDS -> context.getString(R.string.navtex_subject_k)
            NAVIGATIONAL_WARNING_ADDITIONAL -> context.getString(R.string.navtex_subject_l)
            TEST_TRANSMISSIONS -> context.getString(R.string.navtex_subject_t)
            NOTICE_TO_FISHERMEN -> context.getString(R.string.navtex_subject_v)
            ENVIRONMENTAL_MESSAGES -> context.getString(R.string.navtex_subject_w)
            SPECIAL_SERVICES_X -> context.getString(R.string.navtex_subject_x)
            SPECIAL_SERVICES_Y -> context.getString(R.string.navtex_subject_y)
            NO_MESSAGE -> context.getString(R.string.navtex_subject_z)
            UNKNOWN -> context.getString(R.string.navtex_subject_unknown)
        }
    }

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
