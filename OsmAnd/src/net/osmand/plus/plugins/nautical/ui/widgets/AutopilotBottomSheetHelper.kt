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
    private val layoutSecondaryRow3: View,
    private val onInitiateManeuver: (title: String, targetHeading: Int?, onExecute: (targetHeading: Int?) -> Unit) -> Unit
) {

    fun updateManeuverButtons(vesselType: VesselType) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            // Proa Boats: Primary has Shunt & Center Rudder.
            tackPortBtn.text = sheet.getString(R.string.nautical_shunt)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_shunt)
            tackStbdBtn.text = sheet.getString(R.string.nautical_center_rudder)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_center_rudder)

            // Secondary Panel (No duplicate Shunt):
            btnManeuverSec1.text = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec2.text = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec3.text = sheet.getString(R.string.nautical_slip_exit)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_slip_exit)
            btnManeuverSec4.text = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec4.contentDescription = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec4.visibility = View.VISIBLE

            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec6.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.visibility = View.VISIBLE
        } else {
            // Conventional Boats:
            tackPortBtn.text = sheet.getString(R.string.nautical_tack_port_short)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_tack_port)
            tackStbdBtn.text = sheet.getString(R.string.nautical_tack_stbd_short)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_tack_stbd)

            btnManeuverSec1.text = sheet.getString(R.string.nautical_gybe)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_gybe)
            btnManeuverSec2.text = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec3.text = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec4.text = sheet.getString(R.string.nautical_slip_exit)
            btnManeuverSec4.contentDescription = sheet.getString(R.string.nautical_slip_exit)
            btnManeuverSec4.visibility = View.VISIBLE

            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_weigh_anchor)
            btnManeuverSec6.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.visibility = View.VISIBLE
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
                    onInitiateManeuver(sheet.getString(R.string.nautical_heave_to), null) {
                        NauticalPlugin.autopilot?.heaveTo(port = true)
                        mm?.setActiveManeuver("heave_to")
                        mm?.execute()
                    }
                }
                2 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_docking_hold), null) {
                        NauticalPlugin.autopilot?.dockingHold()
                        mm?.setActiveManeuver("docking")
                        mm?.execute()
                    }
                }
                3 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_slip_exit), null) {
                        mm?.setActiveManeuver("slip_exit")
                        mm?.execute()
                    }
                }
                4 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
                        mm?.setActiveManeuver("weigh_anchor")
                        mm?.execute()
                    }
                }
                5 -> {
                    val targetDeg = (((curH - 10.0) % 360.0 + 360.0) % 360.0).toInt()
                    onInitiateManeuver(sheet.getString(R.string.nautical_dodge_port_label), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.dodge(port = true)
                    }
                }
                6 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
                        NauticalPlugin.autopilot?.emergencyStop()
                        mm?.abort("Emergency stop executed", isAlarm = true)
                    }
                }
            }
        } else {
            when (index) {
                1 -> {
                    // Gybe
                    val delta = (180.0 - 2.0 * abs(180.0 - abs(awaDeg))).coerceIn(70.0, 120.0)
                    val isPort = awaDeg > 0 // if wind is on starboard, gybe to port
                    val targetDeg = if (isPort) {
                        (((curH - delta) % 360.0 + 360.0) % 360.0).toInt()
                    } else {
                        (((curH + delta) % 360.0 + 360.0) % 360.0).toInt()
                    }
                    onInitiateManeuver(sheet.getString(R.string.nautical_gybe), targetDeg) { confirmedDeg ->
                        confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
                        NauticalPlugin.autopilot?.gybe(port = isPort)
                        mm?.setActiveManeuver(if (isPort) "gybe_port" else "gybe_stbd")
                        mm?.execute()
                    }
                }
                2 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_heave_to), null) {
                        NauticalPlugin.autopilot?.heaveTo(port = true)
                        mm?.setActiveManeuver("heave_to")
                        mm?.execute()
                    }
                }
                3 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_docking_hold), null) {
                        NauticalPlugin.autopilot?.dockingHold()
                        mm?.setActiveManeuver("docking")
                        mm?.execute()
                    }
                }
                4 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_slip_exit), null) {
                        mm?.setActiveManeuver("slip_exit")
                        mm?.execute()
                    }
                }
                5 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
                        mm?.setActiveManeuver("weigh_anchor")
                        mm?.execute()
                    }
                }
                6 -> {
                    onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
                        NauticalPlugin.autopilot?.emergencyStop()
                        mm?.abort("Emergency stop executed", isAlarm = true)
                    }
                }
            }
        }
    }
}
