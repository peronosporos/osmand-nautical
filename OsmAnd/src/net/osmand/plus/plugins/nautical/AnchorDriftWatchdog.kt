package net.osmand.plus.plugins.nautical

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.anchor.AnchorTrackBuffer
import net.osmand.plus.plugins.nautical.anchor.TrackPoint
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.NotificationState
import net.osmand.plus.plugins.nautical.engine.hasValidFix
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs

/**
 * Background watchdog for anchor drift detection.
 * Implements signal filtering and time-delayed trigger to avoid false alarms.
 */
class AnchorDriftWatchdog(private val app: OsmandApplication) {

    enum class AnchorDriftStage {
        SAFE,
        CAUTION,       // 85% to 100% of swing radius
        CRITICAL_DRAG  // >100% of swing radius for 3 consecutive filtered points
    }

    private val log = PlatformUtil.getLog(AnchorDriftWatchdog::class.java)
    private val arbiter = NauticalAudioArbiter.getInstance(app)
    private var outOfBoundsCount = 0
    private var firstOutOfBoundsTimestampMs: Long = 0L
    private var isAlarmActive = false
    private var isGpsLostAlarmActive = false
    
    private var observationJob: Job? = null
    private val scope = CoroutineScope(NauticalDispatchers.SafetyDispatcher + SupervisorJob())

    private val _driftStage = MutableStateFlow(AnchorDriftStage.SAFE)
    val driftStage: StateFlow<AnchorDriftStage> = _driftStage.asStateFlow()

    private val _shallowHazardSector = MutableStateFlow<Pair<Double, Double>?>(null)
    val shallowHazardSector: StateFlow<Pair<Double, Double>?> = _shallowHazardSector.asStateFlow()

    private val _shallowHazardQuadrant = MutableStateFlow<String?>(null)
    val shallowHazardQuadrant: StateFlow<String?> = _shallowHazardQuadrant.asStateFlow()

    private val _rodeTensionKg = MutableStateFlow(0.0)
    val rodeTensionKg: StateFlow<Double> = _rodeTensionKg.asStateFlow()

    private val _isHighRodeLoad = MutableStateFlow(false)
    val isHighRodeLoad: StateFlow<Boolean> = _isHighRodeLoad.asStateFlow()

    private val _waveSurgeCycles = MutableStateFlow(0)
    val waveSurgeCycles: StateFlow<Int> = _waveSurgeCycles.asStateFlow()

    private val _isChafeAdvisoryActive = MutableStateFlow(false)
    val isChafeAdvisoryActive: StateFlow<Boolean> = _isChafeAdvisoryActive.asStateFlow()

    private var lastPitchRad: Double? = null
    private var pitchDirectionPositive = false

    fun trackWaveSurge(pitchRad: Double?, rollRad: Double?, windSpeedMps: Double?) {
        val currentPitch = pitchRad ?: rollRad ?: return
        val lastPitch = lastPitchRad
        if (lastPitch != null) {
            val delta = currentPitch - lastPitch
            if (kotlin.math.abs(Math.toDegrees(delta)) > 1.0) {
                val currentDirectionPositive = delta > 0
                if (currentDirectionPositive != pitchDirectionPositive) {
                    pitchDirectionPositive = currentDirectionPositive
                    _waveSurgeCycles.value++
                    
                    val windSpeedKn = (windSpeedMps ?: 0.0) * 1.94384
                    if (windSpeedKn > 20.0 && _waveSurgeCycles.value >= 2000) {
                        _isChafeAdvisoryActive.value = true
                    }
                }
            }
        }
        lastPitchRad = currentPitch
    }

    fun resetChafeCycleCounter() {
        _waveSurgeCycles.value = 0
        _isChafeAdvisoryActive.value = false
    }

    private val _isWindlassOverload = MutableStateFlow(false)
    val isWindlassOverload: StateFlow<Boolean> = _isWindlassOverload.asStateFlow()

    private var overloadStartTime = 0L

    private val _isWindShiftBreakoutRisk = MutableStateFlow(false)
    val isWindShiftBreakoutRisk: StateFlow<Boolean> = _isWindShiftBreakoutRisk.asStateFlow()

    private val _windShiftDeltaDeg = MutableStateFlow(0.0)
    val windShiftDeltaDeg: StateFlow<Double> = _windShiftDeltaDeg.asStateFlow()

    private val _predictedSwingAngleDeg = MutableStateFlow(0.0)
    val predictedSwingAngleDeg: StateFlow<Double> = _predictedSwingAngleDeg.asStateFlow()

    private val windHistory = ArrayDeque<Pair<Long, Double>>()

    fun monitorWindShift(windDeg: Double?, windSpeedMps: Double?) {
        if (windDeg == null) return
        val now = System.currentTimeMillis()
        windHistory.addLast(Pair(now, windDeg))

        while (windHistory.isNotEmpty() && (now - windHistory.first().first) > 900_000L) {
            windHistory.removeFirst()
        }

        if (windHistory.size >= 2) {
            val oldestWind = windHistory.first().second
            val shift = kotlin.math.abs(((windDeg - oldestWind + 540.0) % 360.0) - 180.0)
            val twsKn = (windSpeedMps ?: 0.0) * 1.94384
            _windShiftDeltaDeg.value = shift
            _predictedSwingAngleDeg.value = (windDeg + 180.0) % 360.0

            if (shift > 45.0 && twsKn > 20.0) {
                _isWindShiftBreakoutRisk.value = true
            } else {
                _isWindShiftBreakoutRisk.value = false
            }
        }
    }

    fun monitorWindlass(motorCurrentAmps: Double?, isHaulingIn: Boolean) {
        val now = System.currentTimeMillis()
        val current = motorCurrentAmps ?: 0.0

        // Spike > 85A (e.g. > 85% stall current of typical 100A windlass)
        if (isHaulingIn || current > 60.0) {
            if (current >= 85.0) {
                if (overloadStartTime == 0L) {
                    overloadStartTime = now
                } else if (now - overloadStartTime > 2000L) {
                    if (!_isWindlassOverload.value) {
                        _isWindlassOverload.value = true
                        net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                            net.osmand.plus.plugins.nautical.audio.AlarmType.ACTUATOR_OVERLOAD,
                            voiceText = "Anchor snagged, windlass overload"
                        )
                    }
                }
            } else {
                overloadStartTime = 0L
                _isWindlassOverload.value = false
            }
        } else {
            overloadStartTime = 0L
            _isWindlassOverload.value = false
        }
    }

    fun clearWindlassOverload() {
        overloadStartTime = 0L
        _isWindlassOverload.value = false
    }

    private val _trackHistory = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackHistory: StateFlow<List<TrackPoint>> = _trackHistory.asStateFlow()
    private val trackBuffer = AnchorTrackBuffer()

    private val gpsWindow = ArrayDeque<Pair<Double, Double>>(5)

    private var lastMapRefreshTime: Long = 0
    private val minMapRefreshIntervalMs = 500L

    fun computeRodeTension(windSpeedMps: Double?, rodeDeployedM: Double?, depthM: Double?): Double {
        val windSpeedKn = (windSpeedMps ?: 0.0) * 1.94384
        val stateDisplacement = NauticalPlugin.engine?.getCurrentState()?.displacement
        val displacementTons = (stateDisplacement ?: (app.settings.NAUTICAL_VESSEL_LENGTH.get().toDouble() * 1000.0)).coerceAtLeast(3000.0) / 1000.0
        val depth = depthM ?: 5.0
        val scopeRatio = if (rodeDeployedM != null && depth > 0.5) (rodeDeployedM / depth).coerceAtLeast(1.0) else 5.0
        val tensionKg = 0.004 * Math.pow(windSpeedKn, 2.0) * Math.pow(displacementTons, 2.0 / 3.0) / scopeRatio

        val chainSizeMm = 8.0
        val breakingLoadKg = 55.0 * Math.pow(chainSizeMm, 2.0)
        val swlThresholdKg = 0.40 * breakingLoadKg

        _rodeTensionKg.value = tensionKg
        _isHighRodeLoad.value = (tensionKg > swlThresholdKg) && (windSpeedKn > 12.0)
        return tensionKg
    }

    fun probeAnchorSwingDepthHazard(anchorLat: Double, anchorLon: Double, radiusM: Float) {
        if (anchorLat == 0.0 || anchorLon == 0.0 || radiusM <= 0f) {
            _shallowHazardSector.value = null
            _shallowHazardQuadrant.value = null
            return
        }

        val draft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        val keelSafety = app.settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
        val minSafeDepth = draft + keelSafety

        val degRadius = (radiusM / 111320.0) * 1.5
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val soundings = try {
            dbHelper.queryFeatures(
                anchorLat - degRadius, anchorLat + degRadius,
                anchorLon - degRadius, anchorLon + degRadius,
                listOf("SOUNDG", "DEPCNT", "DEPARE"),
                limit = 100
            )
        } catch (e: Exception) {
            emptyList()
        }

        var minBearing: Double? = null
        var maxBearing: Double? = null
        var countShallow = 0
        var sumBearing = 0.0

        for (f in soundings) {
            val depth = f.attributes["VALSOU"]?.toDoubleOrNull()
                ?: f.attributes["DRVAL1"]?.toDoubleOrNull()
                ?: continue

            if (depth < minSafeDepth) {
                for (geom in f.geometries) {
                    val latLon = when (geom) {
                        is net.osmand.plus.plugins.nautical.s57.S57Geometry.Point -> geom.position
                        is net.osmand.plus.plugins.nautical.s57.S57Geometry.Line -> geom.nodes.firstOrNull()
                        is net.osmand.plus.plugins.nautical.s57.S57Geometry.Area -> geom.boundaries.firstOrNull()?.firstOrNull()
                        else -> null
                    } ?: continue

                    val dist = net.osmand.util.MapUtils.getDistance(anchorLat, anchorLon, latLon.latitude, latLon.longitude)
                    if (dist <= radiusM * 1.1) {
                        val bearing = (net.osmand.shared.util.KMapUtils.getBearing(anchorLat, anchorLon, latLon.latitude, latLon.longitude) + 360.0) % 360.0
                        countShallow++
                        sumBearing += bearing
                        minBearing = if (minBearing == null) bearing else minOf(minBearing, bearing)
                        maxBearing = if (maxBearing == null) bearing else maxOf(maxBearing, bearing)
                    }
                }
            }
        }

        if (countShallow > 0 && minBearing != null && maxBearing != null) {
            val avgBearing = sumBearing / countShallow
            val quadrant = when (avgBearing) {
                in 0.0..90.0 -> "NE"
                in 90.0..180.0 -> "SE"
                in 180.0..270.0 -> "SW"
                else -> "NW"
            }
            _shallowHazardSector.value = Pair((minBearing - 15.0).coerceAtLeast(0.0), (maxBearing + 15.0).coerceAtMost(360.0))
            _shallowHazardQuadrant.value = quadrant
        } else {
            _shallowHazardSector.value = null
            _shallowHazardQuadrant.value = null
        }
    }

    private fun requestThrottledMapRefresh() {
        val pm = app.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == false) return // Suppress if screen is off
        val mapActivity = app.osmandMap?.mapView?.mapActivity
        if (mapActivity == null || mapActivity.isActivityDestroyed || mapActivity.isFinishing) return // Suppress if map activity is not active
        val now = System.currentTimeMillis()
        if (now - lastMapRefreshTime >= minMapRefreshIntervalMs) {
            lastMapRefreshTime = now
            app.runInUIThread { app.osmandMap?.refreshMap() }
        }
    }

    companion object {
        private const val CONSECUTIVE_PINGS_THRESHOLD = 3
        private const val MIN_OUT_OF_BOUNDS_DURATION_MS = 5000L
    }

    fun start() {
        observationJob?.cancel()
        observationJob = scope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collect { state ->
                if (!state.hasValidFix) return@collect
                // 1. Sync with server-side anchor notifications
                state.notifications["notifications.navigation.anchor"]?.let { notification ->
                    if ((notification.state == NotificationState.ALARM) || (notification.state == NotificationState.EMERGENCY)) {
                        if (!isAlarmActive) {
                            triggerAlarm("[Server] ${notification.message}")
                        }
                    } else if ((notification.state == NotificationState.NORMAL) && isAlarmActive) {
                        // Remote acknowledgement
                        stopAlarm()
                    }
                }

                // 2. Synchronize server-side anchor position with local settings
                state.anchor?.let { serverAnchor ->
                    val sLat = serverAnchor.latitude
                    val sLon = serverAnchor.longitude
                    val sRadius = serverAnchor.radius ?: serverAnchor.maxDrift
                    
                    val isLocked = app.settings.NAUTICAL_ANCHOR_LOCKED_LOCALLY.get()
                    
                    if ((sLat != null) && (sLon != null) && (sRadius != null)) {
                        val currentLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
                        val currentLon = app.settings.NAUTICAL_ANCHOR_LON.get()
                        val currentRadius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()
                        
                        val dist = KMapUtils.getDistance(currentLat, currentLon, sLat, sLon)
                        val radiusDiff = abs(currentRadius - sRadius)

                        if (!isLocked) {
                            if (dist > 1.0 || radiusDiff > 1.0) {
                                log.info("AnchorWatch: Syncing local anchor settings from server.")
                                app.settings.NAUTICAL_ANCHOR_LAT.set(sLat)
                                app.settings.NAUTICAL_ANCHOR_LON.set(sLon)
                                app.settings.NAUTICAL_ANCHOR_RADIUS.set(sRadius.toFloat())
                                requestThrottledMapRefresh()
                            }
                        } else {
                            // Item 13 Fix: Warn if locked but desynced significantly
                            if (dist > 50.0) {
                                app.runInUIThread {
                                    NauticalPlugin.hudManager?.get()?.showBanner(
                                        app.getString(R.string.nautical_anchor_desync_warning),
                                        15000L,
                                        label = app.getString(R.string.nautical_sync_now),
                                        isWarning = true,
                                        onConfirm = {
                                            app.settings.NAUTICAL_ANCHOR_LOCKED_LOCALLY.set(false)
                                            app.settings.NAUTICAL_ANCHOR_LAT.set(sLat)
                                            app.settings.NAUTICAL_ANCHOR_LON.set(sLon)
                                            app.settings.NAUTICAL_ANCHOR_RADIUS.set(sRadius.toFloat())
                                            requestThrottledMapRefresh()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Compute Rode Tension and Wave Surge Chafe Cycles
                computeRodeTension(
                    windSpeedMps = state.windSpeedTrue ?: state.windSpeedApparent,
                    rodeDeployedM = state.rodeDeployed,
                    depthM = state.depthBelowKeel ?: state.depthBelowTransducer
                )
                trackWaveSurge(
                    pitchRad = state.pitch,
                    rollRad = state.roll,
                    windSpeedMps = state.windSpeedTrue ?: state.windSpeedApparent
                )
                val windlassCurrent = state.actuatorCurrent ?: state.batteries.values.firstOrNull { (it.name ?: "").contains("windlass", ignoreCase = true) }?.current
                val isHauling = state.switches["electrical.switches.windlass.up"] == true
                monitorWindlass(windlassCurrent, isHauling)

                // 4. Handle position updates for fallback calculation
                if (state.connectionStatus == ConnectionStatus.DISCONNECTED) {
                    onGpsLost()
                } else {
                    val lat = state.latitude
                    val lon = state.longitude
                    if ((lat != null) && (lon != null)) {
                        val loc = Location("signalk")
                        loc.latitude = lat
                        loc.longitude = lon
                        loc.accuracy = 5.0f // Filtered data is considered accurate
                        onLocationChanged(loc)
                    }
                }
            }
        }
        log.info("AnchorWatchdog: Started observing MarineState.")
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
        scope.coroutineContext.cancelChildren()
        reset()
        
        // Task: Remote Disarm
        NauticalPlugin.engine?.disarmAnchor()

        log.info("AnchorWatchdog: Stopped.")
    }

    fun onAppBackgrounded() {
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        if (anchorLat == 0.0) {
            log.info("AnchorWatchdog: Suspending location processing in background (Disarmed).")
            observationJob?.cancel()
            observationJob = null
        } else {
            log.info("AnchorWatchdog: Continuing background processing (Armed).")
        }
    }

    fun onAppForegrounded() {
        if (observationJob == null) {
            log.info("AnchorWatchdog: Resuming location processing.")
            start()
        }
    }

    private fun applyMedianFilter(lat: Double, lon: Double): Pair<Double, Double> {
        synchronized(gpsWindow) {
            if (gpsWindow.size >= 5) {
                gpsWindow.removeFirst()
            }
            gpsWindow.addLast(Pair(lat, lon))

            if (gpsWindow.size < 3) {
                return Pair(lat, lon)
            }

            val sortedLats = gpsWindow.map { it.first }.sorted()
            val sortedLons = gpsWindow.map { it.second }.sorted()
            val medianLat = sortedLats[sortedLats.size / 2]
            val medianLon = sortedLons[sortedLons.size / 2]
            return Pair(medianLat, medianLon)
        }
    }

    /**
     * Processes a new location update.
     * Returns true if alarm is triggered or remains active.
     */
    fun onLocationChanged(location: Location): Boolean {
        if (isGpsLostAlarmActive) {
            onGpsRestored()
        }

        // Apply 5-point rolling window median filter on incoming coordinates
        val (filteredLat, filteredLon) = applyMedianFilter(location.latitude, location.longitude)
        val filteredLocation = Location(location.provider).apply {
            latitude = filteredLat
            longitude = filteredLon
            accuracy = location.accuracy
            time = location.time
        }

        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val windDeg = state?.windDirectionTrue?.let { Math.toDegrees(it) }
            ?: state?.trueWindAngle?.let { Math.toDegrees(it) }
        val currentDeg = state?.setTrue?.let { Math.toDegrees(it) }

        monitorWindShift(windDeg, state?.windSpeedTrue)

        // Feed position to the Snail Trail buffer regardless of mode
        if (trackBuffer.addPosition(filteredLocation, windDeg, currentDeg)) {
            _trackHistory.value = trackBuffer.getPoints()
            requestThrottledMapRefresh()
        }

        // Concurrent Safety: We always run local calculation as a validator,
        // even if Signal K is connected, to guard against server-side misconfiguration.
        
        // Smart Offloading (TASK-CPU-001): If Signal K server is actively monitoring,
        // skip local math to save cycles.
        val caps = engine?.capabilityManager?.capabilities?.value
        
        val canOffload = (state?.connectionStatus == ConnectionStatus.CONNECTED) && 
                        (caps?.hasAnchorAlarm == true) && 
                        (state?.anchor?.state?.lowercase() in listOf("armed", "active"))
        
        if (canOffload) {
            return isAlarmActive
        }

        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get()
        var radius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()
        if (radius <= 0f) {
            radius = computeFallbackSwingRadius()
        }

        if ((anchorLat == 0.0) || (anchorLon == 0.0) || (radius <= 0f)) {
            reset()
            return false
        }

        // 1. Signal Filtering: Reject low accuracy pings
        val accuracyThreshold = app.settings.NAUTICAL_ANCHOR_ACCURACY_THRESHOLD.get().toDouble()
        if (location.hasAccuracy() && (location.accuracy > accuracyThreshold)) {
            log.info("AnchorWatch: Ignoring low accuracy fix: ${location.accuracy}m (Threshold: ${accuracyThreshold}m)")
            return isAlarmActive
        }

        val distance = KMapUtils.getDistance(anchorLat, anchorLon, filteredLat, filteredLon)

        // 2. Swing-Depth Integration
        checkShallowSwing()

        // 3. Multi-Stage Drift Detection:
        //    - Caution Stage (85% to 100% of swing radius): silent warning state
        //    - Critical Drag Stage (>100% of swing radius for 3 consecutive filtered points AND >= 5000ms continuous drift): triggers Level 2 CRITICAL alert
        if (distance > radius) {
            val now = System.currentTimeMillis()
            if (firstOutOfBoundsTimestampMs == 0L) {
                firstOutOfBoundsTimestampMs = now
            }
            outOfBoundsCount++
            val durationOutOfBounds = now - firstOutOfBoundsTimestampMs
            log.warn("AnchorWatch (Local): Vessel outside boundary. Count: $outOfBoundsCount, Duration: ${durationOutOfBounds}ms, Dist: ${distance.toInt()}m, Radius: ${radius.toInt()}m")
            
            // Temporal Debounce: require >= 5000ms continuous drift AND >= 3 consecutive fixes
            if (outOfBoundsCount >= CONSECUTIVE_PINGS_THRESHOLD && durationOutOfBounds >= MIN_OUT_OF_BOUNDS_DURATION_MS) {
                _driftStage.value = AnchorDriftStage.CRITICAL_DRAG
                if (!isAlarmActive) {
                    triggerAlarm()
                }
            } else {
                _driftStage.value = AnchorDriftStage.CAUTION
            }
        } else if (distance >= (radius * 0.85)) {
            _driftStage.value = AnchorDriftStage.CAUTION
            if (outOfBoundsCount > 0) {
                outOfBoundsCount = 0
                firstOutOfBoundsTimestampMs = 0L
            }
        } else {
            _driftStage.value = AnchorDriftStage.SAFE
            if (outOfBoundsCount > 0) {
                log.info("AnchorWatch (Local): Vessel back in safe boundary. Resetting counter.")
                outOfBoundsCount = 0
                firstOutOfBoundsTimestampMs = 0L
            }
            if (isAlarmActive && (distance < (radius * 0.85))) { 
                stopAlarm()
            }
        }

        return isAlarmActive
    }

    fun onGpsLost() {
        if (!isGpsLostAlarmActive) {
            log.error("GPS SIGNAL LOST DURING ANCHOR WATCH!")
            isGpsLostAlarmActive = true
            triggerAlarm(app.getString(R.string.nautical_anchor_gps_lost_alarm))
        }
    }

    private fun onGpsRestored() {
        log.info("GPS signal restored during anchor watch")
        isGpsLostAlarmActive = false
        stopAlarm()
    }

    private fun triggerAlarm(customText: String? = null) {
        val mobActive = NauticalPlugin.engine?.getCurrentState()?.isMobActive == true
        if (mobActive) {
            log.warn("AnchorWatch: Active MOB emergency. Anchor drift might be the cause or concurrent!")
        }

        log.error("ANCHOR ALARM TRIGGERED: ${customText ?: "DRIFT"}")
        isAlarmActive = true
        
        val text = customText ?: app.getString(R.string.nautical_anchor_drift_alarm)
        
        arbiter.dispatchAlarm(AlarmType.ANCHOR_DRIFT, voiceText = text)
        
        // Post critical Android notification
        NauticalPlugin.getInstance()?.notificationManager?.postCriticalNotification(
            "anchor_drift",
            app.getString(R.string.nautical_anchor_label),
            text,
        )
        
        // Wake screen via Plugin
        NauticalPlugin.getInstance()?.forceEmergencyBrightness()

        // Single-tap Silence Alarm banner on Map HUD
        NauticalPlugin.getInstance()?.let {
            app.runInUIThread {
                NauticalPlugin.hudManager?.get()?.showBanner(
                    text,
                    durationMs = 60000L,
                    label = app.getString(R.string.nautical_silence_alarm),
                    isWarning = true,
                    onConfirm = {
                        stopAlarm()
                    },
                    secondaryLabel = app.getString(R.string.nautical_disarm_anchor),
                    onSecondaryConfirm = {
                        disarm()
                    }
                )
            }
        }
    }

    fun stopAlarm() {
        if (isAlarmActive || isGpsLostAlarmActive) {
            log.info("Silencing anchor alarm")
            arbiter.muteAlarm(AlarmType.ANCHOR_DRIFT, 60000L) // 1 minute silence
            isAlarmActive = false
            isGpsLostAlarmActive = false
            outOfBoundsCount = 0
            firstOutOfBoundsTimestampMs = 0L
        }
    }

    fun disarm() {
        log.info("AnchorWatch: DISARMING definitively.")
        stopAlarm()
        app.settings.NAUTICAL_ANCHOR_LAT.set(0.0)
        app.settings.NAUTICAL_ANCHOR_LON.set(0.0)
        app.settings.NAUTICAL_ANCHOR_RADIUS.set(0f)
        stop()
        requestThrottledMapRefresh()
    }

    fun computeFallbackSwingRadius(): Float {
        val depth = app.settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
        val scopeRatio = app.settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toDouble().coerceAtLeast(1.0)
        val safetyMargin = app.settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get().toDouble()
        val bowOffset = app.settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
        val computed = (depth * scopeRatio) + safetyMargin + bowOffset
        return computed.toFloat().coerceAtLeast(15.0f)
    }

    fun setAnchor(latitude: Double, longitude: Double, radius: Float = 0f) {
        val finalRadius = if (radius > 0f) radius else {
            val current = app.settings.NAUTICAL_ANCHOR_RADIUS.get()
            if (current > 0f) current else computeFallbackSwingRadius()
        }
        app.settings.NAUTICAL_ANCHOR_LAT.set(latitude)
        app.settings.NAUTICAL_ANCHOR_LON.set(longitude)
        app.settings.NAUTICAL_ANCHOR_RADIUS.set(finalRadius)
        resetCounter()

        scope.launch {
            probeAnchorSwingDepthHazard(latitude, longitude, finalRadius)
        }
        
        // Task: Write-Back to Signal K
        val plugin = NauticalPlugin.getInstance()
        plugin?.pluginScope?.launch {
            val engine = NauticalPlugin.engine
            val rest = engine?.getRestService()
            if (rest != null) {
                try {
                    // Modern SK Course v2 Anchor path
                    val anchor = net.osmand.plus.plugins.nautical.network.SignalKAnchor(
                        latitude = latitude,
                        longitude = longitude,
                        radius = radius.toDouble(),
                    )
                    val response = rest.updateCourse(net.osmand.plus.plugins.nautical.network.SignalKCourse(anchor = anchor))
                    if (response.isSuccessful) {
                        log.info("AnchorWatch: Successfully pushed local anchor to Signal K.")
                    } else {
                        // Fallback to generic delta
                        engine.sendDelta(
                            "navigation.anchor",
                            mapOf(
                                "latitude" to latitude,
                                "longitude" to longitude,
                                "radius" to radius,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    log.error("Failed to push anchor to server: ${e.message}")
                }
            }
        }
        requestThrottledMapRefresh()
    }

    /**
     * Resets the out-of-bounds counter and snail trail buffer.
     * Called when the anchor position is manually updated to avoid false triggers
     * based on old positions.
     */
    fun resetCounter() {
        log.info("AnchorWatch: Resetting watchdog state for new anchor position.")
        outOfBoundsCount = 0
        firstOutOfBoundsTimestampMs = 0L
        synchronized(gpsWindow) {
            gpsWindow.clear()
        }
        _driftStage.value = AnchorDriftStage.SAFE
    }

    fun reset() {
        stopAlarm()
        outOfBoundsCount = 0
        firstOutOfBoundsTimestampMs = 0L
        synchronized(gpsWindow) {
            gpsWindow.clear()
        }
        _driftStage.value = AnchorDriftStage.SAFE
    }

    private fun checkShallowSwing() {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val depth = state.depthBelowKeel ?: return
        
        val safetyContour = app.settings.getCustomRenderProperty("safetyContour", "5.0").get().toDoubleOrNull() ?: 5.0
        
        if (depth < safetyContour) {
            log.warn("AnchorWatch: Shallow swing detected! Depth: ${depth}m < Safety: ${safetyContour}m")
            triggerAlarm(app.getString(R.string.nautical_anchor_shallow_swing_alarm, depth))
        }

        // Task 11: Anchor Type Consistency Check (Dragging Suspected)
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get()
        if ((anchorLat != 0.0) && (state.latitude != null) && (state.longitude != null)) {
            val distance = KMapUtils.getDistance(anchorLat, anchorLon, state.latitude, state.longitude)
            
            // Advanced Drag Logic: Account for Scope Ratio and Water Depth (TASK-110)
            val anchorDepth = app.settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
            val tideRise = app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
            val freeboard = app.settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
            val scopeRatio = app.settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toDouble().coerceAtLeast(1.0)
            
            val totalVertical = anchorDepth + tideRise + freeboard
            
            // If we have chain counter data, we use it. Otherwise fallback to preferred scope ratio.
            val effectiveRode = state.rodeDeployed ?: (totalVertical * scopeRatio)
            
            val maxTheoreticalSwing = if (effectiveRode > totalVertical) {
                kotlin.math.sqrt((effectiveRode * effectiveRode) - (totalVertical * totalVertical))
            } else {
                5.0 // Minimum safety floor
            }

            val bowOffset = app.settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
            val userSafetyMargin = app.settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get().toDouble()
            val totalAllowedDistance = maxTheoreticalSwing + userSafetyMargin + bowOffset

            // If distance > totalAllowedDistance, anchor is dragging
            if (distance > totalAllowedDistance) {
                log.error("AnchorWatch: Dragging Suspected! Distance $distance m > Allowed Swing $totalAllowedDistance m")
                triggerAlarm(app.getString(R.string.nautical_anchor_dragging_alarm, distance, totalAllowedDistance, effectiveRode.toInt(), anchorDepth))
            }
        }
    }
}
