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

    @Keep
    constructor(type: Int) : super(TYPE)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        executeMob(mapActivity, params)
    }

    fun execute(mapActivity: MapActivity) {
        executeMob(mapActivity, null)
    }

    private fun executeMob(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed) {
            return
        }
        mapActivity.layout?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        val plugin = PluginsHelper.getPlugin(NauticalPlugin::class.java)
        if (plugin != null) {
            val loc = mapActivity.app.locationProvider.lastKnownLocation
            if (loc != null) {
                plugin.mobViewModel?.triggerMob(
                    LatLon(loc.latitude, loc.longitude),
                    net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource.BUTTON,
                )
                mapActivity.app.showToastMessage(R.string.nautical_mob_triggered)
                mapActivity.app.osmandMap?.refreshMap()
            } else {
                mapActivity.app.showToastMessage(R.string.nautical_error_no_gps)
            }
        } else {
            mapActivity.app.showToastMessage(R.string.nautical_plugin_inactive)
        }
    }
}
