package net.osmand.plus.plugins.nautical.engine

import java.io.Serializable
import net.osmand.data.LatLon
import net.osmand.plus.settings.enums.XteDirection

@kotlinx.serialization.Serializable
data class Engine(
    val instance: String = "0",
    val revolutions: Double? = null,
    val temperature: Double? = null,
    val coolantTemperature: Double? = null,
    val oilPressure: Double? = null,
    val oilTemperature: Double? = null,
    val fuelRate: Double? = null,
    val fuelEconomy: Double? = null,
    val load: Double? = null,
    val exhaustTemperature: Double? = null,
    val transmissionGear: String? = null,
    val transmissionPressure: Double? = null,
    val transmissionOilTemperature: Double? = null,
    val alternatorVoltage: Double? = null,
    val alternatorCurrent: Double? = null,
    val boostPressure: Double? = null,
    val driveTrimState: Double? = null,
    val runTime: Double? = null,
    val state: String? = null
) : Serializable

@kotlinx.serialization.Serializable
data class Battery(
    val instance: String,
    val name: String? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val temperature: Double? = null,
    val stateOfCharge: Double? = null,
    val stateOfHealth: Double? = null,
    val timeRemaining: Double? = null,
    val timeToFull: Double? = null,
    val cellVoltages: List<Double> = emptyList()
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
data class Charger(
    val instance: String,
    val name: String? = null,
    val state: String? = null,
    val mode: String? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val temperature: Double? = null
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
    val temperature: Double? = null,
    val load: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class Watermaker(
    val instance: String,
    val state: String? = null,
    val rate: Double? = null,
    val salinity: Double? = null,
    val totalProduction: Double? = null
) : Serializable

@kotlinx.serialization.Serializable
data class MediaInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val playbackState: String? = null,
    val source: String? = null,
    val volume: Double? = null,
    val volumeZones: Map<String, Double> = emptyMap()
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
data class Sail(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean = false,
    val area: Double? = null,
    val reefs: Int? = null,
    val maxReefs: Int? = null
) : Serializable

@kotlinx.serialization.Serializable
data class GnssState(
    val method: String? = null,
    val satellites: Int? = null,
    val horizontalDilution: Double? = null,
    val verticalDilution: Double? = null,
    val integrity: String? = null,
    val integrityFlags: List<String> = emptyList()
) : Serializable

@kotlinx.serialization.Serializable
data class AnchorState(
    val state: String? = null,
    @kotlinx.serialization.Contextual
    val position: LatLon? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Double? = null,
    val maxRadius: Double? = null,
    val rodeLength: Double? = null,
    val maxDrift: Double? = null,
    val selection: String? = null
) : Serializable

@kotlinx.serialization.Serializable
data class TideState(
    val stationName: String? = null,
    val stationId: String? = null,
    val height: Double? = null,
    val heightNow: Double? = null,
    val state: String? = null,
    val nextExtreme: String? = null,
    val nextExtremeType: String? = null,
    val nextExtremeHeight: Double? = null,
    val nextExtremeTime: Long? = null
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
enum class NotificationState(val priority: Int) {
    NORMAL(0),
    ALERT(1),
    WARN(2),
    ALARM(3),
    EMERGENCY(4)
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
data class MarineState(
    // Vessel Info
    val vesselName: String? = null,
    val vesselType: Int? = null,
    val vesselCallSign: String? = null,
    val vesselCallsign: String? = null,
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
    val anchorState: AnchorState? = null,
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

    // Autopilot Status
    val autopilotState: String = "standby",
    val autopilotHeadingSet: Double? = null, // Radians (rad)
    val autopilotWindAngleSet: Double? = null, // Radians (rad)
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val isInternalSensorFallback: Boolean = false,

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
    val tidalCurrentSpeed: Double? get() = drift
    val tidalCurrentDirection: Double? get() = setTrue
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
    val actuatorAlarmAcknowledged: Boolean = false,
    val isShunted: Boolean = false,
    val isEngineRunning: Boolean = false,
    val watchdogStatus: SignalKNotification? = null,
    val forwardHazards: List<ForwardHazard> = emptyList(),

    // Environment
    val waterTemperature: Double? = null, // Kelvin (K)
    val waterSalinity: Double? = null, // Ratio 0-1
    val outsideTemperature: Double? = null, // Kelvin (K)
    val outsidePressure: Double? = null, // Pascal (Pa)
    val atmosphericPressureHpa: Double? = null,
    val barometricTendency3hHpa: Double? = null,
    val barometricTendencySymbol: String? = null, // "+", "~", "-", "--"
    val isSquallAdvisoryActive: Boolean = false,
    val outsideHumidity: Double? = null, // Ratio 0-1
    val outsideIlluminance: Double? = null, // Lux
    val airDewPoint: Double? = null, // Kelvin (K)
    val moonPhase: Double? = null, // Ratio 0-1
    val moonIllumination: Double? = null, // Ratio 0-1
    val sunrise: String? = null,
    val sunset: String? = null,
    val sunlightMode: String? = null,

    // Propulsion & Systems (Multi-instance support)
    val engines: Map<String, Engine> = emptyMap(),
    val batteries: Map<String, Battery> = emptyMap(),
    val tanks: Map<String, Tank> = emptyMap(),
    val switches: Map<String, Boolean> = emptyMap(),
    val dimmers: Map<String, Double> = emptyMap(),
    val chargers: Map<String, Charger> = emptyMap(),
    val inverters: Map<String, Inverter> = emptyMap(),
    val riggingLoads: Map<String, Double> = emptyMap(),
    val watermakers: Map<String, Watermaker> = emptyMap(),
    val batteryAutonomyHours: Double? = null,
    val batteryHourlyConsumptionAh: Double? = null,
    val solarYieldWatts: Double? = null,

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
    val flags: List<String> = emptyList(),
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
    @kotlinx.serialization.Contextual
    val serverNextPoint: LatLon? = null,
    val vmgTimeToWaypoint: Double? = null, // Seconds
    val sogTimeToWaypoint: Double? = null, // Seconds
    val rhumbLineBearing: Double? = null,
    val rhumbLineDistance: Double? = null,
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

    val isDeadReckoning: Boolean = false,

    // Pypilot Specific
    val pypilotConfig: PypilotConfig? = null,
    val pypilotServo: PypilotServoState? = null,
    val pypilotCalibration: PypilotCalibrationState? = null,

    // Alarms and Notifications
    val notifications: Map<String, SignalKNotification> = emptyMap(),
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
    val lastMessageTime: Long = 0,

    // Orchestration
    val isochrones: List<net.osmand.plus.plugins.nautical.network.SignalKRegion> = emptyList(),
    val activePlugins: Set<String> = emptySet(),
    val polarProfile: net.osmand.plus.plugins.nautical.network.PolarProfile? = null,
    val lastIsochroneTime: Long = 0,
    val recordingStability: Boolean = false,
    val recordingPointCount: Int = 0,
    val routeConditions: Map<Int, Double> = emptyMap(), // Waypoint index to wind speed example

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

val MarineState.hasValidFix: Boolean
    get() {
        val lat = latitude ?: 0.0
        val lon = longitude ?: 0.0
        val timestamp = timestamps["navigation.position"] ?: 0L
        val age = System.currentTimeMillis() - timestamp
        return (lat != 0.0 || lon != 0.0) && age < 30000
    }

val MarineState.activeSailEfficiency: Double
    get() {
        if (sailInventory.isEmpty()) {
            val mainReef = reefs ?: 0
            val mainFactor = when (mainReef) {
                0 -> 0.50
                1 -> 0.40
                2 -> 0.30
                else -> 0.20
            }
            return (mainFactor + 0.50).coerceIn(0.1, 2.0)
        }
        var mainFactor = 0.0
        var headsailFactor = 0.0
        var downwindFactor = 0.0
        for (sail in sailInventory) {
            if (!sail.active) continue
            val type = sail.type.lowercase(java.util.Locale.US)
            val name = sail.name.lowercase(java.util.Locale.US)
            val baseAreaFactor = (sail.area ?: 1.0).coerceIn(0.2, 2.5)
            when {
                type.contains("main") || name.contains("main") -> {
                    val reef = sail.reefs ?: reefs ?: 0
                    val reefMultiplier = when (reef) {
                        0 -> 1.0
                        1 -> 0.80
                        2 -> 0.60
                        3 -> 0.40
                        else -> 0.30
                    }
                    mainFactor = 0.50 * (baseAreaFactor.coerceAtMost(1.0)) * reefMultiplier
                }
                type.contains("genoa") || name.contains("genoa") -> {
                    headsailFactor = maxOf(headsailFactor, 0.50 * 1.40)
                }
                type.contains("jib") || name.contains("jib") || type.contains("solent") || type.contains("staysail") -> {
                    val factor = if (name.contains("storm") || type.contains("storm")) 0.30
                        else if (name.contains("solent") || type.contains("solent") || name.contains("staysail") || type.contains("staysail")) 0.75
                        else 1.00
                    headsailFactor = maxOf(headsailFactor, 0.50 * factor)
                }
                type.contains("spin") || name.contains("spin") || type.contains("code") || name.contains("code") || type.contains("gennaker") -> {
                    val factor = if (name.contains("code") || type.contains("code")) 1.50 else 1.80
                    downwindFactor = maxOf(downwindFactor, 0.50 * factor)
                }
                else -> {
                    headsailFactor = maxOf(headsailFactor, 0.50 * baseAreaFactor)
                }
            }
        }
        val headOrDown = maxOf(headsailFactor, downwindFactor)
        val total = mainFactor + headOrDown
        return if (total <= 0.0) 0.1 else total.coerceIn(0.1, 2.0)
    }

@kotlinx.serialization.Serializable
enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    STALE,
    UNAUTHORIZED,
    DISCONNECTED
}

@kotlinx.serialization.Serializable
enum class TelemetryChannel {
    GPS_POSITION,
    SOG_COG,
    WIND,
    DEPTH,
    RUDDER,
    HEADING,
    ATTITUDE
}

fun MarineState.getLastUpdatedTimestampMs(channel: TelemetryChannel): Long {
    return when (channel) {
        TelemetryChannel.GPS_POSITION -> timeOfPositionFix
        TelemetryChannel.SOG_COG -> timeOfSogFix
        TelemetryChannel.WIND -> timeOfWindFix
        TelemetryChannel.DEPTH -> timeOfDepthFix
        TelemetryChannel.RUDDER -> timeOfRudderFix
        TelemetryChannel.HEADING -> timeOfHeadingFix
        TelemetryChannel.ATTITUDE -> timeOfAttitudeFix
    }
}

fun MarineState.isChannelStale(channel: TelemetryChannel, timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean {
    val lastFix = getLastUpdatedTimestampMs(channel)
    return lastFix == 0L || (now - lastFix) > timeoutMs
}

fun MarineState.isGpsStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.GPS_POSITION, timeoutMs, now)

fun MarineState.isSogStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.SOG_COG, timeoutMs, now)

fun MarineState.isWindStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.WIND, timeoutMs, now)

fun MarineState.isDepthStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.DEPTH, timeoutMs, now)

fun MarineState.isRudderStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.RUDDER, timeoutMs, now)

fun MarineState.isHeadingStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean =
    isChannelStale(TelemetryChannel.HEADING, timeoutMs, now)

fun MarineState.isStale(timeoutMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean {
    return connectionStatus == ConnectionStatus.STALE ||
           connectionStatus == ConnectionStatus.DISCONNECTED ||
           lastMessageTime == 0L ||
           (now - lastMessageTime) > timeoutMs
}

@kotlinx.serialization.Serializable
data class SignalKNotification(
    val message: String,
    val state: NotificationState,
    val methods: List<String> = emptyList(),
    val source: String? = null
) : Serializable
