package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import net.osmand.data.LatLon
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.plugin.SailingIntegrationPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_MOB_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

class NauticalMobQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE = QuickActionType(NAUTICAL_MOB_ACTION_ID, "nautical.mob.trigger", NauticalMobQuickAction::class.java)
            .nameRes(R.string.nautical_mob_label)
            .iconRes(R.drawable.ic_action_alert)
            .category(QuickActionType.MAP_INTERACTIONS)
    }

    constructor() : super(TYPE)
    constructor(quickAction: QuickAction) : super(quickAction)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        val plugin = PluginsHelper.getPlugin(SailingIntegrationPlugin::class.java)
        if (plugin != null) {
            val loc = mapActivity.app.locationProvider.lastKnownLocation
            if (loc != null) {
                plugin.mobViewModel?.triggerMob(LatLon(loc.latitude, loc.longitude))
            } else {
                mapActivity.app.showToastMessage(R.string.nautical_error_no_gps)
            }
        }
    }
}
