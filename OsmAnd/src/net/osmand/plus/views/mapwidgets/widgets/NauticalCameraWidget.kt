package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

/**
 * Provides access to vessel cameras via a floating PIP window.
 */
class NauticalCameraWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var pipActive = false

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        setText(mapActivity.getString(R.string.nautical_camera), if (pipActive) "LIVE" else "")
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            pipActive = !pipActive
            if (pipActive) {
                showPipOverlay()
            } else {
                hidePipOverlay()
            }
            updateInfo(null)
        }
    }

    private fun showPipOverlay() {
        // Implementation of actual stream would go here.
        // For now we simulate with a toast or placeholder in the HUD.
        mapActivity.app.showToastMessage("Connecting to Signal K ONVIF Stream...")
    }

    private fun hidePipOverlay() {
        mapActivity.app.showToastMessage("Camera Disconnected")
    }
}
