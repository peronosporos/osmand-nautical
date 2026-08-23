package net.osmand.plus.plugins.nautical.quickaction

import android.os.Bundle
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.quickaction.QuickAction
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_TACTICAL_PORT_PIN_ACTION_ID
import net.osmand.plus.quickaction.QuickActionIds.NAUTICAL_TACTICAL_STBD_PIN_ACTION_ID
import net.osmand.plus.quickaction.QuickActionType

@Keep
class NauticalTacticalStartPinQuickAction : QuickAction {

    var isPortPin: Boolean = true

    companion object {
        @JvmField
        val TYPE_PORT: QuickActionType = QuickActionType(NAUTICAL_TACTICAL_PORT_PIN_ACTION_ID, "nautical.start.port_pin", NauticalTacticalStartPinQuickAction::class.java)
            .nameRes(R.string.nautical_port_pin_title)
            .iconRes(R.drawable.ic_action_flag)
            .category(QuickActionType.NAVIGATION)

        @JvmField
        val TYPE_STBD: QuickActionType = QuickActionType(NAUTICAL_TACTICAL_STBD_PIN_ACTION_ID, "nautical.start.stbd_pin", NauticalTacticalStartPinQuickAction::class.java)
            .nameRes(R.string.nautical_stbd_pin_title)
            .iconRes(R.drawable.ic_action_flag)
            .category(QuickActionType.NAVIGATION)
    }

    @Keep
    constructor() : super(TYPE_PORT)

    @Keep
    constructor(quickAction: QuickAction) : super(quickAction) {
        if (quickAction is NauticalTacticalStartPinQuickAction) {
            this.isPortPin = quickAction.isPortPin
        } else if (quickAction.type == NAUTICAL_TACTICAL_STBD_PIN_ACTION_ID) {
            this.isPortPin = false
        }
    }

    @Keep
    constructor(type: Int) : super(if (type == NAUTICAL_TACTICAL_STBD_PIN_ACTION_ID) TYPE_STBD else TYPE_PORT) {
        this.isPortPin = (type != NAUTICAL_TACTICAL_STBD_PIN_ACTION_ID)
    }

    override fun execute(mapActivity: MapActivity, params: Bundle?) {
        if (mapActivity.isFinishing || mapActivity.isDestroyed) return
        val app = mapActivity.app
        val plugin = NauticalPlugin.getInstance()
        if (plugin == null) {
            app.showToastMessage(R.string.nautical_plugin_inactive)
            return
        }

        val startManager = plugin.tacticalStartManager
        if (startManager == null) {
            return
        }

        val loc = app.locationProvider.lastKnownLocation
        if (loc == null) {
            app.showToastMessage(R.string.nautical_error_no_gps)
            return
        }

        val isPort = (type == NAUTICAL_TACTICAL_PORT_PIN_ACTION_ID) || isPortPin
        val isPinSet = if (isPort) startManager.isPortPinSet() else startManager.isStarboardPinSet()

        if (!isPinSet) {
            if (isPort) {
                startManager.setPortPin(loc.latitude, loc.longitude)
                app.showToastMessage(R.string.nautical_port_pin_set)
            } else {
                startManager.setStarboardPin(loc.latitude, loc.longitude)
                app.showToastMessage(R.string.nautical_stbd_pin_set)
            }
        } else {
            if (isPort) {
                startManager.clearPortPin()
            } else {
                startManager.clearStarboardPin()
            }
            app.showToastMessage(R.string.nautical_pin_cleared)
        }
        app.osmandMap?.refreshMap()
    }
}
