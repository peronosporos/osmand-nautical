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
            // Flip Hull-Relative Heading Vectors
            headingTrue = state.headingTrue?.let { flipVector(it) },
            headingMagnetic = state.headingMagnetic?.let { flipVector(it) },

            // Autopilot Targets must be flipped to match new hull orientation
            targetHeading = state.targetHeading?.let { flipVector(it) },
            pendingTargetHeading = state.pendingTargetHeading?.let { flipVector(it) },
            autopilotHeadingSet = state.autopilotHeadingSet?.let { flipVector(it) },
            
            // Invert Relative Wind Angles (AWA/TWA)
            windDirectionApparent = state.windDirectionApparent?.let { flipRelativeAngle(it) },
            trueWindAngle = state.trueWindAngle?.let { flipRelativeAngle(it) },

            // Wind Targets relative to bow must also be flipped
            targetWindAngleApparent = state.targetWindAngleApparent?.let { flipRelativeAngle(it) },
            autopilotWindAngleSet = state.autopilotWindAngleSet?.let { flipRelativeAngle(it) },
            
            // Note: COG (Course Over Ground) and Set (Drift Direction) are absolute 
            // relative to the earth and must NOT be flipped.
            
            // Transverse metrics relative to the vessel centerline
            rateOfTurn = state.rateOfTurn?.let { -it }, 
            leeway = state.leeway?.let { -it }
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
