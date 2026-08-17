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
        val hud = ForwardWatchHudView(activity)
        this.forwardWatchHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 10)
    }

    fun initEnvironmentSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (environmentHud?.context == activity) return
        hudManager?.get()?.removeHeader(environmentHud)
        val hud = NauticalEnvironmentWidgetView(activity)
        this.environmentHud = hud
        hudManager?.get()?.addHeader(hud, priority = 350)
    }

    fun initWatchScheduleSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (watchScheduleHudView?.context == activity) return
        hudManager?.get()?.removeHeader(watchScheduleHudView)
        val hud = WatchScheduleHudView(activity)
        this.watchScheduleHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 500)
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
        if (wearOsManager.isWatchMode()) {
            val hb = HeartbeatHudView(activity)
            this.heartbeatHudView = hb
            hudManager?.get()?.addHeader(hb, priority = 50)

            wearOsManager.isAmbientMode.onEach { ambient ->
                app.runInUIThread {
                    hb.setAmbientMode(ambient)
                }
            }.launchIn(activity.lifecycleScope)

            return
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
        hudManager?.get()?.addHeader(hh, priority = 400)

        val lh = ScreenTouchLockHudView(activity)
        this.screenTouchLockHudView = lh
        hudManager?.get()?.addHeader(lh, priority = 5)

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
                    workflowManager?.getScreenTouchLockManager()?.isTouchLockActive?.collect { locked ->
                        lh.setLocked(locked)
                        hudManager?.get()?.updateLayout()
                    }
                }
                launch {
                    workflowManager?.getScreenTouchLockManager()?.unlockProgress?.collect { progress ->
                        lh.setUnlockProgress(progress)
                    }
                }
            }
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
        val hud = NavtexHudView(activity)
        this.navtexHudView = hud
        hudManager?.get()?.addHeader(hud, priority = 200)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    hud.updateState(state)
                    controller.navtexLayer.updateState(state)
                }
            }
        }
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
        val header = DrWarningHeaderView(activity)
        this.drHeaderView = header
        hudManager?.get()?.addHeader(header, priority = 100)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    header.updateState(state)
                    controller.drLayer.updateState(state)
                }
            }
        }
    }

    fun initTacticalHudSystem(activity: MapActivity, hudManager: WeakReference<NauticalHudManager>?) {
        if (tacticsHudHeader?.context == activity) return
        hudManager?.get()?.removeHeader(startLineHudHeader)
        hudManager?.get()?.removeHeader(tacticsHudHeader)
        hudManager?.get()?.removeHeader(anchorWatchHudView)
        hudManager?.get()?.removeHeader(predictiveSteeringHudView)

        val sl = StartLineHudHeader(activity)
        this.startLineHudHeader = sl
        hudManager?.get()?.addHeader(sl, priority = 200)

        val th = TacticsHudHeader(activity)
        this.tacticsHudHeader = th
        hudManager?.get()?.addHeader(th, priority = 210)

        val aw = AnchorWatchHudView(activity)
        this.anchorWatchHudView = aw
        hudManager?.get()?.addHeader(aw, priority = 260)

        val ps = PredictiveSteeringHudView(activity)
        this.predictiveSteeringHudView = ps
        hudManager?.get()?.addHeader(ps, priority = 270)
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
        val header = MobEmergencyHeaderView(activity)
        header.setViewModel(viewModel)
        this.mobHeaderView = header
        hudManager?.get()?.addHeader(header, priority = 0)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    header.updateState(state)
                    controller.mobLayer.updateState(state)

                    if (state.isMobActive) {
                        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else if (app.settings.USE_SYSTEM_SCREEN_TIMEOUT.get()) {
                        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
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
            mapActivity.app.osmandMap.mapLayers.mapInfoLayer.recreateAllControls(mapActivity)
        }
    }
}
