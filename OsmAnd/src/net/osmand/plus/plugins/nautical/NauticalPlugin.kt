package net.osmand.plus.plugins.nautical

import android.preference.PreferenceManager
import android.util.Log
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.NauticalLocationProvider

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
    }

    private val connection = OkHttpSignalKConnection()
    val engine = SignalKEngine(connection)
    private var locationProvider: NauticalLocationProvider? = null

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = "Nautical Marine Controls"
    override fun getDescription(linksEnabled: Boolean): CharSequence = "SignalK integration."
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    // 1. Manually trigger a lifecycle check whenever the settings are updated
    // We override this to detect when the user changes profiles
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkPluginLifecycle()
    }

    // 2. The central logic using SharedPreferences (No external type dependencies)
    fun checkPluginLifecycle() {
        if (!isEnabled) {
            locationProvider?.stop()
            connection.disconnect()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val currentProfile = prefs.getString("application_mode", "default")?.lowercase() ?: ""
        val isBoatMode = currentProfile.contains("nautical") || currentProfile.contains("boat")

        if (isBoatMode) {
            if (locationProvider == null) {
                locationProvider = NauticalLocationProvider(app, engine)
            }
            locationProvider?.start()
            startEngine()
        } else {
            locationProvider?.stop()
            connection.disconnect()
        }
    }

    // 3. Engine management
    private fun startEngine() {
        // Safe check for connection state if possible, otherwise just re-connect
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) return

        val wsUrl = "ws://$ip:$port/signalk/v1/stream"
        connection.connect(wsUrl) { message ->
            engine.handleIncomingMessage(message)
        }
    }

    override fun getSettingsScreenType(): net.osmand.plus.settings.fragments.SettingsScreenType? {
        return net.osmand.plus.settings.fragments.SettingsScreenType.NAUTICAL_SETTINGS
    }
}