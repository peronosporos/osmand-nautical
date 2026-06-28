package net.osmand.plus.plugins.nautical

import android.util.Log
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
    }

    private val connection = OkHttpSignalKConnection()
    val engine = SignalKEngine(connection)

    override fun getId(): String = NAUTICAL_ID

    override fun getName(): String = "Nautical Marine Controls"

    override fun getDescription(linksEnabled: Boolean): CharSequence {
        return "Connects directly to your SignalK server to stream live telemetry, map AIS targets, and provide direct autopilot tracking controls."
    }

    override fun getLogoResourceId(): Int {
        return R.drawable.ic_action_sail_boat_dark
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled) {
            startEngine()
        } else {
            connection.disconnect()
            Log.d("NauticalPlugin", "Plugin deactivated. WebSocket closed.")
        }
    }

    private fun startEngine() {
        // Pull the settings from the core registry we created
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) {
            Log.w("NauticalPlugin", "Cannot start SignalK: IP is not configured.")
            app.showShortToastMessage("Nautical Plugin: Please configure Server IP in settings.")
            return
        }


        val wsUrl = "ws://$ip:$port/signalk/v1/stream"
        Log.d("NauticalPlugin", "Connecting to SignalK at $wsUrl")

        connection.connect(wsUrl) { message ->
            engine.handleIncomingMessage(message)
        }
    }

    override fun getSettingsScreenType(): net.osmand.plus.settings.fragments.SettingsScreenType? {
        return net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS
    }
}