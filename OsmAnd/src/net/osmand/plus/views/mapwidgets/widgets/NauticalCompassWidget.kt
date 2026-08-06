package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import android.widget.ImageView
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.*

class NauticalCompassWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var compassIcon: ImageView? = null
    
    private val marineStateListener: (MarineState) -> Unit = { _ ->
        mapActivity.runOnUiThread { updateInfo(null) }
    }

    override fun getWidgetName(): String = mapActivity.getString(R.string.map_widget_compass)

    override fun getIconId(): Int = R.drawable.ic_action_direction_compass

    override fun getContentLayoutId(): Int = R.layout.widget_nautical_compass

    override fun setupView(view: View) {
        super.setupView(view)
        compassIcon = view.findViewById(R.id.compass_icon)

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
        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        val headingRad = state?.headingTrue
        
        if (headingRad != null) {
            val headingDeg = Math.toDegrees(headingRad).toFloat()
            val targetRotation = -headingDeg
            
            compassIcon?.let { icon ->
                val currentRotation = icon.rotation
                var diff = targetRotation - currentRotation
                while (diff > 180f) diff -= 360f
                while (diff < -180f) diff += 360f
                
                // Apply smooth interpolation to avoid needle "jump" (800ms for 1Hz updates)
                icon.animate()
                    .rotation(currentRotation + diff)
                    .setDuration(800)
                    .start()
            }
            setText(String.format(Locale.US, "%d°", headingDeg.toInt()), "")
        } else {
            setText("--°", "")
        }
        
        val isStale = (state?.connectionStatus != net.osmand.plus.plugins.nautical.engine.ConnectionStatus.CONNECTED)
        
        if (isStale) {
            compassIcon?.alpha = 0.5f
        } else {
            compassIcon?.alpha = 1.0f
        }
    }
}
