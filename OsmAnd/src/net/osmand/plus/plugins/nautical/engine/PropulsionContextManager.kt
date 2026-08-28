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
        @Volatile
        private var instance: PropulsionContextManager? = null

        fun getInstance(app: OsmandApplication): PropulsionContextManager {
            return instance ?: synchronized(this) {
                instance ?: PropulsionContextManager(app).also { instance = it }
            }
        }
    }

    private var lastState: MarineState? = null
    private var debouncedEngineRunning = false

    init {
        NauticalPlugin.engine?.registerListener { state ->
            lastState = state
            updateDebouncedState()
        }
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
        return state.engineRpm == null && state.engineState == null
    }

    @Suppress("unused")
    fun getPropulsionType(): PropulsionType {
        return if (isEngineRunning()) PropulsionType.POWER else PropulsionType.SAIL
    }
}

enum class PropulsionType {
    SAIL, POWER
}
