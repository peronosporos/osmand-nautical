package net.osmand.plus.plugins.nautical.ui.widgets

import android.view.View
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.settings.enums.VesselType

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
    private val onConfirmManeuver: (label: String, action: () -> Unit) -> Unit
) {

    fun updateManeuverButtons(vesselType: VesselType) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            // Proa Boats: Shunt (180° Reversal), Center Rudder, Docking Hold
            tackPortBtn.text = sheet.getString(R.string.nautical_shunt_180_reversal)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_shunt_180_reversal)
            tackStbdBtn.text = sheet.getString(R.string.nautical_center_rudder)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_center_rudder)

            btnManeuverSec1.text = sheet.getString(R.string.nautical_shunt_180_reversal)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_shunt_180_reversal)
            btnManeuverSec2.text = sheet.getString(R.string.nautical_center_rudder)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_center_rudder)
            btnManeuverSec3.text = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec4.visibility = View.GONE
            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.visibility = View.GONE
        } else {
            // Conventional Boats: Tack Port, Tack Stbd, Gybe, Heave-To, Docking Hold
            tackPortBtn.text = sheet.getString(R.string.nautical_tack_port_label)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_tack_port_label)
            tackStbdBtn.text = sheet.getString(R.string.nautical_tack_stbd_label)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_tack_stbd_label)

            btnManeuverSec1.text = sheet.getString(R.string.nautical_gybe)
            btnManeuverSec1.contentDescription = sheet.getString(R.string.nautical_gybe)
            btnManeuverSec2.text = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec2.contentDescription = sheet.getString(R.string.nautical_heave_to)
            btnManeuverSec3.text = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec3.contentDescription = sheet.getString(R.string.nautical_docking_hold)
            btnManeuverSec4.text = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec4.contentDescription = sheet.getString(R.string.nautical_dodge_port_label)
            btnManeuverSec4.visibility = View.VISIBLE
            layoutSecondaryRow3.visibility = View.VISIBLE
            btnManeuverSec5.text = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec5.contentDescription = sheet.getString(R.string.nautical_dodge_stbd_label)
            btnManeuverSec6.text = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.contentDescription = sheet.getString(R.string.nautical_emergency_stop_label)
            btnManeuverSec6.visibility = View.VISIBLE
        }
    }

    fun handlePrimaryManeuver(vesselType: VesselType, state: MarineState?, isPort: Boolean) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            if (isPort) {
                onConfirmManeuver("SLIDE TO SHUNT (180° REVERSAL)") {
                    NauticalPlugin.autopilot?.shunt()
                }
            } else {
                onConfirmManeuver("SLIDE TO CENTER RUDDER") {
                    NauticalPlugin.autopilot?.setRudderAngle(0.0)
                }
            }
        } else {
            val label = if (isPort) "SLIDE TO TACK PORT (-100°)" else "SLIDE TO TACK STARBOARD (+100°)"
            onConfirmManeuver(label) {
                NauticalPlugin.autopilot?.tack(port = isPort)
            }
        }
    }

    fun handleSecondaryManeuver(index: Int, vesselType: VesselType) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            when (index) {
                1 -> onConfirmManeuver("SLIDE TO SHUNT (180° REVERSAL)") {
                    NauticalPlugin.autopilot?.shunt()
                }
                2 -> onConfirmManeuver("SLIDE TO CENTER RUDDER") {
                    NauticalPlugin.autopilot?.setRudderAngle(0.0)
                }
                3 -> onConfirmManeuver("SLIDE TO DOCKING HOLD") {
                    NauticalPlugin.autopilot?.dockingHold()
                }
                5 -> onConfirmManeuver("SLIDE TO EMERGENCY STOP") {
                    NauticalPlugin.autopilot?.emergencyStop()
                }
            }
        } else {
            when (index) {
                1 -> onConfirmManeuver("SLIDE TO GYBE") {
                    NauticalPlugin.autopilot?.gybe(port = true)
                }
                2 -> onConfirmManeuver("SLIDE TO HEAVE-TO") {
                    NauticalPlugin.autopilot?.heaveTo(port = true)
                }
                3 -> onConfirmManeuver("SLIDE TO DOCKING HOLD") {
                    NauticalPlugin.autopilot?.dockingHold()
                }
                4 -> onConfirmManeuver("SLIDE TO DODGE PORT (-10°)") {
                    NauticalPlugin.autopilot?.dodge(port = true)
                }
                5 -> onConfirmManeuver("SLIDE TO DODGE STARBOARD (+10°)") {
                    NauticalPlugin.autopilot?.dodge(port = false)
                }
                6 -> onConfirmManeuver("SLIDE TO EMERGENCY STOP") {
                    NauticalPlugin.autopilot?.emergencyStop()
                }
            }
        }
    }
}
