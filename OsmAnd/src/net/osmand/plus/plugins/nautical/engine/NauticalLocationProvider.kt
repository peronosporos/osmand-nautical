package net.osmand.plus.plugins.nautical.engine

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import net.osmand.plus.OsmandApplication
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    private var isActive = false
    private val lastUpdateTime = AtomicLong(0L)
    private val lastSignalKTime = AtomicLong(System.currentTimeMillis())

    private val isUsingSignalK = AtomicBoolean(false)
    private var isHardwarePaused = false
    private var dummyListener: LocationListener? = null
    private var watchdogThread: Thread? = null

    // Reflection setup
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)
    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)
    private val setNanos = try { locationClass.getMethod("setElapsedRealtimeNanos", Long::class.java) } catch (e: Exception) { null }
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

    private val listener: (MarineState) -> Unit = { state -> if (isActive) processState(state) }

    init {
        val providerClass = app.locationProvider.javaClass
        val methods = listOf("setLocation", "updateLocation", "setLocationFromService")
        for (name in methods) {
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

        watchdogThread = Thread({
            try {
                while (isActive) {
                    val timeSinceLast = System.currentTimeMillis() - lastSignalKTime.get()
                    if (timeSinceLast > 15000 && isUsingSignalK.get()) {
                        switchMode(false)
                    }
                    Thread.sleep(2000)
                }
            } catch (e: InterruptedException) {}
        }, "NauticalWatchdog").apply { start() }
    }

    private fun processState(state: MarineState) {
        if (state.latitude == null || state.longitude == null) return
        lastSignalKTime.set(System.currentTimeMillis())
        if (!isUsingSignalK.get()) switchMode(true)

        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastUpdateTime.get()) < 1000) return
        lastUpdateTime.set(currentTime)

        try {
            val loc = constructor.newInstance("gps")
            setLat.invoke(loc, state.latitude)
            setLon.invoke(loc, state.longitude)
            setTime.invoke(loc, currentTime)
            setNanos?.invoke(loc, SystemClock.elapsedRealtimeNanos())
            setHasAcc?.invoke(loc, true); setAcc?.invoke(loc, 1.0f)
            state.speedOverGround?.let { setHasSpeed?.invoke(loc, true); setSpeed?.invoke(loc, it.toFloat()) }
            state.headingTrue?.let { setHasBearing?.invoke(loc, true); setBearing?.invoke(loc, Math.toDegrees(it).toFloat()) }
            setBearingAcc?.invoke(loc, 1f)
            app.runInUIThread { cachedInjectMethod?.invoke(app.locationProvider, loc) }
        } catch (e: Exception) {}
    }

    private fun switchMode(useSignalK: Boolean) {
        if (isUsingSignalK.getAndSet(useSignalK) == useSignalK) return
        app.runInUIThread { if (useSignalK) muteHardwareGps() else unmuteHardwareGps() }
    }

    private fun muteHardwareGps() {
        if (isHardwarePaused) return
        // Check for location permissions before accessing GPS_PROVIDER
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NauticalPlugin", "Missing location permission, cannot mute GPS.")
            return
        }
        try {
            pauseHardwareGps?.invoke(app.locationProvider)
            val lm = app.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            dummyListener = object : LocationListener {
                override fun onLocationChanged(l: android.location.Location) {}
                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
                override fun onProviderEnabled(p0: String) {}
                override fun onProviderDisabled(p0: String) {}
            }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, dummyListener!!)
            isHardwarePaused = true
        } catch (se: SecurityException) {
            Log.e("NauticalPlugin", "SecurityException on mute: ${se.message}")
        } catch (e: Exception) { Log.e("NauticalPlugin", "Mute failed: ${e.message}") }
    }

    private fun unmuteHardwareGps() {
        if (!isHardwarePaused) return
        try {
            val lm = app.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            dummyListener?.let { lm.removeUpdates(it) }
            resumeHardwareGps?.invoke(app.locationProvider)
            isHardwarePaused = false
        } catch (e: Exception) {}
    }

    fun stop() {
        isActive = false
        watchdogThread?.interrupt()
        engine.unregisterListener(listener)
        app.runInUIThread { unmuteHardwareGps() }
    }
}