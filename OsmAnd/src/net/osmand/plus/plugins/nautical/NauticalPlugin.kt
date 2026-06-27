package net.osmand.plus.plugins.nautical
import android.util.Log

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
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
        // Reusing the native sport sailing icon resource already built into OsmAnd
        return R.drawable.ic_action_sail_boat_dark
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled) {
            // We will configure the active server IP address and port strings in a later step
            Log.d("NauticalPlugin", "Plugin activated. Engine ready.")
        } else {
            connection.disconnect()
            Log.d("NauticalPlugin", "Plugin deactivated. Network disconnected cleanly.")
        }
    }
}