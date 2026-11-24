package net.osmand.plus.plugins.nautical.ui

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.notifications.OsmandNotification.NotificationType
import net.osmand.plus.plugins.nautical.NauticalHudManager
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.NauticalPresentationManager
import net.osmand.plus.plugins.nautical.WearOsNauticalManager
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.dr.ui.DrWarningHeaderView
import net.osmand.plus.plugins.nautical.dr.viewmodel.DeadReckoningViewModel
import net.osmand.plus.plugins.nautical.engine.NauticalWorkflowManager
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowEngine
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexHudView
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineViewModel
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.mob.engine.MobStateMachine
import net.osmand.plus.plugins.nautical.mob.ui.MobEmergencyHeaderView
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobAudioAlertManager
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchHudView
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.NauticalDisplayMode
import java.lang.ref.WeakReference

class NauticalUiOverlayManager(private val app: OsmandApplication) {

    var mobHeaderView: MobEmergencyHeaderView? = null
        internal set
    var drHeaderView: DrWarningHeaderView? = null
        internal set
    var navtexHudView: NavtexHudView? = null
        internal set
    var environmentHud: NauticalEnvironmentWidgetView? = null
        internal set
    var watchScheduleHudView: WatchScheduleHudView? = null
        internal set
    var workflowHeaderView: WorkflowHeaderView? = null
        internal set
    var tacticalHudView: TacticalHudView? = null
        internal set
    var healthHudView: HardwareHealthHudHeader? = null
        internal set
    var screenTouchLockHudView: ScreenTouchLockHudView? = null
        internal set
    var heartbeatHudView: HeartbeatHudView? = null
        internal set
    var forwardWatchHudView: ForwardWatchHudView? = null
        internal set
    var startLineHudHeader: StartLineHudHeader? = null
        internal set
    var tacticsHudHeader: TacticsHudHeader? = null
        internal set
    var anchorWatchHudView: AnchorWatchHudView? = null
        internal set
    var predictiveSteeringHudView: PredictiveSteeringHudView? = null
        internal set
    var thermalWarningView: ThermalWarningView? = null
        internal set
    var marineAlarmBannerView: net.osmand.plus.plugins.nautical.ui.widgets.MarineAlarmBannerView? = null
        internal set
    var gribTimeScrubberView: net.osmand.plus.plugins.nautical.grib.ui.GribTimeScrubberView? = null
        internal set

    fun initMarineAlarmBannerSystem(
        activity: MapActivity,
        hudManager: WeakReference<NauticalHudManager>?,
        alarmPriorityManager: net.osmand.plus.plugins.nautical.engine.AlarmPriorityManager?
    ) {
        val apm = alarmPriorityManager ?: return
        if (marineAlarmBannerView?.context == activity) return
        hudManager?.get()?.removeHeader(marineAlarmBannerView)

        // Cockpit pane (step 2): lifecycle owned by the shell from here on.
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.MarineAlarmBannerPane(activity, apm)
        this.marineAlarmBannerView = pane.bannerView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initGribTimeScrubberSystem(
        activity: MapActivity,
        hudManager: WeakReference<NauticalHudManager>?
    ) {
        if (gribTimeScrubberView?.context == activity) return
        val scrubber = net.osmand.plus.plugins.nautical.grib.ui.GribTimeScrubberView(activity)
        this.gribTimeScrubberView = scrubber
    }

    var isNightVisionEnabled: Boolean = false
        internal set
    var isSunlightModeEnabled: Boolean = false
        internal set
    private var isSyncingDisplayMode: Boolean = false

    private val RED_FILTER_MATRIX = floatArrayOf(
        0.8f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f
    )

    fun initForwardWatchSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (forwardWatchHudView?.context == activity) return
        hudManager?.get()?.removeHeader(forwardWatchHudView)
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.SimpleViewPane(
            "forward_watch", 10
        ) { ForwardWatchHudView(it) }
        this.forwardWatchHudView = pane.buildView(activity) as ForwardWatchHudView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initEnvironmentSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (environmentHud?.context == activity) return
        hudManager?.get()?.removeHeader(environmentHud)
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.SimpleViewPane(
            "environment", 350
        ) { NauticalEnvironmentWidgetView(it) }
        this.environmentHud = pane.buildView(activity) as NauticalEnvironmentWidgetView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initWatchScheduleSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (watchScheduleHudView?.context == activity) return
        hudManager?.get()?.removeHeader(watchScheduleHudView)
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.SimpleViewPane(
            "watch_schedule", 500
        ) { WatchScheduleHudView(it) }
        this.watchScheduleHudView = pane.buildView(activity) as WatchScheduleHudView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initWorkflowSystem(
        activity: MapActivity,
        hudManager: WeakReference<NauticalHudManager>?,
        workflowEngine: SailingWorkflowEngine?,
        workflowManager: NauticalWorkflowManager?
    ) {
        val workflowEng = workflowEngine ?: return
        if (workflowHeaderView?.context == activity) return
        hudManager?.get()?.removeHeader(workflowHeaderView)
        hudManager?.get()?.removeHeader(tacticalHudView)
        hudManager?.get()?.removeHeader(healthHudView)
        hudManager?.get()?.removeHeader(heartbeatHudView)
        hudManager?.get()?.removeHeader(screenTouchLockHudView)

        val wearOsManager = NauticalPlugin.getWearOsManager(activity)
        val hudW = hudManager?.get()
        val layoutUpdate = { hudW?.updateLayout() }
        if (wearOsManager.isWatchMode()) {
            val hb = net.osmand.plus.plugins.nautical.ui.cockpit.HeartbeatPane(activity, wearOsManager)
            this.heartbeatHudView = hb.heartbeatView
            hudW?.cockpitPaneHost?.register(hb)
            return
        }

        val wh = net.osmand.plus.plugins.nautical.ui.cockpit.WorkflowProposalPane(activity, workflowEng, layoutUpdate)
        this.workflowHeaderView = wh.headerView

        val th = net.osmand.plus.plugins.nautical.ui.cockpit.TacticalStripPane(activity, workflowEng, layoutUpdate)
        this.tacticalHudView = th.stripView

        val lh = net.osmand.plus.plugins.nautical.ui.cockpit.ScreenTouchLockPane(activity, workflowManager, layoutUpdate)
        this.screenTouchLockHudView = lh.lockView

        hudW?.cockpitPaneHost?.let { host ->
            host.register(wh); host.register(th); host.register(lh)
        }
    }

    fun initNavtexSystem(
        activity: MapActivity,
        controller: SailingMapLayerController,
        hudManager: WeakReference<NauticalHudManager>?,
        onNavtexVmCreated: (NavtexViewModel) -> Unit
    ) {
        if (navtexHudView?.context == activity) return
        val repo = net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository(app)
        val viewModel = NavtexViewModel(app, repo)
        onNavtexVmCreated(viewModel)

        hudManager?.get()?.removeHeader(navtexHudView)
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.NavtexPane(activity, controller, viewModel)
        this.navtexHudView = pane.hudView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initLaylineSystem(
        activity: MapActivity,
        controller: SailingMapLayerController,
        onLaylineVmCreated: (LaylineViewModel) -> Unit
    ) {
        val perfRepo = SailingDependencyContainer.performanceRepository ?: return
        val viewModel = LaylineViewModel(
            app,
            perfRepo,
            SailingDependencyContainer.gribRepository
        )
        onLaylineVmCreated(viewModel)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    controller.laylinesLayer.updateState(state)
                }
            }
        }
    }

    fun initDrSystem(
        activity: MapActivity,
        controller: SailingMapLayerController,
        hudManager: WeakReference<NauticalHudManager>?,
        onDrVmCreated: (DeadReckoningViewModel) -> Unit
    ) {
        if (drHeaderView?.context == activity) return
        val perfRepo = SailingDependencyContainer.performanceRepository ?: return
        val viewModel = DeadReckoningViewModel(app, perfRepo)
        onDrVmCreated(viewModel)

        hudManager?.get()?.removeHeader(drHeaderView)
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.DrWarningPane(activity, controller, viewModel)
        this.drHeaderView = pane.headerView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun initTacticalHudSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (tacticsHudHeader?.context == activity) return
        hudManager?.get()?.removeHeader(startLineHudHeader)
        hudManager?.get()?.removeHeader(tacticsHudHeader)
        hudManager?.get()?.removeHeader(anchorWatchHudView)
        hudManager?.get()?.removeHeader(predictiveSteeringHudView)

        fun pane(id: String, p: Int, build: (MapActivity) -> android.view.View) =
            net.osmand.plus.plugins.nautical.ui.cockpit.SimpleViewPane(id, p, build)

        val sl = pane("start_line", 200) { StartLineHudHeader(it) }
        val th = pane("tactics", 210) { TacticsHudHeader(it) }
        val aw = pane("anchor_watch", 260) { AnchorWatchHudView(it) }
        val ps = pane("predictive_steering", 270) { PredictiveSteeringHudView(it) }

        this.startLineHudHeader = sl.buildView(activity) as StartLineHudHeader
        this.tacticsHudHeader = th.buildView(activity) as TacticsHudHeader
        this.anchorWatchHudView = aw.buildView(activity) as AnchorWatchHudView
        this.predictiveSteeringHudView = ps.buildView(activity) as PredictiveSteeringHudView

        hudManager?.get()?.cockpitPaneHost?.let { host ->
            host.register(sl); host.register(th); host.register(aw); host.register(ps)
        }
    }

    fun initMobSystem(
        activity: MapActivity,
        controller: SailingMapLayerController,
        hudManager: WeakReference<NauticalHudManager>?,
        mobStateMachine: MobStateMachine?,
        mobAudioAlertManager: MobAudioAlertManager?,
        onMobVmCreated: (MobViewModel) -> Unit
    ) {
        val sm = mobStateMachine ?: return
        if (mobHeaderView?.context == activity) return
        val am = mobAudioAlertManager ?: MobAudioAlertManager(app)
        val viewModel = MobViewModel(app, sm, am)
        onMobVmCreated(viewModel)

        hudManager?.get()?.removeHeader(mobHeaderView)
        // Cockpit pane (step 2): mounts at EMERGENCY priority once the host has
        // a MarineState; collects uiState while STARTED through the pane scope.
        val pane = net.osmand.plus.plugins.nautical.ui.cockpit.MobEmergencyPane(activity, controller, viewModel)
        this.mobHeaderView = pane.headerView
        hudManager?.get()?.cockpitPaneHost?.register(pane)
    }

    fun updateHudVisibility(hudManager: WeakReference<NauticalHudManager>?) {
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        hudManager?.get()?.setVisible(isBoat)
    }

    fun applyDisplayMode(
        mapActivity: MapActivity,
        mode: NauticalDisplayMode,
        presentationManager: NauticalPresentationManager?,
        onRequestRefresh: () -> Unit
    ) {
        if (isSyncingDisplayMode) return
        val enableNightVision = mode == NauticalDisplayMode.DARK
        val enableSunlight = mode == NauticalDisplayMode.SUNLIGHT

        isSyncingDisplayMode = true
        try {
            app.settings.NAUTICAL_NIGHT_VISION_ENABLED.set(enableNightVision)
            app.settings.NAUTICAL_SUNLIGHT_MODE.set(enableSunlight)
        } finally {
            isSyncingDisplayMode = false
        }

        val renderMode = when (mode) {
            NauticalDisplayMode.DARK -> "night"
            NauticalDisplayMode.SUNLIGHT -> "sunlight"
            else -> "day"
        }
        app.settings.getCustomRenderProperty("nautical_display_mode", "day").set(renderMode)
        app.settings.getCustomRenderProperty("nautical_night_mode", "false").set(if (enableNightVision) "true" else "false")
        app.settings.getCustomRenderProperty("nautical_sunlight_mode", "false").set(if (enableSunlight) "true" else "false")

        if (this.isNightVisionEnabled != enableNightVision || this.isSunlightModeEnabled != enableSunlight) {
            this.isNightVisionEnabled = enableNightVision
            this.isSunlightModeEnabled = enableSunlight
            presentationManager?.setNightMode(enableNightVision)

            val decorView = mapActivity.window.decorView
            val window = mapActivity.window
            val params = window.attributes

            if (enableNightVision) {
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(RED_FILTER_MATRIX)
                }
                decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)

                val nightColor = 0xFF000000.toInt()
                AndroidUiHelper.setStatusBarColor(mapActivity, nightColor)
                AndroidUiHelper.setNavigationBarColor(mapActivity, nightColor, false)
                AndroidUiHelper.setStatusBarContentColor(decorView, false)

                params.screenBrightness = 0.2f
                decorView.invalidate()
            } else if (enableSunlight) {
                decorView.setLayerType(View.LAYER_TYPE_NONE, null)
                mapActivity.updateStatusBarColor()
                mapActivity.updateNavigationBarColor()

                params.screenBrightness = 1.0f
            } else {
                decorView.setLayerType(View.LAYER_TYPE_NONE, null)
                mapActivity.updateStatusBarColor()
                mapActivity.updateNavigationBarColor()
                params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }

        onRequestRefresh()
        app.notificationHelper.refreshNotification(NotificationType.NAUTICAL)

        mapActivity.app.runInUIThread {
            if (!mapActivity.isFinishing && !mapActivity.isDestroyed) {
                mapActivity.app.osmandMap?.mapLayers?.mapInfoLayer?.recreateAllControls(mapActivity)
            }
        }
    }

    fun onPause() {
        // Fast, non-blocking lightweight pause
    }

    fun detach() {
        mobHeaderView = null
        drHeaderView = null
        navtexHudView = null
        environmentHud = null
        watchScheduleHudView = null
        workflowHeaderView = null
        tacticalHudView = null
        healthHudView = null
        screenTouchLockHudView = null
        heartbeatHudView = null
        forwardWatchHudView = null
        startLineHudHeader = null
        tacticsHudHeader = null
        anchorWatchHudView = null
        predictiveSteeringHudView = null
        thermalWarningView = null
        marineAlarmBannerView = null
        gribTimeScrubberView = null
    }
}
