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

    private val log = PlatformUtil.getLog(AnchorDriftWatchdog::class.java)
    private val arbiter = NauticalAudioArbiter.getInstance(app)
    private var outOfBoundsCount = 0
    private var isAlarmActive = false
    private var isGpsLostAlarmActive = false
    
    private var observationJob: Job? = null
    private val scope = CoroutineScope(NauticalDispatchers.SafetyDispatcher + SupervisorJob())

    private val _trackHistory = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackHistory: StateFlow<List<TrackPoint>> = _trackHistory.asStateFlow()
    private val trackBuffer = AnchorTrackBuffer()

    private var lastMapRefreshTime: Long = 0
    private val minMapRefreshIntervalMs = 500L

    private fun requestThrottledMapRefresh() {
        val pm = app.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == false) return // Suppress if screen is off
        val mapView = app.osmandMap?.mapView
        if (mapView == null || !mapView.isShown) return // Suppress if map view is paused or hidden
        val now = System.currentTimeMillis()
        if (now - lastMapRefreshTime >= minMapRefreshIntervalMs) {
            lastMapRefreshTime = now
            app.runInUIThread { app.osmandMap?.refreshMap() }
        }
    }

    companion object {
        private const val CONSECUTIVE_PINGS_THRESHOLD = 3
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

                // 3. Handle position updates for fallback calculation
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

    /**
     * Processes a new location update.
     * Returns true if alarm is triggered or remains active.
     */
    fun onLocationChanged(location: Location): Boolean {
        if (isGpsLostAlarmActive) {
            onGpsRestored()
        }

        // Feed position to the Snail Trail buffer regardless of mode
        if (trackBuffer.addPosition(location)) {
            _trackHistory.value = trackBuffer.getPoints()
            requestThrottledMapRefresh()
        }

        // Concurrent Safety: We always run local calculation as a validator,
        // even if Signal K is connected, to guard against server-side misconfiguration.
        
        // Smart Offloading (TASK-CPU-001): If Signal K server is actively monitoring,
        // skip local math to save cycles.
        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val caps = engine?.capabilityManager?.capabilities?.value
        
        val canOffload = (state?.connectionStatus == ConnectionStatus.CONNECTED) && 
                        (caps?.hasAnchorAlarm == true) && 
                        (state.anchor?.state?.lowercase() in listOf("armed", "active"))
        
        if (canOffload) {
            return isAlarmActive
        }

        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = app.settings.NAUTICAL_ANCHOR_LON.get()
        val radius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()

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

        val distance = KMapUtils.getDistance(anchorLat, anchorLon, location.latitude, location.longitude)

        // 2. Swing-Depth Integration
        checkShallowSwing()

        if (distance > radius) {
            outOfBoundsCount++
            log.warn("AnchorWatch (Local): Vessel outside boundary. Count: $outOfBoundsCount, Dist: ${distance.toInt()}m, Radius: ${radius.toInt()}m")
            
            if ((outOfBoundsCount >= CONSECUTIVE_PINGS_THRESHOLD) && (!isAlarmActive)) {
                triggerAlarm()
            }
        } else {
            if (outOfBoundsCount > 0) {
                log.info("AnchorWatch (Local): Vessel back in boundary. Resetting counter.")
                outOfBoundsCount = 0
            }
            if (isAlarmActive && (distance < (radius * 0.9))) { 
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

    fun setAnchor(latitude: Double, longitude: Double, radius: Float) {
        app.settings.NAUTICAL_ANCHOR_LAT.set(latitude)
        app.settings.NAUTICAL_ANCHOR_LON.set(longitude)
        app.settings.NAUTICAL_ANCHOR_RADIUS.set(radius)
        resetCounter()
        
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
    }

    fun reset() {
        stopAlarm()
        outOfBoundsCount = 0
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
