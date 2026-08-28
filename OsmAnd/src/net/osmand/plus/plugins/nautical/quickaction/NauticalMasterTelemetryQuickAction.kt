package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import androidx.annotation.Keep
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.MasterTelemetryBottomSheet
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_MASTER_TELEMETRY_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalMasterTelemetryQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_MASTER_TELEMETRY_ACTION_ID, "nautical.master_telemetry.open", NauticalMasterTelemetryQuickAction::class.java)
            .nameRes(R.string.nautical_master_telemetry)
            .iconRes(R.drawable.ic_action_info)
            .category(QuickActionType.INTERFACE)
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
        mapActivity.mapView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        if (NauticalPlugin.getInstance() == null) {
            mapActivity.app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }
        MasterTelemetryBottomSheet.show(mapActivity.supportFragmentManager)
    }
}
