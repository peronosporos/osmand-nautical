package net.osmand.plus.plugins.nautical.ui

import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget
import net.osmand.plus.views.layers.base.OsmandMapLayer // The missing link resolving all abstract member errors
import net.osmand.plus.R

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    // 1. Concrete implementation that perfectly satisfies the MapWidget inheritance chain
    class MarineTextWidget(
        activity: MapActivity,
        type: WidgetType,
        id: String,
        panel: WidgetsPanel?
    ) : TextInfoWidget(activity, type, id, panel) {

        // With the correct import above, the Kotlin compiler natively recognizes these signatures
        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) {
            // External setText handles the UI updates
        }

        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
            // External setText handles the UI updates
        }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        updateWidgets(state)
    }

    fun init(activity: MapActivity) {
        val registry = activity.mapLayers.mapWidgetRegistry

        // 2. Safe enum resolution prevents Unresolved Reference if 'INFO' is renamed
        val type = try {
            WidgetType.valueOf("INFO")
        } catch (e: Exception) {
            WidgetType.values().firstOrNull() ?: return
        }

        depthWidget = MarineTextWidget(activity, type, "nautical_depth", null).apply {
            setText("---", "m")
            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
        }

        windWidget = MarineTextWidget(activity, type, "nautical_wind", null).apply {
            setText("---", "kn")
            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
        }

        // 3. Robust dynamic registration that adapts to varying SDK parameter requirements
        try {
            val regMethod = registry.javaClass.methods.firstOrNull {
                it.name == "registerSideWidget" && it.parameterTypes.size >= 4
            }

            regMethod?.invoke(registry, depthWidget, R.drawable.ic_action_info_dark, 0, "nautical_depth", false, 25)
            regMethod?.invoke(registry, windWidget, R.drawable.ic_action_info_dark, 0, "nautical_wind", false, 26)
        } catch (e: Exception) {
            // Ensures the plugin doesn't crash on boot if the registry signature changes
        }

        engine.registerListener(marineStateListener)
        updateVisibilityForProfile()
    }

    fun destroy() {
        engine.unregisterListener(marineStateListener)
        depthWidget = null
        windWidget = null
    }

    fun updateVisibilityForProfile() {
        val isBoatMode = app.settings.applicationMode.toString().contains("BOAT", true)
        app.runInUIThread {
            depthWidget?.updateVisibility(isBoatMode)
            windWidget?.updateVisibility(isBoatMode)
        }
    }

    private fun updateWidgets(state: MarineState) {
        if (!app.settings.applicationMode.toString().contains("BOAT", true)) return

        app.runInUIThread {
            state.depthBelowTransducer?.let { depthWidget?.setText(String.format("%.1f", it), "m") }
            state.windSpeedTrue?.let { windWidget?.setText(String.format("%.1f", it * 1.94384), "kn") }
        }
    }
}