package net.osmand.plus.plugins.nautical.engine

/**
 * Single source of truth for the vessel's status.
 */
data class MarineState(
    // Navigation Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingTrue: Double? = null,
    val speedOverGround: Double? = null,

    // Status Data
    val autopilotState: String = "standby",
    val autopilotHeadingSet: Double? = null,

    // Telemetry Data (Phase 3)
    val depthBelowTransducer: Double? = null,
    val windSpeedTrue: Double? = null,

    // Navigation Deviation (Cross-Track Error)
    val crossTrackError: Double? = null
)