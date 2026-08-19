package net.osmand.plus.notifications

import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import net.osmand.plus.NavigationService
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity

class NauticalNotification(app: OsmandApplication) : OsmandNotification(app, GROUP_NAME) {

    companion object {
        const val GROUP_NAME = "NAUTICAL"
    }

    override fun getType(): NotificationType = NotificationType.NAUTICAL

    override fun getPriority(): Int = NotificationCompat.PRIORITY_LOW

    override fun isActive(): Boolean {
        val plugin = net.osmand.plus.plugins.PluginsHelper.getEnabledPlugin(net.osmand.plus.plugins.nautical.NauticalPlugin::class.java)
        return plugin?.isActive == true && app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(net.osmand.plus.settings.backend.ApplicationMode.BOAT)
    }

    override fun isUsedByService(service: Service?): Boolean {
        if (service is net.osmand.plus.plugins.nautical.NauticalBackgroundService) return true
        val navService = (service as? NavigationService) ?: app.navigationService
        return navService?.isUsedBy(NavigationService.USED_BY_NAUTICAL) == true
    }

    override fun getContentIntent(): Intent {
        val intent = Intent(app, MapActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        return intent
    }

    override fun buildNotification(service: Service?, wearable: Boolean): NotificationCompat.Builder? {
        if (!isEnabled(service) && service == null) {
            return null
        }
        icon = R.drawable.ic_notification_track
        ongoing = true
        val builder = createBuilder(wearable)
            .setContentTitle(app.getString(R.string.plugin_nautical_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        val state = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
        val subtext = if (state != null && (state.speedOverGround != null || state.courseOverGroundTrue != null || state.depthBelowTransducer != null)) {
            val speed = state.speedOverGround?.let { String.format(java.util.Locale.US, "%.1f kts", it * 1.943844) } ?: "--"
            val cog = state.courseOverGroundTrue?.let { String.format(java.util.Locale.US, "%03.0f°", Math.toDegrees(it)) } ?: "--"
            val depth = state.depthBelowTransducer?.let { String.format(java.util.Locale.US, "%.1fm", it) } ?: "--"
            "SOG: $speed | COG: $cog | Depth: $depth"
        } else {
            app.getString(R.string.nautical_receive_in_background)
        }

        builder.setStyle(NotificationCompat.BigTextStyle().bigText(subtext))
            .setContentText(subtext)

        val plugin = net.osmand.plus.plugins.PluginsHelper.getEnabledPlugin(net.osmand.plus.plugins.nautical.NauticalPlugin::class.java)
        if (plugin?.isNightVisionEnabled == true) {
            builder.color = 0xFFFF0000.toInt()
            builder.setColorized(true)
        }

        return builder
    }

    override fun getOsmandNotificationId(): Int = NAUTICAL_NOTIFICATION_SERVICE_ID

    override fun getOsmandWearableNotificationId(): Int = WEAR_NAUTICAL_NOTIFICATION_SERVICE_ID
}
