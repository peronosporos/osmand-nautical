package net.osmand.plus.views.mapwidgets.widgets

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class TargetVmgWidget(
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

        if (state == null || state.velocityMadeGood == null) {
            setText("--", "kn")
            return
        }

        val vmg = state.velocityMadeGood * 1.94384 // m/s to knots
        val text = String.format(Locale.US, "%.1f", vmg)
        setText(text, "kn")
    }
}
