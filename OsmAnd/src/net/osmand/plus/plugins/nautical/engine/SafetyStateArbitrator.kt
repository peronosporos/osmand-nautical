package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter

/**
 * Orchestrates safety states to prevent audio/visual clutter and prioritize high-severity alerts.
 */
class SafetyStateArbitrator(private val app: OsmandApplication) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            val engine = NauticalPlugin.engine ?: return@launch
            engine.marineStateFlow.collectLatest { state ->
                arbitrateAlarms(state)
                arbitrateHud(state)
            }
        }
    }

    private fun arbitrateAlarms(state: MarineState) {
        val audio = NauticalAudioArbiter.getInstance(app)
        
        // MOB is top priority (1). It automatically preempts others in the Arbiter.
        // But we explicitly stop lower ones to clear the queue if they are active.
        if (state.isMobActive) {
            audio.stopAlarm(AlarmType.ANCHOR_DRIFT)
            audio.stopAlarm(AlarmType.XTE_NAVIGATION)
            return
        }
        
        // Anchor Drift (4)
        val isAnchorAlarm = state.notifications.values.any { 
            it.message.contains("Anchor", ignoreCase = true) && it.state >= NotificationState.ALARM 
        }
        if (isAnchorAlarm) {
            audio.stopAlarm(AlarmType.XTE_NAVIGATION)
            return
        }

        // XTE (5) - Low priority safety
        if (!state.isOffCourse) {
            audio.stopAlarm(AlarmType.XTE_NAVIGATION)
        }
    }

    private fun arbitrateHud(state: MarineState) {
        val hudManager = NauticalPlugin.hudManager?.get() ?: return
        
        // Task: Manage the "Visual Emergency Stack"
        app.runInUIThread {
            if (state.isMobActive) {
                // Ensure MOB header is the ONLY one high-priority, hide non-safety headers
                // Example: Hide environment or media widgets if they were pinned
                hudManager.updateLayout()
            }
            
            // Auto-hide stale banners if safety data is restored
            if (state.connectionStatus == ConnectionStatus.CONNECTED && !state.isOffCourse) {
                hudManager.hideBanner()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
