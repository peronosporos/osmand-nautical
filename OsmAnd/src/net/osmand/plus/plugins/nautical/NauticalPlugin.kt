package net.osmand.plus.plugins.nautical

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.StateChangedListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.helpers.DayNightHelper
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.dr.ui.DrWarningHeaderView
import net.osmand.plus.plugins.nautical.dr.viewmodel.DeadReckoningViewModel
import net.osmand.plus.plugins.nautical.engine.AlarmPriorityManager
import net.osmand.plus.plugins.nautical.engine.AutopilotController
import net.osmand.plus.plugins.nautical.engine.AutopilotManager
import net.osmand.plus.plugins.nautical.engine.AutopilotRouteListener
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.ElectricalController
import net.osmand.plus.plugins.nautical.engine.GpxStreamer
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalAisManager
import net.osmand.plus.plugins.nautical.engine.NauticalLocationProvider
import net.osmand.plus.plugins.nautical.engine.NauticalNotificationManager
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.engine.NauticalWorkflowManager
import net.osmand.plus.plugins.nautical.engine.NotificationState
import net.osmand.plus.plugins.nautical.engine.*
import net.osmand.plus.plugins.nautical.network.*
import net.osmand.plus.plugins.nautical.engine.SignalKTideManager
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessageDecoder
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexHudView
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineViewModel
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.logbook.engine.AutomatedLogbookEngine
import net.osmand.plus.plugins.nautical.maneuvers.AnchoringManeuver
import net.osmand.plus.plugins.nautical.maneuvers.DockingManeuver
import net.osmand.plus.plugins.nautical.maneuvers.GybingManeuver
import net.osmand.plus.plugins.nautical.maneuvers.HeavingToManeuver
import net.osmand.plus.plugins.nautical.maneuvers.ManOverboardManeuver
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverManager
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverOverlayWidget
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverSpeechHelper
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverState
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverTtsHelper
import net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver
import net.osmand.plus.plugins.nautical.maneuvers.MooringManeuver
import net.osmand.plus.plugins.nautical.maneuvers.ShuntingManeuver
import net.osmand.plus.plugins.nautical.maneuvers.SlipExitManeuver
import net.osmand.plus.plugins.nautical.maneuvers.TackingManeuver
import net.osmand.plus.plugins.nautical.maneuvers.TacticalProcessor
import net.osmand.plus.plugins.nautical.maneuvers.TacticalStartManager
import net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.mob.engine.MobStateMachine
import net.osmand.plus.plugins.nautical.mob.ui.MobEmergencyHeaderView
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobAudioAlertManager
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.poi.ui.VhfPoiSearchLayer
import net.osmand.plus.plugins.nautical.quickaction.NauticalAnchorQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalMobQuickAction
import net.osmand.plus.plugins.nautical.quickaction.NauticalNightVisionQuickAction
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.ui.ForwardWatchHudView
import net.osmand.plus.plugins.nautical.ui.HardwareHealthHudHeader
import net.osmand.plus.plugins.nautical.ui.HeartbeatHudView
import net.osmand.plus.plugins.nautical.ui.NauticalAisDetailsDialog
import net.osmand.plus.plugins.nautical.ui.NauticalAisLayer
import net.osmand.plus.plugins.nautical.ui.NauticalEnvironmentWidgetView
import net.osmand.plus.plugins.nautical.ui.NauticalSetupWizardDialog
import net.osmand.plus.plugins.nautical.ui.SignalKLogbookLayer
import net.osmand.plus.plugins.nautical.ui.StartLineHudHeader
import net.osmand.plus.plugins.nautical.ui.TacticalHudView
import net.osmand.plus.plugins.nautical.ui.ThermalWarningView
import net.osmand.plus.plugins.nautical.ui.WatchScheduleHudView
import net.osmand.plus.plugins.nautical.ui.WindTrendHudHeader
import net.osmand.plus.plugins.nautical.ui.WorkflowHeaderView
import net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchHudView
import net.osmand.plus.plugins.nautical.utils.NauticalLog
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.plus.quickaction.QuickActionType
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.settings.enums.DayNightMode
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.settings.enums.ScreenLayoutMode
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.settings.enums.VesselContext
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.ActuatorLoadWidget
import net.osmand.plus.views.mapwidgets.widgets.MapWidget
import net.osmand.plus.views.mapwidgets.widgets.MarineTextWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalCameraWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalCompassWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalDisplayModeWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalElectricalDashboardBottomSheet
import net.osmand.plus.views.mapwidgets.widgets.NauticalElectricalWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalFlagsWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalGraphWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalManeuversBottomSheet
import net.osmand.plus.views.mapwidgets.widgets.NauticalMasterTelemetryWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalMobWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalPilotWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalTelltaleWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalVhfWidget
import net.osmand.plus.views.mapwidgets.widgets.PolarSpeedRatioWidget
import net.osmand.plus.views.mapwidgets.widgets.TargetVmgWidget
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import net.osmand.render.RenderingRuleProperty
import net.osmand.shared.aistracker.AisObject
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

class NauticalPlugin(app: OsmandApplication) : OsmandPlugin(app), DayNightHelper.MapThemeProvider {
    private val log = PlatformUtil.getLog(NauticalPlugin::class.java)

    private var thermalListener: Any? = null
    var isThrottlingRedraws = false
        private set
    private var isBatteryLow = false
    private var isPowerSaveMode = false
    private var isAppInBackground = false
    private var lastForcedRefreshTime = 0L
    private var thermalWarningView: ThermalWarningView? = null
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    isBatteryLow = true
                    log.warn("Nautical: Battery LOW detected. Engaging power saving.")
                    applyPowerThrottling()
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    isBatteryLow = false
                    log.info("Nautical: Battery OK.")
                    applyPowerThrottling()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    isPowerSaveMode = pm.isPowerSaveMode
                    log.info("Nautical: Power Save Mode changed: $isPowerSaveMode")
                    applyPowerThrottling()
                }
            }
        }
    }

    private fun applyPowerThrottling() {
        val throttled = isBatteryLow || isPowerSaveMode || isAppInBackground
        SailingDependencyContainer.gribRepository?.isThrottled = throttled
        engine?.setPowerSaveMode(throttled)
        locationProvider?.setAppInBackground(isAppInBackground)
        
        // Adjust refresh baseline: High-freq 10Hz (Normal), Low-freq 1Hz (Background/PowerSave)
        currentRefreshThrottleMs = if (throttled) 1000L else 100L
        
        if (throttled) {
             refreshHandler.removeCallbacks(refreshRunnable)
             isRefreshScheduled = false
        }
        
        requestRefresh()
    }

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

    private fun isWidgetAllowed(type: WidgetType): Boolean {
        return when (type) {
            WidgetType.NAUTICAL_VHF -> isModuleEnabled(NauticalModule.VHF)
            WidgetType.NAUTICAL_MOB -> true // Always allowed for safety
            WidgetType.NAUTICAL_DEPTH, WidgetType.NAUTICAL_WIND -> true // Basic telemetry
            else -> true
        }
    }

    val application: OsmandApplication
        get() = app

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
        fun getAisObject(mmsi: Int): AisObject? = getInstance()?.aisManager?.getAisObjects()?.find { it.mmsi == mmsi }

        @JvmStatic
        var engine: SignalKEngine? = null
            private set

        @JvmStatic
        var autopilot: AutopilotController? = null
            private set

        @JvmStatic
        var electrical: ElectricalController? = null
            private set

        @JvmStatic
        var autopilotManager: AutopilotManager? = null
            private set

        @JvmStatic
        var hudManager: WeakReference<NauticalHudManager>? = null

        private var instanceRef: WeakReference<NauticalPlugin>? = null

        @JvmStatic
        fun getInstance(): NauticalPlugin? = instanceRef?.get()
    }

    internal var okHttpClient: OkHttpClient? = null
        private set

    private var lastUsedTrustAll: Boolean? = null

    private var lastRefreshTime = 0L
    private var currentRefreshThrottleMs = 100L // 10Hz default
    @Volatile
    private var isRefreshScheduled = false

    fun requestRefresh() {
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

    private lateinit var connection: OkHttpSignalKConnection

    fun getConnection(): OkHttpSignalKConnection? = if (::connection.isInitialized) connection else null
    private var locationProvider: NauticalLocationProvider? = null
    var aisManager: NauticalAisManager? = null
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

    var vhfPoiLayer: VhfPoiSearchLayer? = null
        private set

    var skRasterLayer: net.osmand.plus.plugins.nautical.raster.SignalKRasterLayer? = null
        private set

    var skLogbookLayer: SignalKLogbookLayer? = null
        private set

    private var skDiscovery: SignalKDiscovery? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        pluginScope?.launch(Dispatchers.Default) {
            try {
                val combinedNotifications = state.notifications.toMutableMap()
                if (state.isStwUnreliable) {
                    combinedNotifications["safety.speed.stw_failover"] = SignalKNotification(
                        message = app.getString(R.string.nautical_stw_unreliable_fallback),
                        state = NotificationState.WARN
                    )
                }

                // Safety Evaluation Group
                evaluateVesselSafety(state, combinedNotifications)

                // Background processing
                val navtexNotif = state.notifications["navigation.navtex"]
                val decodedNavtex = navtexNotif?.let { NavtexMessageDecoder.decode(it.message) }

                checkConnectionSafety(state)
                checkEmergencyPower(state)
                autopilot?.updateAutoSeaState(state)
                if (state.autopilotState != "standby") {
                    autopilot?.applyWaveBias(state)
                }
                
                // Live recording for Polar Configuration Wizard
                if (polarConfigViewModel?.wizardState?.value == net.osmand.plus.plugins.nautical.viewmodel.WizardState.ACTIVE_LOGGING) {
                    val tws = state.windSpeedTrue ?: 0.0
                    val twa = state.trueWindAngle ?: 0.0
                    val speed = state.speedOverGround ?: 0.0
                    polarConfigViewModel?.recordDataPoint(tws, Math.toDegrees(twa), speed)
                }

                maneuverManager?.updateState(state)
                tacticalProcessor?.update(state)

                withContext(Dispatchers.Main) {
                    val currentStatus = state.connectionStatus
                    if (lastConnectionStatus != currentStatus) {
                        when (currentStatus) {
                            ConnectionStatus.CONNECTED -> {
                                if (lastConnectionStatus == ConnectionStatus.DISCONNECTED || lastConnectionStatus == ConnectionStatus.CONNECTING) {
                                    app.showToastMessage(R.string.nautical_sk_connected)
                                    NauticalAudioArbiter.getInstance(app).dispatchTts(app.getString(R.string.nautical_sk_connected), net.osmand.plus.plugins.nautical.audio.AlarmType.TTS_INSTRUCTION)
                                } else if (lastConnectionStatus == ConnectionStatus.STALE) {
                                    app.showToastMessage(R.string.nautical_connection_restored)
                                }
                            }
                            ConnectionStatus.STALE -> {
                                if (lastConnectionStatus == ConnectionStatus.CONNECTED) {
                                    app.showToastMessage(R.string.nautical_sk_connection_stale)
                                }
                            }
                            ConnectionStatus.DISCONNECTED -> {
                                if (lastConnectionStatus != null && lastConnectionStatus != ConnectionStatus.DISCONNECTED) {
                                    app.showToastMessage(R.string.nautical_sk_connection_lost)
                                }
                            }
                            ConnectionStatus.CONNECTING -> {
                                app.showToastMessage(R.string.nautical_sk_connecting)
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

                    startLineHudHeader?.update()
                    windTrendHudHeader?.update()
                    anchorWatchHudView?.update()
                    lastAutopilotState = state.autopilotState
                    checkScreenAlwaysOn()
                    presentationManager?.updateState(state)

                    if (app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT && state.headingTrue != null) {
                        val mapView = app.osmandMap?.mapView
                        if (mapView != null && app.settings.isCompassMode(net.osmand.plus.settings.enums.CompassMode.COMPASS_DIRECTION)) {
                            val hdgDeg = Math.toDegrees(state.headingTrue).toFloat()
                            if (abs(net.osmand.util.MapUtils.degreesDiff(mapView.rotate.toDouble(), (-hdgDeg).toDouble())) > 0.1) {
                                mapView.setRotate(-hdgDeg, true)
                            }
                        }
                    }
                    requestRefresh()
                }
            } catch (e: Exception) {
                log.error("Error in marineStateListener: ${e.message}", e)
            }
        }
    }

    private fun evaluateVesselSafety(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        checkOffCourseAlert(state)
        checkDepthSafety(state, notifications)
        checkAccidentalGybeAlert(state, notifications)
    }

    var isConnectionLostAlertActive = false
        private set

    private val connectionRestoredListener: () -> Unit = {
        retryAttempt = 0
        autopilot?.pushAllSettings()
        autopilotManager?.reconcileState()
    }

    private fun checkConnectionSafety(state: MarineState) {
        val wasEngaged = (lastAutopilotState != null) && (lastAutopilotState?.uppercase(Locale.US) != "STANDBY")
        val isDisconnected = (state.connectionStatus == ConnectionStatus.DISCONNECTED) || (state.connectionStatus == ConnectionStatus.STALE)
        
        if (wasEngaged && isDisconnected) {
            if (!isConnectionLostAlertActive) {
                isConnectionLostAlertActive = true
                startConnectionLostAudioLoop()
                requestRefresh()
            }
            lastAutopilotState = "STANDBY"
        } else if (!isDisconnected && isConnectionLostAlertActive) {
            isConnectionLostAlertActive = false
            stopConnectionLostAudioLoop()
            app.runInUIThread {
                app.showToastMessage(R.string.nautical_connection_restored)
                requestRefresh()
            }
        }
    }

    private fun checkEmergencyPower(state: MarineState) {
        var lowBatteryDetected = false
        state.batteries.values.forEach { b ->
            val v = b.voltage ?: 0.0
            if (v in 0.1..11.0) { // Critical low for 12V system
                lowBatteryDetected = true
                if (!isBatteryAlertActive) {
                    hudManager?.get()?.showBanner(app.getString(R.string.nautical_emergency_power_low), 30000, isWarning = true)
                    NauticalAudioArbiter.getInstance(app).dispatchTts(app.getString(R.string.nautical_critical_low_battery), net.osmand.plus.plugins.nautical.audio.AlarmType.TTS_INSTRUCTION)
                }
            }
        }
        isBatteryAlertActive = lowBatteryDetected
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
                    log.error("Connection lost audio loop error: ${e.message}", e)
                }
                delay(10.seconds)
            }
        }
    }

    private fun stopConnectionLostAudioLoop() {
        connectionLostAudioJob?.cancel()
        connectionLostAudioJob = null
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
                val bearing = net.osmand.shared.util.KMapUtils.getBearing(state.latitude, state.longitude, nextWaypoint.first, nextWaypoint.second)
                val bearingDeg = if (bearing < 0) bearing + 360 else bearing
                val msg = app.getString(R.string.nautical_proceeding_to_waypoint, bearingDeg)
                NauticalAudioArbiter.getInstance(app).dispatchTts(msg, net.osmand.plus.plugins.nautical.audio.AlarmType.TTS_INSTRUCTION)
            } else {
                app.player?.let { player ->
                    val text = app.getString(R.string.nautical_waypoint_reached)
                    player.playCommands(player.newCommandBuilder().attention(text))
                }
            }
        }
    }
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private val retryRunnable = Runnable { startEngine() }
    private var isAlertActive = false
    private var isBatteryAlertActive = false
    private var lastAutopilotState: String? = null
    private var lastConnectionStatus: ConnectionStatus? = null
    var nauticalMapLayer: NauticalMapLayer? = null
        private set
    var aisAisLayer: NauticalAisLayer? = null
        private set
    var skTideLayer: net.osmand.plus.plugins.nautical.view.SignalKTideLayer? = null
        private set
    var tidalCurrentsMapLayer: net.osmand.plus.plugins.nautical.tide.map.TidalCurrentsMapLayer? = null
        private set
    var oceanographicGribMapLayer: net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer? = null
        private set
    var tidalTimeOffsetMs: Long = 0L
        set(value) {
            field = value
            requestRefresh()
        }
    private val receiveInBackgroundPrefListener = StateChangedListener<Boolean> { state: Boolean? ->
        updateNauticalBackgroundService()
        if ((state != true) && (!app.settings.MAP_ACTIVITY_ENABLED)) {
            if (::connection.isInitialized) {
                connection.disconnect()
            }
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
    private val drEnabledListener = StateChangedListener<Long> { updateFeatureLifecycle() }
    private val navtexEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val aisEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val windyEnabledListener = StateChangedListener<Boolean> { requestRefresh() }
    
    private val aisOwnMmsiListener = StateChangedListener<Int> {
        aisAisLayer?.refreshOwnObjectVisibility()
    }
    
    private val aisDisplayOwnPositionListener = StateChangedListener<Boolean> {
        aisAisLayer?.refreshOwnObjectVisibility()
    }

    private val gribWavesEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val gribPressureEnabledListener = StateChangedListener<Boolean> { updateFeatureLifecycle() }
    private val nightVisionEnabledListener = StateChangedListener<Boolean> { enabled ->
        val activity = app.osmandMap?.mapView?.mapActivity
        if (activity != null && enabled != isNightVisionEnabled) {
            updateDisplayModeFromLegacy(enabled == true, app.settings.NAUTICAL_SUNLIGHT_MODE.get())
        }
    }

    private val sunlightModeListener = StateChangedListener<Boolean> { enabled ->
        updateDisplayModeFromLegacy(app.settings.NAUTICAL_NIGHT_VISION_ENABLED.get(), enabled == true)
    }

    private val displayModeListener = StateChangedListener<NauticalDisplayMode> { mode ->
        val activity = app.osmandMap?.mapView?.mapActivity
        if (activity != null) {
            applyDisplayMode(activity, mode ?: NauticalDisplayMode.NORMAL)
        }
    }

    private fun updateDisplayModeFromLegacy(night: Boolean, sunlight: Boolean) {
        val newMode = when {
            night -> NauticalDisplayMode.DARK
            sunlight -> NauticalDisplayMode.SUNLIGHT
            else -> NauticalDisplayMode.NORMAL
        }
        if (app.settings.NAUTICAL_DISPLAY_MODE.get() != newMode) {
            app.settings.NAUTICAL_DISPLAY_MODE.set(newMode)
        }
    }

    private val heavyWeatherEnabledListener = StateChangedListener<Boolean> { enabled ->
        val activity = app.osmandMap?.mapView?.mapActivity
        workflowManager?.onHeavyWeatherModeChanged(enabled ?: false, activity)
    }

    private var reconnectJob: Job? = null
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            debounceReconnect("Available: $network")
        }

        override fun onLost(network: android.net.Network) {
            debounceReconnect("Lost: $network")
        }

        private fun debounceReconnect(reason: String) {
            reconnectJob?.cancel()
            reconnectJob = pluginScope?.launch {
                delay(2.seconds)
                if (isActive) {
                    log.info("Nautical: Network interface changed ($reason). Executing debounced reconnect.")
                    reconnect()
                }
            }
        }
    }

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
    internal var speechHelper: ManeuverSpeechHelper? = null
    private var ttsHelper: ManeuverTtsHelper? = null
    private var presentationManager: NauticalPresentationManager? = null
    var safetyManager: NauticalSafetyManager? = null
        private set

    // Sailing Integration Components
    private var layerController: SailingMapLayerController? = null
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
    var polarConfigViewModel: net.osmand.plus.plugins.nautical.viewmodel.PolarConfigViewModel? = null
    var routingViewModel: RoutingViewModel? = null
        private set
    var workflowEngine: SailingWorkflowEngine? = null
        private set

    private var wearOsManager: WearOsNauticalManager? = null
    private var heartbeatHudView: HeartbeatHudView? = null
    private var workflowHeaderView: WorkflowHeaderView? = null
    private var tacticalHudView: TacticalHudView? = null
    private var healthHudView: HardwareHealthHudHeader? = null
    private var mobHeaderView: MobEmergencyHeaderView? = null
    private var drHeaderView: DrWarningHeaderView? = null
    private var navtexHudView: NavtexHudView? = null
    private var watchScheduleHudView: WatchScheduleHudView? = null
    private var startLineHudHeader: StartLineHudHeader? = null
    private var windTrendHudHeader: WindTrendHudHeader? = null
    private var anchorWatchHudView: AnchorWatchHudView? = null
    private var forwardWatchHudView: ForwardWatchHudView? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val watchedKeys = setOf(
            app.settings.NAUTICAL_VESSEL_DRAFT.id,
            app.settings.NAUTICAL_SAFETY_MARGIN.id,
            app.settings.NAUTICAL_SHOW_LAYLINES.id,
            app.settings.NAUTICAL_SHOW_TRAJECTORY.id,
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
            app.settings.NAUTICAL_RUDDER_GAIN.id,
            app.settings.NAUTICAL_COUNTER_RUDDER.id,
            app.settings.NAUTICAL_AUTO_TRIM.id,
            app.settings.NAUTICAL_FILTER_SENSITIVITY.id,
            app.settings.NAUTICAL_RUDDER_LIMIT.id,
            app.settings.NAUTICAL_OFF_COURSE_ALARM.id,
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
                    app.settings.NAUTICAL_RUDDER_GAIN.id,
                    app.settings.NAUTICAL_COUNTER_RUDDER.id,
                    app.settings.NAUTICAL_AUTO_TRIM.id,
                    app.settings.NAUTICAL_FILTER_SENSITIVITY.id,
                    app.settings.NAUTICAL_RUDDER_LIMIT.id,
                    app.settings.NAUTICAL_OFF_COURSE_ALARM.id -> autopilot?.pushAllSettings()
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
            
            nauticalMapLayer?.invalidateCache()
            layerController?.s57Layer?.clearCache()
            
            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, 300)
        }
    }

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable {
        val now = System.currentTimeMillis()
        
        // Critical Battery/Thermal Throttling
        val forceThrottle = isThrottlingRedraws || isBatteryLow || isPowerSaveMode
        if (forceThrottle) {
            if (now - lastForcedRefreshTime < 2000) {
                isRefreshScheduled = false
                return@Runnable // Extreme throttle to 0.5Hz
            }
            lastForcedRefreshTime = now
        }
        
        app.osmandMap?.refreshMap()
        lastRefreshTime = System.currentTimeMillis()
        isRefreshScheduled = false
    }

    private fun setupThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                handleThermalStatus(status)
            }
            thermalListener = listener
            pm.addThermalStatusListener(listener)
        }
    }

    private fun handleThermalStatus(status: Int) {
        Handler(Looper.getMainLooper()).post {
            val activity = app.osmandMap?.mapView?.mapActivity ?: return@post

            when (status) {
                PowerManager.THERMAL_STATUS_SEVERE -> {
                    isThrottlingRedraws = true
                    showThermalWarning(activity, true)
                    thermalWarningView?.setWarningText(R.string.nautical_thermal_throttle)
                }
                PowerManager.THERMAL_STATUS_CRITICAL -> {
                    isThrottlingRedraws = true
                    showThermalWarning(activity, true)
                    thermalWarningView?.setWarningText(R.string.nautical_thermal_warning)
                }
                else -> {
                    if (isThrottlingRedraws) {
                        isThrottlingRedraws = false
                        showThermalWarning(activity, false)
                    }
                }
            }
        }
    }

    private fun showThermalWarning(activity: MapActivity, show: Boolean) {
        if (show) {
            if (thermalWarningView == null) {
                thermalWarningView = ThermalWarningView(activity)
                hudManager?.get()?.addHeader(thermalWarningView!!, priority = 5) // High priority, just below MOB
            }
            thermalWarningView?.isVisible = true
        } else {
            thermalWarningView?.isVisible = false
        }
        hudManager?.get()?.updateLayout()
    }

    private fun setupCrashBlackBox() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                log.error("CRASH DETECTED. Executing Black Box Flush...", throwable)
                
                // Task 2: Robust Socket Cleanup & Kernel Lock Release
                val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, pluginScope)
                multiplexer.emergencyShutdown()

                val state = engine?.getCurrentState()
                if (state != null) {
                    val json = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("latitude", state.latitude ?: 0.0)
                        put("longitude", state.longitude ?: 0.0)
                        put("sog", state.speedOverGround ?: 0.0)
                        put("cog", state.courseOverGroundTrue ?: 0.0)
                        put("heading", state.headingTrue ?: 0.0)
                        put("autopilotMode", state.autopilotState)
                        put("stackTrace", android.util.Log.getStackTraceString(throwable))
                    }
                    val file = File(app.filesDir, "blackbox_crash.json")
                    FileOutputStream(file).use { it.write(json.toString().toByteArray()) }
                }
            } catch (_: Exception) {
                // Don't let the crash handler crash
            } finally {
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun processBlackBoxCrash() {
        val file = File(app.filesDir, "blackbox_crash.json")
        if (file.exists()) {
            pluginScope?.launch(Dispatchers.IO) {
                try {
                    val json = JSONObject(file.readText())
                    val entry = LogbookEntry(
                        timestamp = json.optLong("timestamp", TemporalUtils.now()),
                        latitude = json.optDouble("latitude", 0.0),
                        longitude = json.optDouble("longitude", 0.0),
                        sog = json.optDouble("sog", 0.0),
                        cog = json.optDouble("cog", 0.0),
                        heading = json.optDouble("heading", 0.0),
                        tws = null, twa = null, twd = null, pressure = null, 
                        waterDepth = null, waterTemp = null, batteryVoltage = null, engineHours = null,
                        notes = "CRASH RECOVERY: ${json.optString("autopilotMode", "UNKNOWN")}\n${json.optString("stackTrace", "")}"
                    )
                    logbookRepository?.insertEntrySync(entry)
                    file.delete()
                    log.info("Nautical: Processed and cleared blackbox crash log.")
                } catch (e: Exception) {
                    log.error("Nautical: Failed to process blackbox crash log", e)
                    file.delete()
                }
            }
        }
    }

    fun checkScreenAlwaysOn() {
        val activity = app.osmandMap?.mapView?.mapActivity ?: return
        val state = engine?.getCurrentState()
        val autopilotEngaged = state?.autopilotState?.let {
            val m = it.lowercase(Locale.US)
            m == "auto" || m == "wind" || m == "track" || m == "route"
        } ?: false
        val mobActive = state?.isMobActive == true
        
        app.runInUIThread {
            if (autopilotEngaged || mobActive) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else if (maneuverManager?.state == ManeuverState.IDLE) {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private var mobStateMachine: MobStateMachine? = null
    private val mobAudioAlertManager by lazy { MobAudioAlertManager(app) }
    private var safetyArbitrator: SafetyStateArbitrator? = null

    private val applicationModeListener = StateChangedListener<ApplicationMode> { 
        val activity = app.osmandMap?.mapView?.mapActivity
        if (activity != null) {
            updateLayers(app, activity)
            updateHudVisibility()
            
            // Force re-sync of custom render properties
            val props = listOf(
                "nautical_show_laylines",
                "nautical_show_wind_shifts",
                "nautical_raster_charts_opacity",
                "nautical_night_mode"
            )
            props.forEach { prop ->
                app.settings.getCustomRenderProperty(prop, "").let { pref ->
                    // Force value sync with native renderer by accessing it
                    val value = pref.get()
                    if (prop == "nautical_raster_charts_opacity") {
                        skRasterLayer?.setOpacity(value.toFloatOrNull() ?: 1.0f)
                    }
                    if (prop == "nautical_night_mode") {
                         pref.set(if (app.settings.NAUTICAL_DISPLAY_MODE.get() == NauticalDisplayMode.DARK) "true" else "false")
                    }
                }
            }
            requestRefresh()
        }
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
    private fun initPlugin() {
        if (isPluginInitialized) return
        isPluginInitialized = true
        NauticalLog.init(app)
        NauticalLog.i("Nautical Plugin Initializing...")
        
        if (pluginScope == null) {
            pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        val scope = pluginScope!!

        safetyArbitrator = SafetyStateArbitrator(app).apply { start() }
        if (capabilityManager == null) {
            capabilityManager = CapabilityManager(app)
        }
        if (logbookRepository == null) {
            logbookRepository = MarineLogbookRepository(app)
        }
        if (mobStateMachine == null) {
            mobStateMachine = MobStateMachine(logbookRepository, scope)
            restoreTacticalState(scope)
        }
        processBlackBoxCrash()

        scope.launch {
            NauticalEventBus.events.collect { event ->
                if (event is NauticalEvent.MobStateChanged) {
                    app.runInUIThread {
                        requestRefresh()
                    }
                }
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

    fun getSettings(): net.osmand.plus.settings.backend.OsmandSettings = app.settings

    private fun initConnection() {
        val trustAll = app.settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        var client = okHttpClient
        if ((client == null) || (lastUsedTrustAll != trustAll)) {
            client = createHttpClient(trustAll)
            okHttpClient = client
            lastUsedTrustAll = trustAll
            SailingDependencyContainer.setOkHttpClient(client)
        }
        connection = OkHttpSignalKConnection(client)
    }

    private fun createHttpClient(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(java.time.Duration.ofSeconds(5))
        builder.readTimeout(java.time.Duration.ofSeconds(10))
        builder.writeTimeout(java.time.Duration.ofSeconds(10))
        builder.pingInterval(java.time.Duration.ofSeconds(30))

        val factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        val defaultTrustManager = factory.trustManagers[0] as javax.net.ssl.X509TrustManager

        val trustManager = if (trustAll) {
            log.warn("Nautical: Using trust-all SSL configuration. Security is reduced.")
            NauticalTrustManager(defaultTrustManager, log)
        } else {
            defaultTrustManager
        }

        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)

        if (trustAll) {
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    @SuppressLint("CustomX509TrustManager")
    private class NauticalTrustManager(
        private val delegate: javax.net.ssl.X509TrustManager,
        private val log: org.apache.commons.logging.Log
    ) : javax.net.ssl.X509TrustManager {

        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            try {
                delegate.checkClientTrusted(chain, authType)
            } catch (ex: java.security.cert.CertificateException) {
                log.warn("Nautical: Trusting untrusted client certificate: ${ex.message}")
            }
        }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            try {
                delegate.checkServerTrusted(chain, authType)
            } catch (ex: java.security.cert.CertificateException) {
                // If we reach here, the certificate is not trusted by the system.
                // We allow it only because the user explicitly enabled 'trustAll'.
                if (chain.isNotEmpty()) {
                    try {
                        // Still check for basic expiration/validity
                        chain[0].checkValidity()
                    } catch (ce: java.security.cert.CertificateExpiredException) {
                        log.error("Nautical: Certificate expired", ce)
                        throw ce
                    } catch (cn: java.security.cert.CertificateNotYetValidException) {
                        log.error("Nautical: Certificate not yet valid", cn)
                        throw cn
                    } catch (e: Exception) {
                        log.error("Nautical: Certificate check failed", e)
                        throw java.security.cert.CertificateException(e)
                    }
                } else {
                    throw ex
                }
            }
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = delegate.acceptedIssuers
    }

    override fun addMyPlacesTab(myPlacesActivity: net.osmand.plus.myplaces.MyPlacesActivity, mTabs: MutableList<net.osmand.plus.activities.TabActivity.TabItem>, intent: Intent) {
        if (app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT) {
            mTabs.add(myPlacesActivity.getTabIndicator(R.string.logbook_title, net.osmand.plus.plugins.nautical.ui.logbook.MarineLogbookFragment::class.java))
        }
    }

    override fun getQuickActionTypes(): List<QuickActionType> {
        val actions = mutableListOf<QuickActionType>()
        actions.add(NauticalMobQuickAction.TYPE)
        actions.add(NauticalAnchorQuickAction.TYPE)
        actions.add(NauticalNightVisionQuickAction.TYPE)
        return actions
    }

    override fun mapActivityDestroy(activity: MapActivity) {
        // Task 8.0AF: Strict cleanup of window flags to prevent zombie screen-on state
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        layerController?.unregisterLayers()
        layerController = null
        
        hudManager?.get()?.onDestroy()
        hudManager = null
    }

    override fun createMapWidgetForParams(mapActivity: MapActivity, widgetType: WidgetType, customId: String?, widgetsPanel: WidgetsPanel?): MapWidget? {
        // Module Gatekeeping
        if (!isWidgetAllowed(widgetType)) return null

        return when (widgetType) {
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
            WidgetType.NAUTICAL_MAG_VARIATION,
            WidgetType.NAUTICAL_YAW,
            WidgetType.NAUTICAL_CPA,
            WidgetType.NAUTICAL_TCPA,
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT,
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED,
            WidgetType.NAUTICAL_ILLUMINANCE,
            WidgetType.NAUTICAL_RANGE,
            WidgetType.NAUTICAL_GNSS_QUALITY,
            WidgetType.NAUTICAL_HUMIDITY,
            WidgetType.NAUTICAL_MOON_PHASE,
            WidgetType.NAUTICAL_SALINITY,
            WidgetType.NAUTICAL_DEW_POINT,
            WidgetType.NAUTICAL_AC_VOLTAGE,
            WidgetType.NAUTICAL_AC_CURRENT,
            WidgetType.NAUTICAL_AC_FREQUENCY,
            WidgetType.NAUTICAL_VHF_CHANNEL,
            WidgetType.NAUTICAL_RIGGING_LOAD,
        -> MarineTextWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_CAMERA -> NauticalCameraWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_ELECTRICAL -> NauticalElectricalWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MOB -> NauticalMobWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_VHF -> NauticalVhfWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_DEPTH,
            WidgetType.NAUTICAL_WIND,
        -> NauticalGraphWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_NIGHT_VISION -> NauticalDisplayModeWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_PILOT -> NauticalPilotWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_ACTUATOR -> ActuatorLoadWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_COMPASS -> NauticalCompassWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.MANEUVER_OVERLAY -> {
                maneuverManager?.let {
                    ManeuverOverlayWidget(mapActivity, it, widgetType, customId, widgetsPanel)
                }
            }
            WidgetType.NAUTICAL_POLAR_RATIO -> PolarSpeedRatioWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_VMG -> TargetVmgWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_TELLTALE -> NauticalTelltaleWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_FLAGS -> NauticalFlagsWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MASTER_TELEMETRY -> NauticalMasterTelemetryWidget(mapActivity, widgetType, customId, widgetsPanel)
            else -> null
        }
    }

    override fun createWidgets(
        activity: MapActivity,
        widgetInfos: MutableList<MapWidgetInfo>,
        appMode: ApplicationMode,
        layoutMode: ScreenLayoutMode?,
    ) {
        if (appMode != ApplicationMode.BOAT) return
        
        createMapWidgetForParams(activity, WidgetType.MANEUVER_OVERLAY, null, WidgetsPanel.BOTTOM)?.let { widget ->
            widgetInfos.add(
                object : MapWidgetInfo(
                    WidgetType.MANEUVER_OVERLAY.id, widget, 0, 0, R.string.maneuver_overlay, null, 0, 0, WidgetsPanel.BOTTOM,
                ) {
                    override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel {
                        return WidgetsPanel.BOTTOM
                    }
                }
            )
        }

        createMapWidgetForParams(activity, WidgetType.NAUTICAL_POLAR_RATIO, null, WidgetsPanel.RIGHT)?.let { polarRatioWidget ->
            widgetInfos.add(object : MapWidgetInfo(
                WidgetType.NAUTICAL_POLAR_RATIO.id, polarRatioWidget, 0, 0, R.string.nautical_polar_ratio, null, 0, 0, WidgetsPanel.RIGHT
            ) {
                override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel = widgetPanel
            })
        }

        createMapWidgetForParams(activity, WidgetType.NAUTICAL_VMG, null, WidgetsPanel.RIGHT)?.let { targetVmgWidget ->
            widgetInfos.add(object : MapWidgetInfo(WidgetType.NAUTICAL_VMG.id, targetVmgWidget, 0, 0, R.string.nautical_widget_vmg_label, null, 0, 0, WidgetsPanel.RIGHT) {
                override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel = widgetPanel
            })
        }

        createMapWidgetForParams(activity, WidgetType.NAUTICAL_ELECTRICAL, null, WidgetsPanel.RIGHT)?.let { electricalWidget ->
            widgetInfos.add(object : MapWidgetInfo(WidgetType.NAUTICAL_ELECTRICAL.id, electricalWidget, 0, 0, R.string.nautical_switches_label, null, 0, 0, WidgetsPanel.RIGHT) {
                override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel = widgetPanel
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
        isAppInBackground = false
        applyPowerThrottling()
        onAppForegrounded()
        if (!::connection.isInitialized || !connection.isConnected()) {
            startEngine()
        }
        updateNauticalBackgroundService()
        presentationManager?.onResume(activity)
        if (app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()) {
            AndroidUtils.requestNotificationPermissionIfNeeded(activity)
        }

        // TASK-047: Screen Touch Lock Integration
        val mapLayersView = activity.findViewById<View>(R.id.MapLayersView)
        val legacyMapView = activity.findViewById<View>(R.id.MapView)
        val touchListener = View.OnTouchListener { v, event ->
            if (workflowManager?.getScreenTouchLockManager()?.interceptTouchEvent(event) == true) {
                true
            } else if (event.pointerCount > 1) {
                false // PASS THROUGH for multi-touch (pinch/zoom)
            } else {
                v.performClick()
                false // PASS THROUGH for native map gestures
            }
        }
        mapLayersView?.setOnTouchListener(touchListener)
        legacyMapView?.setOnTouchListener(touchListener)

        app.keyEventHelper.setExternalCallback(object : android.view.KeyEvent.Callback {
            private var volUpPressTime = 0L

            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
                if (app.settings.APPLICATION_MODE.get() != ApplicationMode.BOAT) return false
                val manager = maneuverManager ?: return false
                
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (event.repeatCount == 0) {
                            volUpPressTime = System.currentTimeMillis()
                        } else if (volUpPressTime != 0L && (System.currentTimeMillis() - volUpPressTime > 1500)) {
                            val loc = app.locationProvider.lastKnownLocation
                            if (loc != null) {
                                mobViewModel?.triggerMob(net.osmand.data.LatLon(loc.latitude, loc.longitude), net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource.BUTTON)
                                app.showToastMessage(R.string.nautical_mob_label)
                                
                                // TASK-301: Proper non-deprecated vibration handling
                                val vibrator = app.getSystemService(Vibrator::class.java)
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(500)
                                    }
                                }
                                volUpPressTime = 0L 
                            }
                        }
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (event.repeatCount == 0) {
                             val ackSuccessful = if (isConnectionLostAlertActive) {
                                 stopConnectionLostAudioLoop()
                                 isConnectionLostAlertActive = false
                                 true
                             } else {
                                 val engine = engine
                                 val currentAlarms = engine?.getCurrentState()?.notifications ?: emptyMap()
                                 if (currentAlarms.isNotEmpty()) {
                                     // Acknowledge the most severe active alarm
                                     val highestPath = currentAlarms.entries.maxByOrNull { it.value.state }?.key
                                     highestPath?.let { path ->
                                         engine?.acknowledgeNotification(path)
                                         true
                                     } ?: false
                                 } else {
                                     false
                                 }
                             }
                             
                             if (ackSuccessful) {
                                 app.showToastMessage(R.string.nautical_alarm_acknowledged)
                                 return true
                             }
                             
                             if (manager.state == ManeuverState.EXECUTING) {
                                 manager.abort("Hardware Button Abort")
                                 return true
                             }
                        }
                        return false
                    }
                }
                return false
            }

            override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent): Boolean = false
            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
                if (app.settings.APPLICATION_MODE.get() != ApplicationMode.BOAT) return false
                volUpPressTime = 0L
                return keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            }
            override fun onKeyMultiple(keyCode: Int, count: Int, event: android.view.KeyEvent): Boolean = false
        })
    }

    override fun mapActivityPause(activity: MapActivity) {
        isAppInBackground = true
        applyPowerThrottling()
        
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onAppBackgrounded()
        
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
            if (layerController == null) {
                registerLayers(context, activity)
            } else {
                nauticalMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 5.0f) }
                
                if (isModuleEnabled(NauticalModule.AIS)) {
                    aisAisLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 3.5f) }
                } else {
                    aisAisLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.TIDES)) {
                    skTideLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.6f) }
                    tidalCurrentsMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.5f) }
                } else {
                    skTideLayer?.let { mapView.removeLayer(it) }
                    tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.GRIB)) {
                    oceanographicGribMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.0f) }
                } else {
                    oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.VHF)) {
                    vhfPoiLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.8f) }
                } else {
                    vhfPoiLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.RASTER)) {
                    skRasterLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.2f) }
                } else {
                    skRasterLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.LOGBOOK)) {
                    skLogbookLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.9f) }
                } else {
                    skLogbookLayer?.let { mapView.removeLayer(it) }
                }

                layerController?.updateLayerVisibility()
            }
        } else {
            nauticalMapLayer?.let { mapView.removeLayer(it) }
            aisAisLayer?.let { mapView.removeLayer(it) }
            skTideLayer?.let { mapView.removeLayer(it) }
            vhfPoiLayer?.let { mapView.removeLayer(it) }
            tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
            oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
            layerController?.unregisterLayers()
        }
    }

    override fun registerLayers(context: Context, mapActivity: MapActivity?) {
        if (isActive && (mapActivity != null)) {
            val mapView = mapActivity.mapView
            if (nauticalMapLayer == null) {
                nauticalMapLayer = NauticalMapLayer(app)
                mapView.addLayer(nauticalMapLayer!!, 5.0f)
            }
            if (aisAisLayer == null) {
                aisAisLayer = NauticalAisLayer(context)
                mapView.addLayer(aisAisLayer!!, 3.5f)
            }
            if (skTideLayer == null) {
                skTideLayer = net.osmand.plus.plugins.nautical.view.SignalKTideLayer(context)
                mapView.addLayer(skTideLayer!!, 4.6f)
            }
            if (tidalCurrentsMapLayer == null) {
                tidalCurrentsMapLayer = net.osmand.plus.plugins.nautical.tide.map.TidalCurrentsMapLayer(app)
                mapView.addLayer(tidalCurrentsMapLayer!!, 4.5f)
            }
            if (oceanographicGribMapLayer == null) {
                oceanographicGribMapLayer = net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer(app)
                mapView.addLayer(oceanographicGribMapLayer!!, 4.0f)
            }

            if (vhfPoiLayer == null) {
                vhfPoiLayer = VhfPoiSearchLayer(mapActivity)
                mapView.addLayer(vhfPoiLayer!!, 4.8f)
            }
            if (skRasterLayer == null) {
                skRasterLayer = net.osmand.plus.plugins.nautical.raster.SignalKRasterLayer(mapActivity)
                mapView.addLayer(skRasterLayer!!, 4.2f)
            }
            if (skLogbookLayer == null) {
                skLogbookLayer = SignalKLogbookLayer(mapActivity)
                mapView.addLayer(skLogbookLayer!!, 4.9f)
            }

            val controller = SailingMapLayerController(mapActivity, s57SpatialIndex)
            controller.registerLayers()
            layerController = controller

            initMobSystem(mapActivity, controller)
            initDrSystem(mapActivity, controller)
            initLaylineSystem(mapActivity, controller)
            initNavtexSystem(mapActivity, controller)
            initEnvironmentSystem(mapActivity)
            initWatchScheduleSystem(mapActivity)
            initWorkflowSystem(mapActivity)
            initTacticalHudSystem(mapActivity)
            initForwardWatchSystem(mapActivity)
        }
    }

    private fun initForwardWatchSystem(activity: MapActivity) {
        hudManager?.get()?.removeHeader(forwardWatchHudView)
        val hud = ForwardWatchHudView(activity)
        this.forwardWatchHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 10) // Very high priority, just below MOB

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine?.marineStateFlow?.collect { state ->
                    hud.updateHazards(state.forwardHazards)
                    hudManager?.get()?.updateLayout()
                }
            }
        }
    }

    private var environmentHud: NauticalEnvironmentWidgetView? = null

    private fun initEnvironmentSystem(activity: MapActivity) {
        hudManager?.get()?.removeHeader(environmentHud)
        val hud = NauticalEnvironmentWidgetView(activity)
        this.environmentHud = hud
        hudManager?.get()?.addHeader(hud, priority = 350)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine?.marineStateFlow?.collect { state ->
                    hud.updateState(state)
                    hud.isVisible = (state.outsideHumidity != null || state.moonPhase != null)
                    hudManager?.get()?.updateLayout()
                }
            }
        }
    }

    private fun initWatchScheduleSystem(activity: MapActivity) {
        hudManager?.get()?.removeHeader(watchScheduleHudView)
        val hud = WatchScheduleHudView(activity)
        this.watchScheduleHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 500) // Lower priority

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine?.marineStateFlow?.collect { state ->
                    hud.updateState(state)
                    // Only show if we have actual watch data
                    hud.isVisible = state.pathMeta.containsKey("communication.crew.watch.current")
                    hudManager?.get()?.updateLayout()
                }
            }
        }
    }

    private fun initWorkflowSystem(activity: MapActivity) {
        val workflowEng = workflowEngine ?: return
        hudManager?.get()?.removeHeader(workflowHeaderView)
        hudManager?.get()?.removeHeader(tacticalHudView)
        hudManager?.get()?.removeHeader(healthHudView)
        hudManager?.get()?.removeHeader(heartbeatHudView)

        if (wearOsManager?.isWatchMode() == true) {
            val hb = HeartbeatHudView(activity)
            this.heartbeatHudView = hb
            hudManager?.get()?.addHeader(hb, priority = 50)
            
            wearOsManager?.isAmbientMode?.onEach { ambient ->
                app.runInUIThread {
                    hb.setAmbientMode(ambient)
                }
            }?.launchIn(activity.lifecycleScope)
            
            engine?.registerListener { state: MarineState ->
                app.runInUIThread {
                    hb.updateState(state)
                    hudManager?.get()?.updateLayout()
                }
            }
            return // Skip standard complex headers on watch
        }

        val wh = WorkflowHeaderView(activity)
        wh.setEngine(workflowEng)
        this.workflowHeaderView = wh
        hudManager?.get()?.addHeader(wh, priority = 300)

        val th = TacticalHudView(activity)
        this.tacticalHudView = th
        hudManager?.get()?.addHeader(th, priority = 150)

        val hh = HardwareHealthHudHeader(activity)
        this.healthHudView = hh
        hudManager?.get()?.addHeader(hh, priority = 400) // Lower priority (bottom of stack)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    workflowEng.currentWorkflow.collect { state ->
                        th.isVisible = (state == SailingWorkflowState.CLOSE_QUARTERS)
                        hudManager?.get()?.updateLayout()
                    }
                }
                launch {
                    workflowEng.pendingWorkflowFlow.collect { state ->
                        if (state != null) {
                            wh.showProposal(state)
                        } else {
                            wh.isVisible = false
                        }
                        hudManager?.get()?.updateLayout()
                    }
                }
                launch {
                    engine?.marineStateFlow?.collect { state ->
                        th.updateState(state)
                        hh.updateState(state, connection.getLatencyMs())
                    }
                }
            }
        }
    }

    private fun updateHudVisibility() {
        val isBoat = app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT
        hudManager?.get()?.setVisible(isBoat)
    }


    private fun initNavtexSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val repo = net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository(app)
        val viewModel = NavtexViewModel(app, repo)
        this.navtexViewModel = viewModel

        hudManager?.get()?.removeHeader(navtexHudView)
        val hud = NavtexHudView(activity)
        this.navtexHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 200)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    hud.updateState(state)
                    controller.navtexLayer.updateState(state)
                    hudManager?.get()?.updateLayout()
                    requestRefresh()
                }
            }
        }
    }

    private fun initLaylineSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val perfRepo = SailingDependencyContainer.performanceRepository ?: return
        val viewModel = LaylineViewModel(
            app,
            perfRepo,
            SailingDependencyContainer.gribRepository
        )
        this.laylineViewModel = viewModel

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    controller.laylinesLayer.updateState(state)
                    requestRefresh()
                }
            }
        }
    }

    private fun initDrSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val perfRepo = SailingDependencyContainer.performanceRepository ?: return
        val viewModel = DeadReckoningViewModel(app, perfRepo)
        this.drViewModel = viewModel

        hudManager?.get()?.removeHeader(drHeaderView)
        val header = DrWarningHeaderView(activity)
        this.drHeaderView = header
        hudManager?.get()?.addHeader(header, priority = 100)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    header.updateState(state)
                    controller.drLayer.updateState(state)
                    hudManager?.get()?.updateLayout()
                    requestRefresh()
                }
            }
        }
    }

    private fun initTacticalHudSystem(activity: MapActivity) {
        hudManager?.get()?.removeHeader(startLineHudHeader)
        hudManager?.get()?.removeHeader(windTrendHudHeader)
        hudManager?.get()?.removeHeader(anchorWatchHudView)

        val sl = StartLineHudHeader(activity)
        this.startLineHudHeader = sl
        hudManager?.get()?.addHeader(sl, priority = 200)

        val wt = WindTrendHudHeader(activity)
        this.windTrendHudHeader = wt
        hudManager?.get()?.addHeader(wt, priority = 250)
        
        val aw = AnchorWatchHudView(activity)
        this.anchorWatchHudView = aw
        hudManager?.get()?.addHeader(aw, priority = 260)
    }

    private fun initMobSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val sm = mobStateMachine ?: return
        val viewModel = MobViewModel(app, sm, mobAudioAlertManager)
        this.mobViewModel = viewModel

        hudManager?.get()?.removeHeader(mobHeaderView)
        val header = MobEmergencyHeaderView(activity)
        header.setViewModel(viewModel)
        this.mobHeaderView = header
        hudManager?.get()?.addHeader(header, priority = 0) // MOB always at top

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    header.updateState(state)
                    controller.mobLayer.updateState(state)
                    hudManager?.get()?.updateLayout()
                    requestRefresh()

                    // Screen awake logic
                    if (state.isMobActive) {
                        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    private var lastIpToastTime = 0L

    private val locationListener = net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener { location ->
        engine?.onInternalLocationUpdate(location)
    }

    fun updateNmeaSource() {
        val source = app.settings.NAUTICAL_NMEA_SOURCE.get()
        val navtexRepo = net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository(app)
        val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, pluginScope!!, navtexRepo)
        multiplexer.stopAll()

        when (source) {
            net.osmand.plus.settings.enums.NmeaSource.SIGNALK -> {
                // Connection initiated via startEngine() separately if needed, 
                // or we call it here but ensure no double calls.
                startEngine()
            }
            net.osmand.plus.settings.enums.NmeaSource.BLUETOOTH -> {
                val address = app.settings.NAUTICAL_BT_DEVICE_ADDRESS.get()
                if (address.isNotEmpty()) {
                    val client = net.osmand.plus.plugins.nautical.nmea.connection.BluetoothNmeaClient(address, pluginScope!!)
                    multiplexer.start(client)
                }
            }
            net.osmand.plus.settings.enums.NmeaSource.USB -> {
                val deviceName = app.settings.NAUTICAL_USB_DEVICE_NAME.get()
                val usbManager = app.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                val device = usbManager.deviceList[deviceName]
                if (device != null) {
                    val baud = app.settings.NAUTICAL_NMEA_BAUD_RATE.get()
                    val client = net.osmand.plus.plugins.nautical.nmea.connection.UsbNmeaClient(app, device, baud, pluginScope!!)
                    multiplexer.start(client)
                }
            }
            net.osmand.plus.settings.enums.NmeaSource.TCP -> {
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
            // startEngine() is called inside updateNmeaSource() for SIGNALK
            // For other sources, if we still need SignalK (e.g. for telemetry that NMEA doesn't provide), 
            // we might call it, but updateNmeaSource should be the master.
        }
    }

    /**
     * Purges Signal K buffers, GRIB cache, and S-63 temporary files from device storage.
     * Triggered by "Clear Marine Data" or significant chart directory changes.
     */
    fun clearMarineData() {
        pluginScope?.launch(Dispatchers.IO) {
            try {
                log.info("Nautical: Starting Marine Data cleanup...")
                // 1. Signal K Historical Buffers
                engine?.clearBuffers(app)
                
                // 2. GRIB Repository Cache
                SailingDependencyContainer.gribRepository?.cleanup()
                val gribDir = File(app.getAppPath(""), "nautical/grib")
                if (gribDir.exists()) {
                    gribDir.deleteRecursively()
                }

                // 3. S-63 and KAP decrypted fragments
                net.osmand.plus.plugins.nautical.s63.crypto.S63Decryptor.cleanup(app)
                // We only delete temporary or cache files here, not imported charts themselves unless poison is suspected.
                // For now, clean standard temp dirs.
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
        val isBoat = app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT
        val activity = app.osmandMap?.mapView?.mapActivity

        if (!isActive || !isBoat) {
            stopAllFeatures()
            return
        }

        // Laylines
        if (app.settings.NAUTICAL_SHOW_LAYLINES.get()) {
            if (laylineViewModel == null && activity != null) {
                initLaylineSystem(activity, layerController!!)
            }
        } else {
            laylineViewModel?.clear()
            laylineViewModel = null
            layerController?.laylinesLayer?.updateState(net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineUiState())
        }

        // Dead Reckoning
        if (app.settings.NAUTICAL_DR_START_TIME.get() != 0L) {
            if (drViewModel == null && activity != null) {
                initDrSystem(activity, layerController!!)
            }
        } else {
            drViewModel?.clear()
            drViewModel = null
            hudManager?.get()?.removeHeader(drHeaderView)
            drHeaderView = null
        }

        // NAVTEX
        if (app.settings.NAUTICAL_NAVTEX_ENABLED.get()) {
            if (navtexViewModel == null && activity != null) {
                initNavtexSystem(activity, layerController!!)
            }
        } else {
            stopNavtex()
        }

        // AIS
        if (app.settings.NAUTICAL_AIS_ENABLED.get()) {
            if (aisManager == null) {
                aisManager = NauticalAisManager(app)
                aisManager?.startUpdates()
            }
            engine?.registerAisListener { target: AisObject ->
                // Use a more direct sync of fields.
                aisManager?.onAisObjectReceived(target)
            }
        } else {
            clearAisLayer()
            aisManager?.stopUpdates()
            aisManager = null
        }

        // MOB
        if (app.settings.NAUTICAL_MOB_ACTIVE.get()) {
            if (mobViewModel == null && activity != null) {
                initMobSystem(activity, layerController!!)
            }
        } else {
            // Keep mobViewModel if it was active to allow disengage? 
            // Usually MOB is controlled by its own state machine.
        }

        layerController?.updateLayerVisibility()
        app.osmandMap?.refreshMap()
    }

    fun stopNavtex() {
        navtexViewModel?.clear()
        navtexViewModel = null
        hudManager?.get()?.removeHeader(navtexHudView)
        navtexHudView = null
    }

    fun clearAisLayer() {
        engine?.registerAisListener(null)
        aisManager?.cleanupResources()
    }

    private fun stopAllFeatures() {
        stopNavtex()
        clearAisLayer()
        aisManager?.stopUpdates()
        aisManager = null
        laylineViewModel = null
        drViewModel = null
        mobAudioAlertManager.stopAlarm()
        alarmPriorityManager?.stop()
        alarmPriorityManager = null
        engine?.stop()
        pluginScope?.let { SailingDependencyContainer.getNmeaMultiplexer(app, it).stopAll() }
        
        hudManager?.get()?.removeAllHeaders()
        hudManager?.get()?.setVisible(false)
        
        mobHeaderView = null
        drHeaderView = null
        workflowHeaderView = null
        tacticalHudView = null
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val osmandMap = app.osmandMap
        val mapView = osmandMap?.mapView
        val mapActivity = mapView?.mapActivity

        if (enabled) {
            instanceRef = WeakReference(this)
            initPlugin()
            val scope = pluginScope!!

            // Sailing Integration Init
            SailingDependencyContainer.performanceRepository?.fetchPolars()
            val indexManager = S57SpatialIndex(app)
            s57SpatialIndex = indexManager
            scope.launch {
                indexManager.indexCharts()
            }
            routingViewModel = RoutingViewModel()
            app.settings.APPLICATION_MODE.addListener(applicationModeListener)

            scope.launch {
                // Background initialization
                withContext(Dispatchers.IO) {
                    if (!::connection.isInitialized) {
                        initConnection()
                    }

                    val ip = app.settings.NAUTICAL_SERVER_IP.get()
                    val port = app.settings.NAUTICAL_SERVER_PORT.get()
                    if (!ip.isNullOrEmpty()) {
                        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
                        val rest = SignalKRestService.create("$protocol://$ip:$port", okHttpClient!!)
                        capabilityManager?.probe(rest)
                    }

                    if (safetyManager == null) {
                        safetyManager = NauticalSafetyManager.getInstance(app)
                    }

                    if (engine == null) {
                        val newEngine = SignalKEngine(app, scope, capabilityManager)
                        newEngine.addRouteStepListener(routeStepListener)
                        newEngine.deltaSender = { delta -> connection.sendDelta(delta) }
                        engine = newEngine
                        newEngine.loadBuffersFromDisk(app)
                        
                        val alarmManager = AlarmPriorityManager(app, newEngine.dataBroker)
                        alarmPriorityManager = alarmManager
                        
                        // Observe Alarms
                        scope.launch {
                            launch {
                                alarmManager.isCollisionAlarmActive.collect { active ->
                                    if (active) {
                                        val details = alarmManager.threatDetails.value
                                        hudManager?.get()?.showBanner(
                                            app.getString(R.string.nautical_collision_alert) + ": " + (details?.vesselName ?: ""),
                                            0, // Persistent
                                            isWarning = true
                                        )
                                    } else {
                                        hudManager?.get()?.hideBanner()
                                    }
                                }
                            }
                            launch {
                                alarmManager.activeCriticalNotifications.collect { notifications ->
                                    if (notifications.isNotEmpty() && !alarmManager.isCollisionAlarmActive.value) {
                                        val first = notifications.values.first()
                                        hudManager?.get()?.showBanner(first.message, 5000, isWarning = true)
                                    }
                                }
                            }
                        }
                    }

                    val currentEngine = engine
                    if (autopilot == null && currentEngine != null) {
                        okHttpClient?.let { client ->
                            val ap = AutopilotController(app, connection, client, currentEngine.dataBroker)
                            autopilot = ap
                            electrical = ElectricalController(app, ap)
                            
                            SailingDependencyContainer.initialize(app, currentEngine.dataBroker, ap, client)
                            currentEngine.environmentalFilterService = SailingDependencyContainer.environmentalFilterService
                        }
                    }

                    if (autopilotManager == null) {
                        okHttpClient?.let { client ->
                            engine?.dataBroker?.let { broker ->
                                autopilotManager = AutopilotManager(app, client, broker)
                            }
                        }
                    }

                    if (workflowEngine == null) {
                        workflowEngine = SailingWorkflowEngine(app, engine!!.dataBroker)
                    }

                    if (workflowManager == null) {
                        workflowManager = NauticalWorkflowManager(app)
                    }

                    if (presentationManager == null) {
                        presentationManager = NauticalPresentationManager(app)
                    }

                    if (aisManager == null) {
                        aisManager = NauticalAisManager(app)
                        aisManager?.startUpdates()
                    }

                    if (locationProvider == null) locationProvider = NauticalLocationProvider(app, engine)
                    if (tideManager == null) {
                        tideManager = SignalKTideManager(app, scope)
                        tideManager?.start()
                    }
                    if (vhfManager == null) {
                        vhfManager = NauticalVhfManager(app)
                    }
                    vhfManager?.start()

                    if (anchorWatchdog == null) {
                        anchorWatchdog = AnchorDriftWatchdog(app)
                        if (app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0) {
                            anchorWatchdog?.start()
                        }
                    }

                    if (maneuverManager == null) {
                        val mm = ManeuverManager(app)
                        mm.registerManeuver("anchoring", AnchoringManeuver(app))
                        mm.registerManeuver("docking", DockingManeuver(app))
                        mm.registerManeuver("gybing", GybingManeuver(app))
                        mm.registerManeuver("heaving_to", HeavingToManeuver(app))
                        mm.registerManeuver("man_overboard", ManOverboardManeuver(app))
                        mm.registerManeuver("mooring", MooringManeuver(app))
                        mm.registerManeuver("med_mooring", MedMooringManeuver(app))
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
                        tacticalProcessor = TacticalProcessor(app)
                        tacticalStartManager = TacticalStartManager(app)
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

                    if (logbookEngine == null) {
                        val repo = logbookRepository!!
                        val signalK = engine!!
                        val perfRepo = SailingDependencyContainer.performanceRepository
                        if (perfRepo != null) {
                            logbookEngine = AutomatedLogbookEngine(app, repo, signalK, perfRepo)
                            notificationManager = NauticalNotificationManager(app, repo)
                        }
                    }
                    logbookEngine?.start()

                    if (skDiscovery == null) {
                        skDiscovery = SignalKDiscovery(app)
                        skDiscovery?.start()
                    }
                }

                mapActivity?.let {
                    hudManager = WeakReference(NauticalHudManager(it))
                    wearOsManager = WearOsNauticalManager(it)
                    registerLayers(app, it)
                }

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

            engine?.registerListener(marineStateListener)
            engine?.registerAisListener { target: AisObject ->
                aisManager?.onAisObjectReceived(target)
            }
            app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.addListener(receiveInBackgroundPrefListener)
            app.settings.enabledPluginsPreference.addListener(enabledPluginsListener)
            app.locationProvider.addLocationListener(locationListener)

            // Feature Listeners
            app.settings.NAUTICAL_SHOW_LAYLINES.addListener(laylinesEnabledListener)
            app.settings.NAUTICAL_MOB_ACTIVE.addListener(mobActiveListener)
            app.settings.NAUTICAL_DR_START_TIME.addListener(drEnabledListener)
            app.settings.NAUTICAL_NAVTEX_ENABLED.addListener(navtexEnabledListener)
            app.settings.NAUTICAL_AIS_ENABLED.addListener(aisEnabledListener)
            app.settings.NAUTICAL_AIS_OWN_MMSI.addListener(aisOwnMmsiListener)
            app.settings.NAUTICAL_AIS_DISPLAY_OWN_POSITION.addListener(aisDisplayOwnPositionListener)
            app.settings.NAUTICAL_SHOW_GRIB_WAVES.addListener(gribWavesEnabledListener)
            app.settings.NAUTICAL_SHOW_GRIB_PRESSURE.addListener(gribPressureEnabledListener)
            app.settings.NAUTICAL_SHOW_WINDY_TILES.addListener(windyEnabledListener)
            app.settings.NAUTICAL_NIGHT_VISION_ENABLED.addListener(nightVisionEnabledListener)
            app.settings.NAUTICAL_SUNLIGHT_MODE.addListener(sunlightModeListener)
            app.settings.NAUTICAL_DISPLAY_MODE.addListener(displayModeListener)
            app.settings.NAUTICAL_HEAVY_WEATHER_MODE.addListener(heavyWeatherEnabledListener)
            app.daynightHelper.setExternalMapThemeProvider(this@NauticalPlugin)

            app.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefChangeListener)

            updateNmeaSource()
            locationProvider?.start()
            updateNauticalBackgroundService()
            updateFeatureLifecycle()

            checkBatteryOptimization()

                val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            try {
                app.unregisterReceiver(screenStateReceiver)
                app.unregisterReceiver(powerReceiver)
            } catch (_: Exception) {
            }
            app.registerReceiver(screenStateReceiver, filter)
            app.registerReceiver(powerReceiver, filter)

                if (app.settings.NAUTICAL_NIGHT_VISION_ENABLED.get()) {
                    mapActivity?.let { 
                        toggleNightVision(enable = true)
                        presentationManager?.setNightMode(true)
                    }
                }

                setupThermalListener()
                setupCrashBlackBox()
                registerNetworkCallback()
                checkScreenAlwaysOn()

                if (!app.settings.NAUTICAL_SETUP_WIZARD_COMPLETED.get()) {
                    mapActivity?.let {
                        NauticalSetupWizardDialog.show(it.supportFragmentManager)
                    }
                }
            }
        } else {
            instanceRef = null
            unregisterNetworkCallback()
            app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.removeListener(receiveInBackgroundPrefListener)
            app.settings.enabledPluginsPreference.removeListener(enabledPluginsListener)
            app.settings.NAUTICAL_XTE_THRESHOLD.removeListener(xteThresholdListener)
            app.locationProvider.removeLocationListener(locationListener)

            // Remove Feature Listeners
            app.settings.NAUTICAL_SHOW_LAYLINES.removeListener(laylinesEnabledListener)
            app.settings.NAUTICAL_MOB_ACTIVE.removeListener(mobActiveListener)
            app.settings.NAUTICAL_DR_START_TIME.removeListener(drEnabledListener)
            app.settings.NAUTICAL_NAVTEX_ENABLED.removeListener(navtexEnabledListener)
            app.settings.NAUTICAL_AIS_ENABLED.removeListener(aisEnabledListener)
            app.settings.NAUTICAL_AIS_OWN_MMSI.removeListener(aisOwnMmsiListener)
            app.settings.NAUTICAL_AIS_DISPLAY_OWN_POSITION.removeListener(aisDisplayOwnPositionListener)
            app.settings.NAUTICAL_SHOW_GRIB_WAVES.removeListener(gribWavesEnabledListener)
            app.settings.NAUTICAL_SHOW_GRIB_PRESSURE.removeListener(gribPressureEnabledListener)
            app.settings.NAUTICAL_SHOW_WINDY_TILES.removeListener(windyEnabledListener)
            app.settings.NAUTICAL_NIGHT_VISION_ENABLED.removeListener(nightVisionEnabledListener)
            app.settings.NAUTICAL_SUNLIGHT_MODE.removeListener(sunlightModeListener)
            app.settings.NAUTICAL_DISPLAY_MODE.removeListener(displayModeListener)
            app.settings.NAUTICAL_HEAVY_WEATHER_MODE.removeListener(heavyWeatherEnabledListener)
            app.daynightHelper.setExternalMapThemeProvider(null)

            app.settings.APPLICATION_MODE.removeListener(applicationModeListener)
            SailingDependencyContainer.performanceRepository?.disconnect()
            layerController?.unregisterLayers()
            layerController = null
            s57SpatialIndex?.close()
            s57SpatialIndex = null
            routingViewModel = null
            mobAudioAlertManager.stopAlarm()
            hudManager?.get()?.onDestroy()
            hudManager = null
            mobViewModel = null
            drViewModel = null
            laylineViewModel = null
            navtexViewModel = null

            nauticalMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                nauticalMapLayer = null
            }
            aisAisLayer?.let {
                mapView?.removeLayer(it)
                aisAisLayer = null
            }
            skTideLayer?.let {
                mapView?.removeLayer(it)
                skTideLayer = null
            }
            vhfPoiLayer?.let { layer ->
                mapView?.removeLayer(layer)
                vhfPoiLayer = null
            }
            oceanographicGribMapLayer?.let { layer ->
                mapView?.removeLayer(layer)
                oceanographicGribMapLayer = null
            }
            if (isNightVisionEnabled) {
                toggleNightVision(enable = false)
            }

            app.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefChangeListener)

            unregisterThermalListener()
            Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)

            shutdownResources()
            isPluginInitialized = false
        }
    }

    override fun disable(app: OsmandApplication) {
        super.disable(app)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun checkOffCourseAlert(state: MarineState) {
        if (state.isOffCourse) {
            if (!isAlertActive) {
                isAlertActive = true
                log.warn("OFF COURSE ALERT!")
                app.runInUIThread {
                    app.showToastMessage(R.string.nautical_off_course_alert)
                    app.player?.let { player ->
                        val text = app.getString(R.string.nautical_off_course_alert)
                        player.playCommands(player.newCommandBuilder().attention(text))
                    }
                }
            }
        } else {
            isAlertActive = false
        }
    }

    private fun checkAccidentalGybeAlert(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        val awa = state.windDirectionApparent ?: return
        val awaDeg = Math.toDegrees(awa)
        
        if (abs(awaDeg) > 165.0) {
            val msg = app.getString(R.string.nautical_alarm_accidental_gybe)
            notifications["safety.alarm.gybe"] = SignalKNotification(msg, NotificationState.ALARM)

            NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                net.osmand.plus.plugins.nautical.audio.AlarmType.TACTICAL_GYBE,
                voiceText = msg
            )
        }
    }

    private fun checkDepthSafety(state: MarineState, notifications: MutableMap<String, SignalKNotification>) {
        val depth = state.depthBelowKeel ?: return
        val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)
        
        val safeMin = sm.getMinSafeDepth()
        val shallowThreshold = app.settings.getCustomRenderProperty("shallowContour", "2.0").get().toDoubleOrNull() ?: 2.0

        if (depth < shallowThreshold) {
            notifications["safety.depth.shallow"] = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_keel, depth),
                state = NotificationState.EMERGENCY
            )
        } else if (depth < safeMin) {
            notifications["safety.depth.warning"] = SignalKNotification(
                message = app.getString(R.string.nautical_shallow_water_alert_contour, depth),
                state = NotificationState.WARN
            )
        }
    }

    var isNightVisionEnabled = false
        private set

    fun applyDisplayMode(mapActivity: MapActivity, mode: NauticalDisplayMode) {
        val enableNightVision = mode == NauticalDisplayMode.DARK
        val enableSunlight = mode == NauticalDisplayMode.SUNLIGHT

        // Sync legacy prefs for components that haven't migrated yet
        app.settings.NAUTICAL_NIGHT_VISION_ENABLED.set(enableNightVision)
        app.settings.NAUTICAL_SUNLIGHT_MODE.set(enableSunlight)

        // Sync with semantic rendering engine
        app.settings.getCustomRenderProperty("nautical_night_mode", "").set(if (enableNightVision) "true" else "false")

        if (this.isNightVisionEnabled != enableNightVision) {
            this.isNightVisionEnabled = enableNightVision
            presentationManager?.setNightMode(enableNightVision)
            val decorView = mapActivity.window.decorView

            if (enableNightVision) {
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(RED_FILTER_MATRIX)
                }
                decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)

                val nightColor = 0xFF000000.toInt()
                AndroidUiHelper.setStatusBarColor(mapActivity, nightColor)
                AndroidUiHelper.setNavigationBarColor(mapActivity, nightColor, false)
                AndroidUiHelper.setStatusBarContentColor(decorView, false)
                decorView.invalidate()
            } else {
                decorView.setLayerType(View.LAYER_TYPE_NONE, null)
                mapActivity.updateStatusBarColor()
                mapActivity.updateNavigationBarColor()
            }
        }

        requestRefresh()
        app.notificationHelper.refreshNotification(net.osmand.plus.notifications.OsmandNotification.NotificationType.NAUTICAL)

        mapActivity.app.runInUIThread {
            mapActivity.app.osmandMap.mapLayers.mapInfoLayer.recreateAllControls(mapActivity)
        }
    }

    override fun getMapTheme(): DayNightMode? {
        return if (app.settings.NAUTICAL_DISPLAY_MODE.get() == NauticalDisplayMode.DARK) {
            DayNightMode.NIGHT
        } else {
            null // Let default DayNightHelper logic handle it
        }
    }

    fun toggleNightVision(enable: Boolean) {
        val newMode = if (enable) NauticalDisplayMode.DARK else NauticalDisplayMode.NORMAL
        app.settings.NAUTICAL_DISPLAY_MODE.set(newMode)
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

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let {
                pm.removeThermalStatusListener(it)
            }
            thermalListener = null
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
        engine?.dataBroker?.updateState { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }

        val protocol = if (useSecure) "wss" else "ws"
        val wsUrl = "$protocol://$ip:$port/signalk/v1/stream?subscribe=all"
        val authToken = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
        
        val failureCallback = {
            retryHandler.removeCallbacks(retryRunnable)
            val delayMs = min(1000L * (2.0.pow(retryAttempt.toDouble()).toLong()), 60000L)
            retryHandler.postDelayed(retryRunnable, delayMs)
            retryAttempt++
            Unit
        }

        val authErrorCallback = {
            engine?.dataBroker?.updateState { it.copy(connectionStatus = ConnectionStatus.UNAUTHORIZED) }
            app.runInUIThread {
                app.showToastMessage(R.string.nautical_auth_token_required)
            }
        }
        
        engine?.let { e ->
            e.onConnectionLost = failureCallback
            e.onConnectionError = failureCallback
            e.onAuthError = authErrorCallback
            e.onConnectionRestored = connectionRestoredListener
            e.vesselDraft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
            e.corridorWidthNm = app.settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
            e.safetyCorridorBufferNm = app.settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
        }

        connection.connect(wsUrl, username, password, authToken, failureCallback, authErrorCallback) { message -> 
            engine?.handleIncomingMessage(message) 
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            log.error("Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            log.error("Failed to unregister network callback: ${e.message}")
        }
    }

    override fun registerConfigureMapCategoryActions(
        adapter: ContextMenuAdapter,
        mapActivity: MapActivity,
        customRules: MutableList<RenderingRuleProperty>,
    ) {
        if (isActive && app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT) {
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
            
            if (isModuleEnabled(NauticalModule.ENC)) {
                adapter.addItem(createToggleWithGear(R.string.nautical_enc_manager, app.settings.NAUTICAL_MODULE_ENC, mapActivity) {
                    showSettings(mapActivity, SettingsScreenType.ENC_CHART_MANAGER)
                })
            }

            if (isModuleEnabled(NauticalModule.RASTER)) {
                adapter.addItem(createToggleWithGear(R.string.raster_layer_name, app.settings.NAUTICAL_SHOW_RASTER_CHARTS, mapActivity) {
                    net.osmand.plus.plugins.nautical.raster.MarineRasterSettingsControl.show(mapActivity.supportFragmentManager)
                })
            }

            adapter.addItem(createToggle(R.string.nautical_show_laylines, app.settings.NAUTICAL_SHOW_LAYLINES, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_wind_shifts, app.settings.NAUTICAL_SHOW_WIND_SHIFTS, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_trajectory, app.settings.NAUTICAL_SHOW_TRAJECTORY, mapActivity))
            
            if (isModuleEnabled(NauticalModule.TIDES)) {
                adapter.addItem(createToggleWithGear(R.string.layer_tides_title, app.settings.NAUTICAL_SHOW_TIDES, mapActivity) {
                    showSettings(mapActivity, SettingsScreenType.TIDE_DATA_MANAGER)
                })
            }

            if (isModuleEnabled(NauticalModule.GRIB)) {
                adapter.addItem(createToggleWithGear(R.string.grib_layer_waves, app.settings.NAUTICAL_SHOW_GRIB_WAVES, mapActivity) {
                    net.osmand.plus.plugins.nautical.grib.ui.GribManagerBottomSheet.show(mapActivity.supportFragmentManager)
                })
                adapter.addItem(createToggle(R.string.grib_layer_pressure, app.settings.NAUTICAL_SHOW_GRIB_PRESSURE, mapActivity))
            }

            if (isModuleEnabled(NauticalModule.LOGBOOK)) {
                adapter.addItem(createToggleWithGear(R.string.nautical_log_entries, app.settings.NAUTICAL_SHOW_LOGBOOK_LAYER, mapActivity) {
                    showSettings(mapActivity, SettingsScreenType.MARINE_LOGBOOK)
                })
            }

            adapter.addItem(createToggle(R.string.nautical_restricted_area, app.settings.NAUTICAL_RESTRICTED_AREAS_ENABLED, mapActivity))

            // Vessel Projections Group
            adapter.addItem(
                ContextMenuItem("nautical_vessel_group").apply {
                    title = app.getString(R.string.nautical_vessel_indicators)
                }
            )
            adapter.addItem(createToggle(R.string.nautical_show_heading_line, app.settings.NAUTICAL_SHOW_HEADING_LINE, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_cog_line, app.settings.NAUTICAL_SHOW_COG_LINE, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_cmg_line, app.settings.NAUTICAL_SHOW_CMG_LINE, mapActivity))
            adapter.addItem(createToggle(R.string.nautical_show_current_vector, app.settings.NAUTICAL_SHOW_CURRENT_VECTOR, mapActivity))
            
            // Projection Time
            adapter.addItem(
                ContextMenuItem("nautical_look_ahead").apply {
                    title = app.getString(R.string.nautical_look_ahead_time)
                    description = "${app.settings.NAUTICAL_LOOK_AHEAD_TIME.get()} ${app.getString(R.string.shared_string_min)}"
                    icon = R.drawable.ic_action_time
                    setListener { uiAdapter, _, item, _ ->
                        val options = arrayOf<CharSequence>("2", "5", "10", "20", "30", "60")
                        val isNight = app.daynightHelper.isNightMode(app.settings.APPLICATION_MODE.get(), ThemeUsageContext.OVER_MAP)
                        androidx.appcompat.app.AlertDialog.Builder(mapActivity, if (isNight) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
                            .setTitle(R.string.nautical_look_ahead_time)
                            .setItems(options) { _, which ->
                                val mins = options[which].toString().toInt()
                                app.settings.NAUTICAL_LOOK_AHEAD_TIME.set(mins)
                                item.description = "$mins ${app.getString(R.string.shared_string_min)}"
                                uiAdapter.onDataSetChanged()
                                requestRefresh()
                            }
                            .show()
                        true
                    }
                }
            )
        }
    }

    private fun createToggleWithGear(titleId: Int, pref: CommonPreference<Boolean>, mapActivity: MapActivity, onGearClick: () -> Unit): ContextMenuItem {
        return ContextMenuItem("nautical_item_$titleId").apply {
            setTitleId(titleId, mapActivity)
            selected = pref.get()
            layout = R.layout.list_item_icon_and_menu
            icon = R.drawable.ic_action_additional_option
            secondaryIcon = R.drawable.ic_action_settings
            setListener { uiAdapter, _, item, isChecked ->
                pref.set(isChecked)
                item.selected = isChecked
                uiAdapter.onDataSetChanged()
                requestRefresh()
                true
            }
            setSecondaryIconClickListener { _, _, _, _ ->
                onGearClick()
                true
            }
        }
    }

    private fun showSettings(mapActivity: MapActivity, type: SettingsScreenType) {
        net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(mapActivity, type)
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
                requestRefresh()
                true
            }
        }
    }

    private fun shutdownResources() {
        safetyArbitrator?.stop()
        safetyArbitrator = null
        stopNauticalBackgroundService()
        stopConnectionLostAudioLoop()
        try {
            app.unregisterReceiver(screenStateReceiver)
            app.unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            log.error("Failed to unregister receivers: ${e.message}")
        }
        
        SailingDependencyContainer.teardown()
        
        pluginScope?.cancel()
        pluginScope = null
        
        retryHandler.removeCallbacks(retryRunnable)
        refreshHandler.removeCallbacks(refreshRunnable)
        
        locationProvider?.stop()
        locationProvider = null
        anchorWatchdog?.stop()
        anchorWatchdog = null
        alarmPriorityManager?.stop()
        alarmPriorityManager = null
        
        if (::connection.isInitialized) {
            connection.disconnect()
        }
        
        engine?.let {
            it.unregisterListener(marineStateListener)
            it.registerAisListener(null)
            it.removeRouteStepListener(routeStepListener)
            CoroutineScope(Dispatchers.IO).launch {
                it.saveBuffersToDisk(app)
                it.stop()
            }
        }
        engine = null
        autopilot = null
        electrical = null
        autopilotManager = null
        
        autopilotListener?.let {
            app.routingHelper.removeListener(it)
            autopilotListener = null
        }
        maneuverManager = null
        speechHelper?.destroy()
        speechHelper = null
        ttsHelper = null
        NauticalAudioArbiter.getInstance(app).destroy()
        vhfManager?.onDestroy()
        vhfManager = null
        logbookEngine?.stop()
        logbookEngine = null
        logbookRepository = null
        aisManager?.stopUpdates()
        aisManager = null
        skDiscovery?.stop()
        skDiscovery = null
        isNightVisionEnabled = false
        presentationManager?.onPause()
        presentationManager = null
        
        instanceRef = null
    }

    override fun registerMapContextMenuActions(mapActivity: MapActivity, lat: Double, lon: Double, adapter: ContextMenuAdapter, obj: Any?, conf: Boolean) {
        if (app.settings.APPLICATION_MODE.get() != ApplicationMode.BOAT) return

        adapter.addItem(
            ContextMenuItem("nautical_switches").apply {
                setTitleId(R.string.nautical_electrical_dashboard, mapActivity)
                icon = R.drawable.ic_action_nautical_battery_volt
                setListener { _, _, _, _ ->
                    NauticalElectricalDashboardBottomSheet.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_ping_port").apply {
                setTitleId(R.string.nautical_ping_port_pin, mapActivity)
                icon = R.drawable.ic_action_flag
                setListener { _, _, _, _ ->
                    tacticalStartManager?.setPortPin(lat, lon)
                    app.showToastMessage(R.string.nautical_port_pin_set)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_ping_stbd").apply {
                setTitleId(R.string.nautical_ping_stbd_pin, mapActivity)
                icon = R.drawable.ic_action_flag
                setListener { _, _, _, _ ->
                    tacticalStartManager?.setStarboardPin(lat, lon)
                    app.showToastMessage(R.string.nautical_stbd_pin_set)
                    true
                }
            }
        )

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

        if (app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get() != 0.0) {
            adapter.addItem(
                ContextMenuItem("nautical_anchor_set_preview").apply {
                    setTitleId(R.string.nautical_adjust_anchor_here, mapActivity)
                    icon = R.drawable.ic_action_anchor
                    setListener { _, _, _, _ ->
                        app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(lat)
                        app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(lon)
                        app.showToastMessage(R.string.nautical_anchor_moved_to_tap)
                        app.osmandMap?.refreshMap()
                        true
                    }
                }
            )
        }

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
            ContextMenuItem("nautical_maneuvers_menu").apply {
                setTitleId(R.string.nautical_maneuver_menu, mapActivity)
                icon = R.drawable.ic_action_sail_boat_dark
                setListener { _, _, _, _ ->
                    NauticalManeuversBottomSheet.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("mob_maneuver").apply {
                setTitleId(R.string.nautical_mob_label, mapActivity)
                setListener { _, _, _, _ ->
                    this@NauticalPlugin.mobViewModel?.triggerMob(
                        net.osmand.data.LatLon(lat, lon),
                        net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource.MAP
                    )
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

        val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, pluginScope!!)
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
                            // Standardized ISO-8601 Filenames (TASK-100)
                            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                            val dateStr = sdf.format(Date())
                            val name = "trip_$dateStr"
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

        adapter.addItem(
            ContextMenuItem("nautical_sync_routes").apply {
                title = app.getString(R.string.nautical_sync_routes_from_server)
                icon = R.drawable.ic_action_import
                setListener { _, _, _, _ ->
                    pluginScope?.launch {
                        val routes = engine?.fetchRoutesFromServer()
                        if (!routes.isNullOrEmpty()) {
                            val names = routes.values.map { (it.name ?: "Unnamed Route") as CharSequence }.toTypedArray()
                            val ids = routes.keys.toTypedArray()
                            
                            app.runInUIThread {
                                androidx.appcompat.app.AlertDialog.Builder(mapActivity)
                                    .setTitle(R.string.nautical_select_server_route)
                                    .setItems(names) { _, which: Int ->
                                        val routeId = ids[which]
                                        val selectedRoute = routes[routeId]
                                        
                                        // Nested dialog for actions
                                        androidx.appcompat.app.AlertDialog.Builder(mapActivity)
                                            .setTitle(selectedRoute?.name ?: "Route Actions")
                                            .setItems(arrayOf("Load Route", "Update with Active", "Delete from Server")) { _, actionIdx ->
                                                pluginScope?.launch {
                                                    when (actionIdx) {
                                                        0 -> { // Load
                                                            val fullRoute = engine?.getRestService()?.getRouteById(routeId)?.body()
                                                            (fullRoute ?: selectedRoute)?.feature?.geometry?.coordinates?.let { coords ->
                                                                val points = coords.map { Pair(it[1], it[0]) }
                                                                engine?.loadRoute(points)
                                                                app.showToastMessage(R.string.nautical_loaded_points, points.size)
                                                            }
                                                        }
                                                        1 -> { // Update
                                                            val activePoints = engine?.getRoutePoints() ?: emptyList()
                                                            if (activePoints.isNotEmpty()) {
                                                                engine?.updateRouteOnServer(routeId, selectedRoute?.name ?: "Updated", activePoints)
                                                            }
                                                        }
                                                        2 -> { // Delete
                                                            engine?.deleteRouteFromServer(routeId)
                                                        }
                                                    }
                                                }
                                            }
                                            .show()
                                    }
                                    .show()
                            }
                        } else {
                            app.showToastMessage(R.string.nautical_no_routes_found_on_server)
                        }
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_sync_charts").apply {
                setTitleId(R.string.nautical_server_charts, mapActivity)
                icon = R.drawable.ic_action_world_globe
                setListener { _, _, _, _ ->
                    pluginScope?.launch {
                        val charts = engine?.getRestService()?.getCharts()?.body()
                        if (!charts.isNullOrEmpty()) {
                            val names = charts.values.map { (it.name ?: it.identifier) as CharSequence }.toTypedArray()
                            app.runInUIThread {
                                androidx.appcompat.app.AlertDialog.Builder(mapActivity)
                                    .setTitle(R.string.nautical_server_charts)
                                    .setItems(names, null)
                                    .show()
                            }
                        } else {
                            app.showToastMessage(R.string.nautical_no_charts_on_server)
                        }
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_upload_route").apply {
                title = app.getString(R.string.nautical_upload_route_to_server)
                icon = R.drawable.ic_action_export
                setListener { _, _, _, _ ->
                    val name = "Route-${System.currentTimeMillis()}"
                    pluginScope?.launch {
                        engine?.uploadActiveRouteToSignalK(name)
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_weather_route").apply {
                title = mapActivity.getString(R.string.nautical_calculate_weather_route)
                icon = R.drawable.ic_action_wind
                setListener { _, _, _, _ ->
                    calculateWeatherRouteTo(lat, lon)
                    true
                }
            }
        )

        val activeRoute = engine?.getRoutePoints() ?: emptyList()
        if (activeRoute.isNotEmpty()) {
            adapter.addItem(
                ContextMenuItem("nautical_export_route_active").apply {
                    setTitleId(R.string.nautical_export_route_gpx, mapActivity)
                    icon = R.drawable.ic_action_export
                    setListener { _, _, _, _ ->
                        pluginScope?.launch {
                            val file = GpxStreamer(app).exportTrajectory(activeRoute)
                            if (file != null) {
                                app.showToastMessage(R.string.nautical_export_route_success, file.name)
                            } else {
                                app.showToastMessage(R.string.nautical_export_route_failed)
                            }
                        }
                        true
                    }
                }
            )
        }

        routingViewModel?.optimalRoute?.value?.let { weatherRoute ->
            adapter.addItem(
                ContextMenuItem("nautical_export_weather_route").apply {
                    title = app.getString(R.string.nautical_export_route_gpx) + " (Weather)"
                    icon = R.drawable.ic_action_export
                    setListener { _, _, _, _ ->
                        pluginScope?.launch {
                            val file = GpxStreamer(app).exportRouteGpx(weatherRoute)
                            if (file != null) {
                                app.showToastMessage(R.string.nautical_export_route_success, file.name)
                            } else {
                                app.showToastMessage(R.string.nautical_export_route_failed)
                            }
                        }
                        true
                    }
                }
            )
        }

        vhfPoiLayer?.registerContextMenuActions(adapter, obj)

        if (obj is AisObject) {
            adapter.addItem(
                ContextMenuItem("nautical_ais_details").apply {
                    setTitleId(R.string.nautical_vessel_details, mapActivity)
                    icon = R.drawable.ic_action_info
                    setListener { _, _, _, _ ->
                        NauticalAisDetailsDialog.show(mapActivity.supportFragmentManager, obj.mmsi)
                        true
                    }
                }
            )

            val isBuddy = engine?.getCurrentState()?.aisBuddies?.contains(obj.mmsi) ?: false
            adapter.addItem(
                ContextMenuItem("nautical_toggle_buddy").apply {
                    title = if (isBuddy) mapActivity.getString(R.string.nautical_remove_from_buddies) else mapActivity.getString(R.string.nautical_add_to_buddies)
                    icon = if (isBuddy) R.drawable.ic_action_remove else R.drawable.ic_action_add
                    setListener { _, _, _, _ ->
                        val current = engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
                        if (isBuddy) current.remove(obj.mmsi) else current.add(obj.mmsi)
                        engine?.sendDelta("navigation.aisBuddies", current.toList())
                        true
                    }
                }
            )
        }
    }

    private fun calculateWeatherRouteTo(lat: Double, lon: Double) {
        val vm = routingViewModel ?: return
        val gribRepo = SailingDependencyContainer.gribRepository
        val gridData = gribRepo?.gridData
        if (gridData == null) {
            app.showToastMessage(R.string.grib_parse_error)
            return
        }
        
        val lastLoc = app.locationProvider.lastKnownLocation
        if (lastLoc == null) {
            app.showToastMessage(R.string.nautical_error_no_gps)
            return
        }

        val polarProfile = SailingDependencyContainer.performanceRepository?.activePolarProfile?.value
        if (polarProfile == null) {
             app.showToastMessage(R.string.nautical_error_no_polar)
             return
        }

        val request = net.osmand.plus.plugins.nautical.routing.model.RoutingRequest(
            start = net.osmand.plus.plugins.nautical.routing.model.Waypoint(lastLoc.latitude, lastLoc.longitude),
            destination = net.osmand.plus.plugins.nautical.routing.model.Waypoint(lat, lon),
            departureTime = System.currentTimeMillis(),
            polarProfile = polarProfile
        )
        
        s57SpatialIndex?.let { index ->
            val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)
            vm.calculateWeatherRoute(request, gridData, index, sm)
            
            // Observe status to provide feedback
            vm.routingStatus.onEach { status ->
                app.runInUIThread {
                    if (status != "Idle") {
                        app.showToastMessage(status)
                    }
                }
            }.launchIn(pluginScope!!)

            // Observe result to update layer
            vm.optimalRoute.onEach { result ->
                app.runInUIThread {
                    layerController?.setWeatherRoute(result)
                }
            }.launchIn(pluginScope!!)
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

    fun isSignalKConnected(): Boolean = connection.isConnected()

    fun isAudioHardwareAvailable(): Boolean {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        return devices.isNotEmpty()
    }

    private fun isAnchorWatchActive(): Boolean = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0

    fun updateNauticalBackgroundService() {
        val backgroundEnabled = app.settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
        val activeManeuver = maneuverManager?.state != ManeuverState.IDLE
        val anchorActive = isAnchorWatchActive()

        if (isActive && (backgroundEnabled || activeManeuver || anchorActive)) {
            app.startNavigationService(app, net.osmand.plus.NavigationService.USED_BY_NAUTICAL, NauticalBackgroundService::class.java)
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

    private fun onAppBackgrounded() {
        anchorWatchdog?.onAppBackgrounded()
        logbookEngine?.onAppBackgrounded()
        SailingDependencyContainer.nmeaMultiplexer?.onAppBackgrounded()
    }

    private fun onAppForegrounded() {
        anchorWatchdog?.onAppForegrounded()
        logbookEngine?.onAppForegrounded()
        SailingDependencyContainer.nmeaMultiplexer?.onAppForegrounded()
        engine?.refreshVesselState()
    }

    fun applyVesselContext(vesselContext: VesselContext) {
        val settings = app.settings
        when (vesselContext) {
            VesselContext.SAILING -> {
                settings.NAUTICAL_SHOW_LAYLINES.set(true)
                settings.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
                settings.NAUTICAL_LOOK_AHEAD_TIME.set(10)
                settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(0.1f)
            }
            VesselContext.MOTORING -> {
                settings.NAUTICAL_SHOW_LAYLINES.set(false)
                settings.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
                settings.NAUTICAL_LOOK_AHEAD_TIME.set(5)
                settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(0.2f)
            }
            VesselContext.ANCHORED -> {
                val loc = app.locationProvider.lastKnownLocation
                if (loc != null) {
                    settings.NAUTICAL_ANCHOR_LAT.set(loc.latitude)
                    settings.NAUTICAL_ANCHOR_LON.set(loc.longitude)
                    settings.NAUTICAL_ANCHOR_RADIUS.set(50f)
                    anchorWatchdog?.start()
                }
            }
            VesselContext.EMERGENCY_HEAVE_TO -> {
                forceEmergencyBrightness()
                settings.NAUTICAL_OFF_COURSE_ALARM.set(20.0f)
                settings.NAUTICAL_HEAVY_WEATHER_MODE.set(true)
                
                // Check for MOB nearby
                val loc = app.locationProvider.lastKnownLocation
                if (loc != null) {
                    val mobLat = settings.NAUTICAL_MOB_LAT.get()
                    val mobLon = settings.NAUTICAL_MOB_LON.get()
                    if (mobLat != 0.0) {
                        val dist = net.osmand.util.MapUtils.getDistance(loc.latitude, loc.longitude, mobLat, mobLon)
                        if (dist < 500) {
                            log.info("Nautical: Already within MOB vicinity (500m)")
                        }
                    }
                }
            }
        }
        app.runInUIThread {
            app.osmandMap?.refreshMap()
        }
    }

    private fun checkBatteryOptimization() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(app.packageName)) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } catch (e: Exception) {
                log.error("Failed to show battery optimization settings: ${e.message}", e)
            }
        }
    }
}
