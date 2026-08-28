package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import androidx.annotation.Keep
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_SAIL_INVENTORY_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType

@Keep
class NauticalSailInventoryQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_SAIL_INVENTORY_ACTION_ID, "nautical.sail_inventory.open", NauticalSailInventoryQuickAction::class.java)
            .nameRes(R.string.nautical_sail_inventory)
            .iconRes(R.drawable.ic_action_sail_boat_dark)
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
        if (NauticalPlugin.getInstance() == null) {
            mapActivity.app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }
        BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.SAIL_INVENTORY)
    }
}
