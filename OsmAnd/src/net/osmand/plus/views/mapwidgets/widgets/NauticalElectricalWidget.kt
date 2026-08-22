package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalElectricalDashboardBottomSheet
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

    init {
        setIcons(widgetType)
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val color = settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun setupView(view: View) {
        super.setupView(view)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.post {
                        if (v.isAttachedToWindow && !mapActivity.isFinishing && !mapActivity.isDestroyed) {
                            val broker = NauticalPlugin.engine?.dataBroker
                            if (broker != null) {
                                dataJob?.cancel()
                                dataJob = mapActivity.lifecycleScope.launch {
                                    mapActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                        broker.marineState.collect {
                                            updateInfo(null)
                                            updateWidgetView()
                                            v.invalidate()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    dataJob?.cancel()
                    dataJob = null
                }
            },
        )
        
        view.setOnClickListener {
            NauticalElectricalDashboardBottomSheet.show(mapActivity.supportFragmentManager)
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val engine = NauticalPlugin.engine
        if (engine == null) {
            setText("--", "N/A")
            return
        }
        val state = engine.getCurrentState()

        val battery = state.batteries.values.firstOrNull()
        if (battery?.voltage != null) {
            val volt = String.format(Locale.US, "%.1f V", battery.voltage)
            val sub = if (battery.current != null) {
                String.format(Locale.US, "%+.1f A", battery.current)
            } else ""
            setText(volt, sub)
        } else {
            setText("--", "")
        }
    }
}
