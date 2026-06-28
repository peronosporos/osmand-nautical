package net.osmand.plus.plugins.nautical

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

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkPluginLifecycle()
    }

    fun checkPluginLifecycle() {
        if (!isEnabled) {
            stopEverything()
            return
        }

        // Use safe call (?.) and Elvis operator (?:) to handle potential nulls during startup
        val currentModeId = app.settings?.APPLICATION_MODE?.get()?.toString()?.lowercase() ?: "default"
        val isBoatMode = currentModeId.contains("nautical") || currentModeId.contains("boat")

        if (isBoatMode) {
            Log.d("NauticalPlugin", "Boat mode detected. Activating services.")
            if (locationProvider == null) {
                locationProvider = NauticalLocationProvider(app, engine)
            }
            locationProvider?.start()
            startEngine()
        } else {
            Log.d("NauticalPlugin", "Dormant mode ($currentModeId). Shutting down.")
            stopEverything()
        }
    }

    private fun stopEverything() {
        locationProvider?.stop()
        connection.disconnect()
    }

    private fun startEngine() {
        val ip = app.settings?.NAUTICAL_SERVER_IP?.get()
        val port = app.settings?.NAUTICAL_SERVER_PORT?.get()

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