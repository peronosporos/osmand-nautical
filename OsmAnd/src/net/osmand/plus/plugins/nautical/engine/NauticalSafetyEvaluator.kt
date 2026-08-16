package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.settings.enums.VesselContext
import java.util.Locale
import kotlin.math.abs

class NauticalSafetyEvaluator(
    private val app: OsmandApplication,
    private val safetyAlertService: NauticalSafetyAlertService?
) {
    var isConnectionLostAlertActive = false
        internal set

    var isBatteryAlertActive = false
        internal set

    var lastAutopilotState: String? = null

    fun evaluateVesselSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>,
        safetyManager: NauticalSafetyManager?
    ) {
        if (!state.hasValidFix) return
        checkOffCourseAlert(state, notifications)
        checkDepthSafety(state, notifications, safetyManager)
        checkAccidentalGybeAlert(state, notifications)
        safetyAlertService?.processSafetyUpdate(state, notifications)
    }

    fun checkConnectionSafety(state: MarineState, onRequestRefresh: () -> Unit) {
        val wasEngaged = (lastAutopilotState != null) && (lastAutopilotState?.uppercase(Locale.US) != "STANDBY")
        val isDisconnected = (state.connectionStatus == ConnectionStatus.DISCONNECTED) || (state.connectionStatus == ConnectionStatus.STALE)

        if (wasEngaged && isDisconnected) {
            if (!isConnectionLostAlertActive) {
                isConnectionLostAlertActive = true
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                    AlarmType.SOLO_WATCHDOG,
                    voiceText = app.getString(R.string.nautical_sk_connection_lost),
                    loop = true
                )
                onRequestRefresh()
            }
            lastAutopilotState = "STANDBY"
        } else if (!isDisconnected && isConnectionLostAlertActive) {
            isConnectionLostAlertActive = false
            NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.SOLO_WATCHDOG)
            app.runInUIThread {
                app.showToastMessage(R.string.nautical_connection_restored)
                onRequestRefresh()
            }
        }
    }

    fun checkEmergencyPower(state: MarineState) {
        var lowBatteryDetected = false
        val systemVoltage = app.settings.NAUTICAL_BATTERY_SYSTEM_VOLTAGE.get().voltage
        val threshold = systemVoltage * 0.916

        state.batteries.values.forEach { b ->
            val v = b.voltage ?: 0.0
            if (v in 0.1..threshold) {
                lowBatteryDetected = true
                if (!isBatteryAlertActive) {
                    NauticalPlugin.hudManager?.get()?.showBanner(app.getString(R.string.nautical_emergency_power_low), 30000L, isWarning = true)
                    NauticalAudioArbiter.getInstance(app).dispatchTts(app.getString(R.string.nautical_critical_low_battery), AlarmType.TTS_INSTRUCTION)
                }
            }
        }
        isBatteryAlertActive = lowBatteryDetected
    }

    private fun checkOffCourseAlert(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        if (state.isShunted) {
            notifications["navigation.state.shunted"] = SignalKNotification(
                app.getString(R.string.nautical_shunting_maneuver_active),
                NotificationState.NORMAL
            )
        }
        if (state.isOffCourse) {
            val msg = app.getString(R.string.nautical_off_course_alert)
            notifications["navigation.offCourse"] = SignalKNotification(msg, NotificationState.ALARM)
        }
    }

    private fun checkAccidentalGybeAlert(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        val awa = state.windDirectionApparent ?: return
        val awaDeg = Math.toDegrees(awa)

        if (abs(awaDeg) > 172.0) {
            val msg = app.getString(R.string.nautical_alarm_accidental_gybe)
            notifications["safety.alarm.gybe"] = SignalKNotification(msg, NotificationState.ALARM)
        }
    }

    private fun checkDepthSafety(
        state: MarineState,
        notifications: MutableMap<String, SignalKNotification>,
        safetyManager: NauticalSafetyManager?
    ) {
        val depth = state.depthBelowKeel ?: return
        val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)

        val safeMin = sm.getMinSafeDepth()
        val shallowThreshold = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        if (depth < shallowThreshold) {
            notifications["safety.depth.shallow"] = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_keel, depth),
                state = NotificationState.EMERGENCY
            )
        } else if (depth < safeMin) {
            notifications["safety.depth.warning"] = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_contour, depth),
                state = NotificationState.WARN
            )
        }
    }

    fun isVesselOnPassage(engine: SignalKEngine?): Boolean {
        val context = app.settings.NAUTICAL_VESSEL_CONTEXT.get()
        if (context == VesselContext.SAILING || context == VesselContext.MOTORING || context == VesselContext.EMERGENCY_HEAVE_TO) {
            return true
        }
        val state = engine?.getCurrentState()
        val sog = state?.speedOverGround ?: 0.0
        return (sog > 0.25) || (engine?.isFollowingRoute == true)
    }
}
