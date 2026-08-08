package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
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
    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0
    
    private val vesselLengthMeters: Double get() = app.settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get().toDouble()
    private val desiredScope: Double get() = app.settings.NAUTICAL_MED_MOORING_SCOPE.get().toDouble()

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

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
        val state = NauticalPlugin.engine?.getCurrentState()
        val depth = (state?.depthBelowTransducer ?: 5.0) + (state?.depthSurfaceToTransducer ?: 1.0)
        
        val dropDistance = depth * desiredScope + vesselLengthMeters
        speak("Med-mooring armed. Drop anchor at ${dropDistance.toInt()} meters from quay wall.")
        
        pushInstruction("Approaching Drop Zone")
        pushProgress(10)
        
        super.transitionToExecuting()
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return

        val distanceToQuayMeters = calculateDistanceToTarget(state) ?: 1000.0
        val sog = state.speedOverGround ?: 0.0
        val heading = state.headingTrue ?: 0.0
        val cog = state.courseOverGroundTrue ?: 0.0
        val leewayAngle = abs(Math.toDegrees(heading - cog))

        when (currentPhase) {
            MedMooringPhase.APPROACH_DROP_ZONE -> {
                val depth = (state.depthBelowTransducer ?: 5.0) + (state.depthSurfaceToTransducer ?: 1.0)
                val targetDropDistance = depth * desiredScope + vesselLengthMeters
                
                pushProgress(((1.0 - (distanceToQuayMeters / (targetDropDistance * 2)).coerceIn(0.0, 0.5)) * 100).toInt())

                if (distanceToQuayMeters <= targetDropDistance && sog < 1.0) {
                    anchorDropLat = state.latitude
                    anchorDropLon = state.longitude
                    currentPhase = MedMooringPhase.ANCHOR_DROP_PAYOUT
                    speak("Drop anchor now! Paying out rode.")
                    pushInstruction("Drop Anchor!")
                }
            }
            MedMooringPhase.ANCHOR_DROP_PAYOUT -> {
                // Payout rode phase before backing towards quay
                currentPhase = MedMooringPhase.STERN_APPROACH
                speak("Anchor set. Backing stern towards quay. Target reverse speed 0.5 to 1 knot.")
                pushInstruction("Stern Approach")
                
                // Active Helm Assistance: Maintain steady heading (180 deg from quay/wind)
                val currentHdg = state.headingTrue?.let { Math.toDegrees(it) } ?: 0.0
                NauticalPlugin.autopilot?.setTargetHeading(currentHdg)
                NauticalPlugin.autopilot?.setAutopilotMode("auto")
            }
            MedMooringPhase.STERN_APPROACH -> {
                pushProgress(((1.0 - (distanceToQuayMeters / 50.0).coerceIn(0.0, 1.0)) * 100).toInt())
                pushInstruction("Dist to Quay: ${distanceToQuayMeters.toInt()}m")
                // Safety Checks during Stern Approach
                if (leewayAngle > 15.0) {
                    speak("Leeway critical, apply bow thruster / forward burst.")
                }

                if (distanceToQuayMeters <= 10.0 && sog > 1.5) {
                    val warning = "Approach speed excessive near quay! Aborting med-mooring."
                    speak(warning)
                    transitionToAborted(warning)
                }

                // Auto completion
                if (distanceToQuayMeters < 2.0 && sog < 0.1) {
                    speak("Med-mooring completed. Secure lines.")
                    pushInstruction("Maneuver Completed")
                    pushProgress(100)
                    transitionToCompleted()
                }
            }
        }
    }

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        return net.osmand.shared.util.KMapUtils.getDistance(lat, lon, targetLat, targetLon)
    }

    private fun speak(text: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }
}
