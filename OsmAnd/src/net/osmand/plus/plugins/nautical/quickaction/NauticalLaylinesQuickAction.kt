package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import androidx.annotation.Keep
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_LAYLINES_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalLaylinesQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_LAYLINES_ACTION_ID, "nautical.laylines.toggle", NauticalLaylinesQuickAction::class.java)
            .nameRes(R.string.nautical_laylines_title)
            .iconRes(R.drawable.ic_action_sail_boat_dark)
            .category(QuickActionType.NAVIGATION)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(quickAction: QuickAction) : super(quickAction)

    @Keep
    constructor(type: Int) : super(TYPE)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed) return
        val app = mapActivity.app
        if (NauticalPlugin.getInstance() == null) {
            app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }

        val targetLat = params?.getDouble("latitude", 0.0) ?: 0.0
        val targetLon = params?.getDouble("longitude", 0.0) ?: 0.0

        if (targetLat != 0.0 && targetLon != 0.0) {
            val isCurrentTarget = app.settings.NAUTICAL_SHOW_LAYLINES.get() &&
                app.settings.NAUTICAL_TACTICAL_TARGET_LAT.get() == targetLat &&
                app.settings.NAUTICAL_TACTICAL_TARGET_LON.get() == targetLon
            if (isCurrentTarget) {
                app.settings.NAUTICAL_SHOW_LAYLINES.set(false)
                app.settings.NAUTICAL_TACTICAL_TARGET_LAT.set(0.0)
                app.settings.NAUTICAL_TACTICAL_TARGET_LON.set(0.0)
                app.showToastMessage(R.string.nautical_laylines_disabled)
            } else {
                app.settings.NAUTICAL_TACTICAL_TARGET_LAT.set(targetLat)
                app.settings.NAUTICAL_TACTICAL_TARGET_LON.set(targetLon)
                app.settings.NAUTICAL_SHOW_LAYLINES.set(true)
                app.showToastMessage(R.string.nautical_laylines_enabled_target)
            }
        } else {
            val isEnabled = app.settings.NAUTICAL_SHOW_LAYLINES.get()
            if (isEnabled) {
                app.settings.NAUTICAL_SHOW_LAYLINES.set(false)
                app.settings.NAUTICAL_TACTICAL_TARGET_LAT.set(0.0)
                app.settings.NAUTICAL_TACTICAL_TARGET_LON.set(0.0)
                app.showToastMessage(R.string.nautical_laylines_disabled)
            } else {
                app.settings.NAUTICAL_SHOW_LAYLINES.set(true)
                app.showToastMessage(R.string.nautical_laylines_enabled_target)
            }
        }
        app.osmandMap?.refreshMap()
    }
}
