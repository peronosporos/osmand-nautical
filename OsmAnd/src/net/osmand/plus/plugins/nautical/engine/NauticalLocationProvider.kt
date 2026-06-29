package net.osmand.plus.plugins.nautical.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)

    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)

    private val setNanos: Method? = try { locationClass.getMethod("setElapsedRealtimeNanos", Long::class.java) } catch (e: Exception) { null }
    private val setHasAcc: Method? = try { locationClass.getMethod("setHasAccuracy", Boolean::class.java) } catch (e: Exception) { null }
    private val setAcc: Method? = try { locationClass.getMethod("setAccuracy", Float::class.java) } catch (e: Exception) { null }

    private val setHasSpeed: Method? = try { locationClass.getMethod("setHasSpeed", Boolean::class.java) } catch (e: Exception) { null }
    private val setSpeed: Method? = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (e: Exception) { null }
    private val setHasBearing: Method? = try { locationClass.getMethod("setHasBearing", Boolean::class.java) } catch (e: Exception) { null }
    private val setBearing: Method? = try { locationClass.getMethod("setBearing", Float::class.java) } catch (e: Exception) { null }
    private val setBearingAcc: Method? = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (e: Exception) { null }

    private var cachedInjectMethod: Method? = null
    private var pauseHardwareGps: Method? = null
    private var resumeHardwareGps: Method? = null

    private var isHardwarePaused = false
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private val fallbackRunnable = Runnable {
        if (isHardwarePaused) {
            try {
                resumeHardwareGps?.invoke(app.locationProvider)
                isHardwarePaused = false
                Log.d("NauticalPlugin", "SignalK data timeout (15s). Resumed smartphone GPS.")
            } catch (e: Exception) {}
        }
    }

    private val listener: (MarineState) -> Unit = { state -> if (isActive) injectMarineStateAsLocation(state) }

    init {
        val providerClass = app.locationProvider.javaClass

        val potentialInjectMethods = listOf("setLocation", "updateLocation", "setLocationFromService")
        for (methodName in potentialInjectMethods) {
            try {
                val method = providerClass.getMethod(methodName, locationClass)
                method.isAccessible = true
                cachedInjectMethod = method
                break
            } catch (e: Exception) {
                try {
                    val method = providerClass.getDeclaredMethod(methodName, locationClass)
                    method.isAccessible = true
                    cachedInjectMethod = method
                    break
                } catch (e2: Exception) { }
            }
        }

        val pauseMethods = listOf("pauseAllUpdates", "stopLocationUpdates", "pause")
        for (methodName in pauseMethods) {
            try {
                pauseHardwareGps = providerClass.getMethod(methodName)
                break
            } catch (e: Exception) {}
        }

        val resumeMethods = listOf("resumeAllUpdates", "startLocationUpdates", "resume")
        for (methodName in resumeMethods) {
            try {
                resumeHardwareGps = providerClass.getMethod(methodName)
                break
            } catch (e: Exception) {}
        }
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

        fallbackHandler.removeCallbacks(fallbackRunnable)
        app.runInUIThread {
            try {
                resumeHardwareGps?.invoke(app.locationProvider)
                isHardwarePaused = false
            } catch (e: Exception) {}
        }
        Log.d("NauticalPlugin", "Location Bridge: Stopped. Restored smartphone GPS.")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        if (state.latitude == null || state.longitude == null) return

        val currentTime = System.currentTimeMillis()
        val lastTime = lastUpdateTime.get()
        if (lastTime != 0L && (currentTime - lastTime) < 1000) return
        lastUpdateTime.set(currentTime)

        try {
            val loc = constructor.newInstance("gps")
            setLat.invoke(loc, state.latitude)
            setLon.invoke(loc, state.longitude)
            setTime.invoke(loc, currentTime)
            setNanos?.invoke(loc, SystemClock.elapsedRealtimeNanos())

            setHasAcc?.invoke(loc, true)
            setAcc?.invoke(loc, 1.0f)

            // FIX: Set minimum speed to 1.5m/s (3 knots) so OsmAnd disables the compass
            // and renders the Boat Icon based on GPS bearing instead of the Blue Dot.
            val speed = state.speedOverGround?.toFloat() ?: 1.5f
            setHasSpeed?.invoke(loc, true)
            setSpeed?.invoke(loc, speed)

            val bearing = state.headingTrue?.toFloat() ?: 0f
            setHasBearing?.invoke(loc, true)
            setBearing?.invoke(loc, bearing)
            setBearingAcc?.invoke(loc, 1f)

            app.runInUIThread {
                try {
                    // FIX: Aggressively invoke pause on EVERY packet to overpower OsmAnd lifecycle changes
                    pauseHardwareGps?.invoke(app.locationProvider)
                    if (!isHardwarePaused) {
                        isHardwarePaused = true
                        Log.d("NauticalPlugin", "Smartphone GPS antenna powered down. Saving battery.")
                    }

                    fallbackHandler.removeCallbacks(fallbackRunnable)
                    fallbackHandler.postDelayed(fallbackRunnable, 15000)

                    cachedInjectMethod?.invoke(app.locationProvider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Dynamic injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) { Log.e("NauticalPlugin", "Reflection failed: ${e.message}") }
    }
}