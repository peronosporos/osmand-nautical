package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalPilotBottomSheet
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale

class NauticalPilotWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread { updateInfo(null) }
    }

    override fun getWidgetName(): String = mapActivity.getString(R.string.nautical_autopilot)

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
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null) {
            setText("--", "")
            return
        }

        val mode = state.autopilotState.uppercase(Locale.US)
        val mainText = when (mode) {
            "STANDBY" -> mapActivity.getString(R.string.nautical_autopilot_mode_standby).uppercase(Locale.US)
            "AUTO" -> mapActivity.getString(R.string.nautical_autopilot_mode_track).uppercase(Locale.US) // Head hold
            "WIND" -> mapActivity.getString(R.string.nautical_autopilot_mode_wind).uppercase(Locale.US)
            "TRACK" -> mapActivity.getString(R.string.nautical_autopilot_mode_track).uppercase(Locale.US)
            else -> mode
        }

        val target = if (mode == "WIND") {
            state.targetWindAngleApparent?.let { String.format(Locale.US, "%.0f°", Math.toDegrees(it)) }
        } else {
            state.targetHeading?.let { String.format(Locale.US, "%.0f°", Math.toDegrees(it)) }
        }

        setText(mainText, target ?: "")
        updateIcon()
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                NauticalPilotBottomSheet().show(mapActivity.supportFragmentManager, "nautical_pilot")
            }
        }
    }
}
