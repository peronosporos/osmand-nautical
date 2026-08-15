package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.utils.AngleEMA
import net.osmand.plus.plugins.nautical.utils.EMA
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages SignalK data streams with throttling and threshold-based filtering.
 * Uses atomic StateFlow updates to ensure thread safety and prevent torn reads.
 */
class SignalKDataBroker(private val settings: OsmandSettings? = null) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _marineState = MutableStateFlow(MarineState())
    val marineState: StateFlow<MarineState> = _marineState.asStateFlow()

    /**
     * Visual State Flow: Emits only when critical visual fields change significantly.
     * Thresholds: 10m for Position, 0.5 degrees for Heading/COG.
     */
    val visualState: Flow<MarineState> = marineState.distinctUntilChanged { old, new ->
        val posChange = if ((old.latitude != null) && (old.longitude != null) && (new.latitude != null) && (new.longitude != null)) {
            KMapUtils.getDistance(old.latitude, old.longitude, new.latitude, new.longitude) > 10.0
        } else {
            (old.latitude != new.latitude) || (old.longitude != new.longitude)
        }

        val hdgChange = abs(Math.toDegrees((old.headingTrue ?: 0.0) - (new.headingTrue ?: 0.0))) > 0.5
        val cogChange = abs(Math.toDegrees((old.courseOverGroundTrue ?: 0.0) - (new.courseOverGroundTrue ?: 0.0))) > 0.5
        val envChange = (abs((old.depthBelowKeel ?: 0.0) - (new.depthBelowKeel ?: 0.0)) > 0.1) ||
                        (abs((old.windSpeedApparent ?: 0.0) - (new.windSpeedApparent ?: 0.0)) > 0.5)
        val statusChange = (old.connectionStatus != new.connectionStatus) || (old.isMobActive != new.isMobActive) || (old.autopilotState != new.autopilotState)
        
        // Return true to SKIP emission (if NO significant change)
        !(posChange || hdgChange || cogChange || envChange || statusChange)
    }

    // Unified Smoothed Flows
    val headingTrue = marineState.map { it.headingTrue }.distinctUntilChanged()
    val windAngleApparent = marineState.map { it.windDirectionApparent }.distinctUntilChanged()
    val windSpeedApparent = marineState.map { it.windSpeedApparent }.distinctUntilChanged()
    val depthBelowKeel = marineState.map { it.depthBelowKeel }.distinctUntilChanged()
    val rudderAngle = marineState.map { it.rudderAngle }.distinctUntilChanged()
    val gnss = marineState.map { it.gnss }.distinctUntilChanged()
    val batterySoc = marineState.map { it.batteries["0"]?.stateOfCharge }.distinctUntilChanged()
    val tanks = marineState.map { it.tanks }.distinctUntilChanged()
    val cpa = marineState.map { it.cpa }.distinctUntilChanged()
    val tcpa = marineState.map { it.tcpa }.distinctUntilChanged()
    val threatName = marineState.map { it.threatName }.distinctUntilChanged()

    val magneticVariation = marineState.map { it.magneticVariation }.distinctUntilChanged()
    val yaw = marineState.map { it.yaw }.distinctUntilChanged()
    val riggingLoads = marineState.map { it.riggingLoads }.distinctUntilChanged()
    val acSystems = marineState.map { it.inverters.values + it.chargers.values }.distinctUntilChanged()

    val autopilotState: StateFlow<String> = marineState
        .map { it.autopilotState }
        .stateIn(scope, SharingStarted.Eagerly, "standby")

    val autopilotTargetHeadingMag: StateFlow<Double?> = marineState
        .map { it.autopilotHeadingSet }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _manualOverrideTriggered = MutableSharedFlow<Unit>(replay = 0)
    val manualOverrideTriggered: SharedFlow<Unit> = _manualOverrideTriggered.asSharedFlow()

    // Throttling and Threshold settings
    private var throttleInterval = (settings?.NAUTICAL_TELEMETRY_REFRESH_BASE_MS?.get() ?: 100).milliseconds
    private var angleThreshold = Math.toRadians((settings?.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG?.get() ?: 0.5f).toDouble())
    private var speedThreshold = (settings?.NAUTICAL_EMA_SPEED_THRESHOLD_MS?.get() ?: 0.05f).toDouble()

    // EMA Smoothing filters
    private val headingEma = AngleEMA(settings?.NAUTICAL_EMA_ALPHA_HEADING?.get()?.toDouble() ?: 0.2)
    private val windAngleEma = AngleEMA(settings?.NAUTICAL_EMA_ALPHA_WIND_ANGLE?.get()?.toDouble() ?: 0.2)
    private val windSpeedEma = EMA(settings?.NAUTICAL_EMA_ALPHA_WIND_SPEED?.get()?.toDouble() ?: 0.2)
    private val depthEma = EMA(settings?.NAUTICAL_EMA_ALPHA_DEPTH?.get()?.toDouble() ?: 0.1)
    private val rudderEma = AngleEMA(settings?.NAUTICAL_EMA_ALPHA_RUDDER?.get()?.toDouble() ?: 0.3)
    private val simulatedRudderEma = AngleEMA(0.15) // Damping for virtual rudder
    private val rollEma = AngleEMA(0.2)
    private val pitchEma = AngleEMA(0.2)

    private var lastHeadingTime = 0L
    private var lastWindAngleTime = 0L
    private var lastWindSpeedTime = 0L
    private var lastRollTime = 0L
    private var lastPitchTime = 0L
    private var lastRudderTime = 0L

    @Synchronized
    fun applyHeadingUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = headingEma.update(value)
        return if (shouldUpdate(smoothed, state.headingTrue, now, lastHeadingTime, angleThreshold)) {
            lastHeadingTime = now
            state.copy(headingTrue = smoothed, timeOfHeadingFix = now)
        } else state
    }

    @Synchronized
    fun processHeadingUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyHeadingUpdate(it, value, now) }
    }

    @Synchronized
    fun applyWindAngleUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val offsetDeg = settings?.NAUTICAL_WIND_ALIGNMENT?.get() ?: 0.0f
        val offsetRad = Math.toRadians(offsetDeg.toDouble())
        val correctedValue = (((value + offsetRad) % (2 * Math.PI)) + (2 * Math.PI)) % (2 * Math.PI)
        val smoothed = windAngleEma.update(correctedValue)

        return if (shouldUpdate(smoothed, state.windDirectionApparent, now, lastWindAngleTime, angleThreshold)) {
            lastWindAngleTime = now
            state.copy(windDirectionApparent = smoothed, timeOfWindFix = now)
        } else state
    }

    @Synchronized
    fun processWindAngleUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyWindAngleUpdate(it, value, now) }
    }

    @Synchronized
    fun applyWindSpeedUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = windSpeedEma.update(value)
        return if (shouldUpdate(smoothed, state.windSpeedApparent, now, lastWindSpeedTime, speedThreshold)) {
            lastWindSpeedTime = now
            state.copy(windSpeedApparent = smoothed, timeOfWindFix = now)
        } else state
    }

    @Synchronized
    fun processWindSpeedUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyWindSpeedUpdate(it, value, now) }
    }

    @Synchronized
    fun applyDepthUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val safetyManager = NauticalPlugin.getInstance()?.safetyManager
        val keelOffset = safetyManager?.getKeelOffset() ?: settings?.NAUTICAL_KEEL_OFFSET?.get()?.toDouble() ?: 0.0
        val trueDepth = value + keelOffset
        val smoothed = depthEma.update(trueDepth)
        
        return state.copy(
            depthBelowTransducer = value,
            depthBelowKeel = smoothed,
            timeOfDepthFix = now,
        )
    }

    @Synchronized
    fun processDepthUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyDepthUpdate(it, value, now) }
    }

    private var lastStwValue: Double? = null
    private var lastSogValue: Double? = null
    private var stwUnreliableStartTime: Long = 0

    @Synchronized
    fun applySogUpdate(state: MarineState, value: Double, now: Long): MarineState {
        lastSogValue = value
        val unreliable = calculateStwReliabilityStatus(state, now)
        return state.copy(speedOverGround = value, timeOfSogFix = now, isStwUnreliable = unreliable)
    }

    @Synchronized
    fun processSogUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applySogUpdate(it, value, now) }
    }

    @Synchronized
    fun applyStwUpdate(state: MarineState, value: Double, now: Long): MarineState {
        lastStwValue = value
        val unreliable = calculateStwReliabilityStatus(state, now)
        return state.copy(speedThroughWater = value, timeOfSogFix = now, isStwUnreliable = unreliable)
    }

    @Synchronized
    fun processStwUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyStwUpdate(it, value, now) }
    }

    private fun calculateStwReliabilityStatus(state: MarineState, now: Long): Boolean {
        val stw = lastStwValue ?: return state.isStwUnreliable
        val sog = lastSogValue ?: return state.isStwUnreliable
        
        // Link to profile-specific settings (TASK-LOGIC-001)
        val minStw = settings?.NAUTICAL_STW_REL_MIN_STW?.get()?.toDouble() ?: 0.1
        val minSog = settings?.NAUTICAL_STW_REL_MIN_SOG?.get()?.toDouble() ?: 1.03 // ~2 knots
        val delayMs = (settings?.NAUTICAL_STW_REL_DELAY_SEC?.get() ?: 10) * 1000L
        
        // Indicating a fouled paddlewheel: STW is near zero while SOG is steady above stall threshold
        val isPotentiallyUnreliable = (stw < minStw) && (sog > minSog) 
        
        return if (isPotentiallyUnreliable) {
            if (stwUnreliableStartTime == 0L) {
                stwUnreliableStartTime = now
                state.isStwUnreliable
            } else if ((now - stwUnreliableStartTime) > delayMs) { 
                true
            } else {
                state.isStwUnreliable
            }
        } else {
            stwUnreliableStartTime = 0
            false
        }
    }

    @Synchronized
    fun applyRollUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = rollEma.update(value)
        return if (shouldUpdate(smoothed, state.roll, now, lastRollTime, angleThreshold)) {
            lastRollTime = now
            state.copy(roll = smoothed, timeOfAttitudeFix = now)
        } else state
    }

    @Synchronized
    fun processRollUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyRollUpdate(it, value, now) }
    }

    @Synchronized
    fun applyPitchUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = pitchEma.update(value)
        return if (shouldUpdate(smoothed, state.pitch, now, lastPitchTime, angleThreshold)) {
            lastPitchTime = now
            state.copy(pitch = smoothed, timeOfAttitudeFix = now)
        } else state
    }

    @Synchronized
    fun processPitchUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyPitchUpdate(it, value, now) }
    }

    @Synchronized
    fun applyRudderUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = rudderEma.update(value)
        
        val shadowDriveEnabled = settings?.NAUTICAL_SHADOW_DRIVE?.get() ?: true
        if (shadowDriveEnabled && (state.autopilotState != "standby") && (state.pendingCommandPath == null)) {
            state.rudderAngle?.let { lastRudder ->
                if (abs(smoothed - lastRudder) > Math.toRadians(8.0)) {
                    scope.launch { _manualOverrideTriggered.emit(Unit) }
                }
            }
        }

        return if (shouldUpdate(smoothed, state.rudderAngle, now, lastRudderTime, angleThreshold)) {
            lastRudderTime = now
            state.copy(rudderAngle = smoothed, timeOfRudderFix = now)
        } else state
    }

    @Synchronized
    fun processRudderUpdate(value: Double) {
        val now = TemporalUtils.now()
        _marineState.update { applyRudderUpdate(it, value, now) }
    }

    // ITEM 6 FIX: External control for DR to avoid GPS conflicts
    fun setDeadReckoningActive(active: Boolean) {
        if (!active && _marineState.value.isDeadReckoning) {
            _marineState.update { it.copy(isDeadReckoning = false) }
        }
    }

    fun updateSimulatedRudder(target: Double) {
        val now = TemporalUtils.now()
        val smoothed = simulatedRudderEma.update(target)
        _marineState.update { it.copy(simulatedRudderAngle = smoothed, timeOfRudderFix = now) }
    }

    fun processVariationUpdate(value: Double) {
        _marineState.update { it.copy(magneticVariation = value) }
    }

    fun updateAutopilotState(state: String) {
        _marineState.update { it.copy(autopilotState = state) }
    }

    fun updateAutopilotTargetHeadingMag(value: Double) {
        _marineState.update { it.copy(autopilotHeadingSet = value) }
    }

    fun processTideUpdate(transform: (TideState?) -> TideState) {
        _marineState.update { it.copy(tide = transform(it.tide)) }
    }

    fun updateClosestApproach(cpaVal: Double?, tcpaVal: Double?, name: String? = null) {
        _marineState.update { it.copy(cpa = cpaVal, tcpa = tcpaVal, threatName = name ?: it.threatName) }
    }

    fun updateState(transform: (MarineState) -> MarineState) {
        _marineState.update(transform)
    }

    private val _livePerformanceData = MutableStateFlow(LivePerformanceData())
    val livePerformanceData: StateFlow<LivePerformanceData> = _livePerformanceData.asStateFlow()

    fun updatePerformanceData(data: LivePerformanceData) {
        _livePerformanceData.value = data
    }

    fun updateTuning() {
        if (settings == null) return
        headingEma.alpha = settings.NAUTICAL_EMA_ALPHA_HEADING.get().toDouble()
        windAngleEma.alpha = settings.NAUTICAL_EMA_ALPHA_WIND_ANGLE.get().toDouble()
        windSpeedEma.alpha = settings.NAUTICAL_EMA_ALPHA_WIND_SPEED.get().toDouble()
        depthEma.alpha = settings.NAUTICAL_EMA_ALPHA_DEPTH.get().toDouble()
        rudderEma.alpha = settings.NAUTICAL_EMA_ALPHA_RUDDER.get().toDouble()
        throttleInterval = settings.NAUTICAL_TELEMETRY_REFRESH_BASE_MS.get().milliseconds
        angleThreshold = Math.toRadians(settings.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG.get().toDouble())
        speedThreshold = settings.NAUTICAL_EMA_SPEED_THRESHOLD_MS.get().toDouble()
        
        // Task: Reactive restart of staleness monitoring if watchdog timeout changed
        startStalenessMonitoring()
    }

    private var deadReckoningJob: Job? = null
    private var stalenessJob: Job? = null

    init {
        startDeadReckoning()
        startStalenessMonitoring()
    }

    private fun startStalenessMonitoring() {
        stalenessJob?.cancel()
        stalenessJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                val now = TemporalUtils.now()
                val timeoutMs = (settings?.NAUTICAL_WATCHDOG_TIMEOUT_SEC?.get() ?: 10) * 1000L
                
                _marineState.update { state ->
                    val stale = state.timestamps.filter { (_, ts) ->
                        (now - ts) > timeoutMs
                    }.keys
                    if (stale != state.stalePaths) {
                        state.copy(stalePaths = stale)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun startDeadReckoning() {
        deadReckoningJob?.cancel()
        deadReckoningJob = scope.launch {
            while (isActive) {
                delay(500.milliseconds)
                val current = _marineState.value
                val isStale = (current.connectionStatus == ConnectionStatus.STALE) || (current.connectionStatus == ConnectionStatus.DISCONNECTED)
                val drEnabled = settings?.NAUTICAL_ENABLE_AUTO_DR?.get() ?: false
                
                if (isStale && drEnabled) {
                    val lat = current.latitude
                    val lon = current.longitude
                    val cog = current.courseOverGroundTrue
                    val sog = current.speedOverGround
                    
                    if ((lat != null) && (lon != null) && (cog != null) && (sog != null) && (sog > 0.1)) {
                        // Apply Dead Reckoning: move 0.5s worth of distance
                        val distanceMeters = sog * 0.5
                        val next = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, Math.toDegrees(cog), distanceMeters)
                        
                        _marineState.update { s ->
                            s.copy(latitude = next.latitude, longitude = next.longitude, isDeadReckoning = true)
                        }
                    }
                } else if (current.isDeadReckoning) {
                    _marineState.update { it.copy(isDeadReckoning = false) }
                }
            }
        }
    }

    fun stop() {
        deadReckoningJob?.cancel()
        stwUnreliableStartTime = 0L
        scope.cancel()
    }

    private fun shouldUpdate(newValue: Double, lastValue: Double?, now: Long, lastTime: Long, threshold: Double): Boolean {
        if (lastValue == null) return true
        if ((now - lastTime) < throttleInterval.inWholeMilliseconds) return false
        return abs(newValue - lastValue) > threshold
    }
}

