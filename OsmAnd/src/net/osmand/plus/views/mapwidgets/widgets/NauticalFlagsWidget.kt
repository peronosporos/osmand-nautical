package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

/**
 * Visual guide for International Maritime Signal Flags.
 */
class NauticalFlagsWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private val flags = mapOf(
        "A" to "Diver Down",
        "B" to "Dangerous Cargo",
        "O" to "Man Overboard",
        "Q" to "Quarantine",
        "V" to "Require Assistance",
        "W" to "Medical Assistance"
    )

    private var currentFlagIndex = 0
    private val flagKeys = flags.keys.toList()

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val key = flagKeys[currentFlagIndex]
        setText(key, flags[key] ?: "")
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            currentFlagIndex = (currentFlagIndex + 1) % flagKeys.size
            updateInfo(null)
        }
    }
}
