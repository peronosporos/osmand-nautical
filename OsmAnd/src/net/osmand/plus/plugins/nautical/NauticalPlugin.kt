package net.osmand.plus.plugins.nautical

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*

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
}