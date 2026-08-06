package net.osmand.plus.views.mapwidgets.widgets

import androidx.core.content.ContextCompat
import net.osmand.plus.R
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
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        if (state.velocityMadeGood == null) {
            setText("--", "kn")
            return
        }

        val vmg = state.velocityMadeGood * 1.94384 // m/s to knots
        val text = String.format(Locale.US, "%.1f", vmg)
        setText(text, "kn")
    }
}
