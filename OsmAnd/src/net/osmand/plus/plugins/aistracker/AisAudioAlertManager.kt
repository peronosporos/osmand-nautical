package net.osmand.plus.plugins.aistracker

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import net.osmand.PlatformUtil

/**
 * Audio manager for AIS collision alerts.
 * Plays a recurring alarm loop when a dangerous target is detected.
 */
class AisAudioAlertManager(private val context: Context) {
    private val log = PlatformUtil.getLog(AisAudioAlertManager::class.java)
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Starts the collision alarm loop.
     */
    fun startAlarm() {
        if (mediaPlayer?.isPlaying == true) return

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            log.info("AIS Collision Alarm started.")
        } catch (e: Exception) {
            log.error("Failed to start AIS alarm: ${e.message}")
        }
    }

    /**
     * Stops the collision alarm loop.
     */
    fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            log.info("AIS Collision Alarm stopped.")
        } catch (e: Exception) {
            log.error("Failed to stop AIS alarm: ${e.message}")
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}
