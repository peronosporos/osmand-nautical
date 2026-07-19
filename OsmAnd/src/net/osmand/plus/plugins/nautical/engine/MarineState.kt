package net.osmand.plus.plugins.nautical.engine

import java.util.Locale
import java.io.Serializable

data class AisTarget(
    val mmsi: Int,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedOverGround: Float? = null,
    var courseOverGround: Float? = null,
    var headingTrue: Float? = null,
)

/**
 * Single source of truth for the vessel's status.
 */


data class MarineState(
    // Navigation Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingTrue: Double? = null,
    val headingMagnetic: Double? = null,
    val speedOverGround: Double? = null,
    val courseOverGroundTrue: Double? = null,
    val velocityMadeGood: Double? = null,
    val log: Double? = null,
    val tripLog: Double? = null,

    // Attitude
    val roll: Double? = null,
    val pitch: Double? = null,
    val yaw: Double? = null,

    // Status Data
    val autopilotState: String = "standby",
    val autopilotHeadingSet: Double? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,

    // Professional Autopilot Data
    val rudderAngle: Double? = null,
    val targetHeading: Double? = null,
    val targetWindAngleApparent: Double? = null,
    val seaState: Int? = null, // Sensitivity (1-5)

    // Telemetry Data (Phase 3)
    val depthBelowTransducer: Double? = null,
    val depthBelowKeel: Double? = null,
    val windSpeedTrue: Double? = null,
    val windDirectionTrue: Double? = null,
    val windDirectionApparent: Double? = null,
    val windSpeedApparent: Double? = null,
    val speedThroughWater: Double? = null,
    val rateOfTurn: Double? = null,
    val drift: Double? = null,
    val setTrue: Double? = null,
    val trueWindAngle: Double? = null,
    val polarTargetSpeed: Double? = null,
    val timeToWaypoint: Double? = null,

    // Environment
    val waterTemperature: Double? = null,
    val outsideTemperature: Double? = null,
    val outsidePressure: Double? = null,

    // Propulsion
    val engineRpm: Double? = null,
    val engineTemperature: Double? = null,
    val engineOilPressure: Double? = null,
    val fuelRate: Double? = null,
    val engineRunTime: Double? = null,
    val engineLoad: Double? = null,

    // Electrical
    val batteryVoltage: Double? = null,
    val batteryCurrent: Double? = null,
    val batterySoc: Double? = null,
    val solarCurrent: Double? = null,

    // Tanks
    val fuelLevel: Double? = null,
    val freshWaterLevel: Double? = null,
    val wasteWaterLevel: Double? = null,

    // Performance
    val polarSpeedRatio: Double? = null,

    // Navigation Deviation (Cross-Track Error)
    val crossTrackError: Double? = null,
    val distanceToWaypoint: Double? = null,
) : Serializable {
    val autopilotMode: String
        get() = autopilotState.uppercase(Locale.US)
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    STALE
}