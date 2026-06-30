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
    class MarineTextWidget(activity: MapActivity, type: WidgetType, id: String, panel: WidgetsPanel?) :
        TextInfoWidget(activity, type, id, panel) {
        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) { }
        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) { }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    fun init(activity: MapActivity) {
        val registry = activity.mapLayers.mapWidgetRegistry
        val type = try { WidgetType.valueOf("INFO") } catch (e: Exception) { WidgetType.values().firstOrNull() ?: return }

        // Use the factory pattern that the MapWidgetRegistry expects
        try {
            val registryClass = registry.javaClass
            // We search for a method that takes (WidgetType, WidgetCreator)
            val regMethod = registryClass.methods.find {
                it.name == "registerWidget" && it.parameterTypes.size == 2
            }

            if (regMethod != null) {
                val creator = net.osmand.plus.views.mapwidgets.WidgetCreator { wType, wId, wPanel ->
                    if (wId == "nautical_depth") {
                        depthWidget = MarineTextWidget(activity, wType, wId, wPanel).apply {
                            setText("---", "m")
                            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
                        }
                        depthWidget
                    } else {
                        windWidget = MarineTextWidget(activity, wType, wId, wPanel).apply {
                            setText("---", "kn")
                            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
                        }
                        windWidget
                    }
                }

                regMethod.invoke(registry, type, creator)
                Log.d("NauticalPlugin", "Widgets successfully registered via WidgetCreator")
            } else {
                Log.e("NauticalPlugin", "CRITICAL: Could not find registerWidget(WidgetType, WidgetCreator)")
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Registration failed", e)
        }

        engine.registerListener { state ->
            app.runInUIThread {
                state.depthBelowTransducer?.let { depthWidget?.setText(String.format("%.1f", it), "m") }
                state.windSpeedTrue?.let { windWidget?.setText(String.format("%.1f", it * 1.94384), "kn") }
            }
        }
    }

    fun destroy() {
        depthWidget = null
        windWidget = null
    }
}