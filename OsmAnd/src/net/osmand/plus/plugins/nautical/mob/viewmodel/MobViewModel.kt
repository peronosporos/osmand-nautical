package net.osmand.plus.plugins.nautical.mob.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import net.osmand.data.LatLon
import net.osmand.plus.R
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.PluginsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.mob.engine.*
import net.osmand.shared.util.KMapUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * UI State for the MOB interface.
 */
data class MobUiState(
    val isMobActive: Boolean = false,
    val distanceMeters: Double? = null,
    val bearingDegrees: Double? = null,
    val etaSeconds: Double? = null,
    val mobLocation: LatLon? = null,
    val estimatedCasualtyLocation: LatLon? = null,
    val uncertaintyRadiusMeters: Double = 0.0,
    val state: MobState = MobState.INACTIVE,
    val isMotoring: Boolean = false,
    val isUpwind: Boolean = true,
    val isSearching: Boolean = false,
    val muteUntil: Long = 0L,
    val activeMobCount: Int = 0,
    val sarPatternWaypoints: List<LatLon> = emptyList()
)

enum class MobTriggerSource {
    BUTTON,
    MAP
}

/**
 * ViewModel for Man Overboard functionality.
 * Coordinates between the engine, state machine, audio alerts, and persistence.
 */
class MobViewModel(
    private val app: OsmandApplication,
    private val stateMachine: MobStateMachine,
    private val audioManager: MobAudioAlertManager
) : ViewModel(), OsmAndLocationProvider.OsmAndLocationListener {

    private val _uiState = MutableStateFlow(MobUiState())
    val uiState: StateFlow<MobUiState> = _uiState.asStateFlow()
    
    private var guidanceJob: Job? = null

    init {
        // Observe search state from engine
        NauticalPlugin.engine?.let { engine ->
            viewModelScope.launch {
                while (true) {
                    _uiState.update { it.copy(isSearching = engine.isFollowingRoute) }
                    delay(1000.milliseconds)
                }
            }
        }
        
        // Observe MarineState for propulsion-aware UI and remote MOB synchronization
        NauticalPlugin.engine?.marineStateFlow
            ?.onEach { state ->
                val propManager = net.osmand.plus.plugins.nautical.engine.PropulsionContextManager.getInstance(app)
                val twa = state.trueWindAngle ?: 0.0
                val twaDeg = Math.toDegrees(twa)
                _uiState.update { current ->
                    current.copy(
                        isMotoring = propManager.isEngineRunning(),
                        // Optimized for Heave-To: close-hauled is 30-45 deg
                        isUpwind = abs(twaDeg) < 60.0 
                    )
                }

                // ITEM 7: Remote MOB Synchronization
                // If engine reports MOB but state machine is inactive, trigger local state
                if (state.isMobActive && stateMachine.mobStatus.value.state == MobState.INACTIVE) {
                    val lat = state.mobLatitude ?: state.latitude ?: return@onEach
                    val lon = state.mobLongitude ?: state.longitude ?: return@onEach
                    triggerMob(LatLon(lat, lon), MobTriggerSource.MAP) // Remote is usually 'map' or 'system'
                }
            }
            ?.launchIn(viewModelScope)

        // Observe state machine changes
        stateMachine.mobStatus
            .onEach { status ->
                val active = status.state == MobState.ACTIVE_EMERGENCY
                val event = status.event
                val dropLoc = event?.dropLocation
                val state = NauticalPlugin.engine?.getCurrentState()
                val driftMps = state?.drift ?: 0.0
                val setTrueRad = state?.setTrue ?: 0.0
                val twsMps = state?.windSpeedTrue
                val twdDeg: Double? = state?.windDirectionTrue?.let { Math.toDegrees(it) }

                val (estimatedLoc, uncertainty) = if (active && event != null && dropLoc != null) {
                    val timeElapsedSec = (System.currentTimeMillis() - event.dropTimestamp) / 1000.0
                    val tideDx = driftMps * kotlin.math.sin(setTrueRad)
                    val tideDy = driftMps * kotlin.math.cos(setTrueRad)
                    val effectiveTws = twsMps ?: (10.0 * 0.514444)
                    val leewaySpeedMps = 0.03 * effectiveTws
                    val downwindDeg: Double = if (twdDeg != null) (twdDeg + 180.0) % 360.0 else Math.toDegrees(setTrueRad)
                    val leewayDx = leewaySpeedMps * kotlin.math.sin(Math.toRadians(downwindDeg))
                    val leewayDy = leewaySpeedMps * kotlin.math.cos(Math.toRadians(downwindDeg))
                    val totalVx = tideDx + leewayDx
                    val totalVy = tideDy + leewayDy
                    val totalSpeed = kotlin.math.sqrt(totalVx * totalVx + totalVy * totalVy)
                    val totalBearing = (Math.toDegrees(kotlin.math.atan2(totalVx, totalVy)) + 360.0) % 360.0
                    val totalDist = totalSpeed * timeElapsedSec
                    val est = if (totalDist > 0.5) {
                        val p = KMapUtils.rhumbDestinationPoint(dropLoc.latitude, dropLoc.longitude, totalDist, totalBearing)
                        LatLon(p.latitude, p.longitude)
                    } else dropLoc
                    val unc = 15.0 + (totalSpeed * timeElapsedSec * 0.05)
                    Pair(est, unc)
                } else {
                    Pair(dropLoc, 0.0)
                }

                _uiState.update { current ->
                    current.copy(
                        isMobActive = active,
                        distanceMeters = status.returnVector?.distanceMeters,
                        bearingDegrees = status.returnVector?.bearingDegrees,
                        etaSeconds = status.returnVector?.estimatedTimeToMarkerSeconds,
                        mobLocation = dropLoc,
                        estimatedCasualtyLocation = estimatedLoc,
                        uncertaintyRadiusMeters = uncertainty,
                        state = status.state,
                        muteUntil = status.muteUntil,
                        activeMobCount = status.activeEvents.size
                    )
                }

                // Unified state update
                NauticalPlugin.engine?.setMobActive(
                    active = active,
                    lat = status.event?.dropLocation?.latitude,
                    lon = status.event?.dropLocation?.longitude
                )

                // Notify Event Bus for cross-module coordination
                net.osmand.plus.plugins.nautical.NauticalEventBus.publishSync(
                    net.osmand.plus.plugins.nautical.NauticalEvent.MobStateChanged(
                        active = active,
                        lat = status.event?.dropLocation?.latitude,
                        lon = status.event?.dropLocation?.longitude
                    )
                )

                // Handle persistence and audio based on state changes
                when (status.state) {
                    MobState.ACTIVE_EMERGENCY -> {
                        app.settings.NAUTICAL_MOB_ACTIVE.set(true)
                        startGuidanceLoop()
                        // Note: Audio is handled by NauticalAudioArbiter observing the Event Bus
                        // We only ensure the notification is posted here if not already present
                        NauticalPlugin.getInstance()?.notificationManager?.postCriticalNotification(
                            "mob_emergency",
                            app.getString(R.string.nautical_mob_label),
                            "Emergency MOB marker dropped. Returning to position."
                        )
                    }
                    MobState.RESOLVED -> {
                        app.settings.NAUTICAL_MOB_ACTIVE.set(false)
                        stopGuidanceLoop()
                        androidx.core.app.NotificationManagerCompat.from(app).cancel("mob_emergency".hashCode())
                    }
                    MobState.INACTIVE -> {
                        app.settings.NAUTICAL_MOB_ACTIVE.set(false)
                        stopGuidanceLoop()
                        androidx.core.app.NotificationManagerCompat.from(app).cancel("mob_emergency".hashCode())
                    }
                }
            }
            .launchIn(viewModelScope)

        // Register for location updates
        app.locationProvider.addLocationListener(this)

        // Restore active MOB state after process death
        viewModelScope.launch {
            stateMachine.getStoredStatus()?.let { storedStatus ->
                if (storedStatus.state == MobState.ACTIVE_EMERGENCY) {
                    stateMachine.restoreState(storedStatus)
                    audioManager.startAlarm()
                }
            }
        }
    }

    /**
     * Triggers a new MOB emergency.
     */
    fun triggerMob(location: LatLon, source: MobTriggerSource = MobTriggerSource.BUTTON) {
        val lastLoc = app.locationProvider.lastKnownLocation
        val sog = lastLoc?.speed?.toDouble() ?: 0.0
        val cog = Math.toRadians(lastLoc?.bearing?.toDouble() ?: 0.0)
        
        val state = NauticalPlugin.engine?.getCurrentState()
        val driftMps = state?.drift ?: 0.0
        val setTrueRad = state?.setTrue ?: 0.0

        stateMachine.triggerMob(location, sog, cog, driftMps, setTrueRad)
        broadcastMobNetwork(location.latitude, location.longitude)

        val uiState = _uiState.value
        if (!uiState.isMotoring) {
            // Sailing safety: Do not auto-turn without confirmation.
            // Offer Heave-To stabilization as primary safety action
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_mob_prepare_turn),
                15000L,
                "HEAVE-TO",
                isWarning = true,
                onConfirm = {
                    requestHeaveTo()
                }
            )
        } else {
            executeRecommendedTurn(location, source)
        }
    }

    private fun startGuidanceLoop() {
        guidanceJob?.cancel()
        guidanceJob = viewModelScope.launch {
            val interval = app.settings.NAUTICAL_MOB_AUDIO_INTERVAL.get().coerceAtLeast(5).seconds
            while (true) {
                delay(interval)
                announceGuidance()
            }
        }
    }

    private fun stopGuidanceLoop() {
        guidanceJob?.cancel()
        guidanceJob = null
    }

    private fun announceGuidance() {
        if (!app.settings.NAUTICAL_MOB_AUDIO_GUIDANCE.get()) return
        val status = stateMachine.mobStatus.value
        val vector = status.returnVector ?: return
        
        val dist = vector.distanceMeters
        val bearing = vector.bearingDegrees
        
        val msg = app.getString(R.string.nautical_mob_target_bearing, dist.toInt(), bearing.toInt())
        NauticalAudioArbiter.getInstance(app).dispatchTts(msg, AlarmType.TTS_INSTRUCTION)
    }

    private fun broadcastMobNetwork(lat: Double, lon: Double) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ip = app.settings.NAUTICAL_SERVER_IP.get() ?: ""
            val port = app.settings.NAUTICAL_SERVER_PORT.get() ?: "3000"
            if (ip.isEmpty()) return@launch

            val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
            val protocol = if (useSecure) "https" else "http"
            val url = "$protocol://$ip:$port/signalk/v1/api/vessels/self/navigation/manOverboard"

            val timestamp = System.currentTimeMillis()
            val payload = """{ "value": { "position": { "latitude": $lat, "longitude": $lon }, "timestamp": "$timestamp" } }"""

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            
            val requestBuilder = Request.Builder().url(url).put(payload.toRequestBody("application/json".toMediaType()))

            val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
            val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
            }

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    sendDeltaFallback(lat, lon)
                }
                response.close()
            } catch (e: Exception) {
                sendDeltaFallback(lat, lon)
            }
        }
    }

    private fun sendDeltaFallback(lat: Double, lon: Double) {
        val engine = NauticalPlugin.engine ?: return
        val deltaPayload = """{
            "updates": [{
                "values": [{
                    "path": "notifications.security.mob",
                    "value": { "state": "emergency", "message": "Man Overboard at $lat, $lon" }
                }]
            }]
        }"""
        engine.deltaSender?.invoke(deltaPayload)
    }

    private fun executeRecommendedTurn(location: LatLon, source: MobTriggerSource) {
        val apc = NauticalPlugin.autopilot ?: return
        if (!apc.isConnected()) return

        val lastLoc = app.locationProvider.lastKnownLocation ?: return
        val distanceNm = KMapUtils.getDistance(lastLoc.latitude, lastLoc.longitude, location.latitude, location.longitude) / 1852.0
        
        // Integration with Proa shunting
        if (app.settings.NAUTICAL_VESSEL_TYPE.get() == net.osmand.plus.settings.enums.VesselType.PROA) {
            apc.shunt()
            return
        }

        val cog = lastLoc.bearing.toDouble()
        val bearingToMob = Math.toDegrees(KMapUtils.getBearing(lastLoc.latitude, lastLoc.longitude, location.latitude, location.longitude))
        val relBearing = (bearingToMob - cog + 360) % 360
        val turnsPort = relBearing > 180.0

        // Anderson Turn is for immediate action (person in sight)
        if (source == MobTriggerSource.BUTTON) {
            apc.andersonTurn(turnsPort)
            return
        }

        // Delayed action selection based on speed-aware threshold (Item 11)
        // Turning radius increases with speed. Standard rule: ~1-2 boat lengths * speed factor.
        // We use a dynamic threshold: max(0.2, SOG * 60 / 1852) in NM.
        val thresholdNm = maxOf(0.2, (lastLoc.speed.toDouble() * 60.0) / 1852.0)
        
        if (distanceNm > thresholdNm) {
            apc.scharnowTurn(turnsPort)
        } else {
            apc.williamsonTurn(turnsPort)
        }
    }

    fun requestAndersonTurn() {
        NauticalPlugin.autopilot?.andersonTurn()
    }

    fun requestWilliamsonTurn() {
        NauticalPlugin.autopilot?.williamsonTurn()
    }

    fun requestScharnowTurn() {
        NauticalPlugin.autopilot?.scharnowTurn()
    }

    /**
     * Silences the audio alarm while keeping the emergency active.
     */
    fun silenceAlarm() {
        stateMachine.muteSiren()
    }

    /**
     * Restores audio alarm immediately.
     */
    fun unmuteAlarm() {
        stateMachine.unmuteSiren()
        audioManager.startAlarm()
    }

    /**
     * Clears the MOB event and resets state.
     */
    fun clearMob() {
        stateMachine.cancelMob()
        // If we were in RESOLVED, one more call to cancelMob moves it to INACTIVE
        if (stateMachine.mobStatus.value.state == MobState.RESOLVED) {
            stateMachine.cancelMob()
        }
        audioManager.stopAlarm()
        _uiState.update { it.copy(sarPatternWaypoints = emptyList(), isSearching = false) }
        
        // Abort the maneuver engine to reset autopilot state
        val mm = PluginsHelper.getPlugin(NauticalPlugin::class.java)?.maneuverManager
        mm?.abort("MOB Emergency Cancelled")
    }

    /**
     * Generates an IAMSAR Expanding Square Search pattern around the estimated casualty datum.
     */
    fun generateExpandingSquare(trackSpacingMeters: Double = 100.0, legs: Int = 8) {
        val datum = _uiState.value.estimatedCasualtyLocation ?: _uiState.value.mobLocation ?: return
        val waypoints = MobVectorEngine.generateExpandingSquarePattern(datum, trackSpacingMeters, legs)
        _uiState.update { it.copy(sarPatternWaypoints = waypoints, isSearching = true) }
    }

    /**
     * Generates an IAMSAR Sector Search pattern around the estimated casualty datum.
     */
    fun generateSectorSearch(radiusMeters: Double = 300.0) {
        val datum = _uiState.value.estimatedCasualtyLocation ?: _uiState.value.mobLocation ?: return
        val waypoints = MobVectorEngine.generateSectorSearchPattern(datum, radiusMeters)
        _uiState.update { it.copy(sarPatternWaypoints = waypoints, isSearching = true) }
    }

    /**
     * Clears active SAR pattern.
     */
    fun clearSarPattern() {
        _uiState.update { it.copy(sarPatternWaypoints = emptyList(), isSearching = false) }
    }

    fun requestHeaveTo() {
        val apc = NauticalPlugin.autopilot ?: return
        if (!apc.isConnected()) return
        
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        
        // 1. Initiate Tack
        val awa = state.windDirectionApparent ?: 0.0
        val isPortTack = awa < 0
        apc.tack(port = isPortTack, manageLock = false)
        
        // 2. Dynamic Tack Detection + Rudder Lock
        viewModelScope.launch {
            val timeout = System.currentTimeMillis() + 30000
            var tackCompleted = false
            
            while (System.currentTimeMillis() < timeout) {
                val currentState = NauticalPlugin.engine?.getCurrentState() ?: break
                val currentAwa = currentState.windDirectionApparent ?: 0.0
                
                if (isPortTack && currentAwa > 0.05) {
                    tackCompleted = true
                    break
                } else if (!isPortTack && currentAwa < -0.05) {
                    tackCompleted = true
                    break
                }
                delay(200.milliseconds)
            }

            if (tackCompleted) {
                val finalState = NauticalPlugin.engine?.getCurrentState()
                val finalAwa = finalState?.windDirectionApparent ?: 0.0
                apc.setRudderAngle(if (finalAwa > 0) Math.toRadians(35.0) else Math.toRadians(-35.0))
            }
        }
    }

    fun requestMotorReturn() {
        val apc = NauticalPlugin.autopilot ?: return
        if (!apc.isConnected()) return
        
        val status = stateMachine.mobStatus.value
        val mobLoc = status.event?.dropLocation ?: return
        
        apc.sendActiveWaypoint(mobLoc.latitude, mobLoc.longitude)
        
        val lastLoc = app.locationProvider.lastKnownLocation
        if (lastLoc != null) {
            val distanceNm = KMapUtils.getDistance(lastLoc.latitude, lastLoc.longitude, mobLoc.latitude, mobLoc.longitude) / 1852.0
            val cog = lastLoc.bearing.toDouble()
            val bearingToMob = Math.toDegrees(KMapUtils.getBearing(lastLoc.latitude, lastLoc.longitude, mobLoc.latitude, mobLoc.longitude))
            val relBearing = (bearingToMob - cog + 360.0) % 360.0
            val turnsPort = relBearing > 180.0

            if (distanceNm > 0.3) {
                apc.scharnowTurn(turnsPort)
            } else {
                apc.williamsonTurn(turnsPort)
            }
        } else {
            apc.williamsonTurn()
        }
        apc.setAutopilotMode("track")
    }

    fun requestHoldHeading() {
        NauticalPlugin.autopilot?.disengage()
        app.showToastMessage(R.string.nautical_mob_heading_hold)
    }

    override fun updateLocation(location: net.osmand.Location?) {
        if (location != null) {
            val sog = location.speed.toDouble()
            val state = NauticalPlugin.engine?.getCurrentState()
            val driftMps = state?.drift ?: 0.0
            val setTrueRad = state?.setTrue ?: 0.0
            stateMachine.updateCurrentLocation(
                LatLon(location.latitude, location.longitude), 
                sog, driftMps, setTrueRad
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.locationProvider.removeLocationListener(this)
        audioManager.stopAlarm()
    }
}
