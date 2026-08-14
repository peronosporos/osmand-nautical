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

        // Auto-switch to Signal K source once on activation
        if (app.settings.LOCATION_SOURCE.get() != LocationSource.EXTERNAL_SIGNALK) {
            app.runInUIThread {
                app.settings.LOCATION_SOURCE.set(LocationSource.EXTERNAL_SIGNALK)
            }
        }
        
        log.info("Nautical Location Bridge: Active.")
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        engine?.unregisterListener(listener)
        
        // Restore default location source if we were using SignalK
        if (app.settings.LOCATION_SOURCE.get() == LocationSource.EXTERNAL_SIGNALK) {
            app.runInUIThread {
                app.settings.LOCATION_SOURCE.set(LocationSource.ANDROID_API)
            }
        }
        log.info("Nautical Location Bridge: Stopped.")
    }

    private var isHeadingStale = false

    private fun injectMarineStateAsLocation(state: MarineState) {
        if ((state.latitude == null) || (state.longitude == null)) return

        val currentTime = System.currentTimeMillis()
        
        // Staleness detection for Position
        val positionTimestamp = state.timestamps["navigation.position"] ?: 0L
        val positionAge = currentTime - positionTimestamp
        if (positionAge > 2000 && positionTimestamp != 0L) {
            log.warn("Nautical: Rejecting stale location update (${positionAge}ms old)")
            return
        }

        // Staleness detection for Heading
        val headingTimestamp = state.timestamps["navigation.headingTrue"] ?: 0L
        val headingAge = currentTime - headingTimestamp
        
        if (headingAge > 3000 && headingTimestamp != 0L) {
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
        
        val caps = engine?.capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        
        // Fallback logic for bearing
        if (caps.hasGpsHeadingFallback) {
            // SignalK Server Managed Heading
            if (!isHeadingStale && state.headingTrue != null) {
                loc.bearing = Math.toDegrees(state.headingTrue).toFloat()
            } else {
                // Secondary fallback to COG if server heading is lost
                state.courseOverGroundTrue?.let { loc.bearing = Math.toDegrees(it).toFloat() }
            }
        } else if (isHeadingStale || state.headingTrue == null) {
            val internalHeading = app.locationProvider.heading
            if (internalHeading != null) {
                loc.bearing = internalHeading
            } else {
                state.courseOverGroundTrue?.let { loc.bearing = Math.toDegrees(it).toFloat() }
            }
        } else {
            loc.bearing = Math.toDegrees(state.headingTrue).toFloat()
        }
        
        loc.accuracy = 1.0f

        app.runInUIThread {
            app.locationProvider.setLocationFromService(loc)
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
