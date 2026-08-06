package net.osmand.plus.views.mapwidgets.widgets

import androidx.core.content.ContextCompat
import net.osmand.plus.R
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
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    override fun updateIcon() {
        val iconId = getIconId()
        if (iconId != 0) {
            val color = ContextCompat.getColor(app, R.color.map_widget_icon_color)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine
        if (engine == null) {
            setText("--", "N/A")
            return
        }
        val state = engine.getCurrentState()

        if (state.polarSpeedRatio == null) {
            setText("--", "%")
            return
        }

        val ratio = state.polarSpeedRatio * 100.0
        val text = String.format(Locale.US, "%.0f", ratio)
        setText(text, "%")
    }
}
