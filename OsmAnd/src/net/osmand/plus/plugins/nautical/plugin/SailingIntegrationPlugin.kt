package net.osmand.plus.plugins.nautical.plugin

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.dr.ui.DrWarningHeaderView
import net.osmand.plus.plugins.nautical.dr.viewmodel.DeadReckoningViewModel
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexDetailsBottomSheet
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexHudView
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineViewModel
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.mob.engine.MobStateMachine
import net.osmand.plus.plugins.nautical.mob.ui.MobEmergencyHeaderView
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobAudioAlertManager
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.PolarSpeedRatioWidget
import net.osmand.plus.views.mapwidgets.widgets.TargetVmgWidget
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.ScreenLayoutMode

/**
 * Master plugin initializer for advanced sailing performance features.
 */
class SailingIntegrationPlugin(app: OsmandApplication) : OsmandPlugin(app) {

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
    var routingViewModel: RoutingViewModel? = null
        private set
    private var mobHeaderView: MobEmergencyHeaderView? = null
    private var drHeaderView: DrWarningHeaderView? = null
    private var navtexHudView: NavtexHudView? = null
    private var nauticalHudContainer: LinearLayout? = null
    
    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mobStateMachine = MobStateMachine()
    private val mobAudioAlertManager = MobAudioAlertManager(app)

    private val applicationModeListener = net.osmand.StateChangedListener<ApplicationMode> { 
        val activity = app.osmandMap?.mapView?.mapActivity
        if (activity != null) {
            updateLayers(app, activity)
            updateHudVisibility()
        }
    }

    override fun getId(): String = "osmand.sailing.performance"

    override fun getName(): String = app.getString(R.string.sailing_performance_plugin_name)

    override fun getDescription(short: Boolean): CharSequence {
        return app.getString(R.string.sailing_performance_plugin_desc)
    }

    override fun init(app: OsmandApplication, activity: Activity?): Boolean {
        // Initialize performance repository
        SailingDependencyContainer.performanceRepository.fetchPolars()
        
        // Initialize S-57 index
        val indexManager = S57SpatialIndex(app)
        s57SpatialIndex = indexManager
        pluginScope.launch {
            indexManager.indexCharts()
        }

        routingViewModel = RoutingViewModel()
        app.settings.APPLICATION_MODE.addListener(applicationModeListener)
        
        return true
    }

    override fun disable(app: OsmandApplication) {
        app.settings.APPLICATION_MODE.removeListener(applicationModeListener)
        pluginScope.cancel()
        SailingDependencyContainer.performanceRepository.disconnect()
        layerController?.unregisterLayers()
        layerController = null
        s57SpatialIndex?.clearCache()
        s57SpatialIndex = null
        routingViewModel = null
        
        mobAudioAlertManager.stopAlarm()
        
        // Remove HUD views to prevent context leaks
        nauticalHudContainer?.let { hud ->
            (hud.parent as? ViewGroup)?.removeView(hud)
        }
        nauticalHudContainer = null
        mobHeaderView = null
        drHeaderView = null
        navtexHudView = null
        
        mobViewModel = null
        drViewModel = null
        laylineViewModel = null
        navtexViewModel = null
    }

    private fun updateHudVisibility() {
        val isBoat = app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT
        nauticalHudContainer?.visibility = if (isBoat) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun updateLayers(context: Context, activity: MapActivity?) {
        val isBoat = app.settings.APPLICATION_MODE.get() == ApplicationMode.BOAT
        if (isBoat && isActive) {
            if (layerController == null && activity != null) {
                registerLayers(context, activity)
            } else {
                layerController?.registerLayers()
            }
        } else {
            layerController?.unregisterLayers()
        }
    }

    override fun registerLayers(context: Context, activity: MapActivity?) {
        if (activity != null) {
            val controller = SailingMapLayerController(activity, s57SpatialIndex)
            controller.registerLayers()
            layerController = controller

            initMobSystem(activity, controller)
            initDrSystem(activity, controller)
            initLaylineSystem(activity, controller)
            initNavtexSystem(activity, controller)
        }
    }

    private fun getOrCreateNauticalHud(activity: MapActivity): ViewGroup? {
        if (nauticalHudContainer == null) {
            val mapHudLayout = activity.findViewById<ViewGroup>(R.id.map_hud_layout) ?: return null
            nauticalHudContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP
                }
            }
            mapHudLayout.addView(nauticalHudContainer)
        }
        return nauticalHudContainer
    }

    private fun initNavtexSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val repo = SailingDependencyContainer.getNavtexRepository(app)
        val viewModel = NavtexViewModel(app, repo)
        this.navtexViewModel = viewModel

        val hud = NavtexHudView(activity)
        this.navtexHudView = hud

        getOrCreateNauticalHud(activity)?.addView(hud)

        hud.setOnMessageClickListener { msg ->
            NavtexDetailsBottomSheet.newInstance(msg).show(activity.supportFragmentManager, "navtex_details_hud")
        }

        viewModel.uiState.onEach { state ->
            app.runInUIThread {
                hud.updateState(state)
                controller.navtexLayer.updateState(state)
                activity.mapView.refreshMap()
            }
        }.launchIn(activity.lifecycleScope)
    }

    private fun initLaylineSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val viewModel = LaylineViewModel(
            app,
            SailingDependencyContainer.performanceRepository,
            SailingDependencyContainer.gribRepository
        )
        this.laylineViewModel = viewModel

        viewModel.uiState.onEach { state ->
            app.runInUIThread {
                controller.laylinesLayer.updateState(state)
                activity.mapView.refreshMap()
            }
        }.launchIn(activity.lifecycleScope)
    }

    private fun initDrSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val viewModel = DeadReckoningViewModel(app, SailingDependencyContainer.performanceRepository)
        this.drViewModel = viewModel

        val header = DrWarningHeaderView(activity)
        this.drHeaderView = header

        getOrCreateNauticalHud(activity)?.addView(header)

        viewModel.uiState.onEach { state ->
            app.runInUIThread {
                header.updateState(state)
                controller.drLayer.updateState(state)
                activity.mapView.refreshMap()
            }
        }.launchIn(activity.lifecycleScope)
    }

    private fun initMobSystem(activity: MapActivity, controller: SailingMapLayerController) {
        val viewModel = MobViewModel(app, mobStateMachine, mobAudioAlertManager)
        this.mobViewModel = viewModel

        val header = MobEmergencyHeaderView(activity)
        header.setViewModel(viewModel)
        this.mobHeaderView = header

        getOrCreateNauticalHud(activity)?.addView(header, 0) // MOB always at top

        viewModel.uiState.onEach { state ->
            app.runInUIThread {
                header.updateState(state)
                controller.mobLayer.updateState(state)
                activity.mapView.refreshMap()
                
                // Screen awake logic
                if (state.isMobActive) {
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }.launchIn(activity.lifecycleScope)
    }

    override fun createWidgets(map: MapActivity, widgets: MutableList<MapWidgetInfo>, appMode: ApplicationMode, screenLayoutMode: ScreenLayoutMode?) {
        if (appMode != ApplicationMode.BOAT) return
        
        val polarRatioWidget = PolarSpeedRatioWidget(map, WidgetType.NAUTICAL_POLAR_RATIO, null, null)
        widgets.add(object : MapWidgetInfo(WidgetType.NAUTICAL_POLAR_RATIO.id, polarRatioWidget, 0, 0, R.string.nautical_polar_ratio, null, 0, 0, WidgetsPanel.RIGHT) {
            override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel = widgetPanel
        })

        val targetVmgWidget = TargetVmgWidget(map, WidgetType.NAUTICAL_VMG, null, null)
        widgets.add(object : MapWidgetInfo(WidgetType.NAUTICAL_VMG.id, targetVmgWidget, 0, 0, R.string.nautical_widget_vmg_label, null, 0, 0, WidgetsPanel.RIGHT) {
            override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel = widgetPanel
        })
    }

    override fun getSettingsScreenType(): SettingsScreenType {
        return SettingsScreenType.NAUTICAL_SETTINGS
    }
}
