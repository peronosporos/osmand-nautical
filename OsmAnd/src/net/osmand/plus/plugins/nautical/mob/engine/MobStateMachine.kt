package net.osmand.plus.plugins.nautical.mob.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import java.util.*

/**
 * Thread-safe state machine for Man Overboard (MOB) emergency workflow.
 */
class MobStateMachine(
    private val repository: MarineLogbookRepository? = null,
    private val scope: CoroutineScope? = null
) {

    private val log = PlatformUtil.getLog(MobStateMachine::class.java)

    private val _mobStatus = MutableStateFlow(MobStatus(MobState.INACTIVE))
    
    /**
     * Current status of the MOB system.
     */
    val mobStatus: StateFlow<MobStatus> = _mobStatus.asStateFlow()

    companion object {
        const val MOB_STATE_KEY = "tactical.mob_status"
    }

    /**
     * Triggers a new MOB emergency.
     * 
     * @param currentLocation The location where the MOB was triggered.
     * @param sog Current Speed Over Ground (m/s).
     * @param cog Current Course Over Ground (radians).
     * @param driftMps Current drift speed (m/s).
     * @param setTrueRad Current drift direction (radians).
     */
    fun triggerMob(currentLocation: LatLon, sog: Double = 0.0, cog: Double = 0.0, driftMps: Double = 0.0, setTrueRad: Double = 0.0) {
        val newEvent = MobEvent(
            id = UUID.randomUUID().toString(),
            dropLocation = currentLocation,
            dropTimestamp = System.currentTimeMillis(),
            initialSog = sog,
            initialCog = cog
        )
        
        _mobStatus.update { current ->
            val nextEvents = current.activeEvents + newEvent
            val nextVectors = current.returnVectors.toMutableMap()
            nextVectors[newEvent.id] = MobVectorEngine.calculateReturnVector(
                currentLocation, newEvent, sog, driftMps, Math.toDegrees(setTrueRad)
            )
            
            val status = current.copy(
                state = MobState.ACTIVE_EMERGENCY,
                primaryEventId = newEvent.id,
                activeEvents = nextEvents,
                returnVectors = nextVectors,
                muteUntil = 0L // Reset mute on new emergency
            )
            persistState(status)
            status
        }
    }

    /**
     * Updates the boat's current location and recalculates all return vectors.
     * 
     * @param newLocation Live GPS coordinates of the boat.
     * @param sog Current Speed Over Ground (m/s).
     * @param driftMps Current drift speed (m/s).
     * @param setTrueRad Current drift direction (radians).
     */
    fun updateCurrentLocation(newLocation: LatLon, sog: Double, driftMps: Double = 0.0, setTrueRad: Double = 0.0) {
        _mobStatus.update { current ->
            if (current.state == MobState.INACTIVE || current.activeEvents.isEmpty()) {
                current
            } else {
                val nextVectors = current.activeEvents.associate { event ->
                    event.id to MobVectorEngine.calculateReturnVector(
                        newLocation, event, sog, driftMps, Math.toDegrees(setTrueRad)
                    )
                }
                current.copy(returnVectors = nextVectors)
            }
        }
    }

    /**
     * Mutes the MOB siren for a specified duration.
     */
    fun muteSiren(durationMs: Long = 5 * 60 * 1000L) {
        _mobStatus.update { current ->
            val status = current.copy(muteUntil = System.currentTimeMillis() + durationMs)
            persistState(status)
            status
        }
    }

    /**
     * Unmutes the MOB siren immediately.
     */
    fun unmuteSiren() {
        _mobStatus.update { current ->
            val status = current.copy(muteUntil = 0L)
            persistState(status)
            status
        }
    }

    /**
     * Transitions the state machine. 
     * If ACTIVE_EMERGENCY, moves to RESOLVED.
     * If RESOLVED, moves to INACTIVE.
     */
    fun cancelMob() {
        _mobStatus.update { current ->
            val next = when (current.state) {
                MobState.ACTIVE_EMERGENCY -> current.copy(state = MobState.RESOLVED)
                MobState.RESOLVED -> MobStatus(MobState.INACTIVE)
                MobState.INACTIVE -> current
            }
            persistState(next)
            next
        }
    }

    /**
     * Restores state from persistent storage after process death.
     */
    fun restoreState(status: MobStatus) {
        _mobStatus.value = status
    }

    /**
     * Retrieves the stored status from the repository.
     */
    suspend fun getStoredStatus(): MobStatus? = withContext(Dispatchers.IO) {
        try {
            repository?.getTacticalState(MOB_STATE_KEY)?.let { json ->
                Json.decodeFromString<MobStatus>(json)
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve stored MOB status: ${e.message}", e)
            null
        }
    }

    private fun persistState(status: MobStatus) {
        val repo = repository ?: return
        val effectiveScope = scope ?: return
        effectiveScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    if (status.state == MobState.INACTIVE) {
                        repo.deleteTacticalState(MOB_STATE_KEY)
                    } else {
                        val json = Json.encodeToString(status)
                        repo.upsertTacticalState(MOB_STATE_KEY, json)
                    }
                } catch (e: Exception) {
                    log.error("Failed to persist MOB state: ${e.message}", e)
                }
            }
        }
    }
}
