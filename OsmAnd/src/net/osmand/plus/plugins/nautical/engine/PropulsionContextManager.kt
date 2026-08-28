package net.osmand.plus.plugins.nautical.engine

import android.os.Handler
import android.os.Looper
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.*

/**
 * Monitors and provides context about the vessel's propulsion state.
 */
class PropulsionContextManager private constructor(@Suppress("unused") app: OsmandApplication) {

    private val handler = Handler(Looper.getMainLooper())
    private var hysteresisRunnable: Runnable? = null
    private val hysteresisMs = 5000L

    companion object {
        private const val ENGINE_STALE_TIMEOUT_MS = 5000L

        @Volatile
        private var instance: PropulsionContextManager? = null

        fun getInstance(app: OsmandApplication): PropulsionContextManager {
            return instance ?: synchronized(this) {
                instance ?: PropulsionContextManager(app).also { instance = it }
            }
        }
    }

    private var lastState: MarineState? = null
    @Volatile
    private var debouncedEngineRunning = false
    @Volatile
    private var lastEngineTelemetryTimestampMs: Long = 0L

    private val staleWatchdogRunnable = Runnable {
        synchronized(this) {
            if (isEngineStaleInternal()) {
                cancelHysteresis()
                debouncedEngineRunning = false
            }
        }
    }

    init {
        NauticalPlugin.engine?.registerListener { state ->
            synchronized(this) {
                lastState = state
                if (state.engineRpm != null || state.engineState != null) {
                    lastEngineTelemetryTimestampMs = System.currentTimeMillis()
                    handler.removeCallbacks(staleWatchdogRunnable)
                    handler.postDelayed(staleWatchdogRunnable, ENGINE_STALE_TIMEOUT_MS)
                }
                updateDebouncedState()
            }
        }
    }

    private fun isEngineStaleInternal(): Boolean {
        if (lastEngineTelemetryTimestampMs == 0L) return false
        return (System.currentTimeMillis() - lastEngineTelemetryTimestampMs) > ENGINE_STALE_TIMEOUT_MS
    }

    private fun updateDebouncedState() {
        val currentRunning = isEngineRunningInternal()
        if (currentRunning) {
            cancelHysteresis()
            if (!debouncedEngineRunning) {
                debouncedEngineRunning = true
            }
        } else {
            if (debouncedEngineRunning && (hysteresisRunnable == null)) {
                val runnable = Runnable {
                    debouncedEngineRunning = false
                    hysteresisRunnable = null
                }
                hysteresisRunnable = runnable
                handler.postDelayed(runnable, hysteresisMs)
            }
        }
    }

    private fun cancelHysteresis() {
        hysteresisRunnable?.let { handler.removeCallbacks(it) }
        hysteresisRunnable = null
    }

    private fun isEngineRunningInternal(): Boolean {
        if (isEngineStaleInternal()) {
            return false
        }
        val state = lastState ?: return false
        val rpm = state.engineRpm ?: 0.0
        val engineStarted = state.engineState?.lowercase(Locale.US) == "started"
        return rpm > 100.0 || engineStarted
    }

    /**
     * Returns true if the main engine is actively running.
     * Includes 5s hysteresis for "stopped" state to prevent flickering.
     */
    fun isEngineRunning(): Boolean = debouncedEngineRunning

    /**
     * Returns true if engine data is missing or stale.
     */
    fun isEngineStateUnknown(): Boolean {
        val state = lastState ?: return true
        return (state.engineRpm == null && state.engineState == null) || isEngineStaleInternal()
    }

    @Suppress("unused")
    fun getPropulsionType(): PropulsionType {
        return if (isEngineRunning()) PropulsionType.POWER else PropulsionType.SAIL
    }
}

enum class PropulsionType {
    SAIL, POWER
}
