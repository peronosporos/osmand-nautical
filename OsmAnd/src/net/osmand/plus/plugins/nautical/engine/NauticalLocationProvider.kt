package net.osmand.plus.plugins.nautical.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import net.osmand.PlatformUtil
import androidx.core.content.ContextCompat
import net.osmand.plus.OsmandApplication
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine?,
) {
    private val log = PlatformUtil.getLog(NauticalLocationProvider::class.java)
    private var isActive = false
    private val lastUpdateTime = AtomicLong(0L)

    // The Watchdog Handler for the 15-second timeout
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var isHardwarePaused = false
    private var dummyListener: LocationListener? = null

    // Core Reflection Methods for OsmAnd internal API
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)
    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)
    private val setNanos = try { locationClass.getMethod("setElapsedRealtimeNanos", Long::class.java) } catch (_: Exception) { null }
    private val setHasAcc = try { locationClass.getMethod("setHasAccuracy", Boolean::class.java) } catch (_: Exception) { null }
    private val setAcc = try { locationClass.getMethod("setAccuracy", Float::class.java) } catch (_: Exception) { null }
    private val setHasSpeed = try { locationClass.getMethod("setHasSpeed", Boolean::class.java) } catch (_: Exception) { null }
    private val setSpeed = try { locationClass.getMethod("setSpeed", Float::class.java) } catch (_: Exception) { null }
    private val setHasBearing = try { locationClass.getMethod("setHasBearing", Boolean::class.java) } catch (_: Exception) { null }
    private val setBearing = try { locationClass.getMethod("setBearing", Float::class.java) } catch (_: Exception) { null }
    private val setBearingAcc = try { locationClass.getMethod("setBearingAcc", Float::class.java) } catch (_: Exception) { null }

    private var cachedInjectMethod: Method? = null
    private var pauseHardwareGps: Method? = null
    private var resumeHardwareGps: Method? = null

    // The 15-second Timeout Trigger
    private val fallbackRunnable = Runnable {
        if (isHardwarePaused) {
            app.runInUIThread { unmuteHardwareGps() }
            log.debug("SignalK data timeout (15s). Restored smartphone GPS.")
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
            } catch (_: Exception) {}
        }
        val pauseNames = listOf("pauseAllUpdates", "stopLocationUpdates", "pause")
        pauseHardwareGps = pauseNames.firstNotNullOfOrNull {
            try {
                providerClass.getMethod(it)
            } catch (_: Exception) {
                null
            }
        }
        val resumeNames = listOf("resumeAllUpdates", "startLocationUpdates", "resume")
        resumeHardwareGps = resumeNames.firstNotNullOfOrNull {
            try {
                providerClass.getMethod(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun start() {
        if (isActive) return
        isActive = true
        engine?.registerListener(listener)

        app.runInUIThread { muteHardwareGps() }

        // Start the watchdog
        fallbackHandler.removeCallbacks(fallbackRunnable)
        fallbackHandler.postDelayed(fallbackRunnable, 15000)
        log.debug("Location Bridge: Active, GPS Muted, Watchdog started.")
    }

    fun stop() {
        isActive = false
        engine?.unregisterListener(listener)
        fallbackHandler.removeCallbacks(fallbackRunnable)
        app.runInUIThread { unmuteHardwareGps() }
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        // 1. Guard clause: Ensure we have the data we need
        if (state.latitude == null || (state.longitude == null)) return

        // 2. Throttle to 1Hz (1000ms) for OsmAnd stability
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastUpdateTime.get()) < 1000) return
        lastUpdateTime.set(currentTime)

        // 3. Reset the watchdog
        fallbackHandler.removeCallbacks(fallbackRunnable)
        fallbackHandler.postDelayed(fallbackRunnable, 15000)

        try {
            // 4. Build the location object using the reflection constructor
            val loc = constructor.newInstance("gps")

            // 5. Inject values using the reflection methods you defined
            setLat.invoke(loc, state.latitude)
            setLon.invoke(loc, state.longitude)
            setTime.invoke(loc, currentTime)
            setNanos?.invoke(loc, SystemClock.elapsedRealtimeNanos())

            // 6. Set optional telemetry (Speed, Bearing, Accuracy)
            setHasAcc?.invoke(loc, true)
            setAcc?.invoke(loc, 1.0f)
            state.speedOverGround?.let { setHasSpeed?.invoke(loc, true); setSpeed?.invoke(loc, it.toFloat()) }
            state.headingTrue?.let { setHasBearing?.invoke(loc, true); setBearing?.invoke(loc, Math.toDegrees(it).toFloat()) }
            setBearingAcc?.invoke(loc, 1f)

            // 7. Inject into OsmAnd via the cached method
            app.runInUIThread {
                if (!isHardwarePaused) muteHardwareGps()
                cachedInjectMethod?.invoke(app.locationProvider, loc)
            }
        } catch (e: Exception) {
            // 8. Industrial Error Logging: If the reflection fails, log it instead of crashing
            log.error("Injection error: ${e.message}")
        }
    }

    private fun muteHardwareGps() {
        if (isHardwarePaused) return

        // Permission check for LocationManager access
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            log.error("Cannot mute GPS: Missing ACCESS_FINE_LOCATION permission.")
            return
        }

        try {
            pauseHardwareGps?.invoke(app.locationProvider)
            val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            dummyListener = object : LocationListener {
                override fun onLocationChanged(l: android.location.Location) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
                override fun onProviderEnabled(p0: String) {}
                override fun onProviderDisabled(p0: String) {}
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, dummyListener!!)
            isHardwarePaused = true
        } catch (se: SecurityException) {
            log.error("SecurityException: Location permission denied: ${se.message}")
        } catch (e: Exception) {
            log.error("GPS mute failed: ${e.message}")
        }
    }

    private fun unmuteHardwareGps() {
        if (!isHardwarePaused) return
        try {
            val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            dummyListener?.let { locationManager.removeUpdates(it) }
            resumeHardwareGps?.invoke(app.locationProvider)
            isHardwarePaused = false
        } catch (_: Exception) {}
    }
}
