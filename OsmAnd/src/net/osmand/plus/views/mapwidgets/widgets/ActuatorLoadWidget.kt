package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
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

    private var loadText: TextView? = null
    private var currentText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var widgetContent: View? = null
    private var staleBadge: View? = null
    private var lastUpdateTime = 0L

    private val marineStateListener: (MarineState) -> Unit = {
        val now = System.currentTimeMillis()
        if ((now - lastUpdateTime) > 500) {
            lastUpdateTime = now
            mapActivity.runOnUiThread { updateInfo(null) }
        }
    }

    override fun getWidgetName(): String = mapActivity.getString(R.string.nautical_actuator_load)

    override fun getIconId(): Int = R.drawable.ic_action_settings // Generic hardware icon

    override fun getContentLayoutId(): Int = R.layout.map_hud_actuator_widget

    override fun setupView(view: View) {
        super.setupView(view)
        loadText = view.findViewById(R.id.actuator_load_text)
        currentText = view.findViewById(R.id.actuator_current_text)
        progressBar = view.findViewById(R.id.actuator_progress)
        widgetContent = view.findViewById(R.id.widget_content)
        staleBadge = view.findViewById(R.id.stale_badge)

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
        
        val isStale = state.stalePaths.contains("steering.autopilot.actions.dutyCycle")
        if (isStale) {
            widgetContent?.alpha = 0.35f
            staleBadge?.visibility = View.VISIBLE
        } else {
            widgetContent?.alpha = 1.0f
            staleBadge?.visibility = View.GONE
        }

        val load = state.actuatorDutyCycle ?: 0.0
        val current = state.actuatorCurrent
        val threshold = app.settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get() / 100.0
        
        loadText?.text = String.format(Locale.US, "%.0f%%", load * 100)
        progressBar?.progress = (load * 100).toInt()
        
        currentText?.text = if (current != null) {
            String.format(Locale.US, "%.1f A", current)
        } else ""

        // ALARM STATE (Visual)
        if (state.isActuatorOverloaded || (load > threshold)) {
            val alarmColor = ContextCompat.getColor(mapActivity, R.color.text_color_negative)
            loadText?.setTextColor(alarmColor)
            progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(alarmColor)
        } else {
            val textColor = if (nightMode) {
                ContextCompat.getColor(app, R.color.text_color_primary_dark)
            } else {
                ContextCompat.getColor(app, R.color.text_color_primary_light)
            }
            loadText?.setTextColor(textColor)
            progressBar?.progressTintList = null
        }
    }
}
