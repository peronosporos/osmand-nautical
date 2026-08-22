package net.osmand.plus.views.mapwidgets.widgets

import android.util.TypedValue
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalDisplayModeWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    override fun updateIcon() {
        val mode = app.settings.NAUTICAL_DISPLAY_MODE.get() ?: NauticalDisplayMode.NORMAL
        val typedValue = TypedValue()
        mapActivity.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
        var iconColor = typedValue.data
        
        val iconRes = when (mode) {
            NauticalDisplayMode.NORMAL -> R.drawable.ic_action_sun
            NauticalDisplayMode.SUNLIGHT -> R.drawable.ic_action_light_bulb_on
            NauticalDisplayMode.DARK -> {
                iconColor = ContextCompat.getColor(mapActivity, R.color.active_color_primary_light)
                R.drawable.ic_action_red_filter_overlay_on
            }
        }
        setImageDrawable(iconsCache.getPaintedIcon(iconRes, iconColor))
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val mode = app.settings.NAUTICAL_DISPLAY_MODE.get() ?: NauticalDisplayMode.NORMAL
        
        val text = mapActivity.getString(mode.titleId)
        setText(text, "")
        
        updateIcon()

        if (mode == NauticalDisplayMode.SUNLIGHT) {
            view.setBackgroundColor(0x44FFD700) // Semi-transparent Gold
        } else {
            view.background = null
        }
        
        view.setOnClickListener {
            val nextMode = when (mode) {
                NauticalDisplayMode.NORMAL -> NauticalDisplayMode.SUNLIGHT
                NauticalDisplayMode.SUNLIGHT -> NauticalDisplayMode.DARK
                NauticalDisplayMode.DARK -> NauticalDisplayMode.NORMAL
            }
            app.settings.NAUTICAL_DISPLAY_MODE.set(nextMode)
            updateInfo(drawSettings)
        }
    }
}
