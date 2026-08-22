package net.osmand.plus.plugins.nautical.ui.widgets

import android.view.View
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.settings.enums.VesselType
import kotlin.math.abs

class AutopilotBottomSheetHelper(
    private val sheet: NauticalPilotBottomSheet,
    private val tackPortBtn: MaterialButton,
    private val tackStbdBtn: MaterialButton,
    private val btnManeuverSec1: MaterialButton,
    private val btnManeuverSec2: MaterialButton,
    private val btnManeuverSec3: MaterialButton,
    private val btnManeuverSec4: MaterialButton,
    private val btnManeuverSec5: MaterialButton,
    private val btnManeuverSec6: MaterialButton,
    private val btnManeuverSec7: MaterialButton,
    private val btnManeuverSec8: MaterialButton,
    private val btnManeuverSec9: MaterialButton,
    private val btnManeuverSec10: MaterialButton,
    private val btnManeuverSec11: MaterialButton,
    private val btnManeuverSec12: MaterialButton,
    private val btnManeuverSec13: MaterialButton,
    private val btnManeuverSec14: MaterialButton,
    private val layoutSecondaryRow3: View,
    private val layoutSecondaryRow4: View,
    private val layoutSecondaryRow5: View,
    private val layoutSecondaryRow6: View,
    private val layoutSecondaryRow7: View,
    private val onInitiateManeuver: (title: String, targetHeading: Int?, onExecute: (targetHeading: Int?) -> Unit) -> Unit
) {

    fun updateManeuverButtons(vesselType: VesselType) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            // Proa / Shunter: Shunt & Center Rudder on primary row.
            tackPortBtn.text = sheet.getString(R.string.nautical_shunt)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_shunt)
            tackStbdBtn.text = sheet.getString(R.string.nautical_center_rudder)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_center_rudder)

            // Secondary Panel (Heave-To, Tack, and Gybe are hidden):
            // Row 1 (Anchor): Drop & Set Anchor, Weigh Anchor
            btnManeuverSec1.text = sheet.getString(R.string.nautical_maneuver_anchoring)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_maneuver_anchoring)
            btnManeuverSec1.visibility = View.VISIBLE
            btnManeuverSec2.text = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec2.visibility = View.VISIBLE

            // Row 2 (Harbor / Mooring): Alongside Docking, Stern-to Med Mooring
            btnManeuverSec3.text = sheet.getString(R.string.nautical_maneuver_docking)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_maneuver_docking)
            btnManeuverSec3.visibility = View.VISIBLE
            btnManeuverSec4.text = sheet.getString(R.string.nautical_maneuver_med_mooring)
            btnManeuverSec4.contentDescription = sheet.getString(R.string.nautical_maneuver_med_mooring)
            btnManeuverSec4.visibility = View.VISIBLE

            // Row 3 (Harbor / Mooring): Mooring Buoy, Slip Exit
            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_maneuver_mooring)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_maneuver_mooring)
            btnManeuverSec5.visibility = View.VISIBLE
            btnManeuverSec6.text = sheet.getString(R.string.nautical_maneuver_slip_exit)
            btnManeuverSec6.contentDescription = sheet.getString(R.string.nautical_maneuver_slip_exit)
            btnManeuverSec6.visibility = View.VISIBLE

            // Row 4 (Tactical): Dodge Port, Dodge Starboard
            layoutSecondaryRow4.visibility = View.VISIBLE
            btnManeuverSec7.text = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec7.contentDescription = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec7.visibility = View.VISIBLE
            btnManeuverSec8.text = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec8.contentDescription = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec8.visibility = View.VISIBLE

            // Row 5 (Tactical): Holding Pattern, Emergency Stop
            layoutSecondaryRow5.visibility = View.VISIBLE
            btnManeuverSec9.text = sheet.getString(R.string.nautical_holding_pattern)
            btnManeuverSec9.contentDescription = sheet.getString(R.string.nautical_holding_pattern)
            btnManeuverSec9.visibility = View.VISIBLE
            btnManeuverSec10.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec10.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec10.visibility = View.VISIBLE

            layoutSecondaryRow6.visibility = View.GONE
            layoutSecondaryRow7.visibility = View.GONE
        } else {
            // Conventional Monohull / Catamaran:
            tackPortBtn.text = sheet.getString(R.string.nautical_tack_port_short)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_tack_port)
            tackStbdBtn.text = sheet.getString(R.string.nautical_tack_stbd_short)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_tack_stbd)

            // Row 1: Gybe Port, Gybe Starboard
            btnManeuverSec1.text = sheet.getString(R.string.nautical_gybe_port_short)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_gybe_port_label)
            btnManeuverSec1.visibility = View.VISIBLE
            btnManeuverSec2.text = sheet.getString(R.string.nautical_gybe_stbd_short)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_gybe_stbd_label)
            btnManeuverSec2.visibility = View.VISIBLE

            // Row 2: Heave-To, Center Rudder
            btnManeuverSec3.text = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec3.visibility = View.VISIBLE
            btnManeuverSec4.text = sheet.getString(R.string.nautical_center_rudder)
            btnManeuverSec4.contentDescription = sheet.getString(R.string.nautical_center_rudder)
            btnManeuverSec4.visibility = View.VISIBLE

            // Row 3 (Anchor): Drop & Set Anchor, Weigh Anchor
            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_maneuver_anchoring)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_maneuver_anchoring)
            btnManeuverSec5.visibility = View.VISIBLE
            btnManeuverSec6.text = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec6.contentDescription = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec6.visibility = View.VISIBLE

            // Row 4 (Harbor / Mooring): Alongside Docking, Stern-to Med Mooring
            layoutSecondaryRow4.visibility = View.VISIBLE
            btnManeuverSec7.text = sheet.getString(R.string.nautical_maneuver_docking)
            btnManeuverSec7.contentDescription = sheet.getString(R.string.nautical_maneuver_docking)
            btnManeuverSec7.visibility = View.VISIBLE
            btnManeuverSec8.text = sheet.getString(R.string.nautical_maneuver_med_mooring)
            btnManeuverSec8.contentDescription = sheet.getString(R.string.nautical_maneuver_med_mooring)
            btnManeuverSec8.visibility = View.VISIBLE

            // Row 5 (Harbor / Mooring): Mooring Buoy, Slip Exit
            layoutSecondaryRow5.visibility = View.VISIBLE
            btnManeuverSec9.text = sheet.getString(R.string.nautical_maneuver_mooring)
            btnManeuverSec9.contentDescription = sheet.getString(R.string.nautical_maneuver_mooring)
            btnManeuverSec9.visibility = View.VISIBLE
            btnManeuverSec10.text = sheet.getString(R.string.nautical_maneuver_slip_exit)
            btnManeuverSec10.contentDescription = sheet.getString(R.string.nautical_maneuver_slip_exit)
            btnManeuverSec10.visibility = View.VISIBLE

            // Row 6 (Tactical): Dodge Port, Dodge Starboard
            layoutSecondaryRow6.visibility = View.VISIBLE
            btnManeuverSec11.text = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec11.contentDescription = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec11.visibility = View.VISIBLE
            btnManeuverSec12.text = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec12.contentDescription = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec12.visibility = View.VISIBLE

            // Row 7 (Tactical): Holding Pattern, Emergency Stop
            layoutSecondaryRow7.visibility = View.VISIBLE
            btnManeuverSec13.text = sheet.getString(R.string.nautical_holding_pattern)
            btnManeuverSec13.contentDescription = sheet.getString(R.string.nautical_holding_pattern)
            btnManeuverSec13.visibility = View.VISIBLE
            btnManeuverSec14.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec14.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec14.visibility = View.VISIBLE
        }
    }

    fun handlePrimaryManeuver(vesselType: VesselType, state: MarineState?, isPort: Boolean) {
        val isProa = vesselType == VesselType.PROA
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        val awaDeg = state?.windDirectionApparent?.let { Math.toDegrees(it) } ?: 45.0
        val mm = NauticalPlugin.getInstance()?.maneuverManager

        if (isProa) {
            if (isPort) {
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

    fun handleSecondaryManeuver(index: Int, vesselType: VesselType, state: MarineState?) {
        val isProa = vesselType == VesselType.PROA
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        val awaDeg = state?.windDirectionApparent?.let { Math.toDegrees(it) } ?: 45.0
        val mm = NauticalPlugin.getInstance()?.maneuverManager

        if (isProa) {
            when (index) {
                1 -> {
                    // Drop & Set Anchor
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_anchoring), null) {
                        mm?.setActiveManeuver("anchoring")
                        mm?.execute()
                    }
                }
                2 -> {
                    // Weigh Anchor
                    onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
                        mm?.setActiveManeuver("weigh_anchor")
                        mm?.execute()
                    }
                }
                3 -> {
                    // Alongside Docking
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_docking), null) {
                        NauticalPlugin.autopilot?.dockingHold()
                        mm?.setActiveManeuver("docking")
                        mm?.execute()
                    }
                }
                4 -> {
                    // Stern-to Med Mooring
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_med_mooring), null) {
                        mm?.setActiveManeuver("med_mooring")
                        mm?.execute()
                    }
                }
                5 -> {
                    // Mooring Buoy
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_mooring), null) {
                        mm?.setActiveManeuver("mooring")
                        mm?.execute()
                    }
                }
                6 -> {
                    // Slip Exit
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_slip_exit), null) {
                        mm?.setActiveManeuver("slip_exit")
                        mm?.execute()
                    }
                }
                7 -> {
                    // Dodge Port (-10°)
                    val targetDeg = (((curH - 10.0) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_dodge_port_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.dodge(port = true)
                    }
                }
                8 -> {
                    // Dodge Starboard (+10°)
                    val targetDeg = (((curH + 10.0) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_dodge_stbd_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.dodge(port = false)
                    }
                }
                9 -> {
                    // Holding Pattern
                    onInitiateManeuver(sheet.getString(R.string.nautical_holding_pattern), null) {
                        mm?.setActiveManeuver("holding_pattern")
                        mm?.execute()
                    }
                }
                10 -> {
                    // Emergency Stop
                    onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
                        NauticalPlugin.autopilot?.emergencyStop()
                        mm?.abort("Emergency stop executed", isAlarm = true)
                    }
                }
            }
        } else {
            when (index) {
                1 -> {
                    // Gybe Port
                    val delta = (180.0 - 2.0 * abs(180.0 - abs(awaDeg))).coerceIn(70.0, 120.0)
                    val targetDeg = (((curH - delta) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_gybe_port_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.gybe(port = true)
                        mm?.setActiveManeuver("gybe_port")
                        mm?.execute()
                    }
                }
                2 -> {
                    // Gybe Starboard
                    val delta = (180.0 - 2.0 * abs(180.0 - abs(awaDeg))).coerceIn(70.0, 120.0)
                    val targetDeg = (((curH + delta) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_gybe_stbd_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.gybe(port = false)
                        mm?.setActiveManeuver("gybe_stbd")
                        mm?.execute()
                    }
                }
                3 -> {
                    // Heave-To
                    onInitiateManeuver(sheet.getString(R.string.nautical_heave_to), null) {
                        NauticalPlugin.autopilot?.heaveTo(port = true)
                        mm?.setActiveManeuver("heave_to")
                        mm?.execute()
                    }
                }
                4 -> {
                    // Center Rudder
                    onInitiateManeuver(sheet.getString(R.string.nautical_center_rudder), null) {
                        NauticalPlugin.autopilot?.setRudderAngle(0.0)
                    }
                }
                5 -> {
                    // Drop & Set Anchor
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_anchoring), null) {
                        mm?.setActiveManeuver("anchoring")
                        mm?.execute()
                    }
                }
                6 -> {
                    // Weigh Anchor
                    onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
                        mm?.setActiveManeuver("weigh_anchor")
                        mm?.execute()
                    }
                }
                7 -> {
                    // Alongside Docking
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_docking), null) {
                        NauticalPlugin.autopilot?.dockingHold()
                        mm?.setActiveManeuver("docking")
                        mm?.execute()
                    }
                }
                8 -> {
                    // Stern-to Med Mooring
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_med_mooring), null) {
                        mm?.setActiveManeuver("med_mooring")
                        mm?.execute()
                    }
                }
                9 -> {
                    // Mooring Buoy
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_mooring), null) {
                        mm?.setActiveManeuver("mooring")
                        mm?.execute()
                    }
                }
                10 -> {
                    // Slip Exit
                    onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_slip_exit), null) {
                        mm?.setActiveManeuver("slip_exit")
                        mm?.execute()
                    }
                }
                11 -> {
                    // Dodge Port (-10°)
                    val targetDeg = (((curH - 10.0) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_dodge_port_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.dodge(port = true)
                    }
                }
                12 -> {
                    // Dodge Starboard (+10°)
                    val targetDeg = (((curH + 10.0) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_dodge_stbd_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.dodge(port = false)
                    }
                }
                13 -> {
                    // Holding Pattern
                    onInitiateManeuver(sheet.getString(R.string.nautical_holding_pattern), null) {
                        mm?.setActiveManeuver("holding_pattern")
                        mm?.execute()
                    }
                }
                14 -> {
                    // Emergency Stop
                    onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
                        NauticalPlugin.autopilot?.emergencyStop()
                        mm?.abort("Emergency stop executed", isAlarm = true)
                    }
                }
            }
        }
    }
}

