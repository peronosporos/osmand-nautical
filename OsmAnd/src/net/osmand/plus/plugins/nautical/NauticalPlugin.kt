package net.osmand.plus.plugins.nautical

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.routing.IRouteInformationListener
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import kotlin.math.abs
import java.lang.ref.WeakReference

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"

        @JvmStatic
        var engine: SignalKEngine? = null

        @JvmStatic
        var autopilot: AutopilotController? = null

        private var instanceRef: WeakReference<NauticalPlugin>? = null

        @JvmStatic
        fun getInstance(): NauticalPlugin? = instanceRef?.get()


        @JvmStatic
        fun sendWaypoint(lat: Double, lon: Double) {
            engine?.clearRoute()
            engine?.addWaypoint(lat, lon)
            val plugin = getInstance()
            plugin?.app?.osmandMap?.refreshMap()
            val waypointCommand = "WAYPOINT:$lat,$lon"
            engine?.dispatchCommand(waypointCommand)
        }
    }

    private val connection = OkHttpSignalKConnection()
    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private val aisEncoder = AisEncoder()
    private var autopilotListener: AutopilotRouteListener? = null
    private var isAlertActive = false
    private var nauticalMapLayer: NauticalMapLayer? = null

    private val xteThresholdPref = app.settings.registerFloatPreference("nautical_xte_threshold", 0.1f)
    private val nightVisionOpacityPref = app.settings.registerFloatPreference("nautical_night_vision_opacity", 0.5f)
    private val serverIpPref = app.settings.registerStringPreference("nautical_server_ip", "")
    private val serverPortPref = app.settings.registerStringPreference("nautical_server_port", "")

    private val saveFile = java.io.File(app.filesDir, "trajectory.dat")

    private fun saveTrajectory() {
        try {
            val data = engine?.getTrajectory()
            java.io.ObjectOutputStream(saveFile.outputStream()).use { it.writeObject(data) }
        } catch (e: Exception) {
            logError("Failed to save trajectory: ${e.message}")
        }
    }

    private fun loadTrajectory() {
        if (saveFile.exists()) {
            try {
                java.io.ObjectInputStream(saveFile.inputStream()).use {
                    @Suppress("UNCHECKED_CAST")
                    val data = it.readObject() as List<Pair<Double, Double>>
                    data.forEach { p -> engine?.addWaypoint(p.first, p.second) }
                }
            } catch (e: Exception) {
                logError("Failed to load trajectory: ${e.message}")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onGpxFileSelected(uri: Uri?) {
        if (uri == null) return

        val streamer = GpxStreamer(app)
        // Launch a coroutine to parse the file without freezing the UI
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val route = streamer.parseGpx(uri)
            if (route.isNotEmpty()) {
                // Send the entire route to the engine
                engine?.loadRoute(route)

                // Send first batch to hardware
                engine?.pushNextWaypointsToAutopilot()

                Toast.makeText(app, "Route Loaded: ${route.size} points. Autopilot ready.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(app, "Failed to parse GPX or route is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }



    init {
        instanceRef = WeakReference(this)
        engine = SignalKEngine()
        autopilot = AutopilotController(app, connection)
        loadTrajectory()
    }

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = "Nautical Marine Controls"
    override fun getDescription(linksEnabled: Boolean): CharSequence = "SignalK integration."
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val mapView = app.osmandMap?.mapView
        if (enabled) {
            instanceRef = WeakReference(this)

            nauticalMapLayer = NauticalMapLayer(app)
            mapView?.addLayer(nauticalMapLayer!!, 5.0f)

            if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
            if (aisEmitter == null) aisEmitter = AisUdpEmitter()

            val listener = AutopilotRouteListener(app.routingHelper)
            autopilotListener = listener
            app.routingHelper.addListener(listener as IRouteInformationListener)

            engine?.registerAisListener { target ->
                aisEncoder.encodeTargetToAivdm(target)?.let { aisEmitter?.emitNmeaSentence(it) }
            }

            engine?.registerListener { state -> checkOffCourseAlert(state) }

            startEngine()
            aisEmitter?.start()
            locationProvider?.start()
        } else {
            instanceRef = null

            nauticalMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                nauticalMapLayer = null
            }

            shutdownResources()
        }
    }

    private fun checkOffCourseAlert(state: MarineState) {
        val xte: Double? = state.crossTrackError
        val deadband = 0.05
        if (xte != null && abs(xte) > xteThresholdPref.get()) {
            if (abs(xte) > deadband && !isAlertActive) {
                isAlertActive = true
                Log.w("NauticalPlugin", "OFF COURSE ALERT: $xte NM")
            }
        } else {
            isAlertActive = false
        }
    }

    private var isNightVisionEnabled = false

    fun toggleNightVision(mapActivity: MapActivity, enable: Boolean) {
        this.isNightVisionEnabled = enable

        // We target the map activity's root view so the tint covers EVERYTHING
        val container = mapActivity.findViewById<ViewGroup>(android.R.id.content)

        if (enable) {
            val opacity = nightVisionOpacityPref.get().coerceIn(0f, 1f)
            val alphaHex = (opacity * 255).toInt().toString(16).padStart(2, '0')

            // Create a dedicated overlay view that sits on top of everything
            val overlay = View(mapActivity)
            overlay.setBackgroundColor("#${alphaHex}FF0000".toColorInt())
            overlay.isClickable = false // Let touches pass through
            overlay.tag = "nautical_night_overlay" // Tag it so we can find it later

            container.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        } else {
            // Remove the overlay by finding it via the tag
            val overlay = container.findViewWithTag<View>("nautical_night_overlay")
            if (overlay != null) {
                container.removeView(overlay)
            }
        }
    }

    private fun startEngine() {
        val ip: String? = serverIpPref.get()
        val port: String? = serverPortPref.get()

        if (ip.isNullOrEmpty()) return

        val wsUrl = "ws://$ip:$port/signalk/v1/stream?subscribe=all"

        engine?.onConnectionLost = {
            Log.w("NauticalPlugin", "Engine silent. Retrying...")
            retryConnection()
        }

        connection.disconnect()
        connection.connect(wsUrl) { message -> engine?.handleIncomingMessage(message) }
    }

    private fun retryConnection() {
        Handler(Looper.getMainLooper()).postDelayed({ startEngine() }, 5000)
    }

    private fun logError(message: String, tag: String = "NauticalPlugin") {
        Log.e(tag, message)
        try {
            val logFile = java.io.File(app.filesDir, "nautical_errors.log")
            if (logFile.length() > 50000) logFile.delete()
            logFile.appendText("${java.util.Date()}: [$tag] $message\n")
        } catch (_: Exception) { }
    }

    private fun shutdownResources() {
        saveTrajectory()
        aisEmitter?.stop()
        locationProvider?.stop()
        connection.disconnect()
        engine?.stop()

        autopilotListener?.let {
            app.routingHelper.removeListener(it as IRouteInformationListener)
            autopilotListener = null
        }

        isNightVisionEnabled = false
    }

    private var gpxLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {
        autopilot?.isConnected()?.let { if (!it) return }

        adapter.addItem(ContextMenuItem("nautical_night_vision").apply {
            setTitle("Toggle Night Vision")
            setListener { _, _, _, _ ->
                toggleNightVision(mapActivity, !isNightVisionEnabled)
                true
            }
        })

        adapter.addItem(ContextMenuItem("nautical_steer_id").apply {
            setTitle(mapActivity.getString(R.string.nautical_steer))
            setIcon(R.drawable.ic_action_sail_boat_dark)
            setListener { _, _, _, _ ->
                autopilot?.sendActiveWaypoint(lat, lon)
                Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_toast_heading_sent), Toast.LENGTH_SHORT).show()
                true
            }
        })

        adapter.addItem(ContextMenuItem("nautical_follow_gpx").apply {
            setTitle("Follow GPX Route")
            setListener { _, _, _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "*/*" // Or "application/gpx+xml"
                }
                gpxLauncher?.launch(intent)
                true
            }
        })
    }
}