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
                                _threatDetails.value = ThreatInfo(vesselName, 0.0, 0.0) // Specifics might be in message
                                triggerCollisionAlarmAudio(vesselName, 0.1)
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

    private fun evaluateBatteryHealth(batteries: Map<String, Battery>) {
        if (batteries.isEmpty()) return
        if (isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_LOW_BATTERY)) return

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
                val msg = String.format(Locale.US, "Low Battery: %s at %.1fV (%.0f%% SoC)", b.name ?: "House Bank", v, soc * 100)
                val notif = SignalKNotification(
                    message = msg,
                    state = NotificationState.ALARM,
                    methods = listOf("visual", "sound")
                )
                _activeCriticalNotifications.value = _activeCriticalNotifications.value + (SignalKPaths.NOTIFICATIONS_LOW_BATTERY to notif)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                    net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD,
                    voiceText = msg
                )
                break
            } else if (isStarter && v < starterMinV) {
                val msg = String.format(Locale.US, "Low Starter Battery: %s at %.1fV", b.name ?: "Starter Bank", v)
                val notif = SignalKNotification(
                    message = msg,
                    state = NotificationState.ALARM,
                    methods = listOf("visual", "sound")
                )
                _activeCriticalNotifications.value = _activeCriticalNotifications.value + (SignalKPaths.NOTIFICATIONS_LOW_BATTERY to notif)
                NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                    net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD,
                    voiceText = msg
                )
                break
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
        
        val isThreat = cpa > 0.0 && cpa < cpaThreshold && tcpa <= tcpaThreshold && tcpa > 0.0

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

    private fun triggerCollisionAlarmAudio(vesselName: String, cpa: Double) {
        val isCollSnoozed = isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK) || isAlarmSnoozed("collision") || isAlarmSnoozed("collision_danger")
        if (isCollSnoozed) return

        val message = app.getString(R.string.nautical_collision_audio_msg, vesselName, cpa)
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
