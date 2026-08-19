package net.osmand.plus.plugins.nautical.engine

import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.enums.LocationSource
import java.util.concurrent.atomic.AtomicLong

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine?,
) {
    private val log = PlatformUtil.getLog(NauticalLocationProvider::class.java)
    private var isActive = false
    private var isAppInBackground = false
    private val lastUpdateTime = AtomicLong(0L)

    fun setAppInBackground(background: Boolean) {
        this.isAppInBackground = background
    }

    private val listener: (MarineState) -> Unit = { state ->
        if (isActive) injectMarineStateAsLocation(state)
    }

    fun start() {
        if (isActive) return
        isActive = true
        engine?.registerListener(listener)
        log.info("Nautical Location Bridge: Active.")
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        engine?.unregisterListener(listener)
        log.info("Nautical Location Bridge: Stopped.")
    }

    private var isHeadingStale = false

    private fun injectMarineStateAsLocation(state: MarineState) {
        if ((state.latitude == null) || (state.longitude == null)) return

        val currentTime = System.currentTimeMillis()
        
        // Position Staleness check
        val positionTimestamp = state.timestamps["navigation.position"] ?: 0L
        val positionAge = currentTime - positionTimestamp
        if (positionAge > 10000 && positionTimestamp != 0L) {
            log.warn("Nautical: Position stale (${positionAge}ms old)")
            return
        }

        // Heading Staleness check
        val headingTimestamp = state.timestamps["navigation.headingTrue"] ?: 0L
        val headingAge = currentTime - headingTimestamp
        
        if (headingAge > 10000 && headingTimestamp != 0L) {
            if (!isHeadingStale) {
                isHeadingStale = true
                triggerHeadingStaleWarning()
            }
        } else {
            isHeadingStale = false
        }

        val throttleMs = if (isAppInBackground) 1000L else 100L
        if ((currentTime - lastUpdateTime.get()) < throttleMs) return
        lastUpdateTime.set(currentTime)

        val loc = Location("signalk")
        loc.latitude = state.latitude
        loc.longitude = state.longitude
        loc.time = currentTime
        
        state.speedOverGround?.let { loc.speed = it.toFloat() }
        state.altitude?.let { loc.altitude = it }

        // Dynamic Accuracy from HDOP
        val hdop = state.gnss?.horizontalDilution ?: 1.0
        loc.accuracy = (hdop * 5.0).toFloat().coerceIn(1.0f, 100f)
        
        // Bearing logic: Respect server-managed fallbacks from CapabilityManager
        val caps = engine?.capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        val heading = if (!isHeadingStale) state.headingTrue else null
        
        if (caps.hasGpsHeadingFallback && heading != null) {
             loc.bearing = Math.toDegrees(heading).toFloat()
        } else if (heading != null) {
            loc.bearing = Math.toDegrees(heading).toFloat()
        } else {
            state.courseOverGroundTrue?.let { loc.bearing = Math.toDegrees(it).toFloat() }
        }
        
        app.runInUIThread {
            val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(net.osmand.plus.settings.backend.ApplicationMode.BOAT)
            val isSignalKSource = app.settings.LOCATION_SOURCE.get() == LocationSource.EXTERNAL_SIGNALK
            val autoSwitch = app.settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.get()
            
            if (isSignalKSource || (isBoat && autoSwitch)) {
                app.locationProvider.setCustomLocation(loc, 3000L)
            }
        }
    }

    private fun triggerHeadingStaleWarning() {
        app.runInUIThread {
            val msg = app.getString(R.string.nautical_heading_data_stale_fallback)
            app.player?.let { player ->
                player.playCommands(player.newCommandBuilder().attention(msg))
            }
            app.showToastMessage(msg)
        }
    }
}
