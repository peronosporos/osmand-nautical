package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.settings.enums.XteDirection
import java.io.Serializable

@kotlinx.serialization.Serializable
data class Sail(
    val id: String,
    val name: String,
    val type: String,
    val area: Double? = null,
    val active: Boolean = false
) : Serializable

@kotlinx.serialization.Serializable
data class GnssState(
    val method: String? = null,
    val satellites: Int? = null,
    val horizontalDilution: Double? = null,
    val verticalDilution: Double? = null,
    val integrity: String? = null
) : Serializable

@kotlinx.serialization.Serializable
data class Engine(
    val instance: String,
    val revolutions: Double? = null, // Hertz (Hz) - Signal K standard
    val temperature: Double? = null, // Kelvin (K)
    val oilPressure: Double? = null, // Pascal (Pa)
    val oilTemperature: Double? = null, // Kelvin (K)
    val fuelRate: Double? = null, // Cubic meters per second (m3/s)
    val fuelEconomy: Double? = null,
    val boostPressure: Double? = null, // Pascal (Pa)
    val load: Double? = null, // Ratio 0-1
    val coolantTemperature: Double? = null, // Kelvin (K)
    val exhaustTemperature: Double? = null, // Kelvin (K)
    val runTime: Double? = null, // Seconds (s)
    val state: String? = null,
    val driveTrimState: Double? = null,
    val transmissionGear: String? = null,
    val transmissionPressure: Double? = null, // Pascal (Pa)
    val transmissionOilTemperature: Double? = null, // Kelvin (K)
    val alternatorVoltage: Double? = null, // Volt (V)
    val alternatorCurrent: Double? = null // Ampere (A)
) : Serializable

@kotlinx.serialization.Serializable
data class Battery(
    val instance: String,
    val name: String? = null,
    val voltage: Double? = null, // Volt (V)
    val current: Double? = null, // Ampere (A)
    val temperature: Double? = null, // Kelvin (K)
    val stateOfCharge: Double? = null, // Ratio 0-1
    val stateOfHealth: Double? = null, // Ratio 0-1
    val timeRemaining: Double? = null, // Seconds (s)
    val timeToFull: Double? = null, // Seconds (s)
    val cellVoltages: List<Double> = emptyList() // Volts (V)
) : Serializable

@kotlinx.serialization.Serializable
data class Charger(
    val instance: String,
    val name: String? = null,
    val state: String? = null,
    val mode: String? = null,
    val voltage: Double? = null,
    val current: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class Inverter(
    val instance: String,
    val name: String? = null,
    val state: String? = null,
    val mode: String? = null,
    val acVoltage: Double? = null,
    val acCurrent: Double? = null,
    val acFrequency: Double? = null,
    val load: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class AnchorState(
    val state: String? = null,
    val maxDrift: Double? = null,
    val radius: Double? = null,
    val selection: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class Tank(
    val instance: String,
    val type: String,
    val name: String? = null,
    val currentLevel: Double? = null,
    val currentVolume: Double? = null,
    val capacity: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class SignalKNotification(
    val message: String,
    val state: NotificationState,
    val method: List<String> = emptyList(),
) : Serializable

@kotlinx.serialization.Serializable
data class TideState(
    val heightNow: Double? = null,
    val stationName: String? = null,
    val state: String? = null, // "rising", "falling"
    val nextExtremeTime: Long? = null,
    val nextExtremeHeight: Double? = null,
    val nextExtremeType: String? = null // "High", "Low"
) : Serializable

@kotlinx.serialization.Serializable
data class PypilotConfig(
    val p: Double? = null,
    val i: Double? = null,
    val d: Double? = null,
    val dd: Double? = null,
    val pr: Double? = null,
    val ff: Double? = null,
    val wg: Double? = null,
    val deadzone: Double? = null,
    val activeProfile: String? = null
) : Serializable

@kotlinx.serialization.Serializable
data class PypilotServoState(
    val voltage: Double? = null,
    val current: Double? = null,
    val controllerTemp: Double? = null,
    val motorTemp: Double? = null,
    val ampHours: Double? = null,
    val runtime: Double? = null,
    val engagement: String? = null
) : Serializable

@kotlinx.serialization.Serializable
data class PypilotCalibrationState(
    val compassCalibrationProgress: Double? = null,
    val accelCalibrationProgress: Double? = null,
    val rudderCalibrationProgress: Double? = null,
    val isCalibrating: Boolean = false
) : Serializable

@kotlinx.serialization.Serializable
data class ForwardHazard(
    val id: String,
    val name: String,
    val distance: Double,
    val bearing: Double,
    val severity: NotificationState,
    val position: Pair<Double, Double>? = null
) : Serializable

@kotlinx.serialization.Serializable
data class MediaInfo(
    val title: String? = null,
    val artist: String? = null,
    val playbackState: String? = null, // "playing", "paused", "stopped"
    val source: String? = null,
    val volume: Double? = null,
    val volumeZones: Map<String, Double> = emptyMap()
) : Serializable

enum class NotificationState {
    NORMAL, ALERT, WARN, ALARM, EMERGENCY
}

/**
 * Single source of truth for the vessel's status.
 */
object MarineStateConstants {
    const val MIN_LAT = -90.0
    const val MAX_LAT = 90.0
    const val MIN_LON = -180.0
    const val MAX_LON = 180.0
    
    const val MAX_WIND_SPEED_MS = 77.17 // ~150 knots
    const val MAX_BOAT_SPEED_MS = 51.44 // ~100 knots
    const val MAX_DEPTH_METERS = 11000.0
    
    const val MIN_MMSI = 201000000
    const val MAX_MMSI = 775999999

    fun isValidLat(lat: Double) = !lat.isNaN() && lat in MIN_LAT..MAX_LAT
    fun isValidLon(lon: Double) = !lon.isNaN() && lon in MIN_LON..MAX_LON
    fun isValidSpeed(speed: Double) = !speed.isNaN() && speed in 0.0..MAX_BOAT_SPEED_MS
    fun isValidWindSpeed(speed: Double) = !speed.isNaN() && speed in 0.0..MAX_WIND_SPEED_MS
    fun isValidDepth(depth: Double) = !depth.isNaN() && depth in 0.0..MAX_DEPTH_METERS
    fun isValidMmsi(mmsi: Int) = mmsi in MIN_MMSI..MAX_MMSI || mmsi == 0
}

@kotlinx.serialization.Serializable
data class Watermaker(
    val instance: String,
    val state: String? = null,
    val rate: Double? = null, // Liters per hour
    val totalProduction: Double? = null,
    val salinity: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class MarineState(
    // Vessel Info
    val vesselName: String? = null,
    val vesselType: Int? = null,
    val vesselCallSign: String? = null,
    val vesselMmsi: Int? = null,
    val vesselLength: Double? = null, // Meters (m)
    val vesselBeam: Double? = null, // Meters (m)
    val vesselFlag: String? = null,
    val vesselPort: String? = null,
    val vesselRegistrations: Map<String, String> = emptyMap(),
    val vesselUuid: String? = null,

    // Navigation Data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null, // Meters (m)
    val gnss: GnssState? = null,
    val anchor: AnchorState? = null,
    val headingTrue: Double? = null, // Radians (rad)
    val headingMagnetic: Double? = null, // Radians (rad)
    val magneticVariation: Double? = null, // Radians (rad)
    val speedOverGround: Double? = null, // m/s (Signal K standard)
    val courseOverGroundTrue: Double? = null, // Radians (rad)
    val velocityMadeGood: Double? = null, // m/s
    val log: Double? = null, // Meters (m)
    val tripLog: Double? = null, // Meters (m)

    // Attitude
    val roll: Double? = null, // Radians (rad)
    val pitch: Double? = null, // Radians (rad)
    val yaw: Double? = null, // Radians (rad)
    val heel: Double? = null, // Radians (rad) (Sail-specific)

    // Status Data
    val autopilotState: String = "standby", // standby, wind, track, auto
    val autopilotHeadingSet: Double? = null, // Radians (rad)
    val autopilotWindAngleSet: Double? = null, // Radians (rad)
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,

    // Professional Autopilot Data
    val rudderAngle: Double? = null, // Radians (rad)
    val simulatedRudderAngle: Double? = null, // Radians (rad) (Fallback)
    val targetHeading: Double? = null, // Radians (rad)
    val targetWindAngleApparent: Double? = null, // Radians (rad)
    val seaState: Int? = null, // Sensitivity (1-5)
    val isAutoSeaStateEnabled: Boolean = false,

    // Telemetry Data (Phase 3)
    val depthBelowTransducer: Double? = null, // Meters (m)
    val depthSurfaceToTransducer: Double? = null, // Meters (m)
    val depthBelowKeel: Double? = null, // Meters (m)
    val windSpeedTrue: Double? = null, // m/s
    val windDirectionTrue: Double? = null, // Radians (rad) (0 = North)
    val windDirectionApparent: Double? = null, // Radians (rad) (relative to bow, or true if specified by meta)
    val windSpeedApparent: Double? = null, // m/s
    val speedThroughWater: Double? = null, // m/s
    val rateOfTurn: Double? = null, // Radians/s
    val drift: Double? = null, // m/s
    val setTrue: Double? = null, // Radians (rad)
    val trueWindAngle: Double? = null, // Radians (rad) (relative to bow)
    val windShift: Double? = null, // Radians (rad)
    val tackAngle: Double? = null, // Radians (rad)
    val serverLaylines: net.osmand.plus.plugins.nautical.laylines.engine.LaylineData? = null,
    val leeway: Double? = null, // Radians (rad)
    val polarTargetSpeed: Double? = null, // m/s

    // Actuator Health (Phase 7)
    val actuatorDutyCycle: Double? = null, // Ratio 0.0-1.0
    val actuatorCurrent: Double? = null, // Amperes (A)
    val isActuatorOverloaded: Boolean = false,
    val isShunted: Boolean = false,
    val isEngineRunning: Boolean = false,
    val watchdogStatus: SignalKNotification? = null,
    val forwardHazards: List<ForwardHazard> = emptyList(),

    // Environment
    val waterTemperature: Double? = null, // Kelvin (K)
    val waterSalinity: Double? = null, // Ratio 0-1
    val outsideTemperature: Double? = null, // Kelvin (K)
    val outsidePressure: Double? = null, // Pascal (Pa)
    val outsideHumidity: Double? = null, // Ratio 0-1
    val outsideIlluminance: Double? = null, // Lux
    val airDewPoint: Double? = null, // Kelvin (K)
    val moonPhase: Double? = null, // Ratio 0-1
    val sunlightMode: String? = null,

    // Propulsion & Systems (Multi-instance support)
    val engines: Map<String, Engine> = emptyMap(),
    val batteries: Map<String, Battery> = emptyMap(),
    val tanks: Map<String, Tank> = emptyMap(),
    val switches: Map<String, Boolean> = emptyMap(),
    val chargers: Map<String, Charger> = emptyMap(),
    val inverters: Map<String, Inverter> = emptyMap(),
    val riggingLoads: Map<String, Double> = emptyMap(),
    val watermakers: Map<String, Watermaker> = emptyMap(),

    // Legacy Propulsion (for backward compatibility, use 'engines' map instead)
    @Deprecated("Use engines map with specific instance key", ReplaceWith("engines[\"0\"].revolutions"))
    val engineRpm: Double? = null, // Hz (Signal K standard)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].temperature"))
    val engineTemperature: Double? = null, // Kelvin (K)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].coolantTemperature"))
    val engineCoolantTemperature: Double? = null, // Kelvin (K)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].state"))
    val engineState: String? = null,
    @Deprecated("Use engines map keys")
    val engineInstance: String? = null,
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].oilPressure"))
    val engineOilPressure: Double? = null, // Pascal (Pa)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].fuelRate"))
    val fuelRate: Double? = null, // cubic meters per second
    val estimatedRange: Double? = null, // meters (Aggregate)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].runTime"))
    val engineRunTime: Double? = null, // Seconds (s)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].load"))
    val engineLoad: Double? = null, // Ratio 0-1
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].exhaustTemperature"))
    val engineExhaustTemperature: Double? = null, // Kelvin (K)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].transmissionGear"))
    val transmissionGear: String? = null,
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].transmissionPressure"))
    val transmissionPressure: Double? = null, // Pascal (Pa)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].transmissionOilTemperature"))
    val transmissionOilTemperature: Double? = null, // Kelvin (K)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].alternatorVoltage"))
    val alternatorVoltage: Double? = null, // Volt (V)
    @Deprecated("Use engines map", ReplaceWith("engines[\"0\"].alternatorCurrent"))
    val alternatorCurrent: Double? = null, // Ampere (A)

    // Electrical (Use 'batteries', 'chargers', or 'inverters' maps instead)
    @Deprecated("Use batteries map", ReplaceWith("batteries[\"0\"].voltage"))
    val batteryVoltage: Double? = null, // Volt (V)
    @Deprecated("Use batteries map", ReplaceWith("batteries[\"0\"].current"))
    val batteryCurrent: Double? = null, // Ampere (A)
    @Deprecated("Use batteries map", ReplaceWith("batteries[\"0\"].stateOfCharge"))
    val batterySoc: Double? = null, // Ratio 0-1
    @Deprecated("Use electrical paths in telemetry")
    val solarCurrent: Double? = null, // Ampere (A)
    @Deprecated("Use inverters map")
    val acSource: String? = null,
    @Deprecated("Use inverters map")
    val acVoltage: Double? = null, // Volt (V)
    @Deprecated("Use inverters map")
    val acCurrent: Double? = null, // Ampere (A)
    @Deprecated("Use inverters map")
    val acFrequency: Double? = null, // Hertz (Hz)
    @Deprecated("Use inverters map", ReplaceWith("inverters[\"0\"].state"))
    val inverterState: String? = null,
    @Deprecated("Use chargers map", ReplaceWith("chargers[\"0\"].state"))
    val chargerState: String? = null,

    // Legacy Tanks (Use 'tanks' map instead)
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"fuel.0\"].currentLevel"))
    val fuelLevel: Double? = null, // Ratio 0-1
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"freshWater.0\"].currentLevel"))
    val freshWaterLevel: Double? = null, // Ratio 0-1
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"wasteWater.0\"].currentLevel"))
    val wasteWaterLevel: Double? = null, // Ratio 0-1
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"greyWater.0\"].currentLevel"))
    val greyWaterLevel: Double? = null, // Ratio 0-1
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"lubeOil.0\"].currentLevel"))
    val lubeOilLevel: Double? = null, // Ratio 0-1
    @Deprecated("Use tanks map", ReplaceWith("tanks[\"gas.0\"].currentLevel"))
    val gasLevel: Double? = null, // Ratio 0-1

    // Performance
    val polarSpeedRatio: Double? = null,
    val racingTimer: Double? = null, // Seconds (s)

    // Communication
    val vhfChannel: String? = null,
    val crewNames: List<String> = emptyList(),
    val aisBuddies: Set<Int> = emptySet(),

    // Sails
    val sailInventory: List<Sail> = emptyList(),
    val reefs: Int? = null,
    val activeSailPlan: String? = null,

    // Design
    val airDraft: Double? = null, // Meters (m)
    val displacement: Double? = null, // Kilograms (kg)
    val engineHours: Double? = null, // Total run time in seconds (s)

    // Navigation Deviation (Cross-Track Error)
    val crossTrackError: Double? = null, // Meters (m)
    val xteMeters: Double? = null, // Meters (m)
    val xteDirection: XteDirection = XteDirection.ON_COURSE,
    val distanceToWaypoint: Double? = null,
    val timeToWaypoint: Double? = null, // Seconds (Signal K aggregate or derived)
    val serverNextPoint: net.osmand.plus.plugins.nautical.laylines.engine.LatLon? = null,
    val vmgTimeToWaypoint: Double? = null, // Seconds
    val sogTimeToWaypoint: Double? = null, // Seconds
    val isOffCourse: Boolean = false,
    val isOutsideSafetyCorridor: Boolean = false,
    val isStwUnreliable: Boolean = false,
    val rodeDeployed: Double? = null, // Meters
    val mediaInfo: MediaInfo? = null,

    // Collision Awareness
    val cpa: Double? = null, // Meters
    val tcpa: Double? = null, // Seconds
    val threatName: String? = null,

    // MOB State
    val isMobActive: Boolean = false,
    val mobLatitude: Double? = null,
    val mobLongitude: Double? = null,

    // Pypilot Specific
    val pypilotConfig: PypilotConfig? = null,
    val pypilotServo: PypilotServoState? = null,
    val pypilotCalibration: PypilotCalibrationState? = null,

    // Alarms and Notifications
    val notifications: Map<String, SignalKNotification> = emptyMap(),
    @kotlinx.serialization.Transient
    val checklists: Map<String, net.osmand.plus.plugins.nautical.network.SignalKChecklist> = emptyMap(),

    // Tide Data
    val tide: TideState? = null,

    // Custom Telemetry & Metadata
    val customValues: Map<String, Double> = emptyMap(),
    @kotlinx.serialization.Transient
    val pathMeta: Map<String, Map<String, @kotlinx.serialization.Contextual Any>> = emptyMap(),

    // Pending Commands (Reconciliation)
    val pendingTargetHeading: Double? = null,
    val pendingAutopilotState: String? = null,
    val pendingCommandPath: String? = null,
    val commandSentTimestamp: Long = 0,

    val timestamps: Map<String, Long> = emptyMap(),
    val stalePaths: Set<String> = emptySet(),

    // Temporal Fix Metadata (Phase 8.0AC)
    val timeOfHeadingFix: Long = 0,
    val timeOfSogFix: Long = 0,
    val timeOfWindFix: Long = 0,
    val timeOfDepthFix: Long = 0,
    val timeOfAttitudeFix: Long = 0,
    val timeOfPositionFix: Long = 0,
    val timeOfRotFix: Long = 0,
    val timeOfRudderFix: Long = 0,
    val timeOfDriftFix: Long = 0
) : Serializable

@kotlinx.serialization.Serializable
enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    STALE,
    UNAUTHORIZED,
    DISCONNECTED
}
