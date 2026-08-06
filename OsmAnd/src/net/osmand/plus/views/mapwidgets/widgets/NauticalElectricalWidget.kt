package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class NauticalElectricalWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread { updateInfo(null) }
    }

    override fun setupView(view: View) {
        super.setupView(view)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    NauticalPlugin.engine?.registerListener(marineStateListener)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    NauticalPlugin.engine?.unregisterListener(marineStateListener)
                }
            },
        )
        
        view.setOnClickListener {
            NauticalElectricalDashboardBottomSheet.show(mapActivity.supportFragmentManager)
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null) {
            setText("--", "")
            return
        }

        val battery = state.batteries.values.firstOrNull()
        if (battery?.voltage != null) {
            val volt = String.format(Locale.US, "%.1f V", battery.voltage)
            val sub = if (battery.current != null) {
                String.format(Locale.US, "%+.1f A", battery.current)
            } else ""
            setText(volt, sub)
        } else if (state.batteryVoltage != null) {
            setText(String.format(Locale.US, "%.1f V", state.batteryVoltage), "")
        } else {
            setText("--", "")
        }
    }
}
