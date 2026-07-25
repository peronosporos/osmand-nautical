package net.osmand.plus.plugins.nautical.mob.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import net.osmand.PlatformUtil

/**
 * Emergency audio manager for MOB alerts.
 * Plays an escalating alarm loop using Android's alarm stream.
 */
class MobAudioAlertManager(private val context: Context) {
    private val log = PlatformUtil.getLog(MobAudioAlertManager::class.java)
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Starts the emergency alarm loop.
     * Uses TYPE_ALARM to ensure high priority and visibility.
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
            log.info("MOB Alarm started.")
        } catch (e: Exception) {
            log.error("Failed to start MOB alarm: ${e.message}")
        }
    }

    /**
     * Stops the emergency alarm loop.
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
            log.info("MOB Alarm stopped.")
        } catch (e: Exception) {
            log.error("Failed to stop MOB alarm: ${e.message}")
        }
    }
}
