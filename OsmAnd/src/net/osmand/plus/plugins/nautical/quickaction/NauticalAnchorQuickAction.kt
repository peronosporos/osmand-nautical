package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_ANCHOR_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

class NauticalAnchorQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE = QuickActionType(NAUTICAL_ANCHOR_ACTION_ID, "nautical.anchor.toggle", NauticalAnchorQuickAction::class.java)
            .nameRes(R.string.nautical_anchor_label)
            .iconRes(R.drawable.ic_action_anchor)
            .category(QuickActionType.MAP_INTERACTIONS)
    }

    constructor() : super(TYPE)
    constructor(quickAction: QuickAction) : super(quickAction)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        val app = mapActivity.app
        val lat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        if (lat == 0.0) {
            val loc = app.locationProvider.lastKnownLocation
            if (loc != null) {
                app.settings.NAUTICAL_ANCHOR_LAT.set(loc.latitude)
                app.settings.NAUTICAL_ANCHOR_LON.set(loc.longitude)
                app.showToastMessage(R.string.nautical_anchor_set)
            } else {
                app.showToastMessage(R.string.nautical_error_no_gps)
            }
        } else {
            app.settings.NAUTICAL_ANCHOR_LAT.set(0.0)
            app.settings.NAUTICAL_ANCHOR_LON.set(0.0)
            app.showToastMessage(R.string.nautical_anchor_cleared)
        }
        app.osmandMap?.refreshMap()
    }
}
