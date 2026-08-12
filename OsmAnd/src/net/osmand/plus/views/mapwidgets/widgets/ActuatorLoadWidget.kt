package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class ActuatorLoadWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread { updateInfo(null) }
    }

    override fun getWidgetName(): String = mapActivity.getString(R.string.nautical_actuator_load)

    override fun getIconId(): Int = R.drawable.ic_action_settings // Generic hardware icon

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
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        
        val load = state.actuatorDutyCycle ?: 0.0
        val current = state.actuatorCurrent
        
        val mainText = String.format(Locale.US, "%.0f%%", load * 100)
        val subText = if (current != null) {
            String.format(Locale.US, "%.1f A", current)
        } else ""

        setText(mainText, subText)
        
        val isStale = state.stalePaths.contains("steering.autopilot.actions.dutyCycle")
        if (isStale) {
            contentView?.alpha = 0.5f
        } else {
            contentView?.alpha = 1.0f
        }
    }
}
