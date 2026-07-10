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

    // 1. LOCAL REGISTRATION (For your new features that aren't in OsmandSettings.java)
    private val xteThresholdPref = app.settings.registerFloatPreference("nautical_xte_threshold", 0.1f)
    private val nightVisionOpacityPref = app.settings.registerFloatPreference("nautical_night_vision_opacity", 0.5f)

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
                // 1. Safely access OsmAnd's physical GPX directory
                val gpxDir = app.getAppPath(net.osmand.IndexConstants.GPX_INDEX_DIR)
                val gpxFiles = gpxDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".gpx", ignoreCase = true)
                }?.toList() ?: emptyList()

                if (gpxFiles.isEmpty()) {
                    Toast.makeText(mapActivity, "No saved tracks found in OsmAnd's tracks folder.", Toast.LENGTH_SHORT).show()
                    return@setListener true
                }

                // 2. Extract clean names for the UI dialog (removes the .gpx extension)
                val fileNames = gpxFiles.map { it.nameWithoutExtension }.toTypedArray()

                // 3. Show native Android dialog
                android.app.AlertDialog.Builder(mapActivity)
                    .setTitle("Select Track for Autopilot")
                    .setItems(fileNames) { _, which ->
                        val selectedFile = gpxFiles[which]

                        // 4. Parse file safely in background
                        val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                        pluginScope.launch(Dispatchers.IO) {
                            val routePoints = mutableListOf<Pair<Double, Double>>()
                            try {
                                // Fallback to GPXUtilities for maximum compatibility
                                val gpx = net.osmand.gpx.GPXUtilities.loadGPXFile(selectedFile, null, false)

                                // Directly access public fields, avoiding the 'get' methods that might not exist
                                gpx?.tracks?.forEach { track ->
                                    track.segments.forEach { segment ->
                                        segment.points.forEach { point ->
                                            // Directly access 'lat' and 'lon' fields (the most stable API)
                                            routePoints.add(Pair(point.lat, point.lon))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("NauticalPlugin", "Error parsing GPX: ${e.message}")
                            }

                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (routePoints.isNotEmpty()) {
                                    engine?.loadRoute(routePoints)
                                    Toast.makeText(mapActivity, "Autopilot: Loaded ${routePoints.size} points", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(mapActivity, "Track is empty or invalid.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        })
    }
}