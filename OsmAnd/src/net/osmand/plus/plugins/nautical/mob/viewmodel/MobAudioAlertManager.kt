package net.osmand.plus.plugins.nautical.mob.viewmodel

import android.content.Context
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter

/**
 * Emergency audio manager for MOB alerts.
 * Routes all requests through the NauticalAudioArbiter.
 */
class MobAudioAlertManager(private val context: Context) {
    private val arbiter = NauticalAudioArbiter.getInstance(context.applicationContext as OsmandApplication)

    /**
     * Starts the emergency alarm loop.
     */
    fun startAlarm() {
        arbiter.dispatchAlarm(AlarmType.MOB)
        
        // Post critical Android notification
        net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.notificationManager?.postCriticalNotification(
            "mob_emergency",
            context.getString(R.string.nautical_mob_label),
            context.getString(R.string.nautical_mob_emergency_message)
        )
    }

    /**
     * Stops the emergency alarm loop.
     */
    fun stopAlarm() {
        arbiter.stopAlarm(AlarmType.MOB)
        
        // Clear notification
        androidx.core.app.NotificationManagerCompat.from(context).cancel("mob_emergency".hashCode())
    }
}
