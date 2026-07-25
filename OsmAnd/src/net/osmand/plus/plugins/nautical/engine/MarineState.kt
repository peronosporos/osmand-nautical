package net.osmand.plus.plugins.nautical.engine

import java.util.Locale
import java.io.Serializable

data class AisTarget(
    val mmsi: Int,
    var name: String? = null,
    var callSign: String? = null,
    var vesselType: Int? = null,
    var length: Double? = null,
    var beam: Double? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedOverGround: Float? = null,
    var courseOverGround: Float? = null,
    var headingTrue: Float? = null,
    var lastUpdate: Long = 0,
)

data class SignalKNotification(
    val message: String,
    val state: NotificationState,
    val method: List<String> = emptyList()
) : Serializable

enum class NotificationState {
    NORMAL, ALERT, WARN, ALARM, EMERGENCY
}

/**
 * Single source of truth for the vessel's status.
 */


data class MarineState(
    // Vessel Info
    val vesselName: String? = null,
    val vesselType: Int? = null,
    val vesselCallSign: String? = null,
    val vesselMmsi: Int? = null,
    val vesselLength: Double? = null,
    val vesselBeam: Double? = null,

    // Navigation Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingTrue: Double? = null, // Radians
    val headingMagnetic: Double? = null, // Radians
    val magneticVariation: Double? = null, // Radians
    val speedOverGround: Double? = null, // m/s (Signal K standard)
    val courseOverGroundTrue: Double? = null, // Radians
    val velocityMadeGood: Double? = null, // m/s
    val log: Double? = null,
    val tripLog: Double? = null,

    // Attitude
    val roll: Double? = null, // Radians
    val pitch: Double? = null, // Radians
    val yaw: Double? = null, // Radians

    // Status Data
    val autopilotState: String = "standby",
    val autopilotHeadingSet: Double? = null, // Radians
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,

    // Professional Autopilot Data
    val rudderAngle: Double? = null, // Radians
    val targetHeading: Double? = null, // Radians
    val targetWindAngleApparent: Double? = null, // Radians
    val seaState: Int? = null, // Sensitivity (1-5)
    val isAutoSeaStateEnabled: Boolean = false,

    // Telemetry Data (Phase 3)
    val depthBelowTransducer: Double? = null, // Meters
    val depthSurfaceToTransducer: Double? = null, // Meters
    val depthBelowKeel: Double? = null, // Meters
    val windSpeedTrue: Double? = null, // m/s
    val windDirectionTrue: Double? = null, // Radians (0 = North)
    val windDirectionApparent: Double? = null, // Radians (relative to bow, or true if specified by meta)
    val windSpeedApparent: Double? = null, // m/s
    val speedThroughWater: Double? = null, // m/s
    val rateOfTurn: Double? = null, // Radians/s
    val drift: Double? = null, // m/s
    val setTrue: Double? = null, // Radians
    val trueWindAngle: Double? = null, // Radians (relative to bow)
    val polarTargetSpeed: Double? = null, // m/s
    val timeToWaypoint: Double? = null, // Seconds

    // Environment
    val waterTemperature: Double? = null,
    val outsideTemperature: Double? = null,
    val outsidePressure: Double? = null,

    // Propulsion
    val engineRpm: Double? = null,
    val engineTemperature: Double? = null,
    val engineCoolantTemperature: Double? = null,
    val engineState: String? = null,
    val engineInstance: String? = null,
    val engineOilPressure: Double? = null,
    val fuelRate: Double? = null,
    val engineRunTime: Double? = null,
    val engineLoad: Double? = null,

    // Electrical
    val batteryVoltage: Double? = null,
    val batteryCurrent: Double? = null,
    val batterySoc: Double? = null,
    val solarCurrent: Double? = null,
    val switches: Map<String, Boolean> = emptyMap(),

    // Tanks
    val fuelLevel: Double? = null,
    val freshWaterLevel: Double? = null,
    val wasteWaterLevel: Double? = null,

    // Performance
    val polarSpeedRatio: Double? = null,

    // Navigation Deviation (Cross-Track Error)
    val crossTrackError: Double? = null,
    val distanceToWaypoint: Double? = null,
    val isOffCourse: Boolean = false,

    // Alarms and Notifications
    val notifications: Map<String, SignalKNotification> = emptyMap(),

    // Custom Telemetry & Metadata
    val customValues: Map<String, Double> = emptyMap(),
    val pathMeta: Map<String, Map<String, Any>> = emptyMap(),

    // Pending Commands (Reconciliation)
    val pendingTargetHeading: Double? = null,
    val pendingAutopilotState: String? = null,
    val commandSentTimestamp: Long = 0,

    val timestamps: Map<String, Long> = emptyMap()
) : Serializable {
    val autopilotMode: String
        get() = autopilotState.uppercase(Locale.US)
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    STALE
}