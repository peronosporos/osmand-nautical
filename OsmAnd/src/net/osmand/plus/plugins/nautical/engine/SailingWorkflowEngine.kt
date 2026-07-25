package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.Locale

enum class SailingWorkflowState {
    TACTICAL_PASSAGE,
    CLOSE_QUARTERS,
    STATIONARY_ANCHORED
}

/**
 * SailingWorkflowEngine manages seamless transitions between navigation modes based on environmental context,
 * handles one-tap / one-voice confirmations, and automates map zoom and tilt.
 */
class SailingWorkflowEngine(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(SailingWorkflowEngine::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentWorkflow = MutableStateFlow(SailingWorkflowState.TACTICAL_PASSAGE)
    val currentWorkflow: StateFlow<SailingWorkflowState> = _currentWorkflow.asStateFlow()

    private var pendingWorkflow: SailingWorkflowState? = null
    private var anchoredTimeStart: Long = 0L

    init {
        startEvaluation()
    }

    private fun startEvaluation() {
        scope.launch {
            // Monitor telemetry (SOG, Depth, Distance to destination/land)
            // Simulated periodic check or flow collection
            while (isActive) {
                delay(2000L)
                evaluateEnvironment()
            }
        }
    }

    private fun evaluateEnvironment() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return

        // Retrieve latest metrics from MarineState
        // speedOverGround is in m/s, convert to knots (1 m/s ≈ 1.94384 kn)
        val sog = (state.speedOverGround ?: 0.0) * 1.94384
        // depthBelowKeel is in meters
        val depth = state.depthBelowKeel ?: state.depthBelowTransducer ?: 20.0
        // distanceToWaypoint is in meters, convert to NM (1 NM = 1852 m)
        val distanceToWaypointNM = (state.distanceToWaypoint ?: 10000.0) / 1852.0

        val detectedState = when {
            sog < 0.5 && depth < 10.0 -> {
                if (anchoredTimeStart == 0L) anchoredTimeStart = System.currentTimeMillis()
                if (System.currentTimeMillis() - anchoredTimeStart > 120000L) {
                    SailingWorkflowState.STATIONARY_ANCHORED
                } else {
                    SailingWorkflowState.TACTICAL_PASSAGE
                }
            }
            distanceToWaypointNM < 0.5 || depth < 5.0 -> {
                anchoredTimeStart = 0L
                SailingWorkflowState.CLOSE_QUARTERS
            }
            else -> {
                anchoredTimeStart = 0L
                SailingWorkflowState.TACTICAL_PASSAGE
            }
        }

        if (detectedState != _currentWorkflow.value && detectedState != pendingWorkflow) {
            pendingWorkflow = detectedState
            promptWorkflowTransition(detectedState)
        }
    }

    private fun promptWorkflowTransition(newState: SailingWorkflowState) {
        val message = when (newState) {
            SailingWorkflowState.CLOSE_QUARTERS -> "Approaching marina. Close-quarters workflow ready."
            SailingWorkflowState.STATIONARY_ANCHORED -> "Stationary near shallow water. Anchor drop workflow ready."
            SailingWorkflowState.TACTICAL_PASSAGE -> "Open water detected. Tactical passage workflow ready."
        }

        log.info("Workflow transition proposed: $newState. Prompting TTS.")
        speak(message)
    }

    /**
     * Confirm pending workflow transition via voice ("Confirm") or BLE button.
     */
    fun confirmPendingWorkflow(mapActivity: MapActivity?) {
        val target = pendingWorkflow ?: return
        _currentWorkflow.value = target
        pendingWorkflow = null
        log.info("Workflow transition confirmed: $target")
        applyCameraAutomation(mapActivity, target)
        speak("Workflow confirmed.")
    }

    private fun applyCameraAutomation(mapActivity: MapActivity?, workflow: SailingWorkflowState) {
        if (mapActivity == null) return
        mapActivity.runOnUiThread {
            val mapView = mapActivity.mapView
            when (workflow) {
                SailingWorkflowState.CLOSE_QUARTERS,
                SailingWorkflowState.STATIONARY_ANCHORED -> {
                    // Zoom to max scale (e.g. zoom level 19) and center on bow
                    mapView.setZoomWithFloatPart(19, 0f)
                    log.info("Camera automated: Zoom level 19 for Close-Quarters / Stationary.")
                }
                SailingWorkflowState.TACTICAL_PASSAGE -> {
                    // Auto-zoom to fit laylines and next active waypoint
                    mapView.setZoomWithFloatPart(14, 0f)
                    log.info("Camera automated: Zoom level 14 for Tactical Passage.")
                }
            }
            mapView.refreshMap()
        }
    }

    private fun speak(text: String) {
        app.runInUIThread {
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
