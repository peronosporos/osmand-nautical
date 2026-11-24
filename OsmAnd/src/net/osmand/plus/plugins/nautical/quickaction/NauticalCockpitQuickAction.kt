package net.osmand.plus.plugins.nautical.quickaction

import android.content.Intent
import android.os.Bundle
import androidx.annotation.Keep
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.ui.cockpit.CockpitShellActivity
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_COCKPIT_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

/**
 * Opens the Cockpit shell: the full-screen surface that replaces the classical
 * cockpit screens (instruments pane today, helm/nav/VHF/watchkeeper follow).
 */
@Keep
class NauticalCockpitQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_COCKPIT_ACTION_ID, "nautical.cockpit.open", NauticalCockpitQuickAction::class.java)
            .nameRes(R.string.nautical_cockpit_label)
            .iconRes(R.drawable.ic_action_obd_speed)
            .category(QuickActionType.MAP_INTERACTIONS)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(action: QuickAction) : super(action)

    @Keep
    constructor(type: Int) : super(TYPE)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed) {
            return
        }
        mapActivity.startActivity(Intent(mapActivity, CockpitShellActivity::class.java))
    }
}
