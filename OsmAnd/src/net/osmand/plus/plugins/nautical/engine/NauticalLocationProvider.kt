package net.osmand.plus.plugins.nautical.engine

import android.preference.PreferenceManager
import android.util.Log
import net.osmand.plus.OsmandApplication

class NauticalLocationProvider(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    private var isActive = false
    private var lastUpdateTime: Long = 0

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
        lastUpdateTime = 0
        Log.d("NauticalPlugin", "Location Bridge: Stopped")
    }

    private fun injectMarineStateAsLocation(state: MarineState) {
        // 1. Bypass OsmAnd's custom classes and read the active profile directly from Android's SharedPreferences.
        // This relies purely on the Android SDK, eliminating all "Unresolved reference" errors.
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val currentProfileStr = prefs.getString("application_mode", "default")?.lowercase() ?: ""

        Log.d("NauticalPlugin", "Raw SharedPreferences Profile: $currentProfileStr")

        // 2. Filter based on the raw string
        if (!currentProfileStr.contains("nautical") && !currentProfileStr.contains("boat")) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (lastUpdateTime != 0L && (currentTime - lastUpdateTime) > 10000) return
        lastUpdateTime = currentTime

        val lat = state.latitude ?: return
        val lon = state.longitude ?: return

        try {
            val locationClass = Class.forName("net.osmand.Location")
            val constructor = locationClass.getConstructor(String::class.java)
            val loc = constructor.newInstance("SignalKProvider")

            locationClass.getMethod("setLatitude", Double::class.java).invoke(loc, lat)
            locationClass.getMethod("setLongitude", Double::class.java).invoke(loc, lon)
            locationClass.getMethod("setTime", Long::class.java).invoke(loc, currentTime)
            locationClass.getMethod("setAccuracy", Float::class.java).invoke(loc, 5.0f)
            locationClass.getMethod("setHasAccuracy", Boolean::class.java).invoke(loc, true)

            state.speedOverGround?.let {
                locationClass.getMethod("setSpeed", Float::class.java).invoke(loc, it.toFloat())
            }

            if (state.headingTrue != null) {
                locationClass.getMethod("setBearing", Float::class.java).invoke(loc, state.headingTrue.toFloat())
                locationClass.getMethod("setBearingAcc", Float::class.java).invoke(loc, 1f)
            }

            app.runInUIThread {
                try {
                    val provider = app.locationProvider
                    val method = provider.javaClass.getMethod("setLocationFromService", locationClass)
                    method.invoke(provider, loc)
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "Final injection failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Reflection/Injection failed: ${e.message}")
        }
    }
}