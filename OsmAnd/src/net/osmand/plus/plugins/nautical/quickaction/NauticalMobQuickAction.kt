package net.osmand.plus.plugins.nautical.quickaction

import androidx.annotation.Keep
import android.os.Bundle
import net.osmand.data.LatLon
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_MOB_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalMobQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_MOB_ACTION_ID, "nautical.mob.trigger", NauticalMobQuickAction::class.java)
            .nameRes(R.string.nautical_mob_label)
            .iconRes(R.drawable.ic_action_alert)
            .category(QuickActionType.MAP_INTERACTIONS)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(action: QuickAction) : super(action)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed) {
            return
        }
        val plugin = PluginsHelper.getPlugin(NauticalPlugin::class.java)
        if (plugin != null) {
            val loc = mapActivity.app.locationProvider.lastKnownLocation
            if (loc != null) {
                plugin.mobViewModel?.triggerMob(
                    LatLon(loc.latitude, loc.longitude),
                    net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource.BUTTON,
                )
            } else {
                mapActivity.app.showToastMessage(R.string.nautical_error_no_gps)
            }
        }
    }
}
