package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.utils.OsmAndFormatter
import net.osmand.util.MapUtils

class DockingManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_docking
    override val iconRes: Int = R.drawable.ic_action_building
    override val isHighRisk: Boolean = false

    var targetLat: Double = 0.0
        private set
    var targetLon: Double = 0.0
        private set

    private var initialDistance: Double? = null
    private var lastTtsTime: Long = 0L
    var vesselLength: Float = 12.0f

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false
        
        return true
    }

    override fun transitionToArmed() {
        super.transitionToArmed()
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilot
        if (apm?.isEngaged() == true) {
            speak(app.getString(R.string.nautical_docking_ap_active))
        }
    }

    override fun transitionToExecuting() {
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilot
        if (apm?.isEngaged() == true) {
            apm.disengage()
            speak(app.getString(R.string.nautical_docking_ap_disengaged))
        }

        // Acquire helm lock
        val arbitrator = net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.getInstance(app)
        arbitrator.acquireLock(net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator.PRIORITY_TACTICAL_MANEUVER, app.getString(displayNameRes))

        super.transitionToExecuting()
        initialDistance = null
        speak(app.getString(R.string.nautical_docking_executing))
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val dist = calculateDistanceToTarget(state)
            if (dist == null) {
                transitionToAborted(app.getString(R.string.nautical_error_signal_lost))
                return
            }
            
            if (initialDistance == null) initialDistance = dist

            val sog = state.speedOverGround ?: 0.0
            
            // Item 4 Fix: Granular thresholds based on vessel size classes
            val speedLimit = when {
                vesselLength > 20 -> 1.0  // Super-yacht/Large commercial
                vesselLength > 15 -> 1.5  // Large yacht
                vesselLength > 10 -> 2.0  // Standard yacht
                else -> 2.5               // Small boat/Dinghy
            }
            
            val completionDist = when {
                vesselLength > 20 -> 12.0
                vesselLength > 15 -> 8.0
                vesselLength > 10 -> 5.0
                else -> 3.0
            }

            if (dist < 15.0 && sog > speedLimit) {
                transitionToAborted(app.getString(R.string.nautical_docking_speed_too_high))
                return
            }

            // Automatic completion when close and stationary
            if (dist < completionDist && sog < 0.2) {
                pushInstruction(app.getString(R.string.nautical_docking_successful))
                transitionToCompleted()
            } else {
                val now = System.currentTimeMillis()
                val distStr = OsmAndFormatter.getFormattedDistance(dist.toFloat(), app)
                val sogStr = OsmAndFormatter.getFormattedSpeed(sog.toFloat(), app)

                if (now - lastTtsTime > 5000L) {
                    pushInstruction(app.getString(R.string.nautical_docking_approaching, distStr, sogStr))
                    lastTtsTime = now
                }
                
                val progress = initialDistance?.let { init ->
                    if (init > 0.1) ((1.0 - (dist / init).coerceIn(0.0, 1.0)) * 100).toInt() else 100
                } ?: 0
                pushProgress(progress)
            }
        }
    }

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        return MapUtils.getDistance(lat, lon, targetLat, targetLon)
    }
}
