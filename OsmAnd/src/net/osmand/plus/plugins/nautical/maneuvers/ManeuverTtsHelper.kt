package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R

class ManeuverTtsHelper(private val app: OsmandApplication) : ManeuverManager.ManeuverStateListener {

    override fun onStateChanged(newState: ManeuverState) {
        val player = app.player ?: return
        val textId = when (newState) {
            ManeuverState.ARMED -> R.string.maneuver_armed
            ManeuverState.EXECUTING -> R.string.maneuver_executing
            ManeuverState.IDLE -> R.string.maneuver_aborted // Announce abort when returning to idle from non-idle
            else -> return
        }
        
        // Only announce IDLE if we were not already IDLE (though manager handles this)
        if (newState == ManeuverState.IDLE) {
            // We might want to be more specific if it was a success or abort
            // but for foundation we just say aborted if cancelled.
        }

        player.playCommands(player.newCommandBuilder().attention(app.getString(textId)))
    }
}
