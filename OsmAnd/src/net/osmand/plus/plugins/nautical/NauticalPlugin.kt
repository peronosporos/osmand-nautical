package net.osmand.plus.plugins.nautical

import android.content.Context
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

            // Wire AIS pipeline safely
            engine.registerAisListener { target ->
                val nmeaString = aisEncoder.encodeTargetToAivdm(target)
                if (nmeaString != null) {
                    aisEmitter?.emitNmeaSentence(nmeaString)
                }
            }

            startEngine()

            aisEmitter?.start()
            locationProvider?.start()

        } else {
            Log.d("NauticalPlugin", "Plugin explicitly disabled.")
            shutdownResources()
        }
    }

    private fun shutdownResources() {
        aisEmitter?.stop()
        locationProvider?.stop()
        marineDashboard?.destroy()
        marineDashboard = null
        connection.disconnect()
    }

    // Restored the correct hook. Because MarineDashboard no longer
    // aborts on startup, this will successfully register your widgets.
    override fun registerLayers(context: Context, mapActivity: MapActivity?) {
        super.registerLayers(context, mapActivity)

        if (mapActivity != null) {
            if (marineDashboard == null) {
                marineDashboard = MarineDashboard(app, engine)
                marineDashboard?.init(mapActivity)
            }
        }
    }

    // Keep this for proper cleanup
    fun onMapActivityDestroyed(mapActivity: MapActivity) {
        marineDashboard?.destroy()
        marineDashboard = null
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