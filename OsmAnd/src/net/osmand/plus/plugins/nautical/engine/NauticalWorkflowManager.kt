package net.osmand.plus.plugins.nautical.engine

import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.settings.enums.WidgetSize
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing
import net.osmand.plus.settings.enums.CompassMode

class NauticalWorkflowManager(private val app: OsmandApplication) {

    private val screenTouchLockManager = ScreenTouchLockManager()
    private var previousAutoZoom: Boolean? = null

    private val criticalWidgets = setOf(
        WidgetType.NAUTICAL_AWS,
        WidgetType.NAUTICAL_TWA,
        WidgetType.NAUTICAL_TWD,
        WidgetType.NAUTICAL_WIND,
        WidgetType.NAUTICAL_DEPTH,
        WidgetType.NAUTICAL_DEPTH_KEEL,
        WidgetType.NAUTICAL_HEADING_MAGNETIC,
        WidgetType.NAUTICAL_XTE,
    )

    private val nonCriticalWidgets = setOf(
        WidgetType.NAUTICAL_FUEL_LEVEL,
        WidgetType.NAUTICAL_FRESH_WATER_LEVEL,
        WidgetType.NAUTICAL_WASTE_WATER_LEVEL,
        WidgetType.NAUTICAL_WATER_TEMP,
        WidgetType.NAUTICAL_OUTSIDE_TEMP,
        WidgetType.NAUTICAL_BATTERY_VOLT,
        WidgetType.NAUTICAL_BATTERY_SOC,
        WidgetType.NAUTICAL_LOG,
        WidgetType.NAUTICAL_TRIP_LOG,
        WidgetType.NAUTICAL_PRESSURE,
        WidgetType.NAUTICAL_ENGINE_RPM,
        WidgetType.NAUTICAL_ENGINE_TEMP,
        WidgetType.NAUTICAL_OIL_PRESSURE,
        WidgetType.NAUTICAL_ENGINE_LOAD,
        WidgetType.NAUTICAL_BATTERY_CURRENT,
        WidgetType.NAUTICAL_SOLAR_CURRENT,
        WidgetType.NAUTICAL_ENGINE_RUNTIME,
        WidgetType.NAUTICAL_ENGINE_COOLANT,
        WidgetType.NAUTICAL_MAG_VARIATION,
    )

    fun onHeavyWeatherModeChanged(enabled: Boolean, activity: MapActivity?) {
        val registry = app.osmandMap.mapLayers.mapWidgetRegistry
        val appMode = app.settings.APPLICATION_MODE.get()
        
        if (enabled) {
            // 1. Suppress non-critical widgets
            for (widgetType in nonCriticalWidgets) {
                registry.getWidgetInfoForType(widgetType).forEach { info: MapWidgetInfo ->
                    registry.enableDisableWidgetForMode(appMode, info, false, null, false)
                }
            }

            // 2. Enlarge critical widgets
            for (widgetType in criticalWidgets) {
                registry.getWidgetInfoForType(widgetType).forEach { info: MapWidgetInfo ->
                    (info.widget as? ISupportWidgetResizing)?.widgetSizePref?.set(WidgetSize.LARGE)
                }
            }

            // 3. Lock map view to a centered 1-nm vector cone
            activity?.let {
                it.mapView.setZoomWithFloatPart(15, 0f)
                app.settings.compassMode = CompassMode.MOVEMENT_DIRECTION
                app.settings.MAP_LINKED_TO_LOCATION.set(true)
                
                // TASK-047: Disable disruptive Auto-Zoom
                if (previousAutoZoom == null) {
                    previousAutoZoom = app.settings.AUTO_ZOOM_MAP.get()
                }
                app.settings.AUTO_ZOOM_MAP.set(false)
            }

            // 4. Activate ScreenTouchLockManager
            screenTouchLockManager.setTouchLockActive(active = true)
            
            if (app.settings.NAUTICAL_TOUCH_LOCK_TOOLTIP_SHOWN.get() == false) {
                app.runInUIThread {
                    app.showToastMessage(app.getString(net.osmand.plus.R.string.nautical_touch_lock_active_msg))
                    app.settings.NAUTICAL_TOUCH_LOCK_TOOLTIP_SHOWN.set(true)
                }
            } else {
                app.runInUIThread {
                    app.showToastMessage(net.osmand.plus.R.string.nautical_heavy_weather_mode_active)
                }
            }
        } else {
            for (widgetType in nonCriticalWidgets) {
                registry.getWidgetInfoForType(widgetType).forEach { info: MapWidgetInfo ->
                    registry.enableDisableWidgetForMode(appMode, info, null, null, false)
                }
            }
            
            for (widgetType in criticalWidgets) {
                registry.getWidgetInfoForType(widgetType).forEach { info: MapWidgetInfo ->
                    (info.widget as? ISupportWidgetResizing)?.widgetSizePref?.resetToDefault()
                }
            }
            
            screenTouchLockManager.setTouchLockActive(active = false)

            // Restore Auto-Zoom
            previousAutoZoom?.let {
                app.settings.AUTO_ZOOM_MAP.set(it)
                previousAutoZoom = null
            }
        }

        activity?.runOnUiThread {
            app.osmandMap.mapLayers.mapInfoLayer.recreateControls()
            activity.mapView.refreshMap()
        }
    }

    fun getScreenTouchLockManager(): ScreenTouchLockManager = screenTouchLockManager
}
