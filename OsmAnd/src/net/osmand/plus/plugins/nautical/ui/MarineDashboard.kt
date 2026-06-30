package net.osmand.plus.plugins.nautical.ui

import android.util.Log
import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget
import net.osmand.plus.R

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    class MarineTextWidget(activity: MapActivity, type: WidgetType, id: String, panel: Any?) :
        TextInfoWidget(activity, type, id, null) {

        override fun updateInfo(view: View, drawSettings: net.osmand.plus.views.layers.base.OsmandMapLayer.DrawSettings?) { }
        override fun updateInfo(drawSettings: net.osmand.plus.views.layers.base.OsmandMapLayer.DrawSettings?) { }

        fun setViewVisible(visible: Boolean) {
            try {
                // Accessing the view via reflection to avoid Unresolved reference
                val field = TextInfoWidget::class.java.getDeclaredField("view")
                field.isAccessible = true
                val v = field.get(this) as? View
                v?.visibility = if (visible) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("NauticalWidgets", "Visibility field access failed")
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
        try {
            // 1. Get the widgetsPanel via reflection from mapLayers
            val mapLayers = activity.mapLayers
            val panelField = mapLayers.javaClass.getDeclaredField("widgetsPanel")
            panelField.isAccessible = true
            val panel = panelField.get(mapLayers)

            // 2. Create widgets
            val type = WidgetType.values().firstOrNull() ?: return

            depthWidget = MarineTextWidget(activity, type, "nautical_depth", null)
            windWidget = MarineTextWidget(activity, type, "nautical_wind", null)

            // 3. Force add using reflection
            val addMethod = panel?.javaClass?.getMethod("addWidget", TextInfoWidget::class.java)
            addMethod?.invoke(panel, depthWidget)
            addMethod?.invoke(panel, windWidget)

            Log.d("NauticalWidgets", "Widgets manually injected via reflection")
        } catch (e: Exception) {
            Log.e("NauticalWidgets", "Dashboard init failed: ${e.message}", e)
        }

        engine.registerListener(marineStateListener)
    }

    fun destroy() {
        engine.unregisterListener(marineStateListener)
    }
}