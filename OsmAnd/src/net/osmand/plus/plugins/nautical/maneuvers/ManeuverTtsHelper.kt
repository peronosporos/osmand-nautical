package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter

class ManeuverTtsHelper(private val app: OsmandApplication) : ManeuverManager.ManeuverStateListener {

    private val arbiter = NauticalAudioArbiter.getInstance(app)

    override fun onStateChanged(newState: ManeuverState) {
        val textId = when (newState) {
            ManeuverState.ARMED -> R.string.maneuver_armed
            ManeuverState.EXECUTING -> R.string.maneuver_executing
            ManeuverState.IDLE -> R.string.maneuver_aborted
        }
        
        // Phase 8.0R: Dispatch tactical maneuver state changes with high priority
        // to interrupt standard navigation prompts.
        arbiter.dispatchTts(app.getString(textId), AlarmType.TACTICAL_TACK)
    }
}
