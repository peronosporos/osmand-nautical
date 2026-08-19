package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.osmand.PlatformUtil
import net.osmand.StateChangedListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.LocationSource

/**
 * Monitors Signal K position health and automatically toggles OsmAnd's global location source.
 * This ensures that the device GPS is only active when Signal K positioning is unavailable,
 * significantly reducing battery drain in Boat mode.
 */
class NauticalLocationSwitcher(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker
) {
    private val log = PlatformUtil.getLog(NauticalLocationSwitcher::class.java)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val modeListener = StateChangedListener<ApplicationMode> { update() }
    private val autoSwitchListener = StateChangedListener<Boolean> { update() }
    
    private var isInternalSwitching = false
    private val sourceListener = StateChangedListener<LocationSource> { source ->
        if (!isInternalSwitching && source != LocationSource.EXTERNAL_SIGNALK) {
            log.debug("Nautical: User manually changed location source to $source. Remembering for fallback.")
            app.settings.NAUTICAL_LAST_USER_LOCATION_SOURCE.set(source)
        }
    }

    fun start() {
        if (job != null) return
        
        app.settings.APPLICATION_MODE.addListener(modeListener)
        app.settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.addListener(autoSwitchListener)
        app.settings.LOCATION_SOURCE.addListener(sourceListener)
        
        job = scope.launch {
            dataBroker.marineState.collectLatest {
                update()
            }
        }
        update()
        log.info("Nautical Location Switcher: Started.")
    }

    private fun update() {
        val autoSwitchEnabled = app.settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.get()
        val mode = app.settings.APPLICATION_MODE.get()
        val isBoat = mode.isDerivedRoutingFrom(ApplicationMode.BOAT)
        
        val state = dataBroker.marineState.value
        val hasSignalKFix = state.hasValidFix && state.connectionStatus == ConnectionStatus.CONNECTED
        val currentSource = app.settings.LOCATION_SOURCE.get()

        if (autoSwitchEnabled && isBoat && hasSignalKFix) {
            if (currentSource != LocationSource.EXTERNAL_SIGNALK) {
                log.info("Nautical: Intelligent switch to Signal K location source (Fix healthy).")
                switchSource(LocationSource.EXTERNAL_SIGNALK)
            }
        } else if (currentSource == LocationSource.EXTERNAL_SIGNALK) {
            // Restore if: 
            // 1. Auto-switch disabled by user
            // 2. Left Boat profile
            // 3. Signal K fix lost or disconnected
            val restoreSource = app.settings.NAUTICAL_LAST_USER_LOCATION_SOURCE.get()
            val finalRestore = if (restoreSource == LocationSource.EXTERNAL_SIGNALK) {
                if (net.osmand.plus.Version.isGooglePlayEnabled()) LocationSource.GOOGLE_PLAY_SERVICES else LocationSource.ANDROID_API
            } else {
                restoreSource
            }
            
            log.info("Nautical: Restoring location source to $finalRestore (Auto: $autoSwitchEnabled, Boat: $isBoat, Fix: $hasSignalKFix)")
            switchSource(finalRestore)
        }
    }

    private fun switchSource(source: LocationSource) {
        isInternalSwitching = true
        try {
            app.settings.LOCATION_SOURCE.set(source)
        } finally {
            isInternalSwitching = false
        }
    }

    fun stop() {
        app.settings.APPLICATION_MODE.removeListener(modeListener)
        app.settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.removeListener(autoSwitchListener)
        app.settings.LOCATION_SOURCE.removeListener(sourceListener)
        job?.cancel()
        job = null
        log.info("Nautical Location Switcher: Stopped.")
    }
}
