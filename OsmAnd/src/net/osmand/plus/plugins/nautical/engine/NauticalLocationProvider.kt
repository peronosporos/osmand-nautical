package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import net.osmand.plus.OsmandApplication
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    private var isActive = false
    private val lastUpdateTime = AtomicLong(0L)

    // Defensive reflection
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)

    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)

    private val setHasAcc: Method? = try { locationClass.getMethod("setHasAccuracy", Boolean::class.java) } catch (e: Exception) { null }
    private val setAcc: Method? = try { locationClass.getMethod("setAccuracy", Float::class.java) } catch (e: Exception) { null }
    private val setSpeed: Method? = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (e: Exception) { null }
    private val setBearing: Method? = try { locationClass.getMethod("setBearing", Float::class.java) } catch (e: Exception) { null }
    private val setBearingAcc: Method? = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (e: Exception) { null }

    private val setLocationFromService = app.locationProvider.javaClass.getMethod("setLocationFromService", locationClass)

    private val listener: (MarineState) -> Unit = { state -> if (isActive) injectMarineStateAsLocation(state) }

    fun start() {
        if (isActive) return
        isActive = true
        engine.registerListener(listener)
        Log.d("NauticalPlugin", "Location Bridge: Active")
    }

    fun stop() {
        isActive = false
        lastUpdateTime.set(0L)
        // Release the provider back to the native hardware GPS
        app.runInUIThread {
            try {
                val provider = app.locationProvider
                provider.javaClass.getMethod("resume").invoke(provider)
            } catch (e: Exception) { /* ignore */ }
        }
        Log.d("NauticalPlugin", "Location Bridge: Stopped")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        val currentTime = System.currentTimeMillis()

        // The Throttle Fix (Ensures 1 update per second maximum, prevents UI lockup)
        val lastTime = lastUpdateTime.get()
        if (lastTime != 0L && (currentTime - lastTime) < 1000) return

        lastUpdateTime.set(currentTime)

        val lat = state.latitude ?: return
        val lon = state.longitude ?: return

        try {
            val loc = constructor.newInstance("SignalKProvider")
            setLat.invoke(loc, lat)
            setLon.invoke(loc, lon)
            setTime.invoke(loc, currentTime)

            setHasAcc?.invoke(loc, true)
            setAcc?.invoke(loc, 5.0f) // Tight accuracy forces OsmAnd to prefer this over weak hardware GPS

            state.speedOverGround?.let { setSpeed?.invoke(loc, it.toFloat()) }
            state.headingTrue?.let {
                setBearing?.invoke(loc, it.toFloat())
                setBearingAcc?.invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    setLocationFromService.invoke(app.locationProvider, loc)
                } catch (e: Exception) { Log.e("NauticalPlugin", "Injection failed: ${e.message}") }
            }
        } catch (e: Exception) { Log.e("NauticalPlugin", "Reflection failed: ${e.message}") }
    }
}