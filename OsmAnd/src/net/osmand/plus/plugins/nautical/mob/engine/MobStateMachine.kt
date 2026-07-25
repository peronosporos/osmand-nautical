package net.osmand.plus.plugins.nautical.mob.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.osmand.data.LatLon
import java.util.*

/**
 * Thread-safe state machine for Man Overboard (MOB) emergency workflow.
 */
class MobStateMachine {

    private val _mobStatus = MutableStateFlow(MobStatus(MobState.INACTIVE))
    
    /**
     * Current status of the MOB system.
     */
    val mobStatus: StateFlow<MobStatus> = _mobStatus.asStateFlow()

    /**
     * Triggers a new MOB emergency.
     * 
     * @param currentLocation The location where the MOB was triggered.
     * @param sog Current Speed Over Ground (m/s).
     * @param cog Current Course Over Ground (radians).
     */
    fun triggerMob(currentLocation: LatLon, sog: Double = 0.0, cog: Double = 0.0) {
        val event = MobEvent(
            id = UUID.randomUUID().toString(),
            dropLocation = currentLocation,
            dropTimestamp = System.currentTimeMillis(),
            initialSog = sog,
            initialCog = cog
        )
        
        _mobStatus.update {
            MobStatus(
                state = MobState.ACTIVE_EMERGENCY,
                event = event,
                returnVector = MobVectorEngine.calculateReturnVector(currentLocation, event, sog)
            )
        }
    }

    /**
     * Updates the boat's current location and recalculates the return vector.
     * 
     * @param newLocation Live GPS coordinates of the boat.
     * @param sog Current Speed Over Ground (m/s).
     */
    fun updateCurrentLocation(newLocation: LatLon, sog: Double) {
        _mobStatus.update { current ->
            if (current.state == MobState.INACTIVE || current.event == null) {
                current
            } else {
                val newVector = MobVectorEngine.calculateReturnVector(newLocation, current.event, sog)
                current.copy(returnVector = newVector)
            }
        }
    }

    /**
     * Transitions the state machine. 
     * If ACTIVE_EMERGENCY, moves to RESOLVED.
     * If RESOLVED, moves to INACTIVE.
     */
    fun cancelMob() {
        _mobStatus.update { current ->
            when (current.state) {
                MobState.ACTIVE_EMERGENCY -> current.copy(state = MobState.RESOLVED)
                MobState.RESOLVED -> MobStatus(MobState.INACTIVE)
                MobState.INACTIVE -> current
            }
        }
    }
}
