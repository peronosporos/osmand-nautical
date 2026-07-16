package net.osmand.plus.views.mapwidgets.widgets

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.R

class NauticalNightVisionWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val plugin = NauticalPlugin.getInstance()
        val isEnabled = plugin?.isNightVisionEnabled ?: false
        
        val text = if (isEnabled) {
            mapActivity.getString(R.string.shared_string_on)
        } else {
            mapActivity.getString(R.string.shared_string_off)
        }
        
        setText(text, "")
        
        // Update icon color based on state
        val iconColor = if (isEnabled) {
            mapActivity.resources.getColor(R.color.active_color_primary_light, null)
        } else {
            settings.applicationMode.getProfileColor(isNightMode)
        }
        
        val iconRes = if (isEnabled) R.drawable.ic_action_red_filter_overlay_on else R.drawable.ic_action_red_filter_off
        setImageDrawable(iconsCache.getPaintedIcon(iconRes, iconColor))
        
        view.setOnClickListener {
            plugin?.toggleNightVision(mapActivity, !isEnabled)
            updateInfo(drawSettings)
        }
    }
}
