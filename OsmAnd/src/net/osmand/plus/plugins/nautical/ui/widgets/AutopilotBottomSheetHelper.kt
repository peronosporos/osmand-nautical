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
    private val onInitiateManeuver: (title: String, targetHeading: Int?, onExecute: (targetHeading: Int?) -> Unit) -> Unit
) {

    fun updateManeuverButtons(vesselType: VesselType) {
        val isProa = vesselType == VesselType.PROA
        if (isProa) {
            tackPortBtn.text = sheet.getString(R.string.nautical_shunt)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_shunt)
            tackStbdBtn.text = sheet.getString(R.string.nautical_center_rudder)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_center_rudder)
        } else {
            tackPortBtn.text = sheet.getString(R.string.nautical_tack_port_short)
            tackPortBtn.contentDescription = sheet.getString(R.string.nautical_tack_port)
            tackStbdBtn.text = sheet.getString(R.string.nautical_tack_stbd_short)
            tackStbdBtn.contentDescription = sheet.getString(R.string.nautical_tack_stbd)
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

    fun handleGybe(isPort: Boolean, state: MarineState?) {
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        val awaDeg = state?.windDirectionApparent?.let { Math.toDegrees(it) } ?: 45.0
        val delta = (180.0 - 2.0 * abs(180.0 - abs(awaDeg))).coerceIn(70.0, 120.0)
        val targetDeg = if (isPort) {
            (((curH - delta) % 360.0 + 360.0) % 360.0).toInt()
        } else {
            (((curH + delta) % 360.0 + 360.0) % 360.0).toInt()
        }
        val title = if (isPort) sheet.getString(R.string.nautical_gybe_port_label) else sheet.getString(R.string.nautical_gybe_stbd_label)

        onInitiateManeuver(title, targetDeg) { confirmedDeg ->
            confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
            NauticalPlugin.autopilot?.gybe(port = isPort)
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver(if (isPort) "gybe_port" else "gybe_stbd")
            mm?.execute()
        }
    }

    fun handleHeaveTo() {
        onInitiateManeuver(sheet.getString(R.string.nautical_heave_to), null) {
            NauticalPlugin.autopilot?.heaveTo(port = true)
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("heave_to")
            mm?.execute()
        }
    }

    fun handleCenterRudder() {
        onInitiateManeuver(sheet.getString(R.string.nautical_center_rudder), null) {
            NauticalPlugin.autopilot?.setRudderAngle(0.0)
        }
    }

    fun handleDocking() {
        onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_docking), null) {
            NauticalPlugin.autopilot?.dockingHold()
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("docking")
            mm?.execute()
        }
    }

    fun handleMedMooring() {
        onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_med_mooring), null) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("med_mooring")
            mm?.execute()
        }
    }

    fun handleSlipExit() {
        onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_slip_exit), null) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("slip_exit")
            mm?.execute()
        }
    }

    fun handleMooringBuoy() {
        onInitiateManeuver(sheet.getString(R.string.nautical_maneuver_mooring), null) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("mooring")
            mm?.execute()
        }
    }

    fun handleDodge(isPort: Boolean, state: MarineState?) {
        val curH = (state?.targetHeading ?: state?.headingTrue)?.let { Math.toDegrees(it) } ?: 0.0
        val targetDeg = if (isPort) {
            (((curH - 10.0) % 360.0 + 360.0) % 360.0).toInt()
        } else {
            (((curH + 10.0) % 360.0 + 360.0) % 360.0).toInt()
        }
        val title = if (isPort) sheet.getString(R.string.nautical_dodge_port_label) else sheet.getString(R.string.nautical_dodge_stbd_label)

        onInitiateManeuver(title, targetDeg) { confirmedDeg ->
            confirmedDeg?.let { NauticalPlugin.autopilot?.setTargetHeading(it.toDouble()) }
            NauticalPlugin.autopilot?.dodge(port = isPort)
        }
    }

    fun handleWeighAnchor() {
        onInitiateManeuver(sheet.getString(R.string.nautical_weigh_anchor), null) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("weigh_anchor")
            mm?.execute()
        }
    }

    fun handleHoldingPattern() {
        onInitiateManeuver(sheet.getString(R.string.nautical_holding_pattern), null) {
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.setActiveManeuver("holding_pattern")
            mm?.execute()
        }
    }

    fun handleMicroSteer(deltaDeg: Double) {
        NauticalPlugin.autopilot?.adjustHeading(deltaDeg)
    }

    fun handleEmergencyStop() {
        onInitiateManeuver(sheet.getString(R.string.nautical_emergency_stop_label), null) {
            NauticalPlugin.autopilot?.emergencyStop()
            val mm = NauticalPlugin.getInstance()?.maneuverManager
            mm?.abort("Emergency stop executed", isAlarm = true)
        }
    }
}
