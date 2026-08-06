package net.osmand.plus.plugins.nautical.utils

import java.time.Instant

/**
 * Utility for strict temporal synchronization and epoch validation in marine environments.
 * Prevents timezone collisions and 32-bit overflow vulnerabilities.
 */
object TemporalUtils {
    
    // 2500ms threshold for data staleness as per tactical requirements
    const val STALENESS_THRESHOLD_MS = 2500L
    
    // Boundary constants for epoch validation (Jan 1, 2000 to Jan 1, 2100)
    private const val MIN_VALID_EPOCH = 946684800000L
    private const val MAX_VALID_EPOCH = 4102444800000L

    /**
     * Returns current system time in strict UTC epoch milliseconds using java.time.Instant.
     * This avoids issues with implicit Android system timezone shifts.
     */
    fun now(): Long {
        return Instant.now().toEpochMilli()
    }

    /**
     * Validates an incoming 64-bit epoch. If the timestamp is outside the reasonable
     * marine temporal horizon or is malformed, it returns the current UTC 'now' to
     * maintain system integrity while logging the anomaly.
     */
    fun validate(epoch: Long?): Long {
        if (epoch == null || epoch == 0L) return 0L
        
        return if (epoch in MIN_VALID_EPOCH..MAX_VALID_EPOCH) {
            epoch
        } else {
            // Suspicious timestamp (32-bit overflow or extreme future/past)
            0L
        }
    }

    /**
     * Checks if a telemetry point is stale based on its time of fix.
     */
    fun isStale(timeOfFix: Long?): Boolean {
        if (timeOfFix == null || timeOfFix == 0L) return true
        return (now() - timeOfFix) > STALENESS_THRESHOLD_MS
    }

    fun formatIso8601(millis: Long): String {
        return Instant.ofEpochMilli(millis).toString()
    }

    fun parseIso8601(iso: String): Long {
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
