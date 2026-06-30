package net.osmand.plus.plugins.nautical.ui

import android.view.View
import androidx.annotation.StringRes
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.R
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.ScreenLayoutMode
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.shared.settings.enums.MetricsConstants
import android.util.Log

class MarineDashboard(
    private val app: OsmandApplication,
    private val engine: SignalKEngine
) {
    class MarineTextWidget(activity: MapActivity, type: WidgetType, id: String) :
        TextInfoWidget(activity, type, id, null) {
        override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) { }
        override fun updateInfo(drawSettings: OsmandMapLayer.DrawSettings?) { }
    }

    class MarineWidgetInfo(
        key: String,
        widget: MarineTextWidget,
        message: String,
        private val widgetPanel: WidgetsPanel,
        @StringRes messageId: Int
    ) : MapWidgetInfo(
        key,
        widget,
        R.drawable.ic_action_info_dark,
        R.drawable.ic_action_info_dark,
        messageId,
        message,
        0,
        10,
        widgetPanel
    ) {
        override fun getUpdatedPanel(
            appMode: ApplicationMode,
            layoutMode: ScreenLayoutMode?
        ): WidgetsPanel {
            return widgetPanel
        }
    }

    private var depthWidget: MarineTextWidget? = null
    private var windWidget: MarineTextWidget? = null

    private val marineStateListener: (MarineState) -> Unit = { state ->
        app.runInUIThread {
            val metrics = app.settings.METRIC_SYSTEM.getModeValue(ApplicationMode.BOAT)
                ?: MetricsConstants.KILOMETERS_AND_METERS

            val useKnots = (metrics == MetricsConstants.NAUTICAL_MILES_AND_METERS ||
                    metrics == MetricsConstants.NAUTICAL_MILES_AND_FEET)

            state.depthBelowTransducer?.let {
                depthWidget?.setText(String.format("%.1f", it), "m")
            }
            state.windSpeedTrue?.let {
                val speedValue = if (useKnots) it * 1.94384 else it
                val unitLabel = if (useKnots) "kn" else "m/s"
                windWidget?.setText(String.format("%.1f", speedValue), unitLabel)
            }
        }
    }

    fun init(activity: MapActivity) {
        Log.d("NauticalWidgets", "Initializing MarineDashboard widgets unconditionally...")

        val registry = activity.mapLayers.mapWidgetRegistry
        val wType = WidgetType.values().firstOrNull() ?: return

        depthWidget = MarineTextWidget(activity, wType, "nautical_depth").apply {
            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
            setText("---", "m")
        }

        windWidget = MarineTextWidget(activity, wType, "nautical_wind").apply {
            setIcons(R.drawable.ic_action_info_dark, R.drawable.ic_action_info_dark)
            setText("---", "kn")
        }

        val depthInfo = MarineWidgetInfo(
            "nautical_depth",
            depthWidget!!,
            "Depth",
            WidgetsPanel.LEFT,
            R.string.nautical_widget_depth_label
        )

        val windInfo = MarineWidgetInfo(
            "nautical_wind",
            windWidget!!,
            "Wind",
            WidgetsPanel.LEFT,
            R.string.nautical_widget_wind_label
        )

        // Register widgets globally. No ApplicationMode restrictions applied.
        registry.registerWidget(depthInfo)
        registry.registerWidget(windInfo)

        engine.registerListener(marineStateListener)
        Log.d("NauticalWidgets", "Widget registration complete.")
    }

    fun destroy() {
        engine.unregisterListener(marineStateListener)
        depthWidget = null
        windWidget = null
    }
}