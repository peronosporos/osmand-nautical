package net.osmand.plus.plugins.nautical

import android.util.Log
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.NauticalLocationProvider
import net.osmand.plus.plugins.nautical.engine.AisUdpEmitter
import net.osmand.plus.plugins.nautical.engine.AisEncoder

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
    }

    private val connection = OkHttpSignalKConnection()
    val engine = SignalKEngine(connection)

    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private val aisEncoder = AisEncoder()

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = "Nautical Marine Controls"
    override fun getDescription(linksEnabled: Boolean): CharSequence = "SignalK integration."
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled) {
            Log.d("NauticalPlugin", "Plugin explicitly enabled by user.")

            if (locationProvider == null) {
                locationProvider = NauticalLocationProvider(app, engine)
            }
            if (aisEmitter == null) {
                aisEmitter = AisUdpEmitter()
            }

            locationProvider?.start()
            aisEmitter?.start()

            // Wire the live SignalK engine to the AIS Encoder and UDP Emitter
            engine.registerAisListener { target ->
                val nmeaString = aisEncoder.encodeTargetToAivdm(target)
                if (nmeaString != null) {
                    aisEmitter?.emitNmeaSentence(nmeaString)
                }
            }

            startEngine()
        } else {
            Log.d("NauticalPlugin", "Plugin explicitly disabled by user.")

            aisEmitter?.stop()
            locationProvider?.stop()
            connection.disconnect()
        }
    }

    private fun startEngine() {
        val ip = app.settings?.NAUTICAL_SERVER_IP?.get()
        val port = app.settings?.NAUTICAL_SERVER_PORT?.get()

        if (ip.isNullOrEmpty()) {
            Log.e("NauticalPlugin", "Server IP is missing. Cannot connect.")
            return
        }

        connection.disconnect()

        val wsUrl = "ws://$ip:$port/signalk/v1/stream"
        Log.d("NauticalPlugin", "Attempting connection to: $wsUrl")
        connection.connect(wsUrl) { message ->
            engine.handleIncomingMessage(message)
        }
    }

    override fun getSettingsScreenType(): net.osmand.plus.settings.fragments.SettingsScreenType? {
        return net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS
    }
}