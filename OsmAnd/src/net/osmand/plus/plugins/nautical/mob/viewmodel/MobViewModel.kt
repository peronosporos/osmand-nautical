package net.osmand.plus.plugins.nautical.mob.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import net.osmand.data.LatLon
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.mob.engine.*

/**
 * UI State for the MOB interface.
 */
data class MobUiState(
    val isMobActive: Boolean = false,
    val distanceMeters: Double? = null,
    val bearingDegrees: Double? = null,
    val etaSeconds: Double? = null,
    val mobLocation: LatLon? = null,
    val state: MobState = MobState.INACTIVE
)

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
        // Restore state from preferences if active
        val settings = app.settings
        if (settings.NAUTICAL_MOB_ACTIVE.get()) {
            val lat = settings.NAUTICAL_MOB_LAT.get()
            val lon = settings.NAUTICAL_MOB_LON.get()
            
            if (lat != 0.0 || lon != 0.0) {
                val restoredLocation = LatLon(lat, lon)
                stateMachine.triggerMob(restoredLocation)
                audioManager.startAlarm()
            }
        }

        // Observe state machine changes
        stateMachine.mobStatus
            .onEach { status ->
                _uiState.update { current ->
                    current.copy(
                        isMobActive = status.state == MobState.ACTIVE_EMERGENCY,
                        distanceMeters = status.returnVector?.distanceMeters,
                        bearingDegrees = status.returnVector?.bearingDegrees,
                        etaSeconds = status.returnVector?.estimatedTimeToMarkerSeconds,
                        mobLocation = status.event?.dropLocation,
                        state = status.state
                    )
                }

                // Handle persistence and audio based on state changes
                when (status.state) {
                    MobState.ACTIVE_EMERGENCY -> {
                        val event = status.event
                        if (event != null) {
                            settings.NAUTICAL_MOB_LAT.set(event.dropLocation.latitude)
                            settings.NAUTICAL_MOB_LON.set(event.dropLocation.longitude)
                            settings.NAUTICAL_MOB_TIMESTAMP.set(event.dropTimestamp)
                            settings.NAUTICAL_MOB_ACTIVE.set(true)
                        }
                    }
                    MobState.RESOLVED, MobState.INACTIVE -> {
                        // Keep location persisted in RESOLVED for map markers, 
                        // but set ACTIVE to false for auto-restore
                        settings.NAUTICAL_MOB_ACTIVE.set(false)
                        audioManager.stopAlarm()
                        if (status.state == MobState.INACTIVE) {
                            settings.NAUTICAL_MOB_LAT.set(0.0)
                            settings.NAUTICAL_MOB_LON.set(0.0)
                            settings.NAUTICAL_MOB_TIMESTAMP.set(0L)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Register for location updates
        app.locationProvider.addLocationListener(this)
    }

    /**
     * Triggers a new MOB emergency.
     */
    fun triggerMob(location: LatLon) {
        val sog = app.locationProvider.lastKnownLocation?.speed?.toDouble() ?: 0.0
        val cog = Math.toRadians(app.locationProvider.lastKnownLocation?.bearing?.toDouble() ?: 0.0)
        
        stateMachine.triggerMob(location, sog, cog)
        audioManager.startAlarm()
    }

    /**
     * Silences the audio alarm while keeping the emergency active.
     */
    fun silenceAlarm() {
        audioManager.stopAlarm()
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
