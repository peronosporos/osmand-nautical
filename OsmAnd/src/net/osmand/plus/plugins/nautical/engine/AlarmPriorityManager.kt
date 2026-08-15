package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter

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

    data class ThreatInfo(
        val vesselName: String,
        val cpaNm: Double,
        val tcpaSeconds: Double,
    )

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            try {
                dataBroker.marineState.map { it.notifications }
                    .distinctUntilChanged()
                    .collect { notifications ->
                        val critical = notifications.filter { 
                            (it.value.state == NotificationState.ALARM) || (it.value.state == NotificationState.EMERGENCY) 
                        }
                        _activeCriticalNotifications.value = critical

                        // Task 4: Solo Watchdog Alert
                        val watchdog = notifications[SignalKPaths.NOTIFICATIONS_WATCHDOG]
                        if (watchdog != null && (watchdog.state == NotificationState.ALARM || watchdog.state == NotificationState.EMERGENCY)) {
                            NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                                net.osmand.plus.plugins.nautical.audio.AlarmType.SOLO_WATCHDOG,
                                voiceText = app.getString(R.string.nautical_solo_watchdog_timeout)
                            )
                        }

                        // Task 2: Collision Risk from Plugin
                        val collisionRisk = notifications[SignalKPaths.NOTIFICATIONS_COLLISION_RISK]
                        if (collisionRisk != null && (collisionRisk.state == NotificationState.ALARM || collisionRisk.state == NotificationState.EMERGENCY)) {
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

    private fun evaluateThreat(cpa: Double, tcpa: Double, vesselName: String) {
        val cpaThreshold = app.settings.NAUTICAL_AIS_CPA_WARNING_DISTANCE.get()
        val tcpaThreshold = app.settings.NAUTICAL_AIS_CPA_WARNING_TIME.get().toDouble()
        
        val isThreat = cpa > 0.0 && cpa < cpaThreshold && tcpa <= tcpaThreshold && tcpa > 0.0

        if (isThreat) {
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
            }
        }
    }

    private fun triggerCollisionAlarmAudio(vesselName: String, cpa: Double) {
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
