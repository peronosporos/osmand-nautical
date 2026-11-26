package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * AlarmPriorityManager enforces strict safety hierarchy:
 * If TCPA < 3 minutes and CPA < 0.5 NM, it pauses non-critical maneuver TTS announcements
 * and triggers high-priority AIS collision warnings, providing visual/audio override.
 */
class AlarmPriorityManager(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker,
) {
    private val log = PlatformUtil.getLog(AlarmPriorityManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isCollisionAlarmActive = MutableStateFlow(value = false)
    val isCollisionAlarmActive: StateFlow<Boolean> = _isCollisionAlarmActive.asStateFlow()

    private val _threatDetails = MutableStateFlow<ThreatInfo?>(null)
    val threatDetails: StateFlow<ThreatInfo?> = _threatDetails.asStateFlow()

    private val _activeCriticalNotifications = MutableStateFlow<Map<String, SignalKNotification>>(emptyMap())
    val activeCriticalNotifications = _activeCriticalNotifications.asStateFlow()

    private val snoozedAlarms = ConcurrentHashMap<String, Long>()

    data class ThreatInfo(
        val vesselName: String,
        val cpaNm: Double,
        val tcpaSeconds: Double,
    )

    init {
        startMonitoring()
    }

    fun snoozeAudioOnly(notificationKey: String, durationMs: Long = 120_000L) {
        log.info("AlarmPriorityManager: Muting audio only for $notificationKey for ${durationMs}ms")
        mapKeyToAlarmType(notificationKey)?.let { type ->
            NauticalAudioArbiter.getInstance(app).muteAlarm(type, durationMs)
        }
    }

    fun snoozeAlarm(notificationKey: String, durationMs: Long = 300_000L) {
        val expiry = System.currentTimeMillis() + durationMs
        snoozedAlarms[notificationKey] = expiry
        log.info("AlarmPriorityManager: Snoozing alarm $notificationKey for ${durationMs}ms")

        mapKeyToAlarmType(notificationKey)?.let { type ->
            NauticalAudioArbiter.getInstance(app).muteAlarm(type, durationMs)
        }

        refreshActiveNotifications()

        scope.launch {
            delay(durationMs)
            refreshActiveNotifications()
        }
    }

    fun acknowledgeAlarm(notificationKey: String) {
        snoozeAlarm(notificationKey, 300_000L)
    }

    fun isAlarmSnoozed(notificationKey: String): Boolean {
        val expiry = snoozedAlarms[notificationKey] ?: return false
        val now = System.currentTimeMillis()
        if (now >= expiry) {
            snoozedAlarms.remove(notificationKey)
            return false
        }
        return true
    }

    fun clearAlarmSnooze(notificationKey: String) {
        snoozedAlarms.remove(notificationKey)
        refreshActiveNotifications()
    }

    private fun mapKeyToAlarmType(key: String): net.osmand.plus.plugins.nautical.audio.AlarmType? {
        val lower = key.lowercase(Locale.US)
        return when {
            lower.contains("mob") -> net.osmand.plus.plugins.nautical.audio.AlarmType.MOB
            lower.contains("sart") -> net.osmand.plus.plugins.nautical.audio.AlarmType.AIS_SART
            lower.contains("dsc") -> net.osmand.plus.plugins.nautical.audio.AlarmType.DSC_DISTRESS
            lower.contains("collision") -> net.osmand.plus.plugins.nautical.audio.AlarmType.COLLISION_DANGER
            lower.contains("anchor") -> net.osmand.plus.plugins.nautical.audio.AlarmType.ANCHOR_DRIFT
            lower.contains("watchdog") -> net.osmand.plus.plugins.nautical.audio.AlarmType.SOLO_WATCHDOG
            lower.contains("depth") || lower.contains("shallow") -> net.osmand.plus.plugins.nautical.audio.AlarmType.SHALLOW_WATER
            lower.contains("hazard") || lower.contains("navtex") -> net.osmand.plus.plugins.nautical.audio.AlarmType.MAP_HAZARD
            lower.contains("battery") || lower.contains("actuator") -> net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD
            lower.contains("xte") -> net.osmand.plus.plugins.nautical.audio.AlarmType.XTE_NAVIGATION
            else -> null
        }
    }

    private fun startMonitoring() {
        scope.launch {
            try {
                dataBroker.marineState.map { it.notifications }
                    .distinctUntilChanged()
                    .collect { notifications ->
                        val critical = notifications.filter { (key, notif) ->
                            ((notif.state == NotificationState.ALARM) || (notif.state == NotificationState.EMERGENCY)) && !isAlarmSnoozed(key)
                        }
                        _activeCriticalNotifications.value = critical

                        // Task 4: Solo Watchdog Alert
                        val watchdog = notifications[SignalKPaths.NOTIFICATIONS_WATCHDOG]
                        if (watchdog != null && (watchdog.state == NotificationState.ALARM || watchdog.state == NotificationState.EMERGENCY) && !isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_WATCHDOG)) {
                            NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                                net.osmand.plus.plugins.nautical.audio.AlarmType.SOLO_WATCHDOG,
                                voiceText = app.getString(R.string.nautical_solo_watchdog_timeout)
                            )
                        }

                        // Task 2: Collision Risk from Plugin
                        val collisionRisk = notifications[SignalKPaths.NOTIFICATIONS_COLLISION_RISK]
                        if (collisionRisk != null && (collisionRisk.state == NotificationState.ALARM || collisionRisk.state == NotificationState.EMERGENCY) && !isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK)) {
                            if (!_isCollisionAlarmActive.value) {
                                _isCollisionAlarmActive.value = true
                                val vesselName = extractVesselName(collisionRisk.message)
                                // Honesty: use real CPA/TCPA from state when present.
                                // Previously fabricated ThreatInfo(0.0, 0.0) and announced
                                // "CPA 0.1 NM" in the voice message without any real data.
                                val stateNow = dataBroker.marineState.value
                                if (stateNow.cpa != null && stateNow.tcpa != null) {
                                    _threatDetails.value = ThreatInfo(vesselName, stateNow.cpa, stateNow.tcpa)
                                } else {
                                    _threatDetails.value = null
                                }
                                triggerCollisionAlarmAudio(vesselName, stateNow.cpa?.takeIf { it > 0.0 })
                            }
                        }
                    }
            } catch (e: Exception) {
                log.error("Notification monitoring error: ${e.message}")
            }
        }

        scope.launch {
            try {
                dataBroker.marineState.map { it.batteries }
                    .distinctUntilChanged()
                    .collect { batteries ->
                        evaluateBatteryHealth(batteries)
                    }
            } catch (e: Exception) {
                log.error("Battery safety monitoring error: ${e.message}")
            }
        }

        scope.launch {
            try {
                dataBroker.marineState.map { Pair(it.cpa, it.tcpa) }
                    .distinctUntilChanged()
                    .collect { (cpa, tcpa) ->
                    try {
                        if ((cpa != null) && (tcpa != null)) {
                            val vesselName = dataBroker.marineState.value.threatName ?: app.getString(R.string.nautical_target_vessel)
                            evaluateThreat(cpa, tcpa, vesselName)
                        }
                    } catch (e: Exception) {
                        log.error("Threat evaluation error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log.error("Collision monitoring loop error: ${e.message}")
            }
        }
    }

    @Volatile
    private var batteryAlarmActive = false

    private fun evaluateBatteryHealth(batteries: Map<String, Battery>) {
        if (batteries.isEmpty()) return

        // First breached battery (order preserved); null when all banks are healthy.
        var breachMsg: String? = null
        for ((_, b) in batteries) {
            val name = (b.name ?: "").lowercase(Locale.US)
            val v = b.voltage ?: continue
            val soc = b.stateOfCharge ?: 1.0

            val is24V = v > 16.0
            val houseMinV = if (is24V) 23.6 else 11.8
            val starterMinV = if (is24V) 24.0 else 12.0

            val isHouse = name.contains("house") || name.contains("service") || b.instance == "0"
            val isStarter = name.contains("starter") || name.contains("engine") || b.instance == "1"

            if (isHouse && (soc < 0.20 || v < houseMinV)) {
                breachMsg = app.getString(R.string.nautical_battery_low_fmt, b.name ?: b.instance, v, soc * 100)
                break
            } else if (isStarter && v < starterMinV) {
                breachMsg = app.getString(R.string.nautical_battery_low_starter_fmt, b.name ?: b.instance, v)
                break
            }
        }

        if (breachMsg != null) {
            val notif = SignalKNotification(
                message = breachMsg,
                state = NotificationState.ALARM,
                methods = listOf("visual", "sound")
            )
            _activeCriticalNotifications.value = _activeCriticalNotifications.value + (SignalKPaths.NOTIFICATIONS_LOW_BATTERY to notif)
            if (!batteryAlarmActive && !isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_LOW_BATTERY)) {
                // Rising edge only: repeated battery map emissions previously re-voiced
                // the alarm on every voltage sample.
                batteryAlarmActive = true
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                    net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD,
                    voiceText = breachMsg
                )
            } else if (isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_LOW_BATTERY)) {
                // Re-arm while snoozed so the post-snooze breach re-voices exactly once.
                batteryAlarmActive = false
            }
        } else if (batteryAlarmActive) {
            // Recovered: drop the synthetic alert and stop the tone unless another
            // actuator-class alarm (e.g. a server-side actuator overload) is still active.
            batteryAlarmActive = false
            _activeCriticalNotifications.value = _activeCriticalNotifications.value - SignalKPaths.NOTIFICATIONS_LOW_BATTERY
            val otherActuatorActive = dataBroker.marineState.value.notifications.any { (key, notif) ->
                mapKeyToAlarmType(key) == net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD &&
                    (notif.state == NotificationState.ALARM || notif.state == NotificationState.EMERGENCY)
            }
            if (!otherActuatorActive) {
                NauticalAudioArbiter.getInstance(app).stopAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD)
            }
        }
    }

    private fun evaluateThreat(cpa: Double, tcpa: Double, vesselName: String) {
        val isCollSnoozed = isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK) || isAlarmSnoozed("collision") || isAlarmSnoozed("collision_danger")
        if (isCollSnoozed) {
            if (_isCollisionAlarmActive.value) {
                _isCollisionAlarmActive.value = false
                _threatDetails.value = null
                NauticalAudioArbiter.getInstance(app).stopAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.COLLISION_DANGER)
                NauticalAudioArbiter.getInstance(app).stopAisProximityModulation()
            }
            return
        }

        val cpaThreshold = app.settings.NAUTICAL_AIS_CPA_WARNING_DISTANCE.get()
        val tcpaThreshold = app.settings.NAUTICAL_AIS_CPA_WARNING_TIME.get().toDouble()
        
        val isThreat = cpa >= 0.0 && cpa < cpaThreshold && tcpa <= tcpaThreshold && tcpa > 0.0

        if (isThreat) {
            NauticalAudioArbiter.getInstance(app).updateAisCollisionProximity(tcpa, cpa)
            if (!_isCollisionAlarmActive.value) {
                _isCollisionAlarmActive.value = true
                _threatDetails.value = ThreatInfo(vesselName, cpa, tcpa)
                log.error("COLLISION WARNING! Threat: $vesselName, CPA: ${cpa}NM, TCPA: ${tcpa}s")
                triggerCollisionAlarmAudio(vesselName, cpa)
            } else {
                _threatDetails.value = ThreatInfo(vesselName, cpa, tcpa)
            }
        } else {
            // Hysteresis: clear only if significantly safe
            if (_isCollisionAlarmActive.value && (cpa >= cpaThreshold * 1.2 || tcpa > tcpaThreshold * 1.1)) {
                _isCollisionAlarmActive.value = false
                _threatDetails.value = null
                log.info("Collision threat cleared.")
                NauticalAudioArbiter.getInstance(app).stopAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.COLLISION_DANGER)
                NauticalAudioArbiter.getInstance(app).stopAisProximityModulation()
            }
        }
    }

    fun refreshActiveNotifications() {
        val notifications = dataBroker.marineState.value.notifications
        val critical = notifications.filter { (key, notif) ->
            ((notif.state == NotificationState.ALARM) || (notif.state == NotificationState.EMERGENCY)) && !isAlarmSnoozed(key)
        }
        _activeCriticalNotifications.value = critical

        val isCollSnoozed = isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK) || isAlarmSnoozed("collision") || isAlarmSnoozed("collision_danger")
        if (isCollSnoozed) {
            if (_isCollisionAlarmActive.value) {
                _isCollisionAlarmActive.value = false
                _threatDetails.value = null
                NauticalAudioArbiter.getInstance(app).stopAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.COLLISION_DANGER)
            }
        } else {
            val cpa = dataBroker.marineState.value.cpa
            val tcpa = dataBroker.marineState.value.tcpa
            if (cpa != null && tcpa != null) {
                val vesselName = dataBroker.marineState.value.threatName ?: app.getString(R.string.nautical_target_vessel)
                evaluateThreat(cpa, tcpa, vesselName)
            }
        }
    }

    private fun triggerCollisionAlarmAudio(vesselName: String, cpa: Double?) {
        val isCollSnoozed = isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK) || isAlarmSnoozed("collision") || isAlarmSnoozed("collision_danger")
        if (isCollSnoozed) return

        // Only voice a CPA number when real data exists; otherwise use the generic
        // collision-danger phrase instead of inventing "0.1 NM".
        val message = if (cpa != null && cpa > 0.0) {
            app.getString(R.string.nautical_collision_audio_msg, vesselName, cpa)
        } else {
            app.getString(R.string.nautical_collision_danger)
        }
        NauticalAudioArbiter.getInstance(app).dispatchAlarm(
            net.osmand.plus.plugins.nautical.audio.AlarmType.COLLISION_DANGER,
            voiceText = message,
            loop = true
        )
    }

    private fun extractVesselName(message: String): String {
        // Plugin often formats message as "Collision risk with VesselName"
        val match = Regex("""with\s+([^,]+)""").find(message)
        return match?.groupValues?.get(1) ?: app.getString(R.string.nautical_target_vessel)
    }

    fun stop() {
        scope.cancel()
    }
}
