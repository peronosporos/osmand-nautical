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

class NauticalCompassWidget(
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

    override fun getWidgetName(): String = mapActivity.getString(R.string.map_widget_compass)

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

        val heading = state.headingMagnetic ?: state.headingTrue
        val mainText = heading?.let { String.format(Locale.US, "%.0f°", Math.toDegrees(it)) } ?: "--"

        val variation = state.magneticVariation?.let { 
            val deg = Math.toDegrees(it)
            val dir = if (deg >= 0) "E" else "W"
            String.format(Locale.US, "%.1f°%s", Math.abs(deg), dir)
        } ?: ""

        setText(mainText, variation)
        updateIcon()
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                NauticalCompassWizardDialog().show(mapActivity.supportFragmentManager, NauticalCompassWizardDialog.TAG)
            }
        }
    }
}
