package net.osmand.plus.plugins.nautical.quickaction

import androidx.annotation.Keep
import android.os.Bundle
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_NIGHT_VISION_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalNightVisionQuickAction : QuickAction {

    companion object {
        @JvmField
        val TYPE: QuickActionType = QuickActionType(NAUTICAL_NIGHT_VISION_ACTION_ID, "nautical.night_vision.toggle", NauticalNightVisionQuickAction::class.java)
            .nameRes(R.string.nautical_night_vision)
            .iconRes(R.drawable.ic_action_red_filter_overlay_on)
            .category(QuickActionType.CONFIGURE_SCREEN)
    }

    @Keep
    constructor() : super(TYPE)

    @Keep
    constructor(action: QuickAction) : super(action)

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        val plugin = NauticalPlugin.getInstance()
        plugin?.toggleNightVision(mapActivity)
    }
}
