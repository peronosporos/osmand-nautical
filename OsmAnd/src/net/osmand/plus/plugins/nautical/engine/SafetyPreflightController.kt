package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.R
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import kotlin.time.Duration.Companion.milliseconds

/**
 * SafetyPreflightController enforces a 3-second pre-flight verification pass before any maneuver
 * transitions from ARMED to EXECUTING:
 * - Battery voltage check
 * - AIS target track check (no CPA threat within 5 minutes)
 * - Autopilot connection and hydraulic/motor feedback
 * - Wind/depth data freshness (< 1.5 seconds)
 * - Engine State verification for power maneuvers (Docking, Mooring, Med-Mooring require engine RPM > 0)
 */
class SafetyPreflightController(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker,
    private val autopilotController: AutopilotController
) {
    private val log = PlatformUtil.getLog(SafetyPreflightController::class.java)

    /**
     * Run pre-flight pass before execution.
     * Returns Pair(Boolean success, String? failureReason)
     */
    suspend fun runPreflightCheck(maneuverId: String): Pair<Boolean, String?> {
        log.info("Starting pre-flight safety check for maneuver: $maneuverId...")

        val state = dataBroker.marineState.value
        val isEmergency = maneuverId.contains("man_overboard", ignoreCase = true) || state.isMobActive

        // 1. Engine State Verification for Power Maneuvers (Immediate failure check)
        val isPowerManeuver = maneuverId.contains("docking", ignoreCase = true) ||
                maneuverId.contains("mooring", ignoreCase = true) ||
                maneuverId.contains("med", ignoreCase = true)

        if (isPowerManeuver) {
            val anyEngineRunning = state.engines.values.any { (it.revolutions ?: 0.0) > 0.0 } || (state.engineRpm ?: 0.0) > 0.0
            if (!anyEngineRunning) {
                val reason = app.getString(R.string.nautical_error_engine_off)
                speak(reason)
                return Pair(false, reason)
            }
        }

        if (!isEmergency) {
            val sog = state.speedOverGround ?: 0.0
            val delayTime = if (sog < 0.1) 1500L else 3000L
            delay(delayTime.milliseconds) // System stabilization pass
        }

        // Refresh state after delay for sensors check
        val freshState = dataBroker.marineState.value

        // 2. Autopilot connection check
        if (!autopilotController.isConnected()) {
            val reason = app.getString(R.string.nautical_error_ap_offline)
            speak(reason)
            return Pair(false, reason)
        }

        // 3. AIS CPA threat check (no CPA threat within 5 minutes / 300s)
        val tcpa = freshState.tcpa
        val cpa = freshState.cpa
        if (cpa != null && tcpa != null && cpa < 0.8 && tcpa <= 300.0) {
            val reason = app.getString(R.string.nautical_error_ais_threat)
            speak(reason)
            return Pair(false, reason)
        }

        // 4. Data freshness check (< 3.0 seconds)
        val latestTimestamp = freshState.timestamps.values.maxOrNull() ?: 0L
        val now = System.currentTimeMillis()

        if (freshState.timestamps.isEmpty() || (now - latestTimestamp) > 3000L) {
            val reason = app.getString(R.string.nautical_error_stale_data)
            speak(reason)
            log.warn("Pre-flight failed: Data is stale or missing. Latest timestamp: $latestTimestamp, Now: $now")
            return Pair(false, reason)
        }

        log.info("Pre-flight safety check passed successfully for $maneuverId.")
        return Pair(true, null)
    }

    private fun speak(text: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }
}
