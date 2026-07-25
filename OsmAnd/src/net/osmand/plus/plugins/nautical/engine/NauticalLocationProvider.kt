package net.osmand.plus.plugins.nautical.engine

import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import java.util.concurrent.atomic.AtomicLong

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine?,
) {
    private val log = PlatformUtil.getLog(NauticalLocationProvider::class.java)
    private var isActive = false
    private val lastUpdateTime = AtomicLong(0L)

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
        isActive = false
        engine?.unregisterListener(listener)
        log.info("Nautical Location Bridge: Stopped.")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        if ((state.latitude == null) || (state.longitude == null)) return

        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastUpdateTime.get()) < 1000) return
        lastUpdateTime.set(currentTime)

        val loc = Location("signalk")
        loc.latitude = state.latitude
        loc.longitude = state.longitude
        loc.time = currentTime
        
        state.speedOverGround?.let { loc.speed = it.toFloat() }
        state.headingTrue?.let { loc.bearing = Math.toDegrees(it).toFloat() }
        loc.accuracy = 1.0f

        app.runInUIThread {
            app.locationProvider.setLocationFromService(loc)
        }
    }
}
