package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import java.util.concurrent.ConcurrentHashMap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.content.Context
import android.media.AudioManager

class NauticalNotificationManager(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(NauticalNotificationManager::class.java)
    private val processedNotifications = ConcurrentHashMap<String, NotificationState>()

    fun processNotifications(notifications: Map<String, SignalKNotification>) {
        notifications.forEach { (path, notification) ->
            val lastState = processedNotifications[path]
            if (lastState != notification.state) {
                // State changed or new notification
                if (notification.state == NotificationState.ALARM || notification.state == NotificationState.EMERGENCY) {
                    triggerAlert(path, notification)
                }
                processedNotifications[path] = notification.state
            }
        }

        // Clean up cleared notifications
        val iterator = processedNotifications.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!notifications.containsKey(entry.key)) {
                log.info("Notification cleared: ${entry.key}")
                iterator.remove()
            }
        }
    }

    private fun triggerAlert(path: String, notification: SignalKNotification) {
        log.error("SIGNAL K ALERT: [$path] ${notification.message} (State: ${notification.state})")
        
        val isCritical = notification.state == NotificationState.ALARM || notification.state == NotificationState.EMERGENCY

        app.runInUIThread {
            if (isCritical) {
                playCriticalAlarm()
            }

            // Audio alert via OsmAnd's player
            app.player?.let { player ->
                val priority = when (notification.state) {
                    NotificationState.EMERGENCY -> app.getString(R.string.nautical_notification_state_emergency)
                    NotificationState.ALARM -> app.getString(R.string.nautical_notification_state_alarm)
                    else -> app.getString(R.string.nautical_notification_state_warning)
                }
                val message = "$priority ${notification.message}"
                player.playCommands(player.newCommandBuilder().attention(message))
            }
            
            // Visual alert
            app.showToastMessage("${notification.state}: ${notification.message}")
        }
    }

    private fun playCriticalAlarm() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val ringtone = RingtoneManager.getRingtone(app, alarmUri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            // Ensure audible even if system volume is low (safety requirement)
            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (currentVolume < maxVolume / 2) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume / 2, 0)
            }

            ringtone.play()
        } catch (e: Exception) {
            log.error("Failed to play critical notification alarm", e)
        }
    }
}
