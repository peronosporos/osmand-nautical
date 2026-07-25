package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import kotlin.math.abs

class MedMooringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    enum class MedMooringPhase {
        APPROACH_DROP_ZONE,
        ANCHOR_DROP_PAYOUT,
        STERN_APPROACH
    }

    private var currentPhase = MedMooringPhase.APPROACH_DROP_ZONE
    private var anchorDropLat: Double? = null
    private var anchorDropLon: Double? = null
    private var vesselLengthMeters: Double = 12.0 // default 12m boat
    private var desiredScope: Double = 5.0 // 5:1 scope

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false

        val depthBelow = state.depthBelowTransducer
        if (depthBelow == null) {
            val msg = "Med-Mooring blocked: Depth data unavailable."
            speak(msg)
            transitionToAborted(msg)
            return false
        }
        return true
    }

    override fun transitionToExecuting() {
        currentPhase = MedMooringPhase.APPROACH_DROP_ZONE
        val state = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
        val depth = (state?.depthBelowTransducer ?: 5.0) + (state?.depthSurfaceToTransducer ?: 1.0)
        
        val dropDistance = depth * desiredScope + vesselLengthMeters
        speak("Med-mooring armed. Drop anchor at ${dropDistance.toInt()} meters from quay wall.")
        super.transitionToExecuting()
    }

    fun updateTelemetry(state: MarineState, distanceToQuayMeters: Double) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val sog = state.speedOverGround ?: 0.0
        val heading = state.headingTrue ?: 0.0
        val cog = state.courseOverGroundTrue ?: 0.0
        val leewayAngle = abs(heading - cog)

        when (currentPhase) {
            MedMooringPhase.APPROACH_DROP_ZONE -> {
                val depth = (state.depthBelowTransducer ?: 5.0) + (state.depthSurfaceToTransducer ?: 1.0)
                val targetDropDistance = depth * desiredScope + vesselLengthMeters

                if (distanceToQuayMeters <= targetDropDistance && sog < 1.0) {
                    anchorDropLat = state.latitude
                    anchorDropLon = state.longitude
                    currentPhase = MedMooringPhase.ANCHOR_DROP_PAYOUT
                    speak("Drop anchor now! Paying out rode.")
                }
            }
            MedMooringPhase.ANCHOR_DROP_PAYOUT -> {
                // Payout rode phase before backing towards quay
                currentPhase = MedMooringPhase.STERN_APPROACH
                speak("Anchor set. Backing stern towards quay. Target reverse speed 0.5 to 1 knot.")
            }
            MedMooringPhase.STERN_APPROACH -> {
                // Safety Checks during Stern Approach
                if (leewayAngle > 15.0) {
                    speak("Leeway critical, apply bow thruster / forward burst.")
                }

                if (distanceToQuayMeters <= 10.0 && sog > 1.5) {
                    val warning = "Approach speed excessive near quay! Aborting med-mooring."
                    speak(warning)
                    transitionToAborted(warning)
                }
            }
        }
    }

    private fun speak(text: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }
}
