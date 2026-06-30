package net.osmand.plus.plugins.nautical.engine

/**
 * Single source of truth for the vessel's status.
 */
data class MarineState(
    // Navigation Data
    var latitude: Double? = null,
    var longitude: Double? = null,
    var headingTrue: Double? = null,
    var speedOverGround: Double? = null,

    // Status Data
    var autopilotState: String = "standby",
    var autopilotHeadingSet: Double? = null,

    // Telemetry Data (Phase 3)
    var depthBelowTransducer: Double? = null,
    var windSpeedTrue: Double? = null
)