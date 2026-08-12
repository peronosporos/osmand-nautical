package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.shared.util.KMapUtils

class MooringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_mooring
    override val iconRes: Int = R.drawable.ic_action_anchor
    override val isHighRisk: Boolean = false

    private var targetLat: Double = Double.NaN
    private var targetLon: Double = Double.NaN

    private val vesselLengthMeters: Double get() = app.settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get().toDouble()

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

    override fun transitionToArmed() {
        super.transitionToArmed()
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilot
        if (apm?.state?.value != "standby") {
            speak("Autopilot active. Disengage before approach.")
        }
    }

    override fun transitionToExecuting() {
        val apm = net.osmand.plus.plugins.nautical.NauticalPlugin.autopilot
        if (apm?.state?.value != "standby") {
            apm?.disengage()
            speak("Autopilot disengaged for approach.")
        }

        super.transitionToExecuting()
        speak("Mooring maneuver executing. Monitor distance and speed.")
    }

    override fun onStateUpdate(state: MarineState) {
        if (targetLat.isNaN() || targetLon.isNaN()) return
        if (currentState == ManeuverStateMachine.State.EXECUTING) {
            val dist = calculateDistanceToTarget(state) ?: 100.0
            val sog = state.speedOverGround ?: 0.0
            
            val sogKnots = sog * 1.94384
            
            val speedLimitThreshold = vesselLengthMeters
            val approachThreshold = vesselLengthMeters * 3.0
            val completionThreshold = (vesselLengthMeters * 0.3).coerceAtLeast(2.0)

            if (dist < speedLimitThreshold && sogKnots > 2.5) {
                transitionToAborted("Speed too high for mooring: ${sogKnots.format(1)} kn")
                return
            }

            // Auto completion: within scaled threshold and stopped
            if (dist < completionThreshold && sogKnots < 0.2) {
                pushInstruction("Mooring Completed")
                pushProgress(100)
                transitionToCompleted()
            } else if (dist < approachThreshold) {
                pushInstruction(app.getString(net.osmand.plus.R.string.nautical_distance_to_target, dist.toInt()))
                val progress = ((1.0 - (dist / approachThreshold).coerceIn(0.0, 1.0)) * 100).toInt()
                pushProgress(progress)
            }
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        return KMapUtils.getDistance(lat, lon, targetLat, targetLon)
    }
}
