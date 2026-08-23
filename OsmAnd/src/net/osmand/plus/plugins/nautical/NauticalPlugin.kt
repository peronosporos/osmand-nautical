package net.osmand.plus.plugins.nautical

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.StateChangedListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.activities.TabActivity.TabItem
import net.osmand.plus.helpers.DayNightHelper
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.dr.viewmodel.DeadReckoningViewModel
import net.osmand.plus.plugins.nautical.engine.AlarmPriorityManager
import net.osmand.plus.plugins.nautical.engine.AutopilotController
import net.osmand.plus.plugins.nautical.engine.AutopilotRouteListener
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.ElectricalController
import net.osmand.plus.plugins.nautical.engine.GpxStreamer
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalAisManager
import net.osmand.plus.plugins.nautical.location.SignalKLocationManager
import net.osmand.plus.plugins.nautical.engine.NauticalNotificationManager
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyEvaluator
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.engine.NauticalWorkflowManager
import net.osmand.plus.plugins.nautical.engine.NotificationState
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SafetyStateArbitrator
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowEngine
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.SignalKNotification
import net.osmand.plus.plugins.nautical.engine.SignalKTideManager
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessageDecoder
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineViewModel
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.logbook.engine.AutomatedLogbookEngine
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverManager
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverSpeechHelper
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverTtsHelper
import net.osmand.plus.plugins.nautical.maneuvers.TacticalProcessor
import net.osmand.plus.plugins.nautical.maneuvers.TacticalStartManager
import net.osmand.plus.plugins.nautical.map.NauticalLayerManager
import net.osmand.plus.plugins.nautical.mob.engine.MobStateMachine
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobAudioAlertManager
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.network.NauticalConnectionManager
import net.osmand.plus.plugins.nautical.network.NauticalVhfManager
import net.osmand.plus.plugins.nautical.network.SignalKDiscovery
import net.osmand.plus.plugins.nautical.quickaction.NauticalAisQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalAnchorQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalAutopilotQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalLaylinesQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalMasterTelemetryQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalMobQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalNightVisionQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalSailInventoryQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalSwitchQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalTacticalStartPinQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalVhfQuickAction
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.system.NauticalSystemManager
import net.osmand.plus.plugins.nautical.ui.NauticalAisLayer
import net.osmand.plus.plugins.nautical.ui.NauticalContextMenuHelper
import net.osmand.plus.plugins.nautical.ui.NauticalUiOverlayManager
import net.osmand.plus.plugins.nautical.ui.NauticalWidgetFactory
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.plus.plugins.nautical.viewmodel.PolarConfigViewModel
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.plus.plugins.nautical.viewmodel.WizardState
import net.osmand.plus.quickaction.QuickActionType
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.settings.enums.CompassMode
import net.osmand.plus.settings.enums.DayNightMode
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.settings.enums.NmeaSource
import net.osmand.plus.settings.enums.ScreenLayoutMode
import net.osmand.plus.settings.enums.VesselContext
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.MapWidget
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.render.RenderingRuleProperty
import net.osmand.shared.aistracker.AisMessageListener
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.util.KMapUtils
import net.osmand.util.MapUtils
import okhttp3.OkHttpClient
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.abs

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app), DayNightHelper.MapThemeProvider {
    private val log = PlatformUtil.getLog(NauticalPlugin::class.java)

    val wearOsManager = WearOsNauticalManager(app)

    val connectionManager = NauticalConnectionManager(app) { engine }
    val nauticalConnectionManager: NauticalConnectionManager
        get() = connectionManager
    val layerManager = NauticalLayerManager(app, this)
    val uiOverlayManager = NauticalUiOverlayManager(app)
    val widgetFactory = NauticalWidgetFactory()
    val contextMenuHelper = NauticalContextMenuHelper(app)
    val safetyEvaluator = NauticalSafetyEvaluator(app, null)
    val systemManager = NauticalSystemManager(
        app = app,
        engineProvider = { engine },
        maneuverManagerProvider = { maneuverManager },
        mobViewModelProvider = { mobViewModel },
        logbookRepositoryProvider = { logbookRepository },
        hudManagerProvider = { hudManager }
    )

    enum class NauticalModule {
        AIS, TIDES, GRIB, VHF, LOGBOOK, ENC, RASTER, NAVTEX
    }

    fun isModuleEnabled(module: NauticalModule): Boolean {
        return when (module) {
            NauticalModule.AIS -> app.settings.NAUTICAL_AIS_ENABLED.get()
            NauticalModule.TIDES -> app.settings.NAUTICAL_MODULE_TIDES.get()
            NauticalModule.GRIB -> app.settings.NAUTICAL_MODULE_GRIB.get()
            NauticalModule.VHF -> app.settings.NAUTICAL_VHF_ENABLED.get()
            NauticalModule.LOGBOOK -> app.settings.NAUTICAL_MODULE_LOGBOOK.get()
            NauticalModule.ENC -> app.settings.NAUTICAL_MODULE_ENC.get()
            NauticalModule.RASTER -> app.settings.NAUTICAL_MODULE_RASTER.get()
            NauticalModule.NAVTEX -> app.settings.NAUTICAL_NAVTEX_ENABLED.get()
        }
    }

    val application: OsmandApplication
        get() = app

    companion object {
        const val NAUTICAL_ID = "osmand.nautical"

        private val RED_FILTER_MATRIX = ColorMatrix(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        @JvmField
        val NIGHT_VISION_FILTER = ColorMatrixColorFilter(RED_FILTER_MATRIX)

        @JvmStatic
        fun isNightVision(app: OsmandApplication?): Boolean {
            if (app == null) return false
            val plugin = getInstance()
            return (plugin != null) && plugin.isActive && plugin.isNightVisionEnabled
        }

        @JvmStatic
        fun getAisObject(mmsi: Int): AisObject? = getInstance()?.aisManager?.getAisObjects()?.find { it.mmsi == mmsi }

        @JvmStatic
        var engine: SignalKEngine? = null
            internal set

        @JvmStatic
        var autopilot: AutopilotController? = null
            internal set

        @JvmStatic
        var electrical: ElectricalController? = null
            internal set

        @JvmStatic
        var hudManager: WeakReference<NauticalHudManager>? = null

        @JvmStatic
        fun getWearOsManager(context: Context): WearOsNauticalManager {
            return getInstance()?.wearOsManager ?: WearOsNauticalManager(context.applicationContext)
        }

        private var instanceRef: WeakReference<NauticalPlugin>? = null

        @JvmStatic
        fun getInstance(): NauticalPlugin? = instanceRef?.get()
    }

    internal val okHttpClient: OkHttpClient?
        get() = connectionManager.okHttpClient

    fun getConnection(): OkHttpSignalKConnection? = connectionManager.connection

    var isAppInBackground: Boolean
        get() = systemManager.isAppInBackground
        set(value) { systemManager.isAppInBackground = value }

    var isNightVisionEnabled: Boolean
        get() = uiOverlayManager.isNightVisionEnabled
        internal set(value) { uiOverlayManager.isNightVisionEnabled = value }

    val isConnectionLostAlertActive: Boolean
        get() = safetyEvaluator.isConnectionLostAlertActive

    var signalKLocationManager: SignalKLocationManager? = null
        private set
    var aisManager: NauticalAisManager? = null
        private set
    var aisMessageListener: AisMessageListener? = null
        private set
    private var autopilotListener: AutopilotRouteListener? = null
    internal var notificationManager: NauticalNotificationManager? = null
    var workflowManager: NauticalWorkflowManager? = null
        private set
    var capabilityManager: CapabilityManager? = null
        private set
    var tideManager: SignalKTideManager? = null
        private set
    var vhfManager: NauticalVhfManager? = null
        private set
    var skDiscovery: SignalKDiscovery? = null
        private set

    internal var pluginScope: CoroutineScope? = null
    var maneuverManager: ManeuverManager? = null
        private set
    var tacticalProcessor: TacticalProcessor? = null
        private set
    var tacticalStartManager: TacticalStartManager? = null
        private set
    var logbookRepository: MarineLogbookRepository? = null
        private set
    var logbookEngine: AutomatedLogbookEngine? = null
        private set
    var anchorWatchdog: AnchorDriftWatchdog? = null
        private set
    var alarmPriorityManager: AlarmPriorityManager? = null
        private set
    var telemetryFilterEngine: net.osmand.plus.plugins.nautical.telemetry.TelemetryFilterEngine? = null
        private set
    internal var speechHelper: ManeuverSpeechHelper? = null
    private var ttsHelper: ManeuverTtsHelper? = null
    private var presentationManager: NauticalPresentationManager? = null
    var safetyManager: NauticalSafetyManager? = null
        private set
    var safetyArbitrator: SafetyStateArbitrator? = null
        private set
    var mobStateMachine: MobStateMachine? = null
        private set
    var mobAudioAlertManager: MobAudioAlertManager? = null
        private set

    var s57SpatialIndex: S57SpatialIndex? = null
        private set
    var mobViewModel: MobViewModel? = null
        private set
    var drViewModel: DeadReckoningViewModel? = null
        private set
    var laylineViewModel: LaylineViewModel? = null
        private set
    var navtexViewModel: NavtexViewModel? = null
        private set
    var polarConfigViewModel: PolarConfigViewModel? = null
    var routingViewModel: RoutingViewModel? = null
        private set
    var workflowEngine: SailingWorkflowEngine? = null
        private set

    val aisAisLayer: NauticalAisLayer?
        get() = layerManager.aisAisLayer

    var tidalTimeOffsetMs: Long = 0L
        set(value) {
            field = value
            requestRefresh()
        }

    private var lastRefreshTime = 0L
    private var currentRefreshThrottleMs = 100L
    @Volatile
    private var isRefreshScheduled = false

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable {
        val now = System.currentTimeMillis()
        val forceThrottle = systemManager.isThrottlingRedraws || systemManager.isPowerSaveModeActive
        if (forceThrottle) {
            if (now - lastRefreshTime < 2000) {
                isRefreshScheduled = false
                return@Runnable
            }
        }
        app.osmandMap?.refreshMap()
        lastRefreshTime = System.currentTimeMillis()
        isRefreshScheduled = false
    }

    fun requestRefresh() {
        if (isAppInBackground) return
        if (isRefreshScheduled) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefreshTime

        if (elapsed >= currentRefreshThrottleMs) {
            isRefreshScheduled = true
            refreshHandler.post(refreshRunnable)
        } else {
            isRefreshScheduled = true
            refreshHandler.postDelayed(refreshRunnable, currentRefreshThrottleMs - elapsed)
        }
    }

    private var lastStatusToastTime = 0L
    private val minStatusToastInterval = 10000L
    private var lastConnectionStatus: ConnectionStatus? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        telemetryFilterEngine?.processState(state)
        pluginScope?.launch(Dispatchers.Default) {
            try {
                val combinedNotifications = state.notifications.toMutableMap()
                if (state.isStwUnreliable) {
                    combinedNotifications["safety.speed.stw_failover"] = SignalKNotification(
                        message = app.getString(R.string.nautical_stw_unreliable_fallback),
                        state = NotificationState.WARN
                    )
                }

                safetyEvaluator.evaluateVesselSafety(state, combinedNotifications, safetyManager)

                val navtexNotif = state.notifications["navigation.navtex"]
                val decodedNavtex = navtexNotif?.let { NavtexMessageDecoder.decode(it.message) }

                safetyEvaluator.checkConnectionSafety(state) { requestRefresh() }
                safetyEvaluator.checkEmergencyPower(state)
                autopilot?.updateAutoSeaState(state)
                autopilot?.applyWaveBias(state)

                if (polarConfigViewModel?.wizardState?.value == WizardState.ACTIVE_LOGGING) {
                    val tws = state.windSpeedTrue ?: 0.0
                    val twa = state.trueWindAngle ?: 0.0
                    val speed = state.speedOverGround ?: 0.0
                    polarConfigViewModel?.recordDataPoint(tws, Math.toDegrees(twa), speed)
                }

                maneuverManager?.updateState(state)
                tacticalProcessor?.update(state)

                if (!isAppInBackground) {
                    withContext(Dispatchers.Main) {
                        if (isAppInBackground) return@withContext
                        val currentStatus = state.connectionStatus
                        if (lastConnectionStatus != currentStatus) {
                            val now = System.currentTimeMillis()
                            val shouldToast = (now - lastStatusToastTime) > minStatusToastInterval

                            when (currentStatus) {
                                ConnectionStatus.CONNECTED -> {
                                    if (shouldToast) {
                                        if (lastConnectionStatus == ConnectionStatus.DISCONNECTED || lastConnectionStatus == ConnectionStatus.CONNECTING) {
                                            hudManager?.get()?.showBanner(app.getString(R.string.nautical_sk_connected), 3000L)
                                            NauticalAudioArbiter.getInstance(app).dispatchTts(app.getString(R.string.nautical_sk_connected), AlarmType.TTS_INSTRUCTION)
                                            lastStatusToastTime = now
                                        } else if (lastConnectionStatus == ConnectionStatus.STALE) {
                                            hudManager?.get()?.showBanner(app.getString(R.string.nautical_connection_restored), 3000L)
                                            lastStatusToastTime = now
                                        }
                                    }
                                }
                                ConnectionStatus.STALE -> {
                                    if (shouldToast && lastConnectionStatus == ConnectionStatus.CONNECTED) {
                                        hudManager?.get()?.showBanner(app.getString(R.string.nautical_sk_connection_stale), 5000L, isWarning = true)
                                        lastStatusToastTime = now
                                    }
                                }
                                ConnectionStatus.DISCONNECTED -> {
                                    if (shouldToast && lastConnectionStatus != null && lastConnectionStatus != ConnectionStatus.DISCONNECTED) {
                                        hudManager?.get()?.showBanner(app.getString(R.string.nautical_sk_connection_lost), 0L, isWarning = true)
                                        lastStatusToastTime = now
                                    }
                                }
                                ConnectionStatus.CONNECTING -> {
                                    if (shouldToast) {
                                        hudManager?.get()?.showBanner(app.getString(R.string.nautical_sk_connecting), 0L)
                                        lastStatusToastTime = now
                                    }
                                }
                                else -> {}
                            }
                            lastConnectionStatus = currentStatus
                        }

                        notificationManager?.processNotifications(combinedNotifications)
                        decodedNavtex?.let { navtexViewModel?.upsertMessage(it) }

                        state.notifications["notifications.vhf.boundaryApproach"]?.let { notif ->
                            app.showToastMessage(notif.message)
                        }

                        uiOverlayManager.startLineHudHeader?.update()
                        uiOverlayManager.tacticsHudHeader?.update()
                        uiOverlayManager.anchorWatchHudView?.update()
                        uiOverlayManager.predictiveSteeringHudView?.update()

                        uiOverlayManager.forwardWatchHudView?.updateHazards(state.forwardHazards)
                        uiOverlayManager.environmentHud?.let { hud ->
                            hud.updateState(state)
                            hud.isVisible = (state.outsideHumidity != null || state.moonPhase != null)
                        }
                        uiOverlayManager.watchScheduleHudView?.let { hud ->
                            hud.updateState(state)
                            hud.isVisible = state.pathMeta.containsKey("communication.crew.watch.current")
                        }
                        uiOverlayManager.heartbeatHudView?.updateState(state)
                        uiOverlayManager.tacticalHudView?.updateState(state)
                        uiOverlayManager.healthHudView?.updateState(state, connectionManager.connection?.getLatencyMs() ?: 0L)

                        hudManager?.get()?.updateLayout()

                        safetyEvaluator.lastAutopilotState = state.autopilotState
                        systemManager.checkScreenAlwaysOn()
                        presentationManager?.updateState(state)

                        state.sunlightMode?.let { sMode ->
                            val currentMode = app.settings.NAUTICAL_DISPLAY_MODE.get()
                            val targetMode = when (sMode.lowercase(Locale.US)) {
                                "high", "bright", "sunlight" -> NauticalDisplayMode.SUNLIGHT
                                "night", "dark" -> NauticalDisplayMode.DARK
                                else -> NauticalDisplayMode.NORMAL
                            }
                            if (targetMode != currentMode) {
                                app.settings.NAUTICAL_DISPLAY_MODE.set(targetMode)
                            }
                        }

                        if (app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT) && state.headingTrue != null) {
                            val mapView = app.osmandMap?.mapView
                            if (mapView != null && app.settings.isCompassMode(CompassMode.COMPASS_DIRECTION)) {
                                val hdgDeg = Math.toDegrees(state.headingTrue).toFloat()
                                if (abs(MapUtils.degreesDiff(mapView.rotate.toDouble(), (-hdgDeg).toDouble())) > 0.1) {
                                    mapView.setRotate(-hdgDeg, true)
                                }
                            }
                        }
                        requestRefresh()
                    }
                }
            } catch (e: Exception) {
                log.error("Error in marine state processing", e)
            }
        }
    }

    private val routeStepListener: () -> Unit = {
        autopilot?.processRouteStep()
        requestRefresh()
        app.runInUIThread {
            app.showToastMessage(R.string.nautical_waypoint_reached)
            val e = engine
            val state = e?.getCurrentState()
            val nextWaypoint = e?.getNextWaypoint()

            if (state != null && nextWaypoint != null && state.latitude != null && state.longitude != null) {
                val bearing = KMapUtils.getBearing(state.latitude, state.longitude, nextWaypoint.first, nextWaypoint.second)
                val bearingDeg = if (bearing < 0) bearing + 360 else bearing
                val msg = app.getString(R.string.nautical_proceeding_to_waypoint, bearingDeg)
                NauticalAudioArbiter.getInstance(app).dispatchTts(msg, AlarmType.TTS_INSTRUCTION)
            } else {
                app.player?.let { player ->
                    val text = app.getString(R.string.nautical_waypoint_reached)
                    player.playCommands(player.newCommandBuilder().attention(text))
                }
            }
        }
    }

    private val receiveInBackgroundPrefListener = StateChangedListener<Boolean> { state: Boolean? ->
        updateNauticalBackgroundService()
        if ((state != true) && (!app.settings.MAP_ACTIVITY_ENABLED)) {
            connectionManager.connection?.disconnect()
        }
    }

    private val enabledPluginsListener = StateChangedListener<String> {
        updateFeatureLifecycle()
        requestRefresh()
    }

    private val xteThresholdListener = StateChangedListener<Float> { threshold ->
        engine?.xteThresholdNm = (threshold ?: 0.1f).toDouble()
    }

    private val laylinesEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val mobActiveListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private var isDrEnabled: Boolean? = null
    private val drEnabledListener = StateChangedListener<Long> { startTime ->
        val enabled = (startTime ?: 0L) != 0L
        if (isDrEnabled != enabled) {
            isDrEnabled = enabled
            updateFeatureLifecycle()
        }
    }
    private val navtexEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val aisEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val windyEnabledListener = StateChangedListener<Boolean> { requestRefresh() }
    private val aisOwnMmsiListener = StateChangedListener<Int> { layerManager.aisAisLayer?.refreshOwnObjectVisibility() }
    private val aisDisplayOwnPositionListener = StateChangedListener<Boolean> { layerManager.aisAisLayer?.refreshOwnObjectVisibility() }
    private val gribWavesEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val gribPressureEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }

    private val displayModeListener = StateChangedListener<NauticalDisplayMode> { mode ->
        val activity = app.osmandMap?.mapView?.mapActivity
        if (activity != null) {
            applyDisplayMode(activity, mode ?: NauticalDisplayMode.NORMAL)
        }
    }

    private val heavyWeatherEnabledListener = StateChangedListener<Boolean> { enabled ->
        val activity = app.osmandMap?.mapView?.mapActivity
        workflowManager?.onHeavyWeatherModeChanged(enabled ?: false, activity)
    }

    private var isApplyingVesselContext = false
    private val vesselContextListener = StateChangedListener<VesselContext> { ctx ->
        if (ctx != null && !isApplyingVesselContext) {
            applyVesselContext(ctx)
        }
    }
    private val tidesModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val gribModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val vhfModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val logbookModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val encModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val rasterModuleListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val nmeaSourceListener = StateChangedListener<net.osmand.plus.settings.enums.NmeaSource> { updateNmeaSource() }
    private val serverIpListener = StateChangedListener<String> { reconnect() }
    private val serverPortListener = StateChangedListener<String> { reconnect() }
    private val serverSecureListener = StateChangedListener<Boolean> { reconnect() }
    private val serverUsernameListener = StateChangedListener<String> { reconnect() }
    private val serverPasswordListener = StateChangedListener<String> { reconnect() }
    private val signalKTokenListener = StateChangedListener<String> { reconnect() }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val watchedKeys = setOf(
            app.settings.NAUTICAL_VESSEL_DRAFT.id,
            app.settings.NAUTICAL_SAFETY_MARGIN.id,
            app.settings.NAUTICAL_SHOW_LAYLINES.id,
            app.settings.NAUTICAL_SHOW_TRAJECTORY.id,
            app.settings.NAUTICAL_TRAJECTORY_COLOR.id,
            app.settings.NAUTICAL_TRAJECTORY_THICKNESS.id,
            app.settings.NAUTICAL_SHOW_HEADING_LINE.id,
            app.settings.NAUTICAL_SHOW_COG_LINE.id,
            app.settings.NAUTICAL_SHOW_CURRENT_VECTOR.id,
            app.settings.NAUTICAL_RESTRICTED_AREAS_ENABLED.id,
            app.settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id,
            app.settings.NAUTICAL_LOOK_AHEAD_TIME.id,
            app.settings.NAUTICAL_CORRIDOR_WIDTH.id,
            app.settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id,
            app.settings.NAUTICAL_LAYLINES_TACK_ANGLE.id,
            app.settings.NAUTICAL_SHOW_WIND_SHIFTS.id,
            app.settings.NAUTICAL_SHOW_WINDY_TILES.id,
            app.settings.NAUTICAL_SHOW_OPENMETEO_TILES.id,
            app.settings.NAUTICAL_SHOW_NOAA_TILES.id,
            app.settings.NAUTICAL_SHOW_RAIN_RADAR.id,
            app.settings.NAUTICAL_SHOW_LOGBOOK_LAYER.id,
            app.settings.NAUTICAL_SHOW_TIDES.id,
            app.settings.NAUTICAL_SHOW_CMG_LINE.id,
            app.settings.NAUTICAL_SHOW_PMTILES.id,
            app.settings.NAUTICAL_ARRIVAL_RADIUS.id,
            app.settings.NAUTICAL_RUDDER_GAIN.id,
            app.settings.NAUTICAL_COUNTER_RUDDER.id,
            app.settings.NAUTICAL_AUTO_TRIM.id,
            app.settings.NAUTICAL_FILTER_SENSITIVITY.id,
            app.settings.NAUTICAL_RUDDER_LIMIT.id,
            app.settings.NAUTICAL_OFF_COURSE_ALARM.id,
            app.settings.NAUTICAL_PILOT_SEA_STATE.id,
            app.settings.NAUTICAL_PYPILOT_P.id,
            app.settings.NAUTICAL_PYPILOT_I.id,
            app.settings.NAUTICAL_PYPILOT_D.id,
            app.settings.NAUTICAL_PYPILOT_PR.id,
            app.settings.NAUTICAL_PYPILOT_FF.id,
            app.settings.NAUTICAL_EMA_ALPHA_HEADING.id,
            app.settings.NAUTICAL_EMA_ALPHA_WIND_ANGLE.id,
            app.settings.NAUTICAL_EMA_ALPHA_WIND_SPEED.id,
            app.settings.NAUTICAL_EMA_ALPHA_DEPTH.id,
            app.settings.NAUTICAL_EMA_ALPHA_RUDDER.id,
            app.settings.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG.id,
            app.settings.NAUTICAL_EMA_SPEED_THRESHOLD_MS.id,
            app.settings.NAUTICAL_TELEMETRY_REFRESH_BASE_MS.id,
            app.settings.NAUTICAL_STW_REL_DELAY_SEC.id,
            app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.id,
            app.settings.METRIC_SYSTEM.id,
            app.settings.ALTITUDE_METRIC.id
        )

        if (watchedKeys.contains(key)) {
            val e = engine
            if (e != null) {
                when (key) {
                    app.settings.NAUTICAL_VESSEL_DRAFT.id -> e.vesselDraft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
                    app.settings.NAUTICAL_CORRIDOR_WIDTH.id -> e.corridorWidthNm = app.settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
                    app.settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id -> e.safetyCorridorBufferNm = app.settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
                    app.settings.NAUTICAL_ARRIVAL_RADIUS.id -> e.arrivalRadiusMeters = app.settings.NAUTICAL_ARRIVAL_RADIUS.get().toDouble()
                    app.settings.NAUTICAL_RUDDER_GAIN.id,
                    app.settings.NAUTICAL_COUNTER_RUDDER.id,
                    app.settings.NAUTICAL_AUTO_TRIM.id,
                    app.settings.NAUTICAL_FILTER_SENSITIVITY.id,
                    app.settings.NAUTICAL_RUDDER_LIMIT.id,
                    app.settings.NAUTICAL_OFF_COURSE_ALARM.id,
                    app.settings.NAUTICAL_PYPILOT_P.id,
                    app.settings.NAUTICAL_PYPILOT_I.id,
                    app.settings.NAUTICAL_PYPILOT_D.id,
                    app.settings.NAUTICAL_PYPILOT_PR.id,
                    app.settings.NAUTICAL_PYPILOT_FF.id -> autopilot?.pushAllSettings()
                    app.settings.NAUTICAL_PILOT_SEA_STATE.id -> autopilot?.setSeaState(app.settings.NAUTICAL_PILOT_SEA_STATE.get() ?: 3)
                    app.settings.NAUTICAL_EMA_ALPHA_HEADING.id,
                    app.settings.NAUTICAL_EMA_ALPHA_WIND_ANGLE.id,
                    app.settings.NAUTICAL_EMA_ALPHA_WIND_SPEED.id,
                    app.settings.NAUTICAL_EMA_ALPHA_DEPTH.id,
                    app.settings.NAUTICAL_EMA_ALPHA_RUDDER.id,
                    app.settings.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG.id,
                    app.settings.NAUTICAL_EMA_SPEED_THRESHOLD_MS.id,
                    app.settings.NAUTICAL_TELEMETRY_REFRESH_BASE_MS.id,
                    app.settings.NAUTICAL_STW_REL_DELAY_SEC.id,
                    app.settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.id -> e.dataBroker.updateTuning()
                }
            }

            layerManager.nauticalMapLayer?.invalidateCache()
            layerManager.layerController?.s57Layer?.clearCache()

            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, 300)
        }
    }

    private val screenStateReceiver = systemManager.createScreenStateReceiver(
        onUpdateBackgroundService = { updateNauticalBackgroundService() },
        onStartEngine = { connectionManager.startEngine() },
        connectionProvider = { connectionManager.connection }
    )

    private val locationListener = net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener { location ->
        if (location == null) return@OsmAndLocationListener
        engine?.onInternalLocationUpdate(location)
    }

    init {
        instanceRef = WeakReference(this)
    }

    val aisObjLostTimeout: CommonPreference<Int> get() = app.settings.NAUTICAL_AIS_OBJ_LOST_TIMEOUT as CommonPreference<Int>
    val aisShipLostTimeout: CommonPreference<Int> get() = app.settings.NAUTICAL_AIS_SHIP_LOST_TIMEOUT as CommonPreference<Int>
    val aisCpaWarningTime: CommonPreference<Int> get() = app.settings.NAUTICAL_AIS_CPA_WARNING_TIME as CommonPreference<Int>
    val aisCpaWarningDistance: CommonPreference<Float> get() = app.settings.NAUTICAL_AIS_CPA_WARNING_DISTANCE
    val aisOwnMmsi: CommonPreference<Int> get() = app.settings.NAUTICAL_AIS_OWN_MMSI as CommonPreference<Int>
    val aisDisplayOwnPosition: CommonPreference<Boolean> get() = app.settings.NAUTICAL_AIS_DISPLAY_OWN_POSITION

    override fun init(app: OsmandApplication, activity: android.app.Activity?): Boolean {
        initPlugin()
        return super.init(app, activity)
    }

    private var isPluginInitialized = false
    private var isListenersRegistered = false

    private fun registerListeners() {
        if (isListenersRegistered) return
        isListenersRegistered = true

        val settings = app.settings
        settings.NAUTICAL_RECEIVE_IN_BACKGROUND.addListener(receiveInBackgroundPrefListener)
        settings.enabledPluginsPreference.addListener(enabledPluginsListener)
        settings.NAUTICAL_XTE_THRESHOLD.addListener(xteThresholdListener)
        settings.NAUTICAL_SHOW_LAYLINES.addListener(laylinesEnabledListener)
        settings.NAUTICAL_MOB_ACTIVE.addListener(mobActiveListener)
        settings.NAUTICAL_DR_START_TIME.addListener(drEnabledListener)
        settings.NAUTICAL_NAVTEX_ENABLED.addListener(navtexEnabledListener)
        settings.NAUTICAL_AIS_ENABLED.addListener(aisEnabledListener)
        settings.NAUTICAL_SHOW_WINDY_TILES.addListener(windyEnabledListener)
        settings.NAUTICAL_AIS_OWN_MMSI.addListener(aisOwnMmsiListener)
        settings.NAUTICAL_AIS_DISPLAY_OWN_POSITION.addListener(aisDisplayOwnPositionListener)
        settings.NAUTICAL_SHOW_GRIB_WAVES.addListener(gribWavesEnabledListener)
        settings.NAUTICAL_SHOW_GRIB_PRESSURE.addListener(gribPressureEnabledListener)
        settings.NAUTICAL_DISPLAY_MODE.addListener(displayModeListener)
        settings.NAUTICAL_HEAVY_WEATHER_MODE.addListener(heavyWeatherEnabledListener)
        settings.NAUTICAL_VESSEL_CONTEXT.addListener(vesselContextListener)
        settings.NAUTICAL_MODULE_TIDES.addListener(tidesModuleListener)
        settings.NAUTICAL_MODULE_GRIB.addListener(gribModuleListener)
        settings.NAUTICAL_VHF_ENABLED.addListener(vhfModuleListener)
        settings.NAUTICAL_MODULE_LOGBOOK.addListener(logbookModuleListener)
        settings.NAUTICAL_MODULE_ENC.addListener(encModuleListener)
        settings.NAUTICAL_MODULE_RASTER.addListener(rasterModuleListener)
        settings.NAUTICAL_NMEA_SOURCE.addListener(nmeaSourceListener)
        settings.NAUTICAL_SERVER_IP.addListener(serverIpListener)
        settings.NAUTICAL_SERVER_PORT.addListener(serverPortListener)
        settings.NAUTICAL_USE_SECURE_CONNECTION.addListener(serverSecureListener)
        settings.NAUTICAL_SERVER_USERNAME.addListener(serverUsernameListener)
        settings.NAUTICAL_SERVER_PASSWORD.addListener(serverPasswordListener)
        settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.addListener(signalKTokenListener)

        engine?.registerListener(marineStateListener)
        engine?.addRouteStepListener(routeStepListener)

        app.locationProvider.addLocationListener(locationListener)
        autopilotListener?.let { app.routingHelper.addListener(it) }

        app.getSharedPreferences(OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefChangeListener)

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        app.registerReceiver(screenStateReceiver, intentFilter)
    }

    private fun unregisterListeners() {
        if (!isListenersRegistered) return
        isListenersRegistered = false

        val settings = app.settings
        settings.NAUTICAL_RECEIVE_IN_BACKGROUND.removeListener(receiveInBackgroundPrefListener)
        settings.enabledPluginsPreference.removeListener(enabledPluginsListener)
        settings.NAUTICAL_XTE_THRESHOLD.removeListener(xteThresholdListener)
        settings.NAUTICAL_SHOW_LAYLINES.removeListener(laylinesEnabledListener)
        settings.NAUTICAL_MOB_ACTIVE.removeListener(mobActiveListener)
        settings.NAUTICAL_DR_START_TIME.removeListener(drEnabledListener)
        settings.NAUTICAL_NAVTEX_ENABLED.removeListener(navtexEnabledListener)
        settings.NAUTICAL_AIS_ENABLED.removeListener(aisEnabledListener)
        settings.NAUTICAL_SHOW_WINDY_TILES.removeListener(windyEnabledListener)
        settings.NAUTICAL_AIS_OWN_MMSI.removeListener(aisOwnMmsiListener)
        settings.NAUTICAL_AIS_DISPLAY_OWN_POSITION.removeListener(aisDisplayOwnPositionListener)
        settings.NAUTICAL_SHOW_GRIB_WAVES.removeListener(gribWavesEnabledListener)
        settings.NAUTICAL_SHOW_GRIB_PRESSURE.removeListener(gribPressureEnabledListener)
        settings.NAUTICAL_DISPLAY_MODE.removeListener(displayModeListener)
        settings.NAUTICAL_HEAVY_WEATHER_MODE.removeListener(heavyWeatherEnabledListener)
        settings.NAUTICAL_VESSEL_CONTEXT.removeListener(vesselContextListener)
        settings.NAUTICAL_MODULE_TIDES.removeListener(tidesModuleListener)
        settings.NAUTICAL_MODULE_GRIB.removeListener(gribModuleListener)
        settings.NAUTICAL_VHF_ENABLED.removeListener(vhfModuleListener)
        settings.NAUTICAL_MODULE_LOGBOOK.removeListener(logbookModuleListener)
        settings.NAUTICAL_MODULE_ENC.removeListener(encModuleListener)
        settings.NAUTICAL_MODULE_RASTER.removeListener(rasterModuleListener)
        settings.NAUTICAL_NMEA_SOURCE.removeListener(nmeaSourceListener)
        settings.NAUTICAL_SERVER_IP.removeListener(serverIpListener)
        settings.NAUTICAL_SERVER_PORT.removeListener(serverPortListener)
        settings.NAUTICAL_USE_SECURE_CONNECTION.removeListener(serverSecureListener)
        settings.NAUTICAL_SERVER_USERNAME.removeListener(serverUsernameListener)
        settings.NAUTICAL_SERVER_PASSWORD.removeListener(serverPasswordListener)
        settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.removeListener(signalKTokenListener)

        engine?.unregisterListener(marineStateListener)
        engine?.removeRouteStepListener(routeStepListener)

        app.locationProvider.removeLocationListener(locationListener)
        autopilotListener?.let { app.routingHelper.removeListener(it) }

        app.getSharedPreferences(OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        try {
            app.unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun initPlugin() {
        if (isPluginInitialized) {
            registerListeners()
            signalKLocationManager?.start()
            updateNmeaSource()
            val currentSource = app.settings.NAUTICAL_NMEA_SOURCE.get()
            if (currentSource == NmeaSource.SIGNALK) {
                connectionManager.startEngine()
                nauticalConnectionManager.connect()
            }
            return
        }
        isPluginInitialized = true
        NauticalLog.init(app)
        NauticalLog.i("Nautical Plugin Initializing...")

        if (pluginScope == null) {
            pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        val scope = pluginScope!!

        connectionManager.initConnection()
        val client = connectionManager.okHttpClient!!
        val conn = connectionManager.connection!!

        safetyArbitrator = SafetyStateArbitrator(app).apply { start() }
        if (capabilityManager == null) {
            capabilityManager = CapabilityManager(app)
        }

        engine = SignalKEngine(app, scope, capabilityManager)
        if (signalKLocationManager == null) {
            signalKLocationManager = SignalKLocationManager(app, engine!!.dataBroker)
        }
        signalKLocationManager?.start()
        autopilot = AutopilotController(app, conn, client, engine?.dataBroker)
        electrical = ElectricalController(app, autopilot!!)

        SailingDependencyContainer.initialize(app, engine!!.dataBroker, autopilot!!, client)

        if (logbookRepository == null) {
            logbookRepository = MarineLogbookRepository(app)
        }
        val performanceRepo = SailingDependencyContainer.performanceRepository
        if (logbookEngine == null && performanceRepo != null) {
            logbookEngine = AutomatedLogbookEngine(app, logbookRepository!!, engine!!, performanceRepo)
        }
        if (alarmPriorityManager == null) {
            alarmPriorityManager = AlarmPriorityManager(app, engine!!.dataBroker)
        }
        if (maneuverManager == null) {
            maneuverManager = ManeuverManager(app).apply {
                val anchoring = net.osmand.plus.plugins.nautical.maneuvers.AnchoringManeuver(app)
                registerManeuver("anchoring", anchoring)

                val mooring = net.osmand.plus.plugins.nautical.maneuvers.MooringManeuver(app)
                registerManeuver("mooring", mooring)

                val medMooring = net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver(app)
                registerManeuver("med_mooring", medMooring)

                val docking = net.osmand.plus.plugins.nautical.maneuvers.DockingManeuver(app)
                registerManeuver("docking", docking)

                val slipExit = net.osmand.plus.plugins.nautical.maneuvers.SlipExitManeuver(app)
                registerManeuver("slip_exit", slipExit)

                val weighAnchor = net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver(app)
                registerManeuver("weigh_anchor", weighAnchor)
                registerManeuver("weighing_anchor", weighAnchor)

                val heaveTo = net.osmand.plus.plugins.nautical.maneuvers.HeavingToManeuver(app)
                registerManeuver("heave_to", heaveTo)
                registerManeuver("heaving_to", heaveTo)

                val tacking = net.osmand.plus.plugins.nautical.maneuvers.TackingManeuver(app)
                registerManeuver("tack_port", tacking)
                registerManeuver("tack_stbd", tacking)
                registerManeuver("tacking", tacking)

                val gybing = net.osmand.plus.plugins.nautical.maneuvers.GybingManeuver(app)
                registerManeuver("gybe_port", gybing)
                registerManeuver("gybe_stbd", gybing)
                registerManeuver("gybing", gybing)

                val shunting = net.osmand.plus.plugins.nautical.maneuvers.ShuntingManeuver(app)
                registerManeuver("shunt", shunting)
                registerManeuver("shunting", shunting)

                val holdingPattern = net.osmand.plus.plugins.nautical.maneuvers.HoldingPatternManeuver(app)
                registerManeuver("holding_pattern", holdingPattern)
            }
        }
        if (ttsHelper == null) {
            ttsHelper = ManeuverTtsHelper(app)
        }
        if (skDiscovery == null) {
            skDiscovery = SignalKDiscovery(app)
        }
        if (tideManager == null) {
            tideManager = SignalKTideManager(app, scope)
        }
        if (telemetryFilterEngine == null) {
            telemetryFilterEngine = net.osmand.plus.plugins.nautical.telemetry.TelemetryFilterEngine(app, scope)
        }
        if (vhfManager == null) {
            vhfManager = NauticalVhfManager(app)
        }
        if (app.settings.NAUTICAL_VHF_ENABLED.get()) {
            vhfManager?.start()
        }
        if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
            if (aisManager == null) {
                val manager = NauticalAisManager(app)
                aisManager = manager
                layerManager.aisAisLayer?.let { layer ->
                    manager.addListener(layer)
                    layer.onManagerBound(manager)
                }
                manager.startUpdates()
            }
            scope.launch(Dispatchers.IO) {
                if (aisMessageListener == null && aisManager != null) {
                    val listener = AisMessageListener(aisManager!!)
                    aisMessageListener = listener
                    listener.start(port = 10110)
                }
            }
            engine?.registerAisListener { target: AisObject ->
                aisManager?.onAisObjectReceived(target)
                app.runInUIThread {
                    app.osmandMap?.refreshMap()
                }
            }
        }
        if (autopilotListener == null) {
            autopilotListener = AutopilotRouteListener(app.routingHelper)
        }

        if (mobStateMachine == null) {
            mobStateMachine = MobStateMachine(logbookRepository!!, scope)
            restoreTacticalState(scope)
        }
        systemManager.processBlackBoxCrash(scope)

        scope.launch {
            NauticalEventBus.events.collect { event ->
                if (event is NauticalEvent.MobStateChanged) {
                    app.runInUIThread {
                        requestRefresh()
                    }
                }
            }
        }

        registerListeners()

        scope.launch(Dispatchers.IO) {
            updateNmeaSource()
            val currentSource = app.settings.NAUTICAL_NMEA_SOURCE.get()
            if (currentSource == NmeaSource.SIGNALK) {
                connectionManager.startEngine()
                nauticalConnectionManager.connect()
            }
        }
    }

    private fun restoreTacticalState(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = logbookRepository?.getTacticalState(MobStateMachine.MOB_STATE_KEY)
                if (!json.isNullOrEmpty()) {
                    val status = kotlinx.serialization.json.Json.decodeFromString<net.osmand.plus.plugins.nautical.mob.engine.MobStatus>(json)
                    if (status.state == net.osmand.plus.plugins.nautical.mob.engine.MobState.ACTIVE_EMERGENCY) {
                        withContext(Dispatchers.Main) {
                            mobStateMachine?.restoreState(status)
                            log.info("Nautical: Restored active MOB state after process death.")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Nautical: Failed to restore tactical state", e)
            }
        }
    }

    fun getSettings(): OsmandSettings = app.settings

    private val voiceHandler = Handler(Looper.getMainLooper())
    private var lastVoiceHeading: Int? = null
    private val speakRunnable = Runnable {
        lastVoiceHeading?.let { heading ->
            app.player?.let { player ->
                val text = app.getString(R.string.nautical_new_course, heading)
                player.playCommands(player.newCommandBuilder().attention(text))
            }
        }
    }

    fun speakHeading(heading: Int) {
        lastVoiceHeading = heading
        voiceHandler.removeCallbacks(speakRunnable)
        voiceHandler.postDelayed(speakRunnable, 500)
    }

    fun updateNmeaSource() {
        val source = app.settings.NAUTICAL_NMEA_SOURCE.get()
        val navtexRepo = net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository(app)
        val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, pluginScope!!, navtexRepo)
        multiplexer.stopAll()

        when (source) {
            NmeaSource.INTERNAL -> {
                signalKLocationManager?.stop()
            }
            NmeaSource.SIGNALK -> {
                signalKLocationManager?.start()
                connectionManager.startEngine()
            }
            NmeaSource.BLUETOOTH -> {
                signalKLocationManager?.start()
                val address = app.settings.NAUTICAL_BT_DEVICE_ADDRESS.get()
                if (address.isNotEmpty()) {
                    val client = net.osmand.plus.plugins.nautical.nmea.connection.BluetoothNmeaClient(address, pluginScope!!)
                    multiplexer.start(client)
                }
            }
            NmeaSource.USB -> {
                signalKLocationManager?.start()
                val deviceName = app.settings.NAUTICAL_USB_DEVICE_NAME.get()
                val usbManager = app.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                val device = usbManager.deviceList[deviceName]
                if (device != null) {
                    val baud = app.settings.NAUTICAL_NMEA_BAUD_RATE.get()
                    val client = net.osmand.plus.plugins.nautical.nmea.connection.UsbNmeaClient(app, device, baud, pluginScope!!)
                    multiplexer.start(client)
                }
            }
            NmeaSource.TCP -> {
                signalKLocationManager?.start()
                val ip = app.settings.NAUTICAL_SERVER_IP.get()
                val port = app.settings.NAUTICAL_SERVER_PORT.get().toIntOrNull() ?: 3000
                val client = net.osmand.plus.plugins.nautical.nmea.connection.TcpNmeaClient(ip, port, pluginScope!!)
                multiplexer.start(client)
            }
            else -> {}
        }
    }

    fun reconnect() {
        app.runInUIThread {
            updateNmeaSource()
            connectionManager.startEngine()
        }
    }

    fun clearMarineData() {
        pluginScope?.launch(Dispatchers.IO) {
            try {
                log.info("Nautical: Starting Marine Data cleanup...")
                engine?.clearBuffers(app)
                SailingDependencyContainer.gribRepository?.cleanup()
                val gribDir = File(app.getAppPath(""), "nautical/grib")
                if (gribDir.exists()) {
                    gribDir.deleteRecursively()
                }
                aisMessageListener?.stopListener()
                aisMessageListener = null
                aisManager?.cleanupResources()
                aisManager = null
                app.settings.NAUTICAL_ANCHOR_LAT.set(0.0)
                app.settings.NAUTICAL_ANCHOR_LON.set(0.0)
                anchorWatchdog?.stop()
                val cacheDir = File(app.cacheDir, "nautical")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }

                withContext(Dispatchers.Main) {
                    app.showToastMessage(R.string.nautical_data_cleared)
                    requestRefresh()
                }
                log.info("Nautical: Marine Data cleanup completed.")
            } catch (e: Exception) {
                log.error("Nautical: Error during Marine Data cleanup: ${e.message}")
            }
        }
    }

    fun updateFeatureLifecycle() {
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        val activity = app.osmandMap?.mapView?.mapActivity

        if (!isActive || !isBoat) {
            stopAllFeatures()
            suppressBasemap(false)
            return
        }

        suppressBasemap(isModuleEnabled(NauticalModule.ENC))

        if (app.settings.NAUTICAL_SHOW_LAYLINES.get()) {
            if (laylineViewModel == null && activity != null && layerManager.layerController != null) {
                uiOverlayManager.initLaylineSystem(activity, layerManager.layerController!!) { laylineViewModel = it }
            }
        } else {
            laylineViewModel?.clear()
            laylineViewModel = null
            layerManager.layerController?.laylinesLayer?.updateState(net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineUiState())
        }

        if (app.settings.NAUTICAL_DR_START_TIME.get() != 0L) {
            if (drViewModel == null && activity != null && layerManager.layerController != null) {
                uiOverlayManager.initDrSystem(activity, layerManager.layerController!!, hudManager) { drViewModel = it }
            }
        } else {
            drViewModel?.clear()
            drViewModel = null
            hudManager?.get()?.removeHeader(uiOverlayManager.drHeaderView)
            uiOverlayManager.drHeaderView = null
        }

        if (app.settings.NAUTICAL_NAVTEX_ENABLED.get()) {
            if (navtexViewModel == null && activity != null && layerManager.layerController != null) {
                uiOverlayManager.initNavtexSystem(activity, layerManager.layerController!!, hudManager) { navtexViewModel = it }
            }
        } else {
            stopNavtex()
        }

        if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
            if (aisManager == null) {
                aisManager = NauticalAisManager(app)
                aisManager?.startUpdates()
            }
            getScope().launch(Dispatchers.IO) {
                if (aisMessageListener == null && aisManager != null) {
                    val listener = AisMessageListener(aisManager!!)
                    aisMessageListener = listener
                    listener.start(port = 10110)
                }
            }
            engine?.registerAisListener { target: AisObject ->
                aisManager?.onAisObjectReceived(target)
                app.runInUIThread {
                    app.osmandMap?.refreshMap()
                }
            }
        } else {
            clearAisLayer()
            getScope().launch(Dispatchers.IO) {
                aisMessageListener?.stopListener()
                aisMessageListener = null
            }
            aisManager?.stopUpdates()
            aisManager = null
        }

        if (app.settings.NAUTICAL_VHF_ENABLED.get()) {
            if (vhfManager == null) {
                vhfManager = NauticalVhfManager(app)
            }
            vhfManager?.start()
        } else {
            vhfManager?.stop()
        }

        layerManager.layerController?.updateLayerVisibility()
        app.osmandMap?.refreshMap()
    }

    fun stopNavtex() {
        navtexViewModel?.clear()
        navtexViewModel = null
        hudManager?.get()?.removeHeader(uiOverlayManager.navtexHudView)
        uiOverlayManager.navtexHudView = null
    }

    fun clearAisLayer() {
        engine?.registerAisListener(null)
        aisManager?.cleanupResources()
    }

    private fun suppressBasemap(suppress: Boolean) {
        val value = if (suppress) "true" else "false"
        app.settings.getCustomRenderProperty("hide_sea_marks", "false").set(value)
        app.settings.getCustomRenderProperty("hide_coastline", "false").set(value)
        app.settings.getCustomRenderProperty("no_osm_nautical", "false").set(value)
    }

    private fun stopAllFeatures() {
        stopNavtex()
        clearAisLayer()
        aisMessageListener?.stopListener()
        aisMessageListener = null
        aisManager?.stopUpdates()
        aisManager = null
        vhfManager?.stop()
        laylineViewModel?.clear()
        laylineViewModel = null
        drViewModel?.clear()
        drViewModel = null
    }

    fun isSignalKConnected(): Boolean {
        return connectionManager.connection?.isConnected() == true
    }

    fun isAudioHardwareAvailable(): Boolean {
        return NauticalAudioArbiter.getInstance(app).isHardwareAvailable()
    }

    fun isVesselOnPassage(): Boolean {
        return safetyEvaluator.isVesselOnPassage(engine)
    }

    private fun getScope(): CoroutineScope {
        return pluginScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob()).also { pluginScope = it }
    }

    override fun disable(app: OsmandApplication) {
        super.disable(app)
        unregisterListeners()
        aisMessageListener?.stopListener()
        aisMessageListener = null
        aisManager?.cleanupResources()
        aisManager = null
        telemetryFilterEngine?.stop()
        telemetryFilterEngine = null
        signalKLocationManager?.stop()
        signalKLocationManager = null
        refreshHandler.removeCallbacks(refreshRunnable)
        getScope().launch(Dispatchers.IO) {
            try {
                engine?.historyManager?.saveBuffersToDisk(app)
                connectionManager.connection?.disconnect()
                nauticalConnectionManager.disconnect()
            } catch (e: Exception) {
                log.error("Error during plugin disable teardown", e)
            }
        }
    }

    override fun mapActivityCreate(activity: MapActivity) {
        super.mapActivityCreate(activity)
        onMapActivityCreated(activity)
    }

    fun onMapActivityCreated(activity: MapActivity) {
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        if (isActive && isBoat) {
            registerLayers(activity, activity)
            if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
                if (aisManager == null) {
                    aisManager = NauticalAisManager(app)
                    aisManager?.startUpdates()
                }
                getScope().launch(Dispatchers.IO) {
                    if (aisMessageListener == null && aisManager != null) {
                        val listener = AisMessageListener(aisManager!!)
                        aisMessageListener = listener
                        listener.start(port = 10110)
                    }
                }
                engine?.registerAisListener { target: AisObject ->
                    aisManager?.onAisObjectReceived(target)
                    app.runInUIThread {
                        app.osmandMap?.refreshMap()
                    }
                }
            }
            initSubsystems(activity)
            uiOverlayManager.updateHudVisibility(hudManager)
            signalKLocationManager?.start()
            getScope().launch(Dispatchers.IO) {
                updateNmeaSource()
                nauticalConnectionManager.connect()
                engine?.refreshVesselState()
            }
        }
    }

    override fun mapActivityResume(activity: MapActivity) {
        super.mapActivityResume(activity)
        isAppInBackground = false
        presentationManager?.onResume(activity)
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        if (isActive && isBoat) {
            registerLayers(activity, activity)
            if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
                if (aisManager == null) {
                    aisManager = NauticalAisManager(app)
                    aisManager?.startUpdates()
                }
                getScope().launch(Dispatchers.IO) {
                    if (aisMessageListener == null && aisManager != null) {
                        val listener = AisMessageListener(aisManager!!)
                        aisMessageListener = listener
                        listener.start(port = 10110)
                    }
                }
                engine?.registerAisListener { target: AisObject ->
                    aisManager?.onAisObjectReceived(target)
                    app.runInUIThread {
                        app.osmandMap?.refreshMap()
                    }
                }
            }
            initSubsystems(activity)
            uiOverlayManager.updateHudVisibility(hudManager)
            getScope().launch(Dispatchers.IO) {
                updateNmeaSource()
                nauticalConnectionManager.connect()
                engine?.refreshVesselState()
            }
        }
    }

    private fun initSubsystems(activity: MapActivity) {
        if (hudManager == null || hudManager?.get() == null) {
            hudManager = WeakReference(NauticalHudManager(activity))
        }
        val hud = hudManager?.get()
        if (hud?.activity != activity) {
            hudManager = WeakReference(NauticalHudManager(activity))
        }

        uiOverlayManager.initForwardWatchSystem(activity, hudManager)
        uiOverlayManager.initEnvironmentSystem(activity, hudManager)
        uiOverlayManager.initWatchScheduleSystem(activity, hudManager)
        uiOverlayManager.initWorkflowSystem(activity, hudManager, workflowEngine, workflowManager)
        uiOverlayManager.initTacticalHudSystem(activity, hudManager)

        layerManager.layerController?.let { controller ->
            uiOverlayManager.initMobSystem(activity, controller, hudManager, mobStateMachine, mobAudioAlertManager) { mobViewModel = it }
            uiOverlayManager.initDrSystem(activity, controller, hudManager) { drViewModel = it }
            uiOverlayManager.initLaylineSystem(activity, controller) { laylineViewModel = it }
            uiOverlayManager.initNavtexSystem(activity, controller, hudManager) { navtexViewModel = it }
        }
    }

    override fun mapActivityPause(activity: MapActivity) {
        super.mapActivityPause(activity)
        isAppInBackground = true
        isRefreshScheduled = false
        refreshHandler.removeCallbacksAndMessages(null)
        presentationManager?.onPause()
        uiOverlayManager.onPause()
        getScope().launch(Dispatchers.IO) {
            try {
                engine?.historyManager?.saveBuffersToDisk(app)
                updateNauticalBackgroundService()
            } catch (e: Exception) {
                log.error("Error during background pause transition", e)
            }
        }
    }

    override fun mapActivityScreenOff(activity: MapActivity) {
        super.mapActivityScreenOff(activity)
        getScope().launch(Dispatchers.IO) {
            try {
                if (!app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get() && engine?.isFollowingRoute != true) {
                    connectionManager.connection?.disconnect()
                }
                updateNauticalBackgroundService()
            } catch (e: Exception) {
                log.error("Error in mapActivityScreenOff", e)
            }
        }
    }

    override fun mapActivityDestroy(activity: MapActivity) {
        super.mapActivityDestroy(activity)
        isAppInBackground = true
        isRefreshScheduled = false
        refreshHandler.removeCallbacksAndMessages(null)
        presentationManager?.onPause()
        uiOverlayManager.detach()
        hudManager?.get()?.onDestroy()
        hudManager = null
        getScope().launch(Dispatchers.IO) {
            try {
                engine?.historyManager?.saveBuffersToDisk(app)
                updateNauticalBackgroundService()
            } catch (e: Exception) {
                log.error("Error saving buffers in mapActivityDestroy", e)
            }
        }
        layerManager.destroy()
    }

    override fun updateLayers(context: Context, mapActivity: MapActivity?) {
        layerManager.updateLayers(
            context = context,
            mapActivity = mapActivity,
            isPluginActive = isActive,
            s57SpatialIndex = s57SpatialIndex,
            isModuleEnabled = { isModuleEnabled(it) },
            onInitSubsystems = { _ ->
                mapActivity?.let { act -> initSubsystems(act) }
            }
        )
    }

    override fun registerLayers(context: Context, mapActivity: MapActivity?) {
        if (isActive && mapActivity != null) {
            layerManager.registerLayers(context, mapActivity, s57SpatialIndex) { _ ->
                initSubsystems(mapActivity)
            }
            if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
                val mapView = mapActivity.mapView
                val aisLayer = layerManager.aisAisLayer ?: NauticalAisLayer(mapActivity, this).also { layerManager.aisAisLayer = it }
                aisManager?.let { manager ->
                    manager.addListener(aisLayer)
                    aisLayer.onManagerBound(manager)
                }
                if (!mapView.layers.contains(aisLayer)) {
                    mapView.addLayer(aisLayer, 4.5f)
                }
            }
        }
    }

    override fun createMapWidgetForParams(mapActivity: MapActivity, widgetType: WidgetType, customId: String?, widgetsPanel: WidgetsPanel?): MapWidget? {
        return widgetFactory.createMapWidgetForParams(mapActivity, widgetType, customId, widgetsPanel, maneuverManager)
    }

    override fun createWidgets(
        activity: MapActivity,
        widgetInfos: MutableList<MapWidgetInfo>,
        appMode: ApplicationMode,
        layoutMode: ScreenLayoutMode?,
    ) {
        widgetFactory.createWidgets(activity, widgetInfos, appMode, layoutMode, maneuverManager)
    }

    override fun registerConfigureMapCategoryActions(
        adapter: ContextMenuAdapter,
        mapActivity: MapActivity,
        customRules: MutableList<RenderingRuleProperty>,
    ) {
        contextMenuHelper.registerConfigureMapCategoryActions(
            adapter = adapter,
            mapActivity = mapActivity,
            customRules = customRules,
            isPluginActive = isActive,
            isModuleEnabled = { isModuleEnabled(it) },
            onRequestRefresh = { requestRefresh() }
        )
    }

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {
        contextMenuHelper.registerMapContextMenuActions(
            mapActivity = mapActivity,
            lat = lat,
            lon = lon,
            adapter = adapter,
            obj = obj,
            conf = conf,
            tacticalStartManager = tacticalStartManager,
            autopilot = autopilot,
            engine = engine,
            mobViewModel = mobViewModel,
            routingViewModel = routingViewModel,
            safetyManager = safetyManager,
            s57SpatialIndex = s57SpatialIndex,
            layerController = layerManager.layerController,
            vhfPoiLayer = layerManager.vhfPoiLayer,
            skWaypointLayer = layerManager.skWaypointLayer,
            pluginScope = pluginScope,
            onRequestRefresh = { requestRefresh() }
        )
    }

    fun applyDisplayMode(mapActivity: MapActivity, mode: NauticalDisplayMode) {
        uiOverlayManager.applyDisplayMode(mapActivity, mode, presentationManager) { requestRefresh() }
    }

    fun toggleNightVision(mapActivity: MapActivity) {
        val currentMode = app.settings.NAUTICAL_DISPLAY_MODE.get()
        val nextMode = if (currentMode == NauticalDisplayMode.DARK) NauticalDisplayMode.NORMAL else NauticalDisplayMode.DARK
        applyDisplayMode(mapActivity, nextMode)
    }

    override fun getMapTheme(): DayNightMode? {
        val mode = app.settings.NAUTICAL_DISPLAY_MODE.get()
        return when (mode) {
            NauticalDisplayMode.DARK -> DayNightMode.NIGHT
            NauticalDisplayMode.SUNLIGHT -> DayNightMode.DAY
            else -> null
        }
    }

    fun applyVesselContext(context: VesselContext) {
        if (isApplyingVesselContext) return
        isApplyingVesselContext = true
        try {
            val s = app.settings
            if (s.NAUTICAL_VESSEL_CONTEXT.get() != context) {
                s.NAUTICAL_VESSEL_CONTEXT.set(context)
            }
            when (context) {
                VesselContext.SAILING -> {
                    if (s.NAUTICAL_SHOW_LAYLINES.get() != true) s.NAUTICAL_SHOW_LAYLINES.set(true)
                    if (s.NAUTICAL_MODULE_RASTER.get() != true) s.NAUTICAL_MODULE_RASTER.set(true)
                    if (s.NAUTICAL_SHOW_RASTER_CHARTS.get() != true) s.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
                    if (s.NAUTICAL_LOOK_AHEAD_TIME.get() != 10) s.NAUTICAL_LOOK_AHEAD_TIME.set(10)
                    if (s.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get() != 0.1f) s.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(0.1f)
                }
                VesselContext.MOTORING -> {
                    if (s.NAUTICAL_SHOW_LAYLINES.get() != false) s.NAUTICAL_SHOW_LAYLINES.set(false)
                    if (s.NAUTICAL_MODULE_RASTER.get() != true) s.NAUTICAL_MODULE_RASTER.set(true)
                    if (s.NAUTICAL_SHOW_RASTER_CHARTS.get() != true) s.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
                    if (s.NAUTICAL_LOOK_AHEAD_TIME.get() != 5) s.NAUTICAL_LOOK_AHEAD_TIME.set(5)
                    if (s.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get() != 0.2f) s.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(0.2f)
                }
                VesselContext.EMERGENCY_HEAVE_TO -> {
                    if (s.NAUTICAL_HEAVY_WEATHER_MODE.get() != true) s.NAUTICAL_HEAVY_WEATHER_MODE.set(true)
                    if (s.NAUTICAL_OFF_COURSE_ALARM.get() != 20.0f) s.NAUTICAL_OFF_COURSE_ALARM.set(20.0f)
                    if (s.NAUTICAL_ARRIVAL_RADIUS.get() != 500.0f) s.NAUTICAL_ARRIVAL_RADIUS.set(500.0f)
                    workflowManager?.onHeavyWeatherModeChanged(true, app.osmandMap?.mapView?.mapActivity)
                }
                VesselContext.ANCHORED -> {
                    val loc = app.locationProvider.lastKnownLocation
                    if (loc != null) {
                        engine?.setAnchor(loc.latitude, loc.longitude, app.settings.NAUTICAL_ANCHOR_RADIUS.get().toDouble())
                    }
                }
                VesselContext.MOORED, VesselContext.DOCKING -> {
                    engine?.disarmAnchor()
                }
            }
            updateFeatureLifecycle()
            requestRefresh()
        } finally {
            isApplyingVesselContext = false
        }
    }

    fun checkBatteryOptimization() {
        systemManager.checkBatteryOptimization()
    }

    fun forceEmergencyBrightness() {
        systemManager.forceEmergencyBrightness()
    }

    fun updateNauticalBackgroundService() {
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        val receiveInBg = app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
        val isFollowing = engine?.isFollowingRoute == true
        val isAnchorActive = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0

        if (isActive && isBoat && (receiveInBg || isFollowing || isAnchorActive)) {
            NauticalBackgroundService.startService(app)
        } else {
            NauticalBackgroundService.stopService(app)
        }
    }

    override fun getSettingsScreenType(): SettingsScreenType = SettingsScreenType.NAUTICAL_SETTINGS

    override fun showPluginSettings(activity: Activity) {
        if (activity is androidx.fragment.app.FragmentActivity) {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(
                activity,
                SettingsScreenType.NAUTICAL_SETTINGS
            )
        }
    }
    override fun getId(): String = NAUTICAL_ID
    override fun getName(): String = app.getString(R.string.nautical_plugin_name)
    override fun getDescription(linksEnabled: Boolean): CharSequence = app.getString(R.string.nautical_plugin_description)
    override fun getPrefsDescription(): String = app.getString(R.string.nautical_plugin_description)
    override fun getLogoResourceId(): Int = R.drawable.ic_action_sail_boat_dark
    override fun getAssetResourceImage(): Drawable? = app.uiUtilities.getIcon(R.drawable.ic_plugin_nautical_map)
    override fun isMarketPlugin(): Boolean = false

    override fun addMyPlacesTab(myPlacesActivity: net.osmand.plus.myplaces.MyPlacesActivity, mTabs: MutableList<TabItem>, intent: Intent) {
        if (app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)) {
            mTabs.add(
                myPlacesActivity.getTabIndicator(
                    R.string.logbook_title,
                    net.osmand.plus.plugins.nautical.ui.logbook.MarineLogbookFragment::class.java
                )
            )
        }
    }

    override fun getQuickActionTypes(): List<QuickActionType> {
        return listOf(
            NauticalNightVisionQuickAction.TYPE,
            NauticalAnchorQuickAction.TYPE,
            NauticalMobQuickAction.TYPE,
            NauticalVhfQuickAction.TYPE,
            NauticalSwitchQuickAction.TYPE,
            NauticalMasterTelemetryQuickAction.TYPE,
            NauticalAutopilotQuickAction.TYPE,
            NauticalSailInventoryQuickAction.TYPE,
            NauticalTacticalStartPinQuickAction.TYPE_PORT,
            NauticalTacticalStartPinQuickAction.TYPE_STBD,
            NauticalLaylinesQuickAction.TYPE,
            NauticalAisQuickAction.TYPE
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
}
