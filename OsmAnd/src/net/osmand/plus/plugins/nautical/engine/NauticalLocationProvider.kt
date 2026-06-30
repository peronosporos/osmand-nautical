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

    // Heartbeat monitor for auto-recovery
    private val lastReceivedSignalKTime = AtomicLong(System.currentTimeMillis())
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var isHardwarePaused = false

    // Core Reflection Methods
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)

    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)
    private val setNanos = try { locationClass.getMethod("setElapsedRealtimeNanos", Long::class.java) } catch (e: Exception) { null }

    // CRITICAL: Restored Accuracy, Speed, and Bearing.
    // Without these, OsmAnd rejects the location as invalid and falls back to the OS GPS.
    private val setHasAcc = try { locationClass.getMethod("setHasAccuracy", Boolean::class.java) } catch (e: Exception) { null }
    private val setAcc = try { locationClass.getMethod("setAccuracy", Float::class.java) } catch (e: Exception) { null }
    private val setHasSpeed = try { locationClass.getMethod("setHasSpeed", Boolean::class.java) } catch (e: Exception) { null }
    private val setSpeed = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (e: Exception) { null }
    private val setHasBearing = try { locationClass.getMethod("setHasBearing", Boolean::class.java) } catch (e: Exception) { null }
    private val setBearing = try { locationClass.getMethod("setBearing", Float::class.java) } catch (e: Exception) { null }
    private val setBearingAcc = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (e: Exception) { null }

    private var cachedInjectMethod: Method? = null
    private var pauseHardwareGps: Method? = null
    private var resumeHardwareGps: Method? = null

    // Auto-recovery runnable to restore OS GPS if SignalK drops
    private val fallbackRunnable = Runnable {
        if (isHardwarePaused) {
            try {
                resumeHardwareGps?.invoke(app.locationProvider)
                isHardwarePaused = false
                Log.d("NauticalPlugin", "SignalK data timeout. Restored smartphone GPS.")
            } catch (e: Exception) {}
        }
    }

    private val listener: (MarineState) -> Unit = { state ->
        if (isActive) injectMarineStateAsLocation(state)
    }

    init {
        val providerClass = app.locationProvider.javaClass

        val potentialInjectMethods = listOf("setLocation", "updateLocation", "setLocationFromService")
        for (name in potentialInjectMethods) {
            try {
                val method = providerClass.getMethod(name, locationClass)
                method.isAccessible = true
                cachedInjectMethod = method
                break
            } catch (e: Exception) {}
        }

        val pauseNames = listOf("pauseAllUpdates", "stopLocationUpdates", "pause")
        pauseHardwareGps = pauseNames.mapNotNull { try { providerClass.getMethod(it) } catch (e: Exception) { null } }.firstOrNull()

        val resumeNames = listOf("resumeAllUpdates", "startLocationUpdates", "resume")
        resumeHardwareGps = resumeNames.mapNotNull { try { providerClass.getMethod(it) } catch (e: Exception) { null } }.firstOrNull()
    }

    fun start() {
        if (isActive) return
        isActive = true
        engine.registerListener(listener)

        // Force pause hardware GPS immediately upon start to prevent initial fighting
        try {
            pauseHardwareGps?.invoke(app.locationProvider)
            isHardwarePaused = true
            Log.d("NauticalPlugin", "Location Bridge Active. Hardware GPS paused.")
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Failed to pause hardware GPS on start")
        }
    }

    fun stop() {
        isActive = false
        fallbackHandler.removeCallbacks(fallbackRunnable)
        try {
            resumeHardwareGps?.invoke(app.locationProvider)
            isHardwarePaused = false
        } catch (e: Exception) {}
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        if (state.latitude == null || state.longitude == null) return

        lastReceivedSignalKTime.set(System.currentTimeMillis())

        // Throttle to 1Hz
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastUpdateTime.get()) < 1000) return
        lastUpdateTime.set(currentTime)

        try {
            val loc = constructor.newInstance("gps")
            setLat.invoke(loc, state.latitude)
            setLon.invoke(loc, state.longitude)
            setTime.invoke(loc, currentTime)
            setNanos?.invoke(loc, SystemClock.elapsedRealtimeNanos())

            // Feed OsmAnd the exact accuracy data it requires to trust the location
            setHasAcc?.invoke(loc, true)
            setAcc?.invoke(loc, 1.0f)

            val speedMps = state.speedOverGround?.toFloat() ?: 0f
            setHasSpeed?.invoke(loc, true)
            setSpeed?.invoke(loc, speedMps)

            val bearingDeg = Math.toDegrees(state.headingTrue ?: 0.0).toFloat()
            setHasBearing?.invoke(loc, true)
            setBearing?.invoke(loc, bearingDeg)
            setBearingAcc?.invoke(loc, 1f)

            // Auto-pause hardware GPS if it was previously recovered due to a timeout
            if (!isHardwarePaused) {
                try {
                    pauseHardwareGps?.invoke(app.locationProvider)
                    isHardwarePaused = true
                    Log.d("NauticalPlugin", "SignalK resumed. GPS paused again.")
                } catch (e: Exception) {}
            }

            // Reset the 15-second deadman's switch
            fallbackHandler.removeCallbacks(fallbackRunnable)
            fallbackHandler.postDelayed(fallbackRunnable, 15000)

            // Inject location on the UI thread to ensure OsmAnd's internal state handles it cleanly
            app.runInUIThread {
                try {
                    cachedInjectMethod?.invoke(app.locationProvider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Injection error: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Reflection error building location: ${e.message}")
        }
    }
}