package net.osmand.plus.plugins.nautical.engine

import android.os.Handler
import android.os.Looper
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Governs all commands sent to the AutopilotController to prevent state collisions.
 */
class NauticalHelmArbitrator private constructor(private val app: OsmandApplication) {

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private val autoReleaseTimeoutMs = 300000L // 5 minutes safety timeout

    companion object {
        const val PRIORITY_EMERGENCY_MOB = 1
        const val PRIORITY_COLLISION_EVASION = 2
        const val PRIORITY_TACTICAL_MANEUVER = 3
        const val PRIORITY_ACTIVE_ROUTING = 4
        const val PRIORITY_STANDBY_MANUAL = 5

        @Volatile
        private var instance: NauticalHelmArbitrator? = null

        fun getInstance(app: OsmandApplication): NauticalHelmArbitrator {
            return instance ?: synchronized(this) {
                instance ?: NauticalHelmArbitrator(app).also { instance = it }
            }
        }
    }

    private var currentPriority = AtomicInteger(PRIORITY_STANDBY_MANUAL)
    private var activeManeuverName: String? = null
    private val priorityStack = Stack<Pair<Int, String>>()

    /**
     * Attempts to acquire the helm for a specific priority level.
     * @throws HelmLockedException if a higher-priority lock is already held.
     */
    @Synchronized
    fun acquireLock(priority: Int, maneuverName: String) {
        if (priority < currentPriority.get()) {
            // Overriding lower priority: Push current to stack
            priorityStack.push(Pair(currentPriority.get(), activeManeuverName ?: "Unknown"))
            currentPriority.set(priority)
            activeManeuverName = maneuverName
            resetTimeout()
        } else if (priority > currentPriority.get()) {
            // Rejected: higher priority already active
            val msg = "Helm Locked by $activeManeuverName"
            notifyRejection(msg)
            throw HelmLockedException(currentPriority.get(), msg)
        } else {
            // Same priority, just update name and reset timeout
            activeManeuverName = maneuverName
            resetTimeout()
        }
    }

    @Synchronized
    fun releaseLock(priority: Int) {
        if (priority == currentPriority.get()) {
            cancelTimeout()
            if (priorityStack.isNotEmpty()) {
                val previous = priorityStack.pop()
                currentPriority.set(previous.first)
                activeManeuverName = previous.second
                resetTimeout() // Restart timeout for the restored lock
            } else {
                currentPriority.set(PRIORITY_STANDBY_MANUAL)
                activeManeuverName = null
            }
        } else {
            // Releasing a non-active lock from the stack if it exists
            val iterator = priorityStack.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().first == priority) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    private fun resetTimeout() {
        cancelTimeout()
        val runnable = Runnable {
            app.runInUIThread {
                app.showToastMessage("Helm Lock Safety Release triggered")
            }
            releaseLock(currentPriority.get())
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, autoReleaseTimeoutMs)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun isLockedByEmergency(): Boolean {
        return currentPriority.get() <= PRIORITY_COLLISION_EVASION
    }

    @Suppress("unused")
    fun getCurrentPriority(): Int = currentPriority.get()
    
    fun getActiveManeuver(): String? = activeManeuverName

    private fun notifyRejection(message: String) {
        app.runInUIThread {
            app.showToastMessage(message)
        }
        NauticalAudioArbiter.getInstance(app).dispatchAlarm(
            AlarmType.AUTOPILOT_COMMAND_REJECTED,
            voiceText = app.getString(R.string.nautical_autopilot_rejected) + ": " + message,
        )
    }
}
