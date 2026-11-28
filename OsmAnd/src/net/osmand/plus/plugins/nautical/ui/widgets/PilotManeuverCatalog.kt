package net.osmand.plus.plugins.nautical.ui.widgets

import androidx.annotation.StringRes
import net.osmand.plus.R
import net.osmand.plus.settings.enums.VesselType

/**
 * Stable identity of a pilot sheet maneuver. The maneuver execution logic
 * is keyed by this enum, so the same maneuver can appear at different
 * button positions for different vessel types without duplicated code.
 */
enum class PilotManeuverKind {
    GYBE_PORT,
    GYBE_STBD,
    HEAVE_TO,
    CENTER_RUDDER,
    ANCHORING,
    WEIGH_ANCHOR,
    DOCKING,
    MED_MOORING,
    MOORING,
    SLIP_EXIT,
    DODGE_PORT,
    DODGE_STBD,
    HOLDING_PATTERN,
    EMERGENCY_STOP
}

/**
 * One secondary maneuver button: identity plus label and accessibility
 * string resources.
 */
data class PilotManeuver(
    val kind: PilotManeuverKind,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
)

/**
 * Data-driven catalog of the pilot sheet maneuver buttons per vessel type.
 *
 * Previously the sheet wired 14 individually declared buttons into
 * AutopilotBottomSheetHelper, and the helper held two duplicated
 * per-vessel when-blocks that mapped button indices to maneuvers. The
 * order and labels below reproduce that behavior exactly; adding or
 * reordering a maneuver is now a data change instead of a view-wiring
 * change.
 *
 * Layout contract (see nautical_pilot_bottom_sheet.xml): buttons 1-4
 * sit on always visible rows, buttons 5-14 sit in pairs on rows 3-7.
 * Row number k (3..7) is visible when the catalog holds at least
 * 2k-1 maneuvers, and buttons beyond the catalog size are hidden.
 */
object PilotManeuverCatalog {

    fun isProa(vesselType: VesselType): Boolean = vesselType == VesselType.PROA

    @StringRes
    fun primaryPortLabelRes(vesselType: VesselType): Int =
        if (isProa(vesselType)) R.string.nautical_shunt else R.string.nautical_tack_port_short

    @StringRes
    fun primaryPortDescriptionRes(vesselType: VesselType): Int =
        if (isProa(vesselType)) R.string.nautical_shunt else R.string.nautical_tack_port

    @StringRes
    fun primaryStarboardLabelRes(vesselType: VesselType): Int =
        if (isProa(vesselType)) R.string.nautical_center_rudder else R.string.nautical_tack_stbd_short

    @StringRes
    fun primaryStarboardDescriptionRes(vesselType: VesselType): Int =
        if (isProa(vesselType)) R.string.nautical_center_rudder else R.string.nautical_tack_stbd

    /**
     * Secondary maneuvers in button order for the given vessel type.
     * Proa vessels have 10 (no gybe or heave-to); conventional vessels
     * have the full 14.
     */
    fun secondaryManeuvers(vesselType: VesselType): List<PilotManeuver> = if (isProa(vesselType)) {
        listOf(
            PilotManeuver(PilotManeuverKind.ANCHORING, R.string.nautical_maneuver_anchoring, R.string.nautical_maneuver_anchoring),
            PilotManeuver(PilotManeuverKind.WEIGH_ANCHOR, R.string.nautical_weigh_anchor, R.string.nautical_weigh_anchor),
            PilotManeuver(PilotManeuverKind.DOCKING, R.string.nautical_maneuver_docking, R.string.nautical_maneuver_docking),
            PilotManeuver(PilotManeuverKind.MED_MOORING, R.string.nautical_maneuver_med_mooring, R.string.nautical_maneuver_med_mooring),
            PilotManeuver(PilotManeuverKind.MOORING, R.string.nautical_maneuver_mooring, R.string.nautical_maneuver_mooring),
            PilotManeuver(PilotManeuverKind.SLIP_EXIT, R.string.nautical_maneuver_slip_exit, R.string.nautical_maneuver_slip_exit),
            PilotManeuver(PilotManeuverKind.DODGE_PORT, R.string.nautical_dodge_port_label, R.string.nautical_dodge_port_label),
            PilotManeuver(PilotManeuverKind.DODGE_STBD, R.string.nautical_dodge_stbd_label, R.string.nautical_dodge_stbd_label),
            PilotManeuver(PilotManeuverKind.HOLDING_PATTERN, R.string.nautical_holding_pattern, R.string.nautical_holding_pattern),
            PilotManeuver(PilotManeuverKind.EMERGENCY_STOP, R.string.nautical_emergency_stop_label, R.string.nautical_emergency_stop_label)
        )
    } else {
        listOf(
            PilotManeuver(PilotManeuverKind.GYBE_PORT, R.string.nautical_gybe_port_short, R.string.nautical_gybe_port_label),
            PilotManeuver(PilotManeuverKind.GYBE_STBD, R.string.nautical_gybe_stbd_short, R.string.nautical_gybe_stbd_label),
            PilotManeuver(PilotManeuverKind.HEAVE_TO, R.string.nautical_heave_to, R.string.nautical_heave_to),
            PilotManeuver(PilotManeuverKind.CENTER_RUDDER, R.string.nautical_center_rudder, R.string.nautical_center_rudder),
            PilotManeuver(PilotManeuverKind.ANCHORING, R.string.nautical_maneuver_anchoring, R.string.nautical_maneuver_anchoring),
            PilotManeuver(PilotManeuverKind.WEIGH_ANCHOR, R.string.nautical_weigh_anchor, R.string.nautical_weigh_anchor),
            PilotManeuver(PilotManeuverKind.DOCKING, R.string.nautical_maneuver_docking, R.string.nautical_maneuver_docking),
            PilotManeuver(PilotManeuverKind.MED_MOORING, R.string.nautical_maneuver_med_mooring, R.string.nautical_maneuver_med_mooring),
            PilotManeuver(PilotManeuverKind.MOORING, R.string.nautical_maneuver_mooring, R.string.nautical_maneuver_mooring),
            PilotManeuver(PilotManeuverKind.SLIP_EXIT, R.string.nautical_maneuver_slip_exit, R.string.nautical_maneuver_slip_exit),
            PilotManeuver(PilotManeuverKind.DODGE_PORT, R.string.nautical_dodge_port_label, R.string.nautical_dodge_port_label),
            PilotManeuver(PilotManeuverKind.DODGE_STBD, R.string.nautical_dodge_stbd_label, R.string.nautical_dodge_stbd_label),
            PilotManeuver(PilotManeuverKind.HOLDING_PATTERN, R.string.nautical_holding_pattern, R.string.nautical_holding_pattern),
            PilotManeuver(PilotManeuverKind.EMERGENCY_STOP, R.string.nautical_emergency_stop_label, R.string.nautical_emergency_stop_label)
        )
    }
}
