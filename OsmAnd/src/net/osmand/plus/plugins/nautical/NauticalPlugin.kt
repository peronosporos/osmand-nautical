package net.osmand.plus.plugins.nautical

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.maneuvers.*
import net.osmand.plus.plugins.nautical.logbook.data.*
import net.osmand.plus.plugins.nautical.logbook.engine.*
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.quickaction.*
import net.osmand.plus.quickaction.QuickActionType
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.*
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import net.osmand.render.RenderingRuleProperty
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.*

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app) {
    private val log = PlatformUtil.getLog(NauticalPlugin::class.java)

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"

        private val RED_FILTER_MATRIX = ColorMatrix(
            floatArrayOf(
                0.33f, 0.33f, 0.33f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        @JvmField
        val NIGHT_VISION_FILTER = ColorMatrixColorFilter(RED_FILTER_MATRIX)

        @JvmField
        val DIM_FILTER = ColorMatrixColorFilter(
            ColorMatrix().apply {
                setScale(0.5f, 0.5f, 0.5f, 1.0f)
            },
        )

        @JvmStatic
        fun isNightVision(app: OsmandApplication?): Boolean {
            if (app == null) return false
            val plugin = getInstance()
            return (plugin != null) && plugin.isActive && plugin.isNightVisionEnabled
        }

        @JvmStatic
        var engine: SignalKEngine? = null

        @JvmStatic
        var autopilot: AutopilotController? = null

        @JvmStatic
        var electrical: ElectricalController? = null

        @JvmStatic
        var autopilotManager: AutopilotManager? = null

        private var instanceRef: WeakReference<NauticalPlugin>? = null

        @JvmStatic
        fun getInstance(): NauticalPlugin? = instanceRef?.get()
    }

    internal var okHttpClient: OkHttpClient? = null
        private set

    private var lastUsedTrustAll: Boolean? = null

    private lateinit var connection: OkHttpSignalKConnection
    private var locationProvider: NauticalLocationProvider? = null
    private var aisEmitter: AisUdpEmitter? = null
    private val aisEncoder by lazy { AisEncoder() }
    private var autopilotListener: AutopilotRouteListener? = null
    private var notificationManager: NauticalNotificationManager? = null
    private val marineStateListener: (MarineState) -> Unit = { state ->
        try {
            notificationManager?.processNotifications(state.notifications)
            checkOffCourseAlert(state)
            checkDepthSafety(state)
            checkConnectionSafety(state)
            autopilot?.updateAutoSeaState(state)
            maneuverManager?.updateState(state)
            lastAutopilotState = state.autopilotState

            // AIS Bridge re-broadcast: Own vessel
            if (state.vesselMmsi != null && state.latitude != null && state.longitude != null) {
                val own = AisTarget(
                    mmsi = state.vesselMmsi,
                    name = state.vesselName,
                    callSign = state.vesselCallSign,
                    vesselType = state.vesselType,
                    length = state.vesselLength,
                    beam = state.vesselBeam,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    speedOverGround = state.speedOverGround?.toFloat(),
                    courseOverGround = state.courseOverGroundTrue?.toFloat(),
                    headingTrue = state.headingTrue?.toFloat(),
                    lastUpdate = System.currentTimeMillis()
                )
                aisEncoder.encodeTargetToAivdm(own, isClassB = true)?.let {
                    aisEmitter?.emitNmeaSentence(it)
                }
            }
        } catch (e: Exception) {
            log.error("Error in marineStateListener: ${e.message}")
        }
    }

    var isConnectionLostAlertActive = false
        private set

    private val connectionRestoredListener: () -> Unit = {
        retryAttempt = 0
        autopilot?.pushAllSettings()
    }

    private fun checkConnectionSafety(state: MarineState) {
        val wasEngaged = (lastAutopilotState != null) && (lastAutopilotState?.uppercase(Locale.US) != "STANDBY")
        val isDisconnected = (state.connectionStatus == ConnectionStatus.DISCONNECTED) || (state.connectionStatus == ConnectionStatus.STALE)
        
        if (wasEngaged && isDisconnected) {
            if (!isConnectionLostAlertActive) {
                isConnectionLostAlertActive = true
                startConnectionLostAudioLoop()
                app.runInUIThread { app.osmandMap?.refreshMap() }
            }
            lastAutopilotState = "STANDBY"
        } else if (!isDisconnected && isConnectionLostAlertActive) {
            isConnectionLostAlertActive = false
            stopConnectionLostAudioLoop()
            app.runInUIThread {
                app.showToastMessage(R.string.nautical_connection_restored)
                app.osmandMap?.refreshMap()
            }
        }
    }

    private var connectionLostAudioJob: Job? = null
    private fun startConnectionLostAudioLoop() {
        connectionLostAudioJob?.cancel()
        connectionLostAudioJob = pluginScope?.launch {
            while (isActive && isConnectionLostAlertActive) {
                try {
                    app.player?.let { player ->
                        val text = app.getString(R.string.nautical_autopilot_data_lost)
                        player.playCommands(player.newCommandBuilder().attention(text))
                    }
                } catch (e: Exception) {
                    log.error("Connection lost audio loop error: ${e.message}")
                }
                delay(10000)
            }
        }
    }

    private fun stopConnectionLostAudioLoop() {
        connectionLostAudioJob?.cancel()
        connectionLostAudioJob = null
    }
    private val aisTargetListener: (AisTarget) -> Unit = { target ->
        aisEncoder.encodeTargetToAivdm(target)?.let { aisEmitter?.emitNmeaSentence(it) }
    }
    private val routeStepListener: () -> Unit = {
        autopilot?.processRouteStep()
        app.osmandMap?.refreshMap()
        app.player?.let { player ->
            val text = app.getString(R.string.nautical_waypoint_reached)
            player.playCommands(player.newCommandBuilder().attention(text))
        }
    }
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private val retryRunnable = Runnable { startEngine() }
    private var isAlertActive = false
    private var lastAutopilotState: String? = null
    var nauticalMapLayer: NauticalMapLayer? = null
        private set
    var tidalCurrentsMapLayer: net.osmand.plus.plugins.nautical.tide.map.TidalCurrentsMapLayer? = null
        private set
    var oceanographicGribMapLayer: net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer? = null
        private set
    var tidalTimeOffsetMs: Long = 0L
        set(value) {
            field = value
            app.osmandMap?.refreshMap()
        }
    private val receiveInBackgroundPrefListener = StateChangedListener<Boolean> { state: Boolean? ->
        updateNauticalBackgroundService()
        if (state != true && !app.settings.MAP_ACTIVITY_ENABLED) {
            if (::connection.isInitialized) {
                connection.disconnect()
            }
        }
    }
    private val enabledPluginsListener = StateChangedListener<String> { 
        if (isActive) {
            updateAisBridge()
        }
    }
    private val xteThresholdListener = StateChangedListener<Float> { threshold ->
        engine?.xteThresholdNm = (threshold ?: 0.1f).toDouble()
    }
    private var pluginScope: CoroutineScope? = null
    var maneuverManager: ManeuverManager? = null
        private set
    var logbookRepository: MarineLogbookRepository? = null
        private set
    var logbookEngine: AutomatedLogbookEngine? = null
        private set
    var anchorWatchdog: AnchorDriftWatchdog? = null
        private set
    private var speechHelper: ManeuverSpeechHelper? = null
    private var ttsHelper: ManeuverTtsHelper? = null

    init {
        instanceRef = WeakReference(this)
    }

    private fun initConnection() {
        val trustAll = app.settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        var client = okHttpClient
        if (client == null || (lastUsedTrustAll != trustAll)) {
            client = createHttpClient(trustAll)
            okHttpClient = client
            lastUsedTrustAll = trustAll
        }
        connection = OkHttpSignalKConnection(client)
    }

    @SuppressLint("CustomX509TrustManager", "BadHostnameVerifier")
    private fun createHttpClient(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(java.time.Duration.ofSeconds(5))
        builder.readTimeout(java.time.Duration.ofSeconds(5))
        builder.pingInterval(java.time.Duration.ofSeconds(30))
        if (trustAll) {
            try {
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                    object : javax.net.ssl.X509TrustManager {
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    },
                )
                val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
                log.warn("Nautical: Using trust-all SSL configuration. Security is reduced.")
            } catch (e: Exception) {
                log.error("Failed to create trust-all SSL client", e)
            }
        }
        return builder.build()
    }

    override fun getQuickActionTypes(): List<QuickActionType> {
        val actions = mutableListOf<QuickActionType>()
        actions.add(NauticalMobQuickAction.TYPE)
        actions.add(NauticalAnchorQuickAction.TYPE)
        actions.add(NauticalNightVisionQuickAction.TYPE)
        return actions
    }

    override fun createMapWidgetForParams(mapActivity: MapActivity, widgetType: WidgetType, customId: String?, widgetsPanel: WidgetsPanel?): MapWidget? {
        return when (widgetType) {
            WidgetType.NAUTICAL_VMG,
            WidgetType.NAUTICAL_COG,
            WidgetType.NAUTICAL_SOG,
            WidgetType.NAUTICAL_STW,
            WidgetType.NAUTICAL_SET_DRIFT,
            WidgetType.NAUTICAL_HEADING_MAGNETIC,
            WidgetType.NAUTICAL_LOG,
            WidgetType.NAUTICAL_TRIP_LOG,
            WidgetType.NAUTICAL_ROLL,
            WidgetType.NAUTICAL_PITCH,
            WidgetType.NAUTICAL_DEPTH_KEEL,
            WidgetType.NAUTICAL_WATER_TEMP,
            WidgetType.NAUTICAL_OUTSIDE_TEMP,
            WidgetType.NAUTICAL_PRESSURE,
            WidgetType.NAUTICAL_ENGINE_RPM,
            WidgetType.NAUTICAL_ENGINE_TEMP,
            WidgetType.NAUTICAL_BATTERY_VOLT,
            WidgetType.NAUTICAL_BATTERY_SOC,
            WidgetType.NAUTICAL_FUEL_LEVEL,
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL,
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL,
            WidgetType.NAUTICAL_POLAR_RATIO,
            WidgetType.NAUTICAL_ROT,
            WidgetType.NAUTICAL_XTE,
            WidgetType.NAUTICAL_TTW,
            WidgetType.NAUTICAL_DTW,
            WidgetType.NAUTICAL_ETA,
            WidgetType.NAUTICAL_AWA,
            WidgetType.NAUTICAL_AWS,
            WidgetType.NAUTICAL_TWA,
            WidgetType.NAUTICAL_TWD,
            WidgetType.NAUTICAL_OIL_PRESSURE,
            WidgetType.NAUTICAL_ENGINE_LOAD,
            WidgetType.NAUTICAL_BATTERY_CURRENT,
            WidgetType.NAUTICAL_SOLAR_CURRENT,
            WidgetType.NAUTICAL_ENGINE_RUNTIME,
            WidgetType.NAUTICAL_ENGINE_COOLANT,
            WidgetType.NAUTICAL_ENGINE_STATE,
        -> MarineTextWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MOB -> NauticalMobWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_DEPTH,
            WidgetType.NAUTICAL_WIND,
        -> NauticalGraphWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_NIGHT_VISION -> NauticalNightVisionWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_PILOT -> NauticalPilotWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.MANEUVER_OVERLAY -> {
                maneuverManager?.let {
                    ManeuverOverlayWidget(mapActivity, it, widgetType, customId, widgetsPanel)
                }
            }
            else -> null
        }
    }

    override fun createWidgets(
        activity: MapActivity,
        widgetInfos: MutableList<net.osmand.plus.views.mapwidgets.MapWidgetInfo>,
        appMode: ApplicationMode,
        layoutMode: net.osmand.plus.settings.enums.ScreenLayoutMode?
    ) {
        if (appMode != ApplicationMode.BOAT) return
        
        val widget = createMapWidgetForParams(activity, WidgetType.MANEUVER_OVERLAY, null, WidgetsPanel.BOTTOM)
        if (widget != null) {
            widgetInfos.add(object : net.osmand.plus.views.mapwidgets.MapWidgetInfo(WidgetType.MANEUVER_OVERLAY.id, widget, 0, 0, R.string.maneuver_overlay, null, 0, 0, WidgetsPanel.BOTTOM) {
                override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: net.osmand.plus.settings.enums.ScreenLayoutMode?): WidgetsPanel {
                    return WidgetsPanel.BOTTOM
                }
            })
        }
    }

    override fun getSettingsScreenType(): SettingsScreenType = SettingsScreenType.NAUTICAL_SETTINGS

    override fun getPrefsDescription(): String = app.getString(R.string.plugin_nautical_descr, app.getString(R.string.docs_plugin_nautical))

    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = app.getString(R.string.nautical_plugin_name)
    override fun getDescription(linksEnabled: Boolean): CharSequence =
        app.getString(R.string.nautical_plugin_description)

    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark

    override fun getAssetResourceImage(): Drawable? = ContextCompat.getDrawable(app, R.drawable.ic_action_sail_boat_dark)

    override fun isMarketPlugin(): Boolean = false

    override fun mapActivityResume(activity: MapActivity) {
        engine?.setPowerSaveMode(false)
        if (!::connection.isInitialized || !connection.isConnected()) {
            startEngine()
        }
        updateNauticalBackgroundService()
        if (app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            AndroidUtils.requestNotificationPermissionIfNeeded(activity)
        }

        app.keyEventHelper.setExternalCallback(object : android.view.KeyEvent.Callback {
            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
                val manager = maneuverManager ?: return false
                if (manager.state != ManeuverState.IDLE) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            manager.execute()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            manager.abort()
                            return true
                        }
                    }
                }
                return false
            }

            override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent): Boolean = false
            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
                val manager = maneuverManager ?: return false
                if (manager.state != ManeuverState.IDLE) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                        return true
                    }
                }
                return false
            }
            override fun onKeyMultiple(keyCode: Int, count: Int, event: android.view.KeyEvent): Boolean = false
        })
    }

    override fun mapActivityPause(activity: MapActivity) {
        val importantTasks = isAnchorWatchActive() || (engine?.isFollowingRoute == true) || (maneuverManager?.state != ManeuverState.IDLE)
        if (!importantTasks) {
            engine?.setPowerSaveMode(true)
        }
        
        if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            if (::connection.isInitialized) {
                connection.disconnect()
            }
        }
        updateNauticalBackgroundService()
        app.keyEventHelper.setExternalCallback(null)
        speechHelper?.stopListening()
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
                        if (engine?.isFollowingRoute != true) {
                            if (::connection.isInitialized) {
                                connection.disconnect()
                            }
                        }
                    }
                    updateNauticalBackgroundService()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!::connection.isInitialized || !connection.isConnected()) {
                        startEngine()
                    }
                }
            }
        }
    }

    override fun updateLayers(context: Context, mapActivity: MapActivity?) {
        val activity = mapActivity ?: return
        val isBoat = app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT
        val mapView = activity.mapView
        if (isBoat && isActive) {
            if (nauticalMapLayer == null) {
                registerLayers(context, activity)
            } else {
                if (!mapView.layers.contains(nauticalMapLayer)) mapView.addLayer(nauticalMapLayer!!, 5.0f)
                if (tidalCurrentsMapLayer != null && !mapView.layers.contains(tidalCurrentsMapLayer)) {
                    mapView.addLayer(tidalCurrentsMapLayer!!, 4.5f)
                }
                if (oceanographicGribMapLayer != null && !mapView.layers.contains(oceanographicGribMapLayer)) {
                    mapView.addLayer(oceanographicGribMapLayer!!, 4.2f)
                }
            }
        } else {
            nauticalMapLayer?.let { mapView.removeLayer(it) }
            tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
            oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
        }
    }

    override fun registerLayers(context: Context, mapActivity: MapActivity?) {
        if (isActive && (mapActivity != null)) {
            val mapView = mapActivity.mapView
            if (nauticalMapLayer == null) {
                nauticalMapLayer = NauticalMapLayer(app)
                mapView.addLayer(nauticalMapLayer!!, 5.0f)
            }
            if (tidalCurrentsMapLayer == null) {
                tidalCurrentsMapLayer = net.osmand.plus.plugins.nautical.tide.map.TidalCurrentsMapLayer(app)
                mapView.addLayer(tidalCurrentsMapLayer!!, 4.5f)
            }
            if (oceanographicGribMapLayer == null) {
                oceanographicGribMapLayer = net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer(app)
                mapView.addLayer(oceanographicGribMapLayer!!, 4.2f)
            }
        }
    }

    private var lastIpToastTime = 0L

    private val locationListener = object : net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener {
        override fun updateLocation(location: net.osmand.Location) {
            checkAnchorWatch(location)
        }
    }

    private fun checkAnchorWatch(location: net.osmand.Location) {
        if (app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0) {
            anchorWatchdog?.onLocationChanged(location)
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

            pluginScope?.launch {
                // Background initialization
                withContext(Dispatchers.IO) {
                    if (!::connection.isInitialized) {
                        initConnection()
                    }

                    if (engine == null) {
                        val newEngine = SignalKEngine()
                        newEngine.onRouteStepProcessed = routeStepListener
                        newEngine.xteThresholdNm = (app.settings.NAUTICAL_XTE_THRESHOLD.get() ?: 0.1f).toDouble()
                        newEngine.deltaSender = { delta -> connection.sendDelta(delta) }
                        engine = newEngine
                        newEngine.loadBuffersFromDisk(app)
                    }

                    if (autopilot == null) {
                        okHttpClient?.let { client ->
                            val ap = AutopilotController(app, connection, client)
                            autopilot = ap
                            electrical = ElectricalController(app, ap)
                        }
                    }

                    if (autopilotManager == null) {
                        okHttpClient?.let { client ->
                            engine?.dataBroker?.let { broker ->
                                autopilotManager = AutopilotManager(app, client, broker)
                            }
                        }
                    }

                    if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
                    if (aisEmitter == null) aisEmitter = AisUdpEmitter()
                    if (anchorWatchdog == null) anchorWatchdog = AnchorDriftWatchdog(app)

                    if (maneuverManager == null) {
                        val mm = ManeuverManager(app)
                        mm.registerManeuver("anchoring", AnchoringManeuver(app))
                        mm.registerManeuver("docking", DockingManeuver(app))
                        mm.registerManeuver("gybing", GybingManeuver(app))
                        mm.registerManeuver("heaving_to", HeavingToManeuver(app))
                        mm.registerManeuver("man_overboard", ManOverboardManeuver(app))
                        mm.registerManeuver("mooring", MooringManeuver(app))
                        mm.registerManeuver("shunting", ShuntingManeuver(app))
                        mm.registerManeuver("slip_exit", SlipExitManeuver(app))
                        mm.registerManeuver("tacking", TackingManeuver(app))
                        mm.registerManeuver("weighing_anchor", WeighingAnchorManeuver(app))

                        val speech = ManeuverSpeechHelper(app, mm)
                        val tts = ManeuverTtsHelper(app)
                        mm.registerListener(tts)
                        mm.registerListener(object : ManeuverManager.ManeuverStateListener {
                            override fun onStateChanged(newState: ManeuverState) {
                                if (newState == ManeuverState.ARMED) {
                                    speech.startListening()
                                } else {
                                    speech.stopListening()
                                }
                                updateNauticalBackgroundService()
                                updatePowerManagement(newState)
                            }
                        })

                        maneuverManager = mm
                        speechHelper = speech
                        ttsHelper = tts

                        // Restore state if needed
                        val savedId = app.settings.NAUTICAL_ACTIVE_MANEUVER_ID.get()
                        if (!savedId.isNullOrEmpty()) {
                            mm.getManeuverById(savedId)?.let {
                                mm.setActiveManeuver(savedId)
                            }
                        }
                    }

                    if (logbookRepository == null) {
                        logbookRepository = MarineLogbookRepository(app)
                    }
                    if (logbookEngine == null) {
                        val repo = logbookRepository!!
                        val signalK = engine!!
                        val perfRepo = SailingDependencyContainer.performanceRepository
                        logbookEngine = AutomatedLogbookEngine(app, repo, signalK, perfRepo)
                        notificationManager = NauticalNotificationManager(app)
                    }
                    logbookEngine?.start()
                }

                mapActivity?.let { registerLayers(app, it) }

                if (autopilotListener == null) {
                    val listener = AutopilotRouteListener(app.routingHelper)
                    autopilotListener = listener
                    app.routingHelper.addListener(listener)
                }

                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                if (ip.isNullOrEmpty()) {
                    val now = System.currentTimeMillis()
                    if (now - lastIpToastTime > 60000) {
                        app.showToastMessage(R.string.nautical_ip_not_configured)
                        lastIpToastTime = now
                    }
                }

                engine?.registerAisListener(aisTargetListener)
                engine?.registerListener(marineStateListener)
                app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.addListener(receiveInBackgroundPrefListener)
                app.settings.enabledPluginsPreference.addListener(enabledPluginsListener)
                app.locationProvider.addLocationListener(locationListener)

                updateAisBridge()

                app.settings.NAUTICAL_XTE_THRESHOLD.addListener(xteThresholdListener)

                startEngine()
                locationProvider?.start()
                updateNauticalBackgroundService()

                val filter = android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                try {
                    app.unregisterReceiver(screenStateReceiver)
                } catch (e: Exception) {
                    log.error("Failed to unregister screenStateReceiver: ${e.message}")
                }
                app.registerReceiver(screenStateReceiver, filter)

                if (app.settings.NAUTICAL_NIGHT_VISION_ENABLED.get()) {
                    mapActivity?.let { toggleNightVision(it, enable = true) }
                }
            }
        } else {
            instanceRef = null
            app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.removeListener(receiveInBackgroundPrefListener)
            app.settings.enabledPluginsPreference.removeListener(enabledPluginsListener)
            app.settings.NAUTICAL_XTE_THRESHOLD.removeListener(xteThresholdListener)
            app.locationProvider.removeLocationListener(locationListener)

            nauticalMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                nauticalMapLayer = null
            }
            oceanographicGribMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                oceanographicGribMapLayer = null
            }
            if (isNightVisionEnabled) {
                mapActivity?.let { toggleNightVision(it, enable = false) }
            }
            shutdownResources()
        }
    }

    private fun updateAisBridge() {
        val aisPlugin = net.osmand.plus.plugins.PluginsHelper.getPlugin(net.osmand.plus.plugins.aistracker.AisTrackerPlugin::class.java)
        if ((aisPlugin != null) && aisPlugin.isEnabled) {
            aisEmitter?.setTargetPort(aisPlugin.AIS_NMEA_UDP_PORT.get())
            aisEmitter?.start()
        } else {
            aisEmitter?.stop()
        }
    }

    private fun checkOffCourseAlert(state: MarineState) {
        if (state.isOffCourse) {
            if (!isAlertActive) {
                isAlertActive = true
                log.warn("OFF COURSE ALERT!")
                app.player.let { player ->
                    val text = app.getString(R.string.nautical_off_course_alert)
                    player.playCommands(player.newCommandBuilder().attention(text))
                }
            }
        } else {
            isAlertActive = false
        }
    }

    private fun checkDepthSafety(state: MarineState) {
        val depth = state.depthBelowKeel ?: return
        
        val safetyDepth = app.settings.getCustomRenderProperty("safetyContour", "5.0").get().toDoubleOrNull() ?: 5.0
        val shallowDepth = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        if (depth < shallowDepth) {
            // Shallow Water Emergency
            val notification = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_keel, depth),
                state = NotificationState.EMERGENCY
            )
            notificationManager?.processNotifications(mapOf("safety.depth.shallow" to notification))
        } else if (depth < safetyDepth) {
            // Depth Warning
            val notification = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_contour, depth),
                state = NotificationState.WARN
            )
            notificationManager?.processNotifications(mapOf("safety.depth.warning" to notification))
        } else {
            // Clear depth alerts
            notificationManager?.processNotifications(emptyMap())
        }
    }

    var isNightVisionEnabled = false
        private set

    fun toggleNightVision(mapActivity: MapActivity, enable: Boolean) {
        this.isNightVisionEnabled = enable
        app.settings.NAUTICAL_NIGHT_VISION_ENABLED.set(enable)
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

    fun reconnect() {
        if (isActive) {
            app.showToastMessage(R.string.nautical_reconnecting)
            retryHandler.removeCallbacks(retryRunnable)
            startEngine()
        }
    }

    private fun startEngine() {
        var ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()

        if (ip.isNullOrEmpty()) return

        // Sanitize IP
        ip = ip.substringAfter("://").substringBefore("/")

        if (::connection.isInitialized) {
            connection.disconnect()
        }
        initConnection()

        val protocol = if (useSecure) "wss" else "ws"
        val wsUrl = "$protocol://$ip:$port/signalk/v1/stream?subscribe=all"
        
        val failureCallback = {
            retryHandler.removeCallbacks(retryRunnable)
            val delayMs = (5000L * (1 shl kotlin.math.min(retryAttempt, 4))).coerceAtMost(60000L)
            retryHandler.postDelayed(retryRunnable, delayMs)
            retryAttempt++
            Unit
        }
        
        engine?.let { e ->
            e.onConnectionLost = failureCallback
            e.onConnectionError = failureCallback
            e.onConnectionRestored = connectionRestoredListener
            e.vesselDraft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        }

        connection.connect(wsUrl, username, password, failureCallback) { message -> 
            engine?.handleIncomingMessage(message) 
        }
    }

    override fun registerConfigureMapCategoryActions(
        adapter: ContextMenuAdapter,
        mapActivity: MapActivity,
        customRules: MutableList<RenderingRuleProperty>,
    ) {
        if (isActive) {
            adapter.addItem(
                ContextMenuItem("nautical_category").apply {
                    isCategory = true
                    setTitleId(R.string.nautical_category, mapActivity)
                    layout = R.layout.list_group_title_with_switch
                }
            )

            // Overlays Group
            adapter.addItem(
                ContextMenuItem("nautical_overlays_group").apply {
                    title = app.getString(R.string.nautical_map_overlays)
                }
            )
            adapter.addItem(createToggle(R.string.nautical_show_laylines, app.settings.NAUTICAL_SHOW_LAYLINES, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_wind_shifts, app.settings.NAUTICAL_SHOW_WIND_SHIFTS, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_trajectory, app.settings.NAUTICAL_SHOW_TRAJECTORY, mapActivity))
            adapter.addItem(createToggle(R.string.layer_tides_title, app.settings.NAUTICAL_SHOW_TIDES, mapActivity))
            adapter.addItem(createToggle(R.string.grib_layer_waves, app.settings.NAUTICAL_SHOW_GRIB_WAVES, mapActivity))
            adapter.addItem(createToggle(R.string.grib_layer_pressure, app.settings.NAUTICAL_SHOW_GRIB_PRESSURE, mapActivity))

            // Vessel Projections Group
            adapter.addItem(
                ContextMenuItem("nautical_vessel_group").apply {
                    title = app.getString(R.string.nautical_vessel_indicators)
                }
            )
            adapter.addItem(createToggle(R.string.nautical_show_heading_line, app.settings.NAUTICAL_SHOW_HEADING_LINE, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_cog_line, app.settings.NAUTICAL_SHOW_COG_LINE, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_current_vector, app.settings.NAUTICAL_SHOW_CURRENT_VECTOR, mapActivity))
            
            // Projection Time
            adapter.addItem(
                ContextMenuItem("nautical_look_ahead").apply {
                    title = app.getString(R.string.nautical_look_ahead_time)
                    description = "${app.settings.NAUTICAL_LOOK_AHEAD_TIME.get()} ${app.getString(R.string.shared_string_min)}"
                    icon = R.drawable.ic_action_time
                    setListener { uiAdapter, _, item, _ ->
                        val options = arrayOf("2", "5", "10", "20", "30", "60")
                        val isNight = app.daynightHelper.isNightMode(ThemeUsageContext.OVER_MAP)
                        androidx.appcompat.app.AlertDialog.Builder(mapActivity, if (isNight) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
                            .setTitle(R.string.nautical_look_ahead_time)
                            .setItems(options) { _, which ->
                                val mins = options[which].toInt()
                                app.settings.NAUTICAL_LOOK_AHEAD_TIME.set(mins)
                                item.description = "$mins ${app.getString(R.string.shared_string_min)}"
                                uiAdapter.onDataSetChanged()
                                app.osmandMap?.refreshMap()
                            }
                            .show()
                        true
                    }
                }
            )
        }
    }

    private fun createToggle(titleId: Int, pref: CommonPreference<Boolean>, mapActivity: MapActivity): ContextMenuItem {
        return ContextMenuItem("nautical_item_$titleId").apply {
            setTitleId(titleId, mapActivity)
            selected = pref.get()
            icon = R.drawable.ic_action_additional_option
            setListener { uiAdapter, _, item, isChecked ->
                pref.set(isChecked)
                item.selected = isChecked
                uiAdapter.onDataSetChanged()
                app.osmandMap?.refreshMap()
                true
            }
        }
    }

    private fun shutdownResources() {
        stopNauticalBackgroundService()
        stopConnectionLostAudioLoop()
        try {
            app.unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            log.error("Failed to unregister screenStateReceiver: ${e.message}")
        }
        pluginScope?.cancel()
        pluginScope = null
        retryHandler.removeCallbacks(retryRunnable)
        aisEmitter?.stop()
        aisEmitter = null
        locationProvider?.stop()
        locationProvider = null
        anchorWatchdog?.stopAlarm()
        anchorWatchdog = null
        if (::connection.isInitialized) {
            connection.disconnect()
        }
        engine?.let {
            it.unregisterListener(marineStateListener)
            it.registerAisListener(null)
            it.onRouteStepProcessed = null
            it.saveBuffersToDisk(app, sync = true)
            it.stop()
        }
        engine = null
        autopilot?.stop()
        autopilot = null
        autopilotManager?.stop()
        autopilotManager = null
        autopilotListener?.let {
            app.routingHelper.removeListener(it)
            autopilotListener = null
        }
        maneuverManager = null
        speechHelper = null
        ttsHelper = null
        logbookEngine?.stop()
        logbookEngine = null
        logbookRepository = null
        isNightVisionEnabled = false
    }

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {
        if (app.settings.APPLICATION_MODE.get() != ApplicationMode.BOAT) return

        adapter.addItem(
            ContextMenuItem("nautical_anchor_watch").apply {
                setTitleId(R.string.nautical_anchor_label, mapActivity)
                icon = R.drawable.ic_action_anchor
                selected = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0
                setListener { _, _, _, _ ->
                    NauticalAnchorQuickAction().execute(mapActivity, null)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_autopilot_mode").apply {
                val mode = autopilot?.let { " (${engine?.getCurrentState()?.autopilotState ?: "standby"})" } ?: ""
                title = app.getString(R.string.nautical_autopilot) + mode
                icon = R.drawable.ic_action_settings
                setListener { _, _, _, _ ->
                    val options = arrayOf<CharSequence>(
                        app.getString(R.string.nautical_autopilot_mode_standby),
                        app.getString(R.string.nautical_autopilot_mode_track),
                        app.getString(R.string.nautical_autopilot_mode_wind)
                    )
                    val values = arrayOf("standby", "track", "wind")
                    androidx.appcompat.app.AlertDialog.Builder(mapActivity)
                        .setTitle(R.string.nautical_autopilot)
                        .setItems(options) { _, which ->
                            autopilot?.setAutopilotMode(values[which])
                        }
                        .show()
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("mob_maneuver").apply {
                setTitleId(R.string.nautical_mob_label, mapActivity)
                setListener { _, _, _, _ ->
                    val sailingPlugin = net.osmand.plus.plugins.PluginsHelper.getPlugin(net.osmand.plus.plugins.nautical.plugin.SailingIntegrationPlugin::class.java)
                    sailingPlugin?.mobViewModel?.triggerMob(net.osmand.data.LatLon(lat, lon))
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_replay_controls").apply {
                title = app.getString(R.string.nautical_replay_title)
                icon = R.drawable.ic_action_play_dark
                setListener { _, _, _, _ ->
                    net.osmand.plus.plugins.nautical.replay.NmeaPlaybackControlBottomSheet.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("marine_raster_charts").apply {
                title = app.getString(R.string.raster_layer_name)
                icon = R.drawable.ic_action_world_globe
                setListener { _, _, _, _ ->
                    net.osmand.plus.plugins.nautical.raster.MarineRasterSettingsControl.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        val multiplexer = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.getNmeaMultiplexer(app)
        val recorder = multiplexer.recorder
        if (recorder != null) {
            adapter.addItem(
                ContextMenuItem("nautical_toggle_recording").apply {
                    val recording = recorder.isRecording.value
                    title = if (recording) app.getString(R.string.nautical_replay_btn_stop) else app.getString(R.string.nautical_replay_btn_record)
                    icon = if (recording) R.drawable.ic_action_stop else R.drawable.ic_action_track_recordable
                    setListener { _, _, _, _ ->
                        if (recording) {
                            recorder.stopRecording()
                            app.showToastMessage(R.string.nautical_replay_recording_stopped, recorder.currentFile.value ?: "")
                        } else {
                            val name = "log_${System.currentTimeMillis()}"
                            recorder.startRecording(name)
                            app.showToastMessage(R.string.nautical_replay_recording_started)
                        }
                        true
                    }
                }
            )
        }

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

        adapter.addItem(
            ContextMenuItem("nautical_steer_id").apply {
                title = mapActivity.getString(R.string.nautical_steer_here)
                icon = R.drawable.ic_action_direction_compass
                setListener { _, _, _, _ ->
                    if (autopilot?.isConnected() == true) {
                        autopilot?.sendActiveWaypoint(lat, lon)
                        app.showToastMessage(R.string.nautical_command_sent)
                    } else {
                        app.showToastMessage(R.string.nautical_autopilot_not_connected)
                    }
                    true
                }
            }
        )
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
        net.osmand.plus.track.GpxDialogs.selectGPXFile(
            mapActivity, false, false,
            { result ->
                if (!result.isNullOrEmpty()) {
                    val gpx = result[0]
                    val points = mutableListOf<Pair<Double, Double>>()
                    gpx.getPointsList().forEach { wpt ->
                        points.add(Pair(wpt.lat, wpt.lon))
                    }
                    gpx.routes.forEach { rte ->
                        rte.points.forEach { pt ->
                            points.add(Pair(pt.lat, pt.lon))
                        }
                    }
                    gpx.tracks.forEach { track ->
                        track.segments.forEach { segment ->
                            segment.points.forEach { pt ->
                                points.add(Pair(pt.lat, pt.lon))
                            }
                        }
                    }
                    if (points.isNotEmpty()) {
                        engine?.loadRoute(points)
                        app.showToastMessage(R.string.nautical_loaded_points, points.size)
                    }
                }
                true
            },
            isNightVision(app),
        )
    }

    private fun exportCurrentTrajectory() {
        val points = mutableListOf<Pair<Double, Double>>()
        engine?.copyTrajectoryTo(points)
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

    private fun isAnchorWatchActive(): Boolean = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0

    private fun updateNauticalBackgroundService() {
        val backgroundEnabled = app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
        val activeManeuver = maneuverManager?.state != ManeuverState.IDLE
        val anchorActive = isAnchorWatchActive()

        if (isActive && (backgroundEnabled || activeManeuver || anchorActive)) {
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

    private fun updatePowerManagement(state: ManeuverState) {
        val mapActivity = app.osmandMap?.mapView?.mapActivity ?: return
        app.runInUIThread {
            val window = mapActivity.window
            val params = window.attributes
            when (state) {
                ManeuverState.EXECUTING -> {
                    params.screenBrightness = 1.0f
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ManeuverState.ARMED -> {
                    params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                ManeuverState.IDLE -> {
                    params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mapActivity.changeKeyguardFlags()
                }
            }
            window.attributes = params
        }
    }

    fun forceEmergencyBrightness() {
        val mapActivity = app.osmandMap?.mapView?.mapActivity ?: return
        app.runInUIThread {
            val window = mapActivity.window
            val params = window.attributes
            params.screenBrightness = if (isNightVision(app)) 0.2f else 1.0f
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = params
            
            retryHandler.postDelayed({
                if (maneuverManager?.state == ManeuverState.IDLE) {
                    updatePowerManagement(ManeuverState.IDLE)
                }
            }, 30000)
        }
    }
}
