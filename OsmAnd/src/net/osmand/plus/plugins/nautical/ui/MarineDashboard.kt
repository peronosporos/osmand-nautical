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
import java.lang.reflect.Proxy

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    class MarineTextWidget(activity: MapActivity, type: WidgetType, id: String, panel: WidgetsPanel?) :
        TextInfoWidget(activity, type, id, panel) {

        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) { }
        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) { }

        fun setViewVisible(visible: Boolean) {
            try {
                // Using 'view' field as identified in your build
                val field = TextInfoWidget::class.java.getDeclaredField("view")
                field.isAccessible = true
                val v = field.get(this) as? View
                v?.visibility = if (visible) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("NauticalWidgets", "Visibility field not found", e)
            }
        }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        app.runInUIThread {
            state.depthBelowTransducer?.let {
                depthWidget?.setText(String.format("%.1f", it), "m")
                depthWidget?.setViewVisible(true)
            }
            state.windSpeedTrue?.let {
                windWidget?.setText(String.format("%.1f", it * 1.94384), "kn")
                windWidget?.setViewVisible(true)
            }
        }
    }

    fun init(activity: MapActivity) {
        val registry = activity.mapLayers.mapWidgetRegistry

        try {
            // 1. DYNAMIC TYPE RESOLUTION: Get the first available WidgetType to avoid 'INFO' error
            val widgetType = WidgetType.values().firstOrNull() ?: return

            // 2. DYNAMIC REGISTRATION
            val creatorClass = Class.forName("net.osmand.plus.views.mapwidgets.WidgetCreator")
            val creator = Proxy.newProxyInstance(creatorClass.classLoader, arrayOf(creatorClass)) { _, method, args ->
                if (method.name == "createWidget") {
                    val wType = args[0] as WidgetType
                    val wId = args[1] as String
                    val wPanel = args[2] as WidgetsPanel?

                    Log.d("NauticalWidgets", "OsmAnd requested creation of: $wId")

                    val widget = MarineTextWidget(activity, wType, wId, wPanel).apply {
                        setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
                    }

                    if (wId == "nautical_depth") depthWidget = widget
                    if (wId == "nautical_wind") windWidget = widget

                    // FORCE ADD via Reflection
                    try {
                        val addMethod = wPanel?.javaClass?.getMethod("addWidget", TextInfoWidget::class.java)
                        addMethod?.invoke(wPanel, widget)
                        Log.d("NauticalWidgets", "Widget $wId forced into panel")
                    } catch (e: Exception) {
                        Log.e("NauticalWidgets", "Could not force add $wId: ${e.message}")
                    }

                    widget
                } else null
            }

            val regMethod = registry.javaClass.getMethod("registerWidget", WidgetType::class.java, creatorClass)
            regMethod.invoke(registry, widgetType, creator)
            Log.d("NauticalWidgets", "Registration call complete")

        } catch (e: Exception) {
            Log.e("NauticalWidgets", "Registration error", e)
        }

        engine.registerListener(marineStateListener)
    }

    fun destroy() {
        engine.unregisterListener(marineStateListener)
    }
}