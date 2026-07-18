package net.osmand.plus.plugins.nautical

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.StateChangedListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.dashboard.DashboardType
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.settings.enums.DayNightMode
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.*
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import okhttp3.OkHttpClient
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {
    private val log = PlatformUtil.getLog(NauticalPlugin::class.java)

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"
        private const val GPX_INDEX_DIR = "tracks/"

        private val RED_FILTER_MATRIX = ColorMatrix(
            floatArrayOf(
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        @JvmField
        val NIGHT_VISION_FILTER = ColorMatrixColorFilter(RED_FILTER_MATRIX)

        @JvmField
        val DIM_FILTER = ColorMatrixColorFilter(
            ColorMatrix().apply {
                setScale(0.5f, 0.5f, 0.5f, 1.0f)
            }
        )

        @JvmStatic
        fun isNightVision(app: OsmandApplication?): Boolean {
            if (app == null) return false
            val plugin = getInstance()
            return plugin != null && plugin.isActive && plugin.isNightVisionEnabled
        }

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
            engine?.addWaypointToRoute(lat, lon)
            val plugin = getInstance()
            plugin?.app?.osmandMap?.refreshMap()
            val waypointCommand = "WAYPOINT:$lat,$lon"
            engine?.dispatchCommand(waypointCommand)
        }
    }

    private val okHttpClient = OkHttpClient.Builder().build()
    private val connection = OkHttpSignalKConnection(okHttpClient)
    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private val aisEncoder = AisEncoder()
    private var autopilotListener: AutopilotRouteListener? = null
    private val marineStateListener: (MarineState) -> Unit = { state -> checkOffCourseAlert(state) }
    private val aisTargetListener: (AisTarget) -> Unit = { target ->
        aisEncoder.encodeTargetToAivdm(target)?.let { aisEmitter?.emitNmeaSentence(it) }
    }
    private val routeStepListener: () -> Unit = {
        autopilot?.processRouteStep()
        app.osmandMap?.refreshMap()
    }
    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { startEngine() }
    private var isAlertActive = false
    var nauticalMapLayer: NauticalMapLayer? = null
        private set
    private val receiveInBackgroundPrefListener = StateChangedListener<Boolean> { state: Boolean? ->
        if (state == true) {
            updateNauticalBackgroundService()
        } else {
            stopNauticalBackgroundService()
            if (!app.settings.MAP_ACTIVITY_ENABLED) {
                connection.disconnect()
            }
        }
    }
    private var pluginScope: CoroutineScope? = null

    val nauticalNightVisionEnabled: CommonPreference<Boolean> = registerBooleanPreference("nautical_night_vision_enabled", false).makeProfile()

    override fun createMapWidgetForParams(mapActivity: MapActivity, widgetType: WidgetType, customId: String?, widgetsPanel: WidgetsPanel?): MapWidget? {
        return when (widgetType) {
            WidgetType.NAUTICAL_VMG,
            WidgetType.NAUTICAL_COG,
        -> MarineTextWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_DEPTH,
            WidgetType.NAUTICAL_WIND,
        -> NauticalGraphWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_NIGHT_VISION -> NauticalNightVisionWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_PILOT -> NauticalPilotWidget(mapActivity, widgetType, customId, widgetsPanel)
            else -> null
        }
    }

    override fun getSettingsScreenType(): SettingsScreenType = SettingsScreenType.NAUTICAL_SETTINGS

    override fun getPrefsDescription(): String = app.getString(R.string.plugin_nautical_descr, app.getString(R.string.docs_plugin_nautical))

    init {
        instanceRef = WeakReference(this)
    }

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = app.getString(R.string.nautical_plugin_name)
    override fun getDescription(linksEnabled: Boolean): CharSequence =
        app.getString(R.string.nautical_plugin_description)

    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun getAssetResourceImage(): Drawable? = ContextCompat.getDrawable(app, R.drawable.ic_action_sail_boat_dark)

    override fun isMarketPlugin(): Boolean = false

    override fun mapActivityResume(activity: MapActivity) {
        if (!connection.isConnected()) {
            startEngine()
        }
        updateNauticalBackgroundService()
        if (app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            AndroidUtils.requestNotificationPermissionIfNeeded(activity)
        }
    }

    override fun mapActivityPause(activity: MapActivity) {
        if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            connection.disconnect()
            stopNauticalBackgroundService()
        } else {
            updateNauticalBackgroundService()
        }
    }

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
                        if (engine?.isFollowingRoute != true) {
                            connection.disconnect()
                        }
                        stopNauticalBackgroundService()
                    } else {
                        updateNauticalBackgroundService()
                    }
                }
                android.content.Intent.ACTION_SCREEN_ON -> {
                    if (!connection.isConnected()) {
                        startEngine()
                    }
                }
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val osmandMap = app.osmandMap
        val mapView = osmandMap?.mapView
        val mapActivity = mapView?.mapActivity

        if (enabled) {
            instanceRef = WeakReference(this)
            if (pluginScope == null) {
                pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            }

            if (engine == null) {
                engine = SignalKEngine()
                engine?.onRouteStepProcessed = routeStepListener
                engine?.loadBuffersFromDisk(app)
            }
            if (autopilot == null) autopilot = AutopilotController(app, connection, okHttpClient)

            if ((nauticalMapLayer == null) && (mapView != null)) {
                nauticalMapLayer = NauticalMapLayer(app)
                mapView.addLayer(nauticalMapLayer!!, 5.0f)
            }

            if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
            if (aisEmitter == null) aisEmitter = AisUdpEmitter()

            if (autopilotListener == null) {
                val listener = AutopilotRouteListener(app.routingHelper)
                autopilotListener = listener
                app.routingHelper.addListener(listener)
            }

            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            if (ip.isNullOrEmpty()) {
                app.showToastMessage(R.string.nautical_ip_not_configured)
            }

            engine?.registerAisListener(aisTargetListener)
            engine?.registerListener(marineStateListener)
            app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.addListener(receiveInBackgroundPrefListener)

            startEngine()
            aisEmitter?.start()
            locationProvider?.start()
            updateNauticalBackgroundService()

            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
                addAction(android.content.Intent.ACTION_SCREEN_ON)
            }
            app.registerReceiver(screenStateReceiver, filter)

            if (nauticalNightVisionEnabled.get()) {
                app.runInUIThread {
                    mapActivity?.let { toggleNightVision(it, enable = true) }
                }
            }
        } else {
            instanceRef = null
            app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.removeListener(receiveInBackgroundPrefListener)
            stopNauticalBackgroundService()
            nauticalMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                nauticalMapLayer = null
            }
            shutdownResources()
        }
    }

    private fun checkOffCourseAlert(state: MarineState) {
        val xteMeters = state.crossTrackError ?: return
        val thresholdNm = (app.settings.NAUTICAL_XTE_THRESHOLD.get() ?: 0.1f).toDouble()
        val xteNm = abs(xteMeters) / 1852.0

        if (xteNm > thresholdNm) {
            if (!isAlertActive) {
                isAlertActive = true
                log.warn(String.format(Locale.US, "OFF COURSE ALERT: %.2f NM", xteNm))
            }
        } else {
            isAlertActive = false
        }
    }

    var isNightVisionEnabled = false
        private set

    fun toggleNightVision(mapActivity: MapActivity, enable: Boolean) {
        this.isNightVisionEnabled = enable
        nauticalNightVisionEnabled.set(enable)
        val decorView = mapActivity.window.decorView

        if (enable) {
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(RED_FILTER_MATRIX)
            }
            decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)

            AndroidUiHelper.setStatusBarColor(mapActivity, Color.RED)
            AndroidUiHelper.setNavigationBarColor(mapActivity, Color.RED, false)
            AndroidUiHelper.setStatusBarContentColor(decorView, false)
        } else {
            decorView.setLayerType(View.LAYER_TYPE_NONE, null)
            mapActivity.updateStatusBarColor()
            mapActivity.updateNavigationBarColor()
        }

        app.osmandMap.mapView.refreshMap()
        app.notificationHelper.refreshNotification(net.osmand.plus.notifications.OsmandNotification.NotificationType.NAUTICAL)

        // Update all widgets
        mapActivity.app.runInUIThread {
            mapActivity.app.osmandMap.mapLayers.mapInfoLayer.recreateAllControls(mapActivity)
        }
    }

    fun applyNightVisionFilter(view: View) {
        if (isNightVisionEnabled) {
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(RED_FILTER_MATRIX)
            }
            view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun startEngine() {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()

        if (ip.isNullOrEmpty()) return

        val protocol = if (useSecure) "wss" else "ws"
        val wsUrl = "$protocol://$ip:$port/signalk/v1/stream?subscribe=all"
        engine?.onConnectionLost = {
            retryHandler.removeCallbacks(retryRunnable)
            retryHandler.postDelayed(retryRunnable, 5000)
        }

        connection.disconnect()
        connection.connect(wsUrl, username, password) { message -> engine?.handleIncomingMessage(message) }
    }

    private fun shutdownResources() {
        stopNauticalBackgroundService()
        try {
            app.unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) { }
        pluginScope?.cancel()
        pluginScope = null
        retryHandler.removeCallbacks(retryRunnable)
        aisEmitter?.stop()
        aisEmitter = null
        locationProvider?.stop()
        locationProvider = null
        connection.disconnect()
        engine?.let {
            it.unregisterListener(marineStateListener)
            it.registerAisListener(null)
            it.onRouteStepProcessed = null
            it.saveBuffersToDisk(app)
            it.stop()
        }
        engine = null
        autopilot = null
        autopilotListener?.let {
            app.routingHelper.removeListener(it)
            autopilotListener = null
        }
        isNightVisionEnabled = false
    }

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {
        if (app.settings.APPLICATION_MODE.get() != ApplicationMode.BOAT) return

        adapter.addItem(
            ContextMenuItem("nautical_follow_gpx").apply {
                title = mapActivity.getString(R.string.nautical_follow_gpx_route)
                icon = R.drawable.ic_action_track_16
                setListener { _, _, _, _ ->
                    handleGpxSelection(mapActivity)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_export_trajectory").apply {
                title = app.getString(R.string.nautical_export_trajectory)
                icon = R.drawable.ic_action_export
                setListener { _, _, _, _ ->
                    exportCurrentTrajectory()
                    true
                }
            }
        )

        if (autopilot?.isConnected() == true) {
            adapter.addItem(
                ContextMenuItem("nautical_steer_id").apply {
                    title = mapActivity.getString(R.string.nautical_steer_here)
                    icon = R.drawable.ic_action_direction_compass
                    setListener { _, _, _, _ ->
                        autopilot?.sendActiveWaypoint(lat, lon)
                        app.showToastMessage(R.string.nautical_command_sent)
                        true
                    }
                }
            )
        }
    }

    fun onGpxFileSelected(uri: Uri) {
        pluginScope?.launch {
            val routePoints = GpxStreamer(app).parseGpx(uri)
            if (routePoints.isNotEmpty()) {
                engine?.loadRoute(routePoints)
                app.showToastMessage(R.string.nautical_loaded_points, routePoints.size)
            }
        }
    }

    private fun handleGpxSelection(mapActivity: MapActivity) {
        val gpxDir = app.getAppPath(GPX_INDEX_DIR)
        val gpxFiles = gpxDir.listFiles { f -> f.isFile && f.name.endsWith(".gpx", ignoreCase = true) }?.toList() ?: emptyList()

        if (gpxFiles.isEmpty()) {
            app.showToastMessage(R.string.nautical_no_tracks_found)
            return
        }

        android.app.AlertDialog.Builder(mapActivity)
            .setTitle(R.string.nautical_select_track)
            .setItems(gpxFiles.map { it.nameWithoutExtension }.toTypedArray()) { _, which ->
                val selectedFile = gpxFiles[which]
                pluginScope?.launch {
                    val routePoints = GpxStreamer(app).parseGpx(Uri.fromFile(selectedFile))
                    if (routePoints.isNotEmpty()) {
                        engine?.loadRoute(routePoints)
                        app.showToastMessage(R.string.nautical_loaded_points, routePoints.size)
                    }
                }
            }.show()
    }

    private fun exportCurrentTrajectory() {
        val points = engine?.getTrajectory() ?: emptyList()
        if (points.isEmpty()) {
            app.showToastMessage(R.string.nautical_no_trajectory_data)
            return
        }

        pluginScope?.launch {
            val file = GpxStreamer(app).exportTrajectory(points)
            if (file != null) {
                app.showToastMessage(R.string.nautical_trajectory_exported, file.name)
            } else {
                app.showToastMessage(R.string.nautical_export_trajectory_failed)
            }
        }
    }

    private fun updateNauticalBackgroundService() {
        if (isActive && app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            app.startNavigationService(net.osmand.plus.NavigationService.USED_BY_NAUTICAL)
            app.notificationHelper.refreshNotification(net.osmand.plus.notifications.OsmandNotification.NotificationType.NAUTICAL)
        } else {
            stopNauticalBackgroundService()
        }
    }

    private fun stopNauticalBackgroundService() {
        app.navigationService?.let {
            if (it.isUsedBy(net.osmand.plus.NavigationService.USED_BY_NAUTICAL)) {
                it.stopIfNeeded(app, net.osmand.plus.NavigationService.USED_BY_NAUTICAL)
            }
        }
        app.notificationHelper.refreshNotification(net.osmand.plus.notifications.OsmandNotification.NotificationType.NAUTICAL)
    }
}
