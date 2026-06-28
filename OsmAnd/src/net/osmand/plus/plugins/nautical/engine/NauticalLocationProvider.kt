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

    // Defensive reflection
    private val locationClass = Class.forName("net.osmand.Location")
    private val constructor = locationClass.getConstructor(String::class.java)

    private val setLat = locationClass.getMethod("setLatitude", Double::class.java)
    private val setLon = locationClass.getMethod("setLongitude", Double::class.java)
    private val setTime = locationClass.getMethod("setTime", Long::class.java)

    // Nanos are CRITICAL for tricking OsmAnd into accepting the packet over hardware GPS
    private val setNanos: Method? = try { locationClass.getMethod("setElapsedRealtimeNanos", Long::class.java) } catch (e: Exception) { null }

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
        Log.d("NauticalPlugin", "Location Bridge: Stopped")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        // DIAGNOSTIC 1: Is the data actually reaching the provider?
        Log.d("NauticalPlugin", "RX Packet -> Lat: ${state.latitude}, Lon: ${state.longitude}")

        if (state.latitude == null || state.longitude == null) {
            // This fulfills your fallback logic: If SignalK has no GPS, we do nothing.
            // Hardware GPS (Greece) naturally takes over.
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastTime = lastUpdateTime.get()
        if (lastTime != 0L && (currentTime - lastTime) < 1000) return
        lastUpdateTime.set(currentTime)

        try {
            // PERFECT SPOOF: We must name it "gps" so OsmAnd treats it as high-priority hardware data
            val loc = constructor.newInstance("gps")
            setLat.invoke(loc, state.latitude)
            setLon.invoke(loc, state.longitude)
            setTime.invoke(loc, currentTime)
            setNanos?.invoke(loc, SystemClock.elapsedRealtimeNanos())

            setHasAcc?.invoke(loc, true)
            setAcc?.invoke(loc, 1.0f) // Hyper-accurate radius to beat the smartphone GPS priority

            state.speedOverGround?.let { setSpeed?.invoke(loc, it.toFloat()) }
            state.headingTrue?.let {
                setBearing?.invoke(loc, it.toFloat())
                setBearingAcc?.invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    val provider = app.locationProvider
                    // 1. Force the engine to accept this as the absolute last known location
                    // We use reflection to reach deep into the LocationProvider's map-update logic
                    val method = provider.javaClass.getMethod("setLocationFromService", locationClass)
                    method.invoke(provider, loc)

                    // 2. EXTRA: Force the map's last known location to the new coordinates
                    // This bypasses the priority-arbitration logic
                    val mapMethod = provider.javaClass.getMethod("setLastKnownLocation", locationClass)
                    mapMethod.invoke(provider, loc)

                    Log.d("NauticalPlugin", "Force-injected Finland coordinate as LastKnown")
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Force-injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) { Log.e("NauticalPlugin", "Reflection failed: ${e.message}") }
    }
}