package net.osmand.plus.plugins.nautical.ui

import android.util.Log
import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.R

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    class MarineTextWidget(
        activity: MapActivity,
        type: WidgetType,
        id: String,
        panel: WidgetsPanel?
    ) : TextInfoWidget(activity, type, id, panel) {

        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) { }

        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) { }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        updateWidgets(state)
    }

    fun init(activity: MapActivity) {
        val registry = activity.mapLayers.mapWidgetRegistry

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

        // Modern Registration Block targeting the correct method
        try {
            val methods = registry.javaClass.methods

            // Find the modern registerWidget method that takes 1 parameter (the MapWidget itself)
            val regMethod = methods.find { it.name == "registerWidget" && it.parameterTypes.size == 1 }

            if (regMethod != null) {
                regMethod.invoke(registry, depthWidget)
                regMethod.invoke(registry, windWidget)
                Log.d("NauticalPlugin", "Widgets successfully registered to the UI!")
            } else {
                Log.e("NauticalPlugin", "CRITICAL: Could not find a 1-parameter registerWidget method.")
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "CRITICAL: Widget registration failed during invoke!", e)
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