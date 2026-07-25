package net.osmand.plus.plugins.nautical

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.anchor.AnchorTrackBuffer
import net.osmand.plus.plugins.nautical.anchor.TrackPoint
import net.osmand.shared.util.KMapUtils

/**
 * Background watchdog for anchor drift detection.
 * Implements signal filtering and time-delayed trigger to avoid false alarms.
 */
class AnchorDriftWatchdog(private val app: OsmandApplication) {

    private val log = PlatformUtil.getLog(AnchorDriftWatchdog::class.java)
    private var alarmRingtone: Ringtone? = null
    private var outOfBoundsCount = 0
    private var isAlarmActive = false
    private var isGpsLostAlarmActive = false

    private val _trackHistory = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackHistory: StateFlow<List<TrackPoint>> = _trackHistory.asStateFlow()
    private val trackBuffer = AnchorTrackBuffer()

    companion object {
        private const val MAX_ACCURACY_METERS = 15.0
        private const val CONSECUTIVE_PINGS_THRESHOLD = 3
    }

    /**
     * Processes a new location update.
     * Returns true if alarm is triggered or remains active.
     */
    fun onLocationChanged(location: Location): Boolean {
        if (isGpsLostAlarmActive) {
            onGpsRestored()
        }
        
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get()
        val radius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()

        if (anchorLat == 0.0 || anchorLon == 0.0 || radius <= 0f) {
            reset()
            return false
        }

        // 1. Signal Filtering: Reject low accuracy pings (e.g. below deck or GPS bounce)
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) {
            log.info("AnchorWatch: Ignoring low accuracy fix: ${location.accuracy}m")
            return isAlarmActive
        }

        // Feed position to the Snail Trail buffer
        if (trackBuffer.addPosition(location)) {
            _trackHistory.value = trackBuffer.getPoints()
            // Trigger map refresh to show the new snail trail point
            app.osmandMap?.refreshMap()
        }

        val distance = KMapUtils.getDistance(anchorLat, anchorLon, location.latitude, location.longitude)

        if (distance > radius) {
            outOfBoundsCount++
            log.warn("AnchorWatch: Vessel outside boundary. Count: $outOfBoundsCount, Dist: ${distance.toInt()}m, Radius: ${radius.toInt()}m")
            
            // 2. Time-Delayed Trigger: Must be outside for 3 consecutive pings
            if (outOfBoundsCount >= CONSECUTIVE_PINGS_THRESHOLD && !isAlarmActive) {
                triggerAlarm()
            }
        } else {
            if (outOfBoundsCount > 0) {
                log.info("AnchorWatch: Vessel back in boundary. Resetting counter.")
                outOfBoundsCount = 0
            }
            if (isAlarmActive && distance < radius * 0.9) { // Small hysteresis to stop alarm if we moved back significantly
                stopAlarm()
            }
        }

        return isAlarmActive
    }

    fun onGpsLost() {
        if (!isGpsLostAlarmActive) {
            log.error("GPS SIGNAL LOST DURING ANCHOR WATCH!")
            isGpsLostAlarmActive = true
            triggerAlarm(app.getString(R.string.nautical_anchor_gps_lost_alarm))
        }
    }

    private fun onGpsRestored() {
        log.info("GPS signal restored during anchor watch")
        isGpsLostAlarmActive = false
        stopAlarm()
    }

    private fun triggerAlarm(customText: String? = null) {
        log.error("ANCHOR ALARM TRIGGERED: ${customText ?: "DRIFT"}")
        isAlarmActive = true
        
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val ringtone = RingtoneManager.getRingtone(app, alarmUri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            // Ensure maximum volume for critical safety alarm
            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            ringtone.play()
            alarmRingtone = ringtone
            
            app.player?.let { player ->
                val text = customText ?: app.getString(R.string.nautical_anchor_drift_alarm)
                player.playCommands(player.newCommandBuilder().attention(text))
            }
            
            // Wake screen via Plugin
            NauticalPlugin.getInstance()?.forceEmergencyBrightness()
        } catch (e: Exception) {
            log.error("Failed to play anchor alarm", e)
        }
    }

    fun stopAlarm() {
        if (isAlarmActive || isGpsLostAlarmActive) {
            log.info("Silencing anchor alarm")
            alarmRingtone?.stop()
            alarmRingtone = null
            isAlarmActive = false
            isGpsLostAlarmActive = false
            outOfBoundsCount = 0
        }
    }

    /**
     * Resets the out-of-bounds counter and snail trail buffer.
     * Called when the anchor position is manually updated to avoid false triggers
     * based on old positions.
     */
    fun resetCounter() {
        log.info("AnchorWatch: Resetting watchdog state for new anchor position.")
        outOfBoundsCount = 0
        // We keep the snail trail history as it shows where the vessel was, 
        // but we might want to clear it if the anchor drop point is completely new.
        // For now, just reset the counter.
    }

    fun reset() {
        stopAlarm()
        outOfBoundsCount = 0
    }
}
