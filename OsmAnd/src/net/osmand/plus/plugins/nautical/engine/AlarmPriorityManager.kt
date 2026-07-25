package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication

/**
 * AlarmPriorityManager enforces strict safety hierarchy:
 * If TCPA < 3 minutes and CPA < 0.5 NM, it pauses non-critical maneuver TTS announcements
 * and triggers high-priority AIS collision warnings, providing visual/audio override.
 */
class AlarmPriorityManager(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(AlarmPriorityManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isCollisionAlarmActive = MutableStateFlow(false)
    val isCollisionAlarmActive: StateFlow<Boolean> = _isCollisionAlarmActive.asStateFlow()

    private val _threatDetails = MutableStateFlow<ThreatInfo?>(null)
    val threatDetails: StateFlow<ThreatInfo?> = _threatDetails.asStateFlow()

    data class ThreatInfo(
        val vesselName: String,
        val cpaNm: Double,
        val tcpaSeconds: Double
    )

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            try {
                dataBroker.cpa.combine(dataBroker.tcpa) { cpa, tcpa ->
                    Pair(cpa, tcpa)
                }.collect { (cpa, tcpa) ->
                    try {
                        if (cpa != null && tcpa != null) {
                            val vesselName = dataBroker.threatName.value ?: app.getString(net.osmand.plus.R.string.nautical_target_vessel)
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
        // TCPA threshold: 3 minutes = 180 seconds
        // CPA threshold: 0.5 Nautical Miles
        val isThreat = cpa < 0.5 && tcpa <= 180.0 && tcpa >= 0.0

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
            if (_isCollisionAlarmActive.value && (cpa >= 0.6 || tcpa > 200.0)) {
                _isCollisionAlarmActive.value = false
                _threatDetails.value = null
                log.info("Collision threat cleared.")
            }
        }
    }

    private fun triggerCollisionAlarmAudio(vesselName: String, cpa: Double) {
        app.runInUIThread {
            app.player?.let { player ->
                val message = app.getString(net.osmand.plus.R.string.nautical_collision_audio_msg, vesselName, cpa)
                player.playCommands(player.newCommandBuilder().attention(message))
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
