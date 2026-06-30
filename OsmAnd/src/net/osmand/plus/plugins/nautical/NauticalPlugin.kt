package net.osmand.plus.plugins.nautical

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.NauticalLocationProvider
import net.osmand.plus.plugins.nautical.engine.AisUdpEmitter
import net.osmand.plus.plugins.nautical.engine.AisEncoder
import net.osmand.plus.plugins.nautical.ui.MarineDashboard

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
    }

    private val connection = OkHttpSignalKConnection()
    val engine = SignalKEngine(connection)

    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private var marineDashboard: MarineDashboard? = null
    private val aisEncoder = AisEncoder()

    // The native Android OS interceptor
    private var lifecycleCallback: Application.ActivityLifecycleCallbacks? = null

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = "Nautical Marine Controls"
    override fun getDescription(linksEnabled: Boolean): CharSequence = "SignalK integration."
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled) {
            Log.d("NauticalPlugin", "Plugin explicitly enabled.")

            if (locationProvider == null) {
                locationProvider = NauticalLocationProvider(app, engine)
            }
            if (aisEmitter == null) {
                aisEmitter = AisUdpEmitter()
            }

            engine.registerAisListener { target ->
                val nmeaString = aisEncoder.encodeTargetToAivdm(target)
                if (nmeaString != null) {
                    aisEmitter?.emitNmeaSentence(nmeaString)
                }
            }

            startEngine()
            aisEmitter?.start()
            locationProvider?.start()

            // Start listening to the Android OS directly
            attachAndroidLifecycleInterceptor()

        } else {
            Log.d("NauticalPlugin", "Plugin explicitly disabled.")
            shutdownResources()
        }
    }

    private fun attachAndroidLifecycleInterceptor() {
        if (lifecycleCallback != null) return

        lifecycleCallback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                // The absolute second the MapActivity hits the screen, we intercept it.
                if (activity is MapActivity) {
                    if (marineDashboard == null) {
                        Log.d("NauticalPlugin", "OS Intercepted MapActivity. Booting Dashboard.")
                        marineDashboard = MarineDashboard(app, engine)
                        marineDashboard?.init(activity)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is MapActivity) {
                    marineDashboard?.destroy()
                    marineDashboard = null
                }
            }
        }

        app.registerActivityLifecycleCallbacks(lifecycleCallback)
    }

    private fun shutdownResources() {
        aisEmitter?.stop()
        locationProvider?.stop()
        marineDashboard?.destroy()
        marineDashboard = null
        connection.disconnect()

        lifecycleCallback?.let {
            app.unregisterActivityLifecycleCallbacks(it)
            lifecycleCallback = null
        }
    }

    private fun startEngine() {
        val ip = app.settings?.NAUTICAL_SERVER_IP?.get()
        val port = app.settings?.NAUTICAL_SERVER_PORT?.get()

        if (ip.isNullOrEmpty()) {
            Log.e("NauticalPlugin", "Server IP is missing. Engine startup aborted.")
            return
        }

        connection.disconnect()

        val wsUrl = "ws://$ip:$port/signalk/v1/stream?subscribe=all"
        Log.d("NauticalPlugin", "Connecting to SignalK: $wsUrl")

        connection.connect(wsUrl) { message ->
            engine.handleIncomingMessage(message)
        }
    }

    override fun getSettingsScreenType(): net.osmand.plus.settings.fragments.SettingsScreenType? {
        return net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS
    }
}