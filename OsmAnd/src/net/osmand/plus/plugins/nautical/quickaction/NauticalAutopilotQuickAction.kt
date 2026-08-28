package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import androidx.annotation.Keep
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalPilotBottomSheet
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_AUTOPILOT_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalAutopilotQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_AUTOPILOT_ACTION_ID, "nautical.autopilot.open", NauticalAutopilotQuickAction::class.java)
            .nameRes(R.string.nautical_pilot_title)
            .iconRes(R.drawable.ic_action_compass)
            .category(QuickActionType.NAVIGATION)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(action: QuickAction) : super(action)

    @Keep
    constructor(type: Int) : super(TYPE)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed || mapActivity.supportFragmentManager.isStateSaved) {
            return
        }
        mapActivity.layout?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        if (NauticalPlugin.getInstance() == null) {
            mapActivity.app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }
        NauticalPilotBottomSheet.show(mapActivity.supportFragmentManager)
    }
}
