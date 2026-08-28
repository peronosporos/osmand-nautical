package net.osmand.plus.plugins.nautical.quickaction

import androidx.annotation.Keep
import android.os.Bundle
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.routing.ui.PassagePlanBottomSheet
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_PASSAGE_PLAN_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalPassagePlanQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_PASSAGE_PLAN_ACTION_ID, "nautical.passage_plan.open", NauticalPassagePlanQuickAction::class.java)
            .nameRes(R.string.nautical_passage_plan_title)
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
        if (mapActivity.isFinishing || mapActivity.isDestroyed) {
            return
        }
        mapActivity.layout?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        val app = mapActivity.app
        val plugin = PluginsHelper.getPlugin(NauticalPlugin::class.java)
        if (plugin == null) {
            app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }

        PassagePlanBottomSheet.show(mapActivity.supportFragmentManager)
    }
}
