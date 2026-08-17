package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
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

    private var dataJob: kotlinx.coroutines.Job? = null

    override fun setupView(view: View) {
        super.setupView(view)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val broker = NauticalPlugin.engine?.dataBroker
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = mapActivity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            broker.marineState.collect {
                                updateInfo(null)
                                updateWidgetView()
                                v.invalidate()
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
