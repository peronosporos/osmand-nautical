package net.osmand.plus.plugins.nautical

import android.widget.Toast
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
        lateinit var engine: SignalKEngine
        lateinit var autopilot: AutopilotController

        @JvmStatic
        fun getAutopilot(): AutopilotController = autopilot
    }

    private val connection = OkHttpSignalKConnection()

    init {
        engine = SignalKEngine(connection)
        autopilot = AutopilotController(app)
    }

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
            if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
            if (aisEmitter == null) aisEmitter = AisUdpEmitter()

            engine.registerAisListener { target ->
                aisEncoder.encodeTargetToAivdm(target)?.let { aisEmitter?.emitNmeaSentence(it) }
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
    }

    private fun startEngine() {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        if (ip.isNullOrEmpty()) return

        connection.disconnect()
        val wsUrl = "ws://$ip:$port/signalk/v1/stream?subscribe=all"
        connection.connect(wsUrl) { message -> engine.handleIncomingMessage(message) }
    }

    // --- PHASE 4: THE MAP CONTEXT MENU INJECTION ---
    override fun registerMapContextMenuActions(
        mapActivity: MapActivity,
        latitude: Double,
        longitude: Double,
        adapter: ContextMenuAdapter,
        selectedObj: Any?,
        configureMenu: Boolean
    ) {
        val item = ContextMenuItem("nautical_steer_id")
        item.setTitleId(R.string.nautical_steer_here, mapActivity)
        item.setIcon(R.drawable.widget_target_day)

        // The compiler error told us exactly what the signature is:
        // (OnDataChangeUiAdapter, View, ContextMenuItem, Boolean) -> Boolean
        item.setListener { adapter, view, item, isChecked ->
            autopilot.sendActiveWaypoint(latitude, longitude)

            Toast.makeText(mapActivity, "Autopilot Engaged", Toast.LENGTH_SHORT).show()

            // Return true to indicate the click was handled
            true
        }

        adapter.addItem(item)
    }
}