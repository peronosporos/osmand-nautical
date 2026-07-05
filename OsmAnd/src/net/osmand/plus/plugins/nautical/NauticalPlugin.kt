package net.osmand.plus.plugins.nautical

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.routing.IRouteInformationListener
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

    private fun startEngine() {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        if (ip.isNullOrEmpty()) return

        val wsUrl = "ws://$ip:$port/signalk/v1/stream?subscribe=all"
        connection.connect(wsUrl) { message -> engine.handleIncomingMessage(message) }
    }

    override fun registerMapContextMenuActions(
        mapActivity: MapActivity,
        latitude: Double,
        longitude: Double,
        adapter: ContextMenuAdapter,
        selectedObj: Any?,
        configureMenu: Boolean
    ) {
        if (!autopilot.isConnected()) return

        // 1. Steer Here
        val steerItem = ContextMenuItem("nautical_steer_id")
        steerItem.setTitle(mapActivity.getString(R.string.nautical_steer))
        steerItem.setIcon(R.drawable.ic_action_sail_boat_dark)
        steerItem.setListener { _, _, _, _ ->
            autopilot.sendActiveWaypoint(latitude, longitude)
            Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_toast_heading_sent), Toast.LENGTH_SHORT).show()
            true
        }
        adapter.addItem(steerItem)

        // 2. Stop Autopilot
        val stopItem = ContextMenuItem("nautical_stop_id")
        stopItem.setTitle(mapActivity.getString(R.string.nautical_stop))
        stopItem.setIcon(R.drawable.ic_action_close)
        stopItem.setListener { _, _, _, _ ->
            autopilot.stopNavigation()
            Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_toast_stopped), Toast.LENGTH_SHORT).show()
            true
        }
        adapter.addItem(stopItem)

        // 3. Hold Heading
        val holdItem = ContextMenuItem("nautical_hold_id")
        holdItem.setTitle(mapActivity.getString(R.string.nautical_hold))
        holdItem.setIcon(R.drawable.ic_action_sail_boat_dark)
        holdItem.setListener { _, _, _, _ ->
            val input = EditText(mapActivity)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

            AlertDialog.Builder(mapActivity)
                .setTitle(mapActivity.getString(R.string.nautical_dialog_title_heading))
                .setView(input)
                .setPositiveButton(mapActivity.getString(R.string.nautical_dialog_button_hold)) { _, _ ->
                    val heading = input.text.toString().toDoubleOrNull()
                    if (heading != null) {
                        autopilot.holdHeading(heading)
                        Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_toast_heading_set, heading), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(mapActivity.getString(R.string.nautical_dialog_button_cancel), null)
                .show()
            true
        }
        adapter.addItem(holdItem)
    }
}