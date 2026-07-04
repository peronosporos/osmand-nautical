package net.osmand.plus.plugins.nautical

import android.widget.Toast
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.routing.IRouteInformationListener // Added explicit import
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
        lateinit var engine: SignalKEngine
        @JvmStatic
        lateinit var autopilot: AutopilotController
    }

    private val connection = OkHttpSignalKConnection()
    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private val aisEncoder = AisEncoder()
    private var autopilotListener: AutopilotRouteListener? = null

    init {
        engine = SignalKEngine(connection)
        autopilot = AutopilotController(app, connection)
    }

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = "Nautical Marine Controls"
    override fun getDescription(linksEnabled: Boolean): CharSequence = "SignalK integration."
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled) {
            if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
            if (aisEmitter == null) aisEmitter = AisUdpEmitter()

            val listener = AutopilotRouteListener(engine, app.routingHelper)
            autopilotListener = listener
            app.routingHelper.addListener(listener as IRouteInformationListener)

            engine.registerAisListener { target ->
                aisEncoder.encodeTargetToAivdm(target)?.let { sentence ->
                    aisEmitter?.emitNmeaSentence(sentence)
                }
            }
            startEngine()
            aisEmitter?.start()
            locationProvider?.start()
        } else {
            shutdownResources()
        }
    }

    private fun shutdownResources() {
        aisEmitter?.stop()
        locationProvider?.stop()
        connection.disconnect()

        val listener = autopilotListener
        if (listener != null) {
            app.routingHelper.removeListener(listener as IRouteInformationListener)
            autopilotListener = null
        }
        engine.stop()
    }

    private fun startEngine() { /* ... unchanged ... */ }

    override fun registerMapContextMenuActions(
        mapActivity: MapActivity,
        latitude: Double,
        longitude: Double,
        adapter: ContextMenuAdapter,
        selectedObj: Any?,
        configureMenu: Boolean
    ) {
        // ... (Keep your existing context menu logic)
    }
}