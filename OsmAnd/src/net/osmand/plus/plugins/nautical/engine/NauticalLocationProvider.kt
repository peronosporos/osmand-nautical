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

    // NEW: Needed to render the Boat icon instead of the Blue Dot
    private val setHasSpeed: Method? = try { locationClass.getMethod("setHasSpeed", Boolean::class.java) } catch (e: Exception) { null }
    private val setSpeed: Method? = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (e: Exception) { null }
    private val setHasBearing: Method? = try { locationClass.getMethod("setHasBearing", Boolean::class.java) } catch (e: Exception) { null }
    private val setBearing: Method? = try { locationClass.getMethod("setBearing", Float::class.java) } catch (e: Exception) { null }
    private val setBearingAcc: Method? = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (e: Exception) { null }

    // Hardware GPS Control
    private val pauseHardwareGps: Method? = try { app.locationProvider.javaClass.getMethod("pause") } catch (e: Exception) { null }
    private val resumeHardwareGps: Method? = try { app.locationProvider.javaClass.getMethod("resume") } catch (e: Exception) { null }

    private var cachedInjectMethod: Method? = null

    // The Dead-Man's Switch variables
    private var isHardwarePaused = false
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private val fallbackRunnable = Runnable {
        if (isHardwarePaused) {
            try {
                resumeHardwareGps?.invoke(app.locationProvider)
                isHardwarePaused = false
                Log.d("NauticalPlugin", "SignalK data timeout. Resumed hardware GPS fallback.")
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

        // Clean up the dead-man's switch and restore GPS when plugin is turned off
        fallbackHandler.removeCallbacks(fallbackRunnable)
        if (isHardwarePaused) {
            app.runInUIThread {
                try {
                    resumeHardwareGps?.invoke(app.locationProvider)
                    isHardwarePaused = false
                } catch (e: Exception) {}
            }
        }
        Log.d("NauticalPlugin", "Location Bridge: Stopped")
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

            // NEW: Explicitly declare the metadata exists so the Boat icon renders
            state.speedOverGround?.let {
                setHasSpeed?.invoke(loc, true)
                setSpeed?.invoke(loc, it.toFloat())
            }
            state.headingTrue?.let {
                setHasBearing?.invoke(loc, true)
                setBearing?.invoke(loc, it.toFloat())
                setBearingAcc?.invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    // Mute hardware GPS dynamically to stop ping-pong
                    if (!isHardwarePaused) {
                        pauseHardwareGps?.invoke(app.locationProvider)
                        isHardwarePaused = true
                        Log.d("NauticalPlugin", "Valid SignalK data received. Hardware GPS paused.")
                    }

                    // Reset the 5-second dead-man's switch
                    fallbackHandler.removeCallbacks(fallbackRunnable)
                    fallbackHandler.postDelayed(fallbackRunnable, 5000)

                    cachedInjectMethod?.invoke(app.locationProvider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Dynamic injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) { Log.e("NauticalPlugin", "Reflection failed: ${e.message}") }
    }
}