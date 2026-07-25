package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.plugins.nautical.engine.MarineState

interface ManeuverStateMachine {
    enum class State {
        ARMED,
        EXECUTING,
        COMPLETED,
        ABORTED
    }

    val currentState: State

    fun checkSafetyPreconditions(state: MarineState): Boolean

    fun transitionToArmed()
    fun transitionToExecuting()
    fun transitionToCompleted()
    fun transitionToAborted(reason: String? = null)
}
