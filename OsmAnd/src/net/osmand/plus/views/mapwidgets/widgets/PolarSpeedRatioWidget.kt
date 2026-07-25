package net.osmand.plus.views.mapwidgets.widgets

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class PolarSpeedRatioWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        if (state == null || state.polarSpeedRatio == null) {
            setText("--", "%")
            return
        }

        val ratio = state.polarSpeedRatio * 100.0
        val text = String.format(Locale.US, "%.0f", ratio)
        setText(text, "%")
    }
}
