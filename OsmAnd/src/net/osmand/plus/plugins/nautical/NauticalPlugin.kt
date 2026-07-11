package net.osmand.plus.plugins.nautical

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.net.Uri
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.routing.IRouteInformationListener
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import kotlin.math.abs
import java.lang.ref.WeakReference
import kotlinx.coroutines.cancel
import net.osmand.IndexConstants
import net.osmand.gpx.GPXUtilities

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
    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    override fun getSettingsScreenType(): SettingsScreenType {
        return SettingsScreenType.NAUTICAL_SETTINGS
    }

    override fun getPrefsDescription(): String {
        return "Configure SignalK connection settings"
    }


    val nauticalServerIp: CommonPreference<String> = registerStringPreference("server_ip", "")
        .makeGlobal().cache() as CommonPreference<String>

    val nauticalServerPort: CommonPreference<String> = registerStringPreference("server_port", "3000")
        .makeGlobal().cache() as CommonPreference<String>

    init {
        instanceRef = WeakReference(this)
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

            // 1. Initialize components if they are null
            if (engine == null) {
                engine = SignalKEngine()
                engine?.loadBuffersFromDisk(app)
            }
            if (autopilot == null) autopilot = AutopilotController(app, connection)
            if (nauticalMapLayer == null) {
                nauticalMapLayer = NauticalMapLayer(app)
                mapView?.addLayer(nauticalMapLayer!!, 5.0f)
            }
            if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
            if (aisEmitter == null) aisEmitter = AisUdpEmitter()
            if (autopilotListener == null) {
                val listener = AutopilotRouteListener(app.routingHelper)
                autopilotListener = listener
                app.routingHelper.addListener(listener as IRouteInformationListener)
            }

            // 2. Check settings
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            if (ip.isNullOrEmpty()) {
                Toast.makeText(app, "Nautical Plugin active, but IP is not configured.", Toast.LENGTH_LONG).show()
            }

            // 3. Setup UI and Listeners
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

            // 4. Start operations
            startEngine()
            aisEmitter?.start()
            locationProvider?.start()

        } else {
            // SHUTDOWN
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

        // Safely extract the local preference and convert to Double for math logic
        val threshold = (xteThresholdPref.get() ?: 0.1f).toDouble()

        if (xte != null && abs(xte) > threshold) {
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

        val container = mapActivity.findViewById<ViewGroup>(android.R.id.content)

        if (enable) {
            // Safely extract the local preference as Float
            val rawOpacity = nightVisionOpacityPref.get() ?: 0.5f
            val opacity = rawOpacity.coerceIn(0f, 1f)
            val alphaHex = (opacity * 255).toInt().toString(16).padStart(2, '0')

            val overlay = View(mapActivity)
            overlay.setBackgroundColor("#${alphaHex}FF0000".toColorInt())
            overlay.isClickable = false
            overlay.tag = "nautical_night_overlay"

            container.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        } else {
            val overlay = container.findViewWithTag<View>("nautical_night_overlay")
            if (overlay != null) {
                container.removeView(overlay)
            }
        }
    }

    private fun startEngine() {
        // 2. GLOBAL ACCESS (For your old features that DO exist in OsmandSettings.java)
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()

        if (ip.isNullOrEmpty()) {
            Log.w("NauticalPlugin", "IP not configured")
            return
        }

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
        pluginScope.cancel()
        saveTrajectory()
        aisEmitter?.stop()
        locationProvider?.stop()
        connection.disconnect()
        engine?.let {
            it.saveBuffersToDisk(app)
            it.stop()
        }

        autopilotListener?.let {
            app.routingHelper.removeListener(it as IRouteInformationListener)
            autopilotListener = null
        }

        isNightVisionEnabled = false
    }

    fun onGpxFileSelected(uri: Uri) {
        val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        pluginScope.launch {
            val routePoints = GpxStreamer(app).parseGpx(uri)
            if (routePoints.isNotEmpty()) {
                engine?.loadRoute(routePoints)
                Toast.makeText(app, "Autopilot: Loaded ${routePoints.size} points", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(app, "Track is empty or invalid.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseGpxPoints(file: java.io.File): List<Pair<Double, Double>> {
        val routePoints = mutableListOf<Pair<Double, Double>>()
        try {
            val gpx = GPXUtilities.loadGPXFile(file, null, false)
            gpx?.tracks?.forEach { track ->
                track.segments?.forEach { segment ->
                    segment.points?.forEach { point ->
                        routePoints.add(Pair(point.lat, point.lon))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Error parsing GPX: ${e.message}")
        }
        return routePoints
    }

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {

        val appMode = app.settings.APPLICATION_MODE.get()

        if (appMode != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }

        if (autopilot?.isConnected() != true) {
            return
        }

        // Night Vision
        adapter.addItem(ContextMenuItem("nautical_night_vision").apply {
            setTitle("Toggle Night Vision")
            setIcon(R.drawable.ic_action_red_filter_off)
            setListener { _, _, _, _ ->
                toggleNightVision(mapActivity, !isNightVisionEnabled)
                true
            }
        })

        // Steer Here
        adapter.addItem(ContextMenuItem("nautical_steer_id").apply {
            setTitle(mapActivity.getString(R.string.nautical_steer))
            setIcon(R.drawable.ic_action_sail_boat_dark)
            setListener { _, _, _, _ ->
                autopilot?.sendActiveWaypoint(lat, lon)
                Toast.makeText(mapActivity, mapActivity.getString(R.string.nautical_toast_heading_sent), Toast.LENGTH_SHORT).show()
                true
            }
        })

        // Follow GPX
        adapter.addItem(ContextMenuItem("nautical_follow_gpx").apply {
            setTitle("Follow GPX Route")
            setIcon(R.drawable.ic_action_track_16)
            setListener { _, _, _, _ ->
                handleGpxSelection(mapActivity)
                true
            }
        })
    }

    private fun handleGpxSelection(mapActivity: MapActivity) {
        val gpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR)
        val gpxFiles = gpxDir.listFiles { f -> f.isFile && f.name.endsWith(".gpx", true) }?.toList() ?: emptyList()

        if (gpxFiles.isEmpty()) {
            Toast.makeText(mapActivity, "No tracks found.", Toast.LENGTH_SHORT).show()
            return
        }

        android.app.AlertDialog.Builder(mapActivity)
            .setTitle("Select Track")
            .setItems(gpxFiles.map { it.nameWithoutExtension }.toTypedArray()) { _, which ->
                val selectedFile = gpxFiles[which]

                // Use the managed pluginScope here
                pluginScope.launch(Dispatchers.IO) {
                    val routePoints = parseGpxPoints(selectedFile)
                    withContext(Dispatchers.Main) {
                        if (routePoints.isNotEmpty()) {
                            engine?.loadRoute(routePoints)
                            Toast.makeText(mapActivity, "Loaded ${routePoints.size} points", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.show()
    }
}