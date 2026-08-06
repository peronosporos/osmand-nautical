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
import net.osmand.plus.plugins.nautical.maneuvers.ManOverboardManeuver
import net.osmand.plus.plugins.nautical.mob.engine.*
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI State for the MOB interface.
 */
data class MobUiState(
    val isMobActive: Boolean = false,
    val distanceMeters: Double? = null,
    val bearingDegrees: Double? = null,
    val etaSeconds: Double? = null,
    val mobLocation: LatLon? = null,
    val state: MobState = MobState.INACTIVE,
    val isMotoring: Boolean = false,
    val isUpwind: Boolean = true,
    val isSearching: Boolean = false,
    val muteUntil: Long = 0L,
    val activeMobCount: Int = 0,
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

    init {
        // Observe search state from engine
        NauticalPlugin.engine?.let { engine ->
            viewModelScope.launch {
                while (true) {
                    _uiState.update { it.copy(isSearching = engine.isFollowingRoute) }
                    kotlinx.coroutines.delay(1000.milliseconds)
                }
            }
        }
        
        // Observe MarineState for propulsion-aware UI
        NauticalPlugin.engine?.marineStateFlow
            ?.onEach { state ->
                val rpm = state.engines.values.maxOfOrNull { it.revolutions ?: 0.0 } ?: 0.0
                val motoringByState = state.engines.values.any { it.state?.lowercase(java.util.Locale.US) == "started" }
                val twa = state.trueWindAngle ?: 0.0
                val twaDeg = Math.toDegrees(twa)
                _uiState.update { current ->
                    current.copy(
                        isMotoring = (rpm > 100) || motoringByState,
                        isUpwind = abs(twaDeg) < 110.0
                    )
                }
            }
            ?.launchIn(viewModelScope)

        // Observe state machine changes
        stateMachine.mobStatus
            .onEach { status ->
                val active = status.state == MobState.ACTIVE_EMERGENCY
                _uiState.update { current ->
                    current.copy(
                        isMobActive = active,
                        distanceMeters = status.returnVector?.distanceMeters,
                        bearingDegrees = status.returnVector?.bearingDegrees,
                        etaSeconds = status.returnVector?.estimatedTimeToMarkerSeconds,
                        mobLocation = status.event?.dropLocation,
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
                        audioManager.startAlarm() // Ensure alarm is started on activation or restoration
                    }
                    MobState.RESOLVED -> {
                        app.settings.NAUTICAL_MOB_ACTIVE.set(false)
                        audioManager.stopAlarm()
                    }
                    MobState.INACTIVE -> {
                        app.settings.NAUTICAL_MOB_ACTIVE.set(false)
                        audioManager.stopAlarm()
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
        
        stateMachine.triggerMob(location, sog, cog)
        audioManager.startAlarm()

        val uiState = _uiState.value
        if (!uiState.isMotoring) {
            // Sailing safety: Do not auto-turn without confirmation
            NauticalPlugin.hudManager?.get()?.showBanner(
                app.getString(R.string.nautical_mob_prepare_turn),
                15000,
                app.getString(R.string.nautical_auto).uppercase(),
                isWarning = true
            ) {
                executeRecommendedTurn(location, source)
            }
        } else {
            executeRecommendedTurn(location, source)
        }
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

        // Delayed action selection based on distance
        if (distanceNm > 0.3) {
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
     * Clears the MOB event and resets state.
     */
    fun clearMob() {
        stateMachine.cancelMob()
        // If we were in RESOLVED, one more call to cancelMob moves it to INACTIVE
        if (stateMachine.mobStatus.value.state == MobState.RESOLVED) {
            stateMachine.cancelMob()
        }
        audioManager.stopAlarm()
        
        // Abort the maneuver engine to reset autopilot state
        val mm = PluginsHelper.getPlugin(NauticalPlugin::class.java)?.maneuverManager
        mm?.abort("MOB Emergency Cancelled")
    }

    fun requestHeaveTo() {
        val maneuver = PluginsHelper.getPlugin(NauticalPlugin::class.java)?.maneuverManager?.getManeuverById("man_overboard") as? ManOverboardManeuver
        maneuver?.executeHeaveTo()
    }

    fun requestMotorReturn() {
        val maneuver = PluginsHelper.getPlugin(NauticalPlugin::class.java)?.maneuverManager?.getManeuverById("man_overboard") as? ManOverboardManeuver
        maneuver?.executeMotorReturn()
    }

    fun requestHoldHeading() {
        val maneuver = PluginsHelper.getPlugin(NauticalPlugin::class.java)?.maneuverManager?.getManeuverById("man_overboard") as? ManOverboardManeuver
        maneuver?.executeHoldHeading()
    }

    override fun updateLocation(location: net.osmand.Location?) {
        if (location != null) {
            val sog = location.speed.toDouble()
            stateMachine.updateCurrentLocation(LatLon(location.latitude, location.longitude), sog)
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.locationProvider.removeLocationListener(this)
        audioManager.stopAlarm()
    }
}
