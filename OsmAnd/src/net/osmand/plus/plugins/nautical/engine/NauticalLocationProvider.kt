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

    // 1. Thread Safety: Using AtomicLong for thread-safe timestamp updates
    private val lastUpdateTime = AtomicLong(0L)

    // 2. Performance: Cache all reflection methods to avoid lookup overhead per packet
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)

    private val setLat: Method = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon: Method = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime: Method = locationClass.getMethod("setTime", Long::class.java)
    private val setHasAcc: Method = locationClass.getMethod("setHasAccuracy", Boolean::class.java)
    private val setAcc: Method = locationClass.getMethod("setAccuracy", Float::class.java)
    private val setSpeed: Method = locationClass.getMethod("setSpeed", Float::class.java)
    private val setBearing: Method = locationClass.getMethod("setBearing", Float::class.java)
    private val setBearingAcc: Method = locationClass.getMethod("setBearingAcc", Float::class.java)

    // Cache the final injection method lookup as well
    private val setLocationFromService: Method = app.locationProvider.javaClass.getMethod("setLocationFromService", locationClass)

    private val listener: (MarineState) -> Unit = { state ->
        if (isActive) injectMarineStateAsLocation(state)
    }

    fun start() {
        if (isActive) return
        isActive = true
        engine.registerListener(listener)
        Log.d("NauticalPlugin", "Location Bridge: Active")
    }

    fun stop() {
        isActive = false
        lastUpdateTime.set(0L)
        Log.d("NauticalPlugin", "Location Bridge: Stopped")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastUpdateTime.get()

        if (lastTime != 0L && (currentTime - lastTime) > 10000) return
        lastUpdateTime.set(currentTime)

        val lat = state.latitude ?: return
        val lon = state.longitude ?: return

        try {
            val loc = constructor.newInstance("SignalKProvider")

            setLat.invoke(loc, lat)
            setLon.invoke(loc, lon)
            setTime.invoke(loc, currentTime)

            // 3. Accuracy sequence: Set flag BEFORE value
            setHasAcc.invoke(loc, true)
            setAcc.invoke(loc, 5.0f)

            state.speedOverGround?.let {
                setSpeed.invoke(loc, it.toFloat())
            }

            if (state.headingTrue != null) {
                setBearing.invoke(loc, state.headingTrue.toFloat())
                setBearingAcc.invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    // Use cached reflection method for final injection
                    setLocationFromService.invoke(app.locationProvider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Final injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Injection failed: ${e.message}")
        }
    }
}