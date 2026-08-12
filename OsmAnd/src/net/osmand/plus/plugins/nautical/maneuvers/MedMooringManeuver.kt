package net.osmand.plus.plugins.nautical.maneuvers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MedMooringManeuver(app: OsmandApplication) : ManeuverEngine(app) {

    override val displayNameRes: Int = R.string.nautical_maneuver_med_mooring
    override val iconRes: Int = R.drawable.ic_action_anchor
    override val isHighRisk: Boolean = false

    enum class MedMooringPhase {
        APPROACH_DROP_ZONE,
        ANCHOR_DROP,
        PAYOUT_RODE,
        STERN_APPROACH
    }

    private var currentPhase = MedMooringPhase.APPROACH_DROP_ZONE
    var anchorDropLat: Double = Double.NaN
    var anchorDropLon: Double = Double.NaN
    var targetLat: Double = Double.NaN
    var targetLon: Double = Double.NaN
    
    private var initialApMode: String? = null
    private var phaseStartTime: Long = 0
    private var helmOverrideListener: kotlinx.coroutines.Job? = null

    override val maneuverTimeoutMs: Long = 300000L // 5 minutes for Med-mooring

    private val vesselLengthMeters: Double get() = app.settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get().toDouble()
    private val desiredScope: Double get() = app.settings.NAUTICAL_MED_MOORING_SCOPE.get().toDouble()

    fun setTarget(lat: Double, lon: Double) {
        targetLat = lat
        targetLon = lon
    }

    override fun checkSafetyPreconditions(state: MarineState): Boolean {
        if (!super.checkSafetyPreconditions(state)) return false

        val depthBelow = state.depthBelowKeel ?: state.depthBelowTransducer
        if (depthBelow == null) {
            speak(app.getString(R.string.nautical_med_mooring_depth_warning))
        } else {
            val draft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
            if (depthBelow < (draft + 0.5)) { // 0.5m safety margin
                val msg = app.getString(R.string.nautical_med_mooring_depth_unsafe)
                speak(msg)
                transitionToAborted(msg)
                return false
            }
        }
        return true
    }

    override fun transitionToExecuting() {
        currentPhase = MedMooringPhase.APPROACH_DROP_ZONE
        val state = NauticalPlugin.engine?.getCurrentState()
        
        // Item 7: Proactively acknowledge shallow water alarms as this is a deliberate maneuver
        NauticalPlugin.engine?.acknowledgeNotification("safety.depth.warning")
        NauticalPlugin.engine?.acknowledgeNotification("safety.depth.shallow")

        // Save initial autopilot state for restoration
        initialApMode = NauticalPlugin.autopilot?.state?.value

        val currentTideHeight = state?.tide?.heightNow ?: 0.0
        var tideRise = state?.tide?.heightNow ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()

        // Automated Tide-Awareness
        state?.tide?.let { tide ->
            val nextHeight = tide.nextExtremeHeight
            if (tide.nextExtremeType == "High" && nextHeight != null && nextHeight > currentTideHeight) {
                if (nextHeight - currentTideHeight > 0.1) {
                    tideRise = nextHeight
                    val (valTide, unitTide) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(app, app.settings, nextHeight, "depth")
                    speak(app.getString(R.string.nautical_warn_tide_rising, valTide, unitTide))
                }
            }
        }

        val depth = (state?.depthBelowTransducer ?: 5.0) + (state?.depthSurfaceToTransducer ?: 1.0)
        
        val dropDistance = (depth + (tideRise - currentTideHeight)) * desiredScope + vesselLengthMeters
        speak(app.getString(R.string.nautical_med_mooring_armed_msg, dropDistance.toInt()))
        
        pushInstruction(app.getString(R.string.nautical_med_mooring_approaching))
        pushProgress(0)
        
        setupHelmOverrideListener()
        super.transitionToExecuting()
    }

    private fun setupHelmOverrideListener() {
        helmOverrideListener?.cancel()
        val broker = NauticalPlugin.engine?.dataBroker ?: return
        helmOverrideListener = CoroutineScope(Dispatchers.Main).launch {
            broker.manualOverrideTriggered.collect {
                transitionToAborted("Manual helm override detected")
            }
        }
    }

    override fun onStateUpdate(state: MarineState) {
        if (currentState != ManeuverStateMachine.State.EXECUTING) return
        if (targetLat.isNaN() || targetLon.isNaN()) return

        val distanceToQuayMeters = calculateDistanceToTarget(state)
        if (distanceToQuayMeters == null) {
            pushInstruction(app.getString(R.string.nautical_offline_status)) // Reusing existing offline string for GPS loss
            return
        }

        val sog = state.speedOverGround ?: 0.0
        val sogKnots = sog * 1.94384
        val heading = state.headingTrue ?: 0.0
        val cog = state.courseOverGroundTrue ?: 0.0
        val leewayAngle = abs(Math.toDegrees(heading - cog))
        
        // Stern-way detection: COG is roughly 180 degrees from heading
        var angleDiff = abs(Math.toDegrees(cog - heading))
        while (angleDiff > 180) angleDiff = 360 - angleDiff
        val isReversing = angleDiff > 135.0 && sogKnots > 0.1

        // Item 5: Refined Anchor Dragging Detection
        if (!anchorDropLat.isNaN() && !anchorDropLon.isNaN() && (currentPhase == MedMooringPhase.PAYOUT_RODE || currentPhase == MedMooringPhase.STERN_APPROACH)) {
            val distFromAnchor = KMapUtils.getDistance(state.latitude ?: 0.0, state.longitude ?: 0.0, anchorDropLat, anchorDropLon)
            val depth = (state.depthBelowTransducer ?: 5.0) + (state.depthSurfaceToTransducer ?: 1.0)
            
            // Account for vessel length and potential GPS inaccuracy (2m)
            val maxAllowedDist = (depth * desiredScope) + vesselLengthMeters + 2.0
            if (distFromAnchor > maxAllowedDist * 1.2) { // 20% buffer for catenary stretch
                speak(app.getString(R.string.nautical_warn_anchor_dragging))
            }
        }

        when (currentPhase) {
            MedMooringPhase.APPROACH_DROP_ZONE -> {
                val depth = (state.depthBelowTransducer ?: 5.0) + (state.depthSurfaceToTransducer ?: 1.0)
                val targetDropDistance = depth * desiredScope + vesselLengthMeters
                
                val progress = ((1.0 - (distanceToQuayMeters / (targetDropDistance * 1.5)).coerceIn(0.0, 1.0)) * 50).toInt()
                pushProgress(progress)

                if (distanceToQuayMeters <= targetDropDistance && sogKnots < 1.0) {
                    anchorDropLat = state.latitude ?: Double.NaN
                    anchorDropLon = state.longitude ?: Double.NaN
                    currentPhase = MedMooringPhase.ANCHOR_DROP
                    phaseStartTime = System.currentTimeMillis()
                    speak(app.getString(R.string.nautical_med_mooring_drop_anchor_now))
                    pushInstruction(app.getString(R.string.nautical_med_mooring_drop_anchor_now))
                }
            }
            MedMooringPhase.ANCHOR_DROP -> {
                // Wait for anchor to hit bottom OR boat to start reversing
                if (isReversing || System.currentTimeMillis() - phaseStartTime > 10000) {
                    currentPhase = MedMooringPhase.PAYOUT_RODE
                    speak(app.getString(R.string.nautical_med_mooring_anchor_away))
                    pushInstruction(app.getString(R.string.nautical_med_mooring_paying_out))
                }
            }
            MedMooringPhase.PAYOUT_RODE -> {
                val depth = (state.depthBelowTransducer ?: 5.0) + (state.depthSurfaceToTransducer ?: 1.0)
                val targetRode = depth * desiredScope
                
                state.rodeDeployed?.let { rode ->
                    pushInstruction(app.getString(R.string.nautical_med_mooring_rode_payout_format, rode, targetRode))
                }

                if (isReversing) {
                    currentPhase = MedMooringPhase.STERN_APPROACH
                    speak(app.getString(R.string.nautical_med_mooring_anchor_set))
                    pushInstruction(app.getString(R.string.nautical_med_mooring_stern_approach))
                    
                    // Active Helm Assistance: Maintain perpendicular heading to quay
                    val quayBearing = calculateBearingToTarget(state)
                    if (quayBearing != null) {
                        val approachHeading = (quayBearing + 180.0) % 360.0
                        // Item 3 Fix: Convert Degrees to Radians for Autopilot
                        NauticalPlugin.autopilot?.setTargetHeading(Math.toRadians(approachHeading))
                        NauticalPlugin.autopilot?.setAutopilotMode("auto")
                    }
                }
            }
            MedMooringPhase.STERN_APPROACH -> {
                val completionThreshold = (vesselLengthMeters * 0.2).coerceAtLeast(2.0)
                val speedLimitThreshold = vesselLengthMeters

                pushProgress((50 + (1.0 - (distanceToQuayMeters / 50.0).coerceIn(0.0, 1.0)) * 50).toInt())
                pushInstruction(app.getString(R.string.nautical_distance_to_quay, distanceToQuayMeters.toInt()))
                
                if (leewayAngle > 15.0) {
                    speak(app.getString(R.string.nautical_med_mooring_leeway_warning))
                }

                if (distanceToQuayMeters <= speedLimitThreshold && sogKnots > 1.5) {
                    val warning = app.getString(R.string.nautical_med_mooring_speed_abort)
                    NauticalAudioArbiter.getInstance(app).dispatchAlarm(AlarmType.AUTOPILOT_COMMAND_REJECTED, voiceText = warning)
                    transitionToAborted(warning)
                }

                // Auto completion
                if (distanceToQuayMeters < completionThreshold && sogKnots < 0.2) {
                    speak(app.getString(R.string.nautical_med_mooring_completed))
                    pushInstruction(app.getString(R.string.nautical_med_mooring_maneuver_completed))
                    pushProgress(100)
                    transitionToCompleted()
                }
            }
        }
    }

    override fun transitionToCompleted() {
        helmOverrideListener?.cancel()
        restoreAutopilot()
        super.transitionToCompleted()
    }

    override fun transitionToAborted(reason: String?) {
        helmOverrideListener?.cancel()
        restoreAutopilot()
        super.transitionToAborted(reason)
    }

    private fun restoreAutopilot() {
        val ap = NauticalPlugin.autopilot
        initialApMode?.let { mode ->
            ap?.setAutopilotMode(mode)
        } ?: ap?.setAutopilotMode("standby")
    }

    private fun calculateDistanceToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        return KMapUtils.getDistance(lat, lon, targetLat, targetLon)
    }

    private fun calculateBearingToTarget(state: MarineState): Double? {
        val lat = state.latitude ?: return null
        val lon = state.longitude ?: return null
        if (targetLat.isNaN() || targetLon.isNaN()) return null
        
        val dLon = Math.toRadians(targetLon - lon)
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(targetLat)
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }
}
