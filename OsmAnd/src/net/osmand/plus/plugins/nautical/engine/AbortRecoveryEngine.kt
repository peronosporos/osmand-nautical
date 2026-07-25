package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.R
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication

/**
 * AbortRecoveryEngine handles graceful mid-maneuver aborts and failures,
 * preventing the boat from drifting in irons by executing safe recovery actions
 * (e.g. reverting autopilot setpoint or falling off to gather speed) and announcing via TTS.
 */
class AbortRecoveryEngine(
    private val app: OsmandApplication,
    private val autopilotController: AutopilotController
) {
    private val log = PlatformUtil.getLog(AbortRecoveryEngine::class.java)

    /**
     * Handle mid-maneuver abort or failure with safe recovery action.
     */
    fun executeRecovery(maneuverId: String, reason: String? = null) {
        val finalReason = reason ?: app.getString(R.string.nautical_user_abort)
        log.warn("Executing mid-maneuver abort recovery for $maneuverId due to: $finalReason")

        val isWindManeuver = maneuverId.contains("tack", ignoreCase = true) ||
                maneuverId.contains("gybe", ignoreCase = true)

        if (isWindManeuver) {
            // Revert autopilot or fall off to gather SOG
            val recoveryMessage = app.getString(R.string.nautical_recovery_tack_aborted)
            log.info(recoveryMessage)
            speak(recoveryMessage)
            
            // Adjust heading / fall off by 10 degrees to regain speed
            autopilotController.adjustHeading(10.0)
            autopilotController.setAutopilotMode("wind")
        } else {
            val recoveryMessage = app.getString(R.string.nautical_recovery_maneuver_aborted)
            log.info(recoveryMessage)
            speak(recoveryMessage)
            autopilotController.setAutopilotMode("standby")
        }
    }

    private fun speak(text: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }
}
