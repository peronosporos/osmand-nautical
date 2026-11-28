package net.osmand.plus.plugins.nautical.ui.widgets

import android.view.View
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.settings.enums.VesselType
import kotlin.math.abs

/**
 * Binds the pilot sheet maneuver buttons to their actions.
 *
 * Button texts, accessibility labels, and row visibility come from
 * [PilotManeuverCatalog]; execution is keyed by [PilotManeuverKind], so
 * each maneuver action exists exactly once regardless of which button
 * position it occupies for a given vessel type.
 */
class AutopilotBottomSheetHelper(
    private val sheet: NauticalPilotBottomSheet,
    private val tackPortBtn: MaterialButton,
    private val tackStbdBtn: MaterialButton,
    private val secondaryButtons: List<MaterialButton>,
    private val secondaryRows: List<View>,
    private val onInitiateManeuver: (title: String, targetHeading: Int?, onExecute: (targetHeading: Int?) -> Unit) -> Unit
) {

    fun updateManeuverButtons(vesselType: VesselType) {
        tackPortBtn.text = sheet.getString(PilotManeuverCatalog.primaryPortLabelRes(vesselType))
        tackPortBtn.contentDescription = sheet.getString(PilotManeuverCatalog.primaryPortDescriptionRes(vesselType))
        tackStbdBtn.text = sheet.getString(PilotManeuverCatalog.primaryStarboardLabelRes(vesselType))
        tackStbdBtn.contentDescription = sheet.getString(PilotManeuverCatalog.primaryStarboardDescriptionRes(vesselType))

        val maneuvers = PilotManeuverCatalog.secondaryManeuvers(vesselType)
        secondaryButtons.forEachIndexed { index, btn ->
            val maneuver = maneuvers.getOrNull(index)
            if (maneuver == null) {
                btn.visibility = View.GONE
            } else {
                btn.text = sheet.getString(maneuver.labelRes)
                btn.contentDescription = sheet.getString(maneuver.descriptionRes)
                btn.visibility = View.VISIBLE
            }
        }
        // Rows 3..7 hold buttons 5..14 in pairs. Row number k is visible
        // when the catalog holds at least 2k-1 maneuvers (see catalog doc).
        secondaryRows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 3
            row.visibility = if (maneuvers.size >= 2 * rowNumber - 1) View.VISIBLE else View.GONE
        }
    }

    fun handlePrimaryManeuver(vesselType: VesselType, state: MarineState?, isPort: Boolean) {
        val isProa = PilotManeuverCatalog.isProa(vesselType)
        // No invented 0-degree base heading: maneuvers that compute a target
        // from the current heading refuse when nothing was measured.
        val hasHeading = state?.targetHeading != null || state?.headingTrue != null
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        val mm = NauticalPlugin.getInstance()?.maneuverManager

        if (isProa) {
            if (isPort) {
                if (!hasHeading) {
                    NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_heading)
                    return
                }
                val targetDeg = (((curH + 180.0) % 360.0 + 360.0) % 360.0).toInt()
                onInitiateManeuver(sheet.getString(R.string.nautical_shunt), targetDeg) {
                    NauticalPlugin.autopilot?.shunt()
                    mm?.setActiveManeuver("shunt")
                    mm?.execute()
                }
            } else {
                onInitiateManeuver(sheet.getString(R.string.nautical_center_rudder), null) {
                    NauticalPlugin.autopilot?.setRudderAngle(0.0)
                }
            }
        } else {
            // Tack planning needs real wind AND a real heading: refuse
            // rather than plan a turn from invented values.
            val awaDeg = state?.windDirectionApparent?.let { Math.toDegrees(it) }
            if (awaDeg == null) {
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_wind)
                return
            }
            if (!hasHeading) {
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_heading)
                return
            }
            val delta = (2.0 * abs(awaDeg)).coerceIn(70.0, 110.0)
            val title = if (isPort) sheet.getString(R.string.nautical_tack_port) else sheet.getString(R.string.nautical_tack_stbd)
            val targetDeg = if (isPort) {
                (((curH - delta) % 360.0 + 360.0) % 360.0).toInt()
            } else {
                (((curH + delta) % 360.0 + 360.0) % 360.0).toInt()
            }

            onInitiateManeuver(title, targetDeg) { confirmedDeg ->
                confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                NauticalPlugin.autopilot?.tack(port = isPort)
                mm?.setActiveManeuver(if (isPort) "tack_port" else "tack_stbd")
                mm?.execute()
            }
        }
    }

    fun handleSecondaryManeuver(kind: PilotManeuverKind, state: MarineState?) {
        // No invented 0-degree base heading: dodge/gybe below refuse when
        // nothing was measured (same honesty rule as the primary maneuvers).
        val hasHeading = state?.targetHeading != null || state?.headingTrue != null
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        // Nullable: gybe branches below refuse when the wind is unknown
        // (formerly they plotted the turn from an invented 45° AWA).
        val awaDeg = state?.windDirectionApparent?.let { Math.toDegrees(it) }
        val mm = NauticalPlugin.getInstance()?.maneuverManager

        when (kind) {
            PilotManeuverKind.GYBE_PORT,
            PilotManeuverKind.GYBE_STBD -> {
                val port = kind == PilotManeuverKind.GYBE_PORT
                if (awaDeg == null) {
                    NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_wind)
                    return
                }
                if (!hasHeading) {
                    NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_heading)
                    return
                }
                val delta = (180.0 - 2.0 * abs(180.0 - abs(awaDeg))).coerceIn(70.0, 120.0)
                val targetDeg = if (port) {
                    (((curH - delta) % 360.0 + 360.0) % 360.0).toInt()
                } else {
                    (((curH + delta) % 360.0 + 360.0) % 360.0).toInt()
                }
                val title = if (port) sheet.getString(R.string.nautical_gybe_port_label) else sheet.getString(R.string.nautical_gybe_stbd_label)
                onInitiateManeuver(title, targetDeg) { confirmedDeg ->
                    confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                    NauticalPlugin.autopilot?.gybe(port = port)
                    mm?.setActiveManeuver(if (port) "gybe_port" else "gybe_stbd")
                    mm?.execute()
                }
            }
            PilotManeuverKind.HEAVE_TO -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_heave_to), null) {
                    NauticalPlugin.autopilot?.heaveTo(port = true)
                    mm?.setActiveManeuver("heave_to")
                    mm?.execute()
                }
            }
            PilotManeuverKind.CENTER_RUDDER -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_center_rudder), null) {
                    NauticalPlugin.autopilot?.setRudderAngle(0.0)
                }
            }
            PilotManeuverKind.ANCHORING -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_anchoring), null) {
                    mm?.setActiveManeuver("anchoring")
                    mm?.execute()
                }
            }
            PilotManeuverKind.WEIGH_ANCHOR -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
                    mm?.setActiveManeuver("weigh_anchor")
                    mm?.execute()
                }
            }
            PilotManeuverKind.DOCKING -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_docking), null) {
                    NauticalPlugin.autopilot?.dockingHold()
                    mm?.setActiveManeuver("docking")
                    mm?.execute()
                }
            }
            PilotManeuverKind.MED_MOORING -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_med_mooring), null) {
                    mm?.setActiveManeuver("med_mooring")
                    mm?.execute()
                }
            }
            PilotManeuverKind.MOORING -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_mooring), null) {
                    mm?.setActiveManeuver("mooring")
                    mm?.execute()
                }
            }
            PilotManeuverKind.SLIP_EXIT -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_slip_exit), null) {
                    mm?.setActiveManeuver("slip_exit")
                    mm?.execute()
                }
            }
            PilotManeuverKind.DODGE_PORT,
            PilotManeuverKind.DODGE_STBD -> {
                val port = kind == PilotManeuverKind.DODGE_PORT
                if (!hasHeading) {
                    NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_maneuver_no_heading)
                    return
                }
                val offset = if (port) -10.0 else 10.0
                val targetDeg = (((curH + offset) % 360.0 + 360.0) % 360.0).toInt()
                val title = if (port) sheet.getString(R.string.nautical_dodge_port_label) else sheet.getString(R.string.nautical_dodge_stbd_label)
                onInitiateManeuver(title, targetDeg) { confirmedDeg ->
                    confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                    NauticalPlugin.autopilot?.dodge(port = port)
                }
            }
            PilotManeuverKind.HOLDING_PATTERN -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_holding_pattern), null) {
                    mm?.setActiveManeuver("holding_pattern")
                    mm?.execute()
                }
            }
            PilotManeuverKind.EMERGENCY_STOP -> {
                onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
                    NauticalPlugin.autopilot?.emergencyStop()
                    mm?.abort(sheet.getString(R.string.nautical_emergency_stop_executed), isAlarm = true)
                }
            }
        }
    }
}
