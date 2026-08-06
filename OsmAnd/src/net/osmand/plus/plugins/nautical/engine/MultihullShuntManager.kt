package net.osmand.plus.plugins.nautical.engine

import kotlin.math.PI

/**
 * Manages vessel orientation transformation for shunting multihulls (e.g., Proas).
 * Inverts vectors and relative angles when the vessel swaps bow/stern roles.
 */
object MultihullShuntManager {

    /**
     * Transforms the raw Signal K state into a shunted state if active.
     */
    fun transformState(state: MarineState): MarineState {
        if (!state.isShunted) return state

        return state.copy(
            // Flip Bow/Stern Vectors
            headingTrue = state.headingTrue?.let { flipVector(it) },
            headingMagnetic = state.headingMagnetic?.let { flipVector(it) },
            courseOverGroundTrue = state.courseOverGroundTrue?.let { flipVector(it) },
            
            // Invert Relative Wind Angles (AWA/TWA)
            windDirectionApparent = state.windDirectionApparent?.let { flipRelativeAngle(it) },
            trueWindAngle = state.trueWindAngle?.let { flipRelativeAngle(it) },
            
            // Note: Depth and other scalar telemetry remain unchanged
            // Rate of turn and drift vectors might need flipping if they are relative to ship axes
            rateOfTurn = state.rateOfTurn?.let { -it }, // Turning 'starboard' becomes 'port' if bow/stern flip?
            // Actually, in a proa, the ama stays on the same side, so rotation direction is consistent 
            // with the boat's frame, but the definition of 'forward' flipped.
        )
    }

    /**
     * Flips a compass-referenced vector by 180 degrees.
     */
    private fun flipVector(rad: Double): Double {
        return (rad + PI) % (2 * PI)
    }

    /**
     * Shifts a relative angle (AWA/TWA) by 180 degrees.
     * Signal K AWA is usually -PI to PI.
     */
    private fun flipRelativeAngle(rad: Double): Double {
        var flipped = rad + PI
        while (flipped > PI) flipped -= 2 * PI
        while (flipped <= -PI) flipped += 2 * PI
        return flipped
    }
}
