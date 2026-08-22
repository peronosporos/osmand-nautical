package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalMobWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    override fun getWidgetName(): String? = null

    override fun updateIcon() {
        val isActive = settings.NAUTICAL_MOB_ACTIVE.get()
        val color = if (isActive) {
            ContextCompat.getColor(app, R.color.text_color_negative) // Emergency Red
        } else {
            settings.applicationMode.getProfileColor(isNightMode)
        }
        setImageDrawable(iconsCache.getPaintedIcon(R.drawable.ic_action_alert, color))
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun setupView(view: View) {
        super.setupView(view)
        view.setOnClickListener {
            val isActive = settings.NAUTICAL_MOB_ACTIVE.get()
            if (isActive) {
                // Clear MOB
                settings.NAUTICAL_MOB_ACTIVE.set(false)
                updateInfo(null)
            }
        }
        view.setOnLongClickListener {
            val isActive = settings.NAUTICAL_MOB_ACTIVE.get()
            if (!isActive) {
                // Trigger MOB
                mapActivity.app.runInUIThread {
                    mapActivity.app.showToastMessage(R.string.nautical_mob_label)
                    settings.NAUTICAL_MOB_ACTIVE.set(true)
                    val loc = mapActivity.app.locationProvider.lastKnownLocation
                    if (loc != null) {
                        settings.NAUTICAL_MOB_LAT.set(loc.latitude)
                        settings.NAUTICAL_MOB_LON.set(loc.longitude)
                        settings.NAUTICAL_MOB_TIMESTAMP.set(System.currentTimeMillis())
                    }
                }
                updateInfo(null)
                true
            } else {
                false
            }
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val isActive = settings.NAUTICAL_MOB_ACTIVE.get()
        if (isActive) {
            setText(mapActivity.getString(R.string.nautical_mob_label), "")
        } else {
            setText("MOB", "")
        }
        updateIcon()
    }
}
