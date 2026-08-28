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
    val tanks = marineState.map { it.tanks }.distinctUntilChanged()
    val cpa = marineState.map { it.cpa }.distinctUntilChanged()
    val tcpa = marineState.map { it.tcpa }.distinctUntilChanged()
    val threatName = marineState.map { it.threatName }.distinctUntilChanged()

    val magneticVariation = marineState.map { it.magneticVariation }.distinctUntilChanged()
    val yaw = marineState.map { it.yaw }.distinctUntilChanged()

    val autopilotState: StateFlow<String> = marineState
        .map { it.autopilotState }
        .stateIn(scope, SharingStarted.Eagerly, "standby")

    val autopilotTargetHeadingMag: StateFlow<Double?> = marineState
        .map { it.autopilotHeadingSet }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _manualOverrideTriggered = MutableSharedFlow<Unit>(replay = 0)
    val manualOverrideTriggered: SharedFlow<Unit> = _manualOverrideTriggered.asSharedFlow()

    enum class PacketCategory {
        ALL,
        NAVIGATION,
        ENGINES_TANKS,
        ALARMS_AIS,
        ERRORS
    }

    data class DiagnosticPacket(
        val timestamp: Long,
        val path: String,
        val value: String,
        val category: PacketCategory,
        val isError: Boolean = false
    )

    private val _livePackets = MutableSharedFlow<DiagnosticPacket>(extraBufferCapacity = 200)
    val livePackets: SharedFlow<DiagnosticPacket> = _livePackets.asSharedFlow()

    fun recordDiagnosticPacket(path: String, value: Any?, isError: Boolean = false) {
        val cat = when {
            isError -> PacketCategory.ERRORS
            path.startsWith("navigation") || path.startsWith("environment") -> PacketCategory.NAVIGATION
            path.startsWith("propulsion") || path.startsWith("tanks") || path.startsWith("electrical") -> PacketCategory.ENGINES_TANKS
            path.startsWith("notifications") || path.startsWith("ais") || path.startsWith("alarm") -> PacketCategory.ALARMS_AIS
            else -> PacketCategory.NAVIGATION
        }
        _livePackets.tryEmit(
            DiagnosticPacket(
                timestamp = System.currentTimeMillis(),
                path = path,
                value = value?.toString() ?: "null",
                category = cat,
                isError = isError
            )
        )
    }

    // Throttling and Threshold settings
    private var throttleInterval = (settings?.NAUTICAL_TELEMETRY_REFRESH_BASE_MS?.get() ?: 100).milliseconds
    private var angleThreshold = Math.toRadians((settings?.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG?.get() ?: 0.5f).toDouble())
    private var speedThreshold = (settings?.NAUTICAL_EMA_SPEED_THRESHOLD_MS?.get() ?: 0.05f).toDouble()

    // High-Frequency Conflation Buffer (Atomic Bitfield Containers)
    private val rawHeadingTrueBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val rawHeadingMagBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val rawPitchBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val rawRollBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val rawWindAngleBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val rawWindSpeedBits = java.util.concurrent.atomic.AtomicLong(Double.NaN.toBits())
    private val hasPendingHfUpdates = java.util.concurrent.atomic.AtomicBoolean(false)

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
    private var lastExternalGnssTime = 0L
    private var lastExternalHeadingTime = 0L
    private var isFallbackBannerShown = false

    init {
        startConflationDispatcher()
        startSensorFallbackWatchdog()
    }

    /**
     * Starts the 60Hz (16ms) sample-and-hold conflation dispatcher.
     * Batches high-frequency updates into MarineState atomically with zero allocations.
     */
    private fun startConflationDispatcher() {
        scope.launch {
            while (isActive) {
                delay(16.milliseconds) // ~60Hz dispatch loop
                if (hasPendingHfUpdates.compareAndSet(true, false)) {
                    flushHighFrequencyTelemetry()
                }
            }
        }
    }

    /**
     * Telemetry Source Health & Auto-Fallback Watchdog:
     * If external Signal K GNSS/Heading drops for >3.0 seconds, smoothly falls back
     * to Android internal GNSS / sensor fusion without interrupting route tracking or navigation layers.
     */
    private fun startSensorFallbackWatchdog() {
        scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                val now = System.currentTimeMillis()
                val current = _marineState.value

                val isExternalGnssStale = (lastExternalGnssTime > 0L) && ((now - lastExternalGnssTime) > 3000L)
                val isExternalHeadingStale = (lastExternalHeadingTime > 0L) && ((now - lastExternalHeadingTime) > 3000L)

                if (isExternalGnssStale || isExternalHeadingStale || (current.connectionStatus == ConnectionStatus.CONNECTED && current.timeOfFix > 0L && (now - current.timeOfFix) > 3000L)) {
                    val app = NauticalPlugin.getInstance()?.app
                    val internalLoc = app?.locationProvider?.lastKnownLocation

                    if (internalLoc != null) {
                        _marineState.update { st ->
                            st.copy(
                                latitude = internalLoc.latitude,
                                longitude = internalLoc.longitude,
                                speedOverGround = (internalLoc.speed.toDouble()).coerceAtLeast(0.0),
                                courseOverGroundTrue = if (internalLoc.hasBearing()) Math.toRadians(internalLoc.bearing.toDouble()) else st.courseOverGroundTrue,
                                isInternalSensorFallback = true,
                                timeOfFix = now
                            )
                        }

                        if (!isFallbackBannerShown) {
                            isFallbackBannerShown = true
                            NauticalPlugin.hudManager?.get()?.showBanner("TELEMETRY FALLBACK: INTERNAL SENSORS ACTIVE", 8000L, isWarning = true, priority = 2)
                        }
                    }
                } else if ((now - lastExternalGnssTime) <= 1500L && isFallbackBannerShown) {
                    isFallbackBannerShown = false
                    _marineState.update { it.copy(isInternalSensorFallback = false) }
                }
            }
        }
    }

    fun ingestHighFrequencyHeading(headingTrueRad: Double, isMagnetic: Boolean = false) {
        if (isMagnetic) {
            rawHeadingMagBits.set(headingTrueRad.toBits())
        } else {
            rawHeadingTrueBits.set(headingTrueRad.toBits())
        }
        hasPendingHfUpdates.set(true)
    }

    fun ingestHighFrequencyAttitude(pitchRad: Double, rollRad: Double) {
        rawPitchBits.set(pitchRad.toBits())
        rawRollBits.set(rollRad.toBits())
        hasPendingHfUpdates.set(true)
    }

    fun ingestHighFrequencyWind(angleRad: Double, speedMps: Double) {
        rawWindAngleBits.set(angleRad.toBits())
        rawWindSpeedBits.set(speedMps.toBits())
        hasPendingHfUpdates.set(true)
    }

    @Synchronized
    private fun flushHighFrequencyTelemetry() {
        val now = System.currentTimeMillis()
        val hTrue = Double.fromBits(rawHeadingTrueBits.getAndSet(Double.NaN.toBits()))
        val hMag = Double.fromBits(rawHeadingMagBits.getAndSet(Double.NaN.toBits()))
        val pitch = Double.fromBits(rawPitchBits.getAndSet(Double.NaN.toBits()))
        val roll = Double.fromBits(rawRollBits.getAndSet(Double.NaN.toBits()))
        val windAngle = Double.fromBits(rawWindAngleBits.getAndSet(Double.NaN.toBits()))
        val windSpeed = Double.fromBits(rawWindSpeedBits.getAndSet(Double.NaN.toBits()))

        _marineState.update { current ->
            var state = current
            if (!hTrue.isNaN()) {
                val smoothed = headingEma.update(hTrue)
                lastHeadingTime = now
                state = state.copy(headingTrue = smoothed, timeOfHeadingFix = now)
            }
            if (!hMag.isNaN()) {
                lastHeadingTime = now
                state = state.copy(headingMagnetic = hMag, timeOfHeadingFix = now)
            }
            if (!pitch.isNaN()) {
                val smoothed = pitchEma.update(pitch)
                lastPitchTime = now
                state = state.copy(pitch = smoothed, timeOfAttitudeFix = now)
            }
            if (!roll.isNaN()) {
                val smoothed = rollEma.update(roll)
                lastRollTime = now
                state = state.copy(roll = smoothed, timeOfAttitudeFix = now)
            }
            if (!windAngle.isNaN()) {
                val offsetDeg = settings?.NAUTICAL_WIND_ALIGNMENT?.get() ?: 0.0f
                val offsetRad = Math.toRadians(offsetDeg.toDouble())
                val corrected = (((windAngle + offsetRad) % (2 * Math.PI)) + (2 * Math.PI)) % (2 * Math.PI)
                val smoothed = windAngleEma.update(corrected)
                lastWindAngleTime = now
                state = state.copy(windDirectionApparent = smoothed, timeOfWindFix = now)
            }
            if (!windSpeed.isNaN()) {
                val smoothed = windSpeedEma.update(windSpeed)
                lastWindSpeedTime = now
                state = state.copy(windSpeedApparent = smoothed, timeOfWindFix = now)
            }
            state
        }
    }

    @Synchronized
    fun applyHeadingTrueUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("navigation.headingTrue", String.format(java.util.Locale.US, "%.1f°", Math.toDegrees(value)))
        lastExternalHeadingTime = now
        val smoothed = headingEma.update(value)
        return if (shouldUpdate(smoothed, state.headingTrue, now, lastHeadingTime, angleThreshold)) {
            lastHeadingTime = now
            state.copy(headingTrue = smoothed, timeOfHeadingFix = now)
        } else state
    }

    @Synchronized
    fun applyHeadingMagneticUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("navigation.headingMagnetic", String.format(java.util.Locale.US, "%.1f°", Math.toDegrees(value)))
        lastExternalHeadingTime = now
        return if ((now - lastHeadingTime) > throttleInterval.inWholeMilliseconds) {
            state.copy(headingMagnetic = value, timeOfHeadingFix = now)
        } else state
    }

    @Synchronized
    fun applyWindAngleApparentUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("environment.wind.angleApparent", String.format(java.util.Locale.US, "%.1f°", Math.toDegrees(value)))
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
    fun applyWindSpeedApparentUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("environment.wind.speedApparent", String.format(java.util.Locale.US, "%.1f kn", value * 1.94384))
        val smoothed = windSpeedEma.update(value)
        return if (shouldUpdate(smoothed, state.windSpeedApparent, now, lastWindSpeedTime, speedThreshold)) {
            lastWindSpeedTime = now
            state.copy(windSpeedApparent = smoothed, timeOfWindFix = now)
        } else state
    }

    @Synchronized
    fun applyDepthUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("environment.depth.belowTransducer", String.format(java.util.Locale.US, "%.2f m", value))
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

    private var lastStwValue: Double? = null
    private var lastSogValue: Double? = null
    private var stwUnreliableStartTime: Long = 0

    @Synchronized
    fun applySpeedOverGroundUpdate(state: MarineState, value: Double, now: Long): MarineState {
        recordDiagnosticPacket("navigation.speedOverGround", String.format(java.util.Locale.US, "%.1f kn", value * 1.94384))
        lastExternalGnssTime = now
        lastSogValue = value
        val unreliable = calculateStwReliabilityStatus(state, now)
        return state.copy(speedOverGround = value, timeOfSogFix = now, isStwUnreliable = unreliable)
    }

    @Synchronized
    fun applySpeedThroughWaterUpdate(state: MarineState, value: Double, now: Long): MarineState {
        lastStwValue = value
        val unreliable = calculateStwReliabilityStatus(state, now)
        return state.copy(speedThroughWater = value, timeOfSogFix = now, isStwUnreliable = unreliable)
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
    fun applyPitchUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = pitchEma.update(value)
        return if (shouldUpdate(smoothed, state.pitch, now, lastPitchTime, angleThreshold)) {
            lastPitchTime = now
            state.copy(pitch = smoothed, timeOfAttitudeFix = now)
        } else state
    }

    @Synchronized
    fun applyRudderUpdate(state: MarineState, value: Double, now: Long): MarineState {
        val smoothed = rudderEma.update(value)
        
        val shadowDriveEnabled = settings?.NAUTICAL_SHADOW_DRIVE?.get() ?: true
        if (shadowDriveEnabled && (state.autopilotState != "standby") && (state.pendingCommandPath == null)) {
            state.rudderAngle?.let { lastRudder ->
                if (abs(smoothed - lastRudder) > Math.toRadians(4.0)) {
                    scope.launch { _manualOverrideTriggered.emit(Unit) }
                }
            }
        }

        return if (shouldUpdate(smoothed, state.rudderAngle, now, lastRudderTime, angleThreshold)) {
            lastRudderTime = now
            state.copy(rudderAngle = smoothed, timeOfRudderFix = now)
        } else state
    }

    private val pressureHistory = ArrayDeque<Pair<Long, Double>>()

    @Synchronized
    fun applyAtmosphericPressureUpdate(state: MarineState, pressurePa: Double, now: Long): MarineState {
        val hPa = pressurePa / 100.0
        pressureHistory.addLast(Pair(now, hPa))

        // Purge samples older than 3 hours
        val cutoff = now - (3 * 3600 * 1000L)
        while (pressureHistory.isNotEmpty() && pressureHistory.first().first < cutoff) {
            pressureHistory.removeFirst()
        }

        // Calculate 3-hour delta P
        val oldest = pressureHistory.firstOrNull()?.second ?: hPa
        val deltaP3h = hPa - oldest

        val tendencySymbol = when {
            deltaP3h < -3.0 -> "--"
            deltaP3h < -0.5 -> "-"
            deltaP3h > 0.5 -> "+"
            else -> "~"
        }

        val isSquall = deltaP3h < -3.0
        if (isSquall && !state.isSquallAdvisoryActive) {
            try {
                NauticalPlugin.hudManager?.get()?.showBanner(
                    "BAROMETRIC SQUALL ADVISORY: Rapid pressure drop ${String.format(java.util.Locale.US, "%.1f", deltaP3h)} hPa / 3h",
                    12000L,
                    isWarning = true
                )
            } catch (e: Exception) {
                // ignore
            }
        }

        recordDiagnosticPacket("environment.outside.pressure", String.format(java.util.Locale.US, "%.1f hPa (%s %.1f)", hPa, tendencySymbol, deltaP3h))

        return state.copy(
            outsidePressure = pressurePa,
            atmosphericPressureHpa = hPa,
            barometricTendency3hHpa = deltaP3h,
            barometricTendencySymbol = tendencySymbol,
            isSquallAdvisoryActive = isSquall
        )
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

    fun updateAutopilotState(state: String) {
        _marineState.update { it.copy(autopilotState = state) }
    }

    fun updateState(transform: (MarineState) -> MarineState) {
        _marineState.update(transform)
    }

    private val _livePerformanceData = MutableStateFlow(LivePerformanceData())
    val livePerformanceData: StateFlow<LivePerformanceData> = _livePerformanceData.asStateFlow()

    fun updatePerformanceData(data: LivePerformanceData) {
        _livePerformanceData.value = data
    }

    /**
     * Returns true if general Signal K telemetry or connection is stale (> timeoutMs).
     */
    fun isStale(timeoutMs: Long = 5000L): Boolean {
        return _marineState.value.isStale(timeoutMs)
    }

    /**
     * Checks whether a specific critical telemetry channel (GPS, SOG, Wind, Depth, Rudder, etc.)
     * has not received an update for longer than timeoutMs.
     */
    fun isChannelStale(channel: TelemetryChannel, timeoutMs: Long = 5000L): Boolean {
        return _marineState.value.isChannelStale(channel, timeoutMs)
    }

    /**
     * Gets the last update timestamp in epoch milliseconds for a specific telemetry channel.
     */
    fun getLastUpdatedTimestamp(channel: TelemetryChannel): Long {
        return _marineState.value.getLastUpdatedTimestampMs(channel)
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
        // Consolidated into SignalKSessionManager
    }

    private var deadReckoningJob: Job? = null

    init {
        startDeadReckoning()
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

