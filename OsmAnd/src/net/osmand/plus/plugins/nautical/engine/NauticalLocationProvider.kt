package net.osmand.plus.plugins.nautical.engine

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
    private val setSpeed: Method? = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (e: Exception) { null }
    private val setBearing: Method? = try { locationClass.getMethod("setBearing", Float::class.java) } catch (e: Exception) { null }
    private val setBearingAcc: Method? = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (e: Exception) { null }

    // Dynamically bound map-updating method
    private var cachedInjectMethod: Method? = null

    private val listener: (MarineState) -> Unit = { state -> if (isActive) injectMarineStateAsLocation(state) }

    init {
        // THE FIX: Scan the OsmAnd engine for the correct map update method.
        // We prioritize 'setLocation' as it directly forces the map UI to redraw.
        val providerClass = app.locationProvider.javaClass
        val potentialInjectMethods = listOf("setLocation", "updateLocation", "setLocationFromService")

        for (methodName in potentialInjectMethods) {
            try {
                // Try to find the public method
                val method = providerClass.getMethod(methodName, locationClass)
                method.isAccessible = true
                cachedInjectMethod = method
                Log.d("NauticalPlugin", "Success: Bound public injection method -> $methodName")
                break
            } catch (e: Exception) {
                try {
                    // Fallback to searching private methods if OsmAnd hid it
                    val method = providerClass.getDeclaredMethod(methodName, locationClass)
                    method.isAccessible = true
                    cachedInjectMethod = method
                    Log.d("NauticalPlugin", "Success: Bound private injection method -> $methodName")
                    break
                } catch (e2: Exception) { /* ignore and check next method */ }
            }
        }

        if (cachedInjectMethod == null) {
            Log.e("NauticalPlugin", "CRITICAL ERROR: No valid location injection method found in OsmAnd!")
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

            state.speedOverGround?.let { setSpeed?.invoke(loc, it.toFloat()) }
            state.headingTrue?.let {
                setBearing?.invoke(loc, it.toFloat())
                setBearingAcc?.invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    // Safely invoke the dynamically found method
                    cachedInjectMethod?.invoke(app.locationProvider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Dynamic injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) { Log.e("NauticalPlugin", "Reflection failed: ${e.message}") }
    }
}