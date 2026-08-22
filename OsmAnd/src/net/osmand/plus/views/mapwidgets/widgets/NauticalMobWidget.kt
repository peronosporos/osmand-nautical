package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.data.LatLon
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

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
                androidx.appcompat.app.AlertDialog.Builder(mapActivity)
                    .setTitle(R.string.nautical_mob_label)
                    .setMessage(R.string.nautical_disarm_mob_confirm)
                    .setPositiveButton(R.string.shared_string_yes) { _, _ ->
                        NauticalPlugin.getInstance()?.mobViewModel?.cancelMob()
                        settings.NAUTICAL_MOB_ACTIVE.set(false)
                        updateInfo(null)
                    }
                    .setNegativeButton(R.string.shared_string_no, null)
                    .show()
            } else {
                val loc = mapActivity.app.locationProvider.lastKnownLocation
                val latLon = if (loc != null) LatLon(loc.latitude, loc.longitude) else LatLon(0.0, 0.0)
                
                settings.NAUTICAL_MOB_ACTIVE.set(true)
                settings.NAUTICAL_MOB_LAT.set(latLon.latitude)
                settings.NAUTICAL_MOB_LON.set(latLon.longitude)
                settings.NAUTICAL_MOB_TIMESTAMP.set(System.currentTimeMillis())
                
                NauticalPlugin.getInstance()?.mobViewModel?.triggerMob(latLon, MobTriggerSource.BUTTON)
                mapActivity.app.showToastMessage(R.string.nautical_mob_triggered)
                
                try {
                    val targetPointsHelper = mapActivity.app.targetPointsHelper
                    targetPointsHelper.removeAllWayPoints(false, true)
                    targetPointsHelper.navigateToPoint(latLon, true, -1)
                } catch (_: Exception) {}
                
                updateInfo(null)
            }
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val isActive = settings.NAUTICAL_MOB_ACTIVE.get()
        if (isActive) {
            val mobLat = settings.NAUTICAL_MOB_LAT.get()
            val mobLon = settings.NAUTICAL_MOB_LON.get()
            val ownLoc = mapActivity.app.locationProvider.lastKnownLocation
            if (ownLoc != null && mobLat != 0.0 && mobLon != 0.0) {
                val targetLoc = net.osmand.Location("MOB").apply {
                    latitude = mobLat
                    longitude = mobLon
                }
                val distNm = ownLoc.distanceTo(targetLoc) / 1852.0
                val bearingDeg = (ownLoc.bearingTo(targetLoc) + 360f) % 360f
                setText(String.format(Locale.US, "%.2f NM", distNm), String.format(Locale.US, "%03.0f°", bearingDeg))
            } else {
                setText(mapActivity.getString(R.string.nautical_mob_label), "")
            }
        } else {
            setText("MOB", "")
        }
        updateIcon()
    }
}
