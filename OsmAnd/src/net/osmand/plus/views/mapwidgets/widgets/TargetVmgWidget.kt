package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

class TargetVmgWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var dataJob: Job? = null

    init {
        setIcons(widgetType)
    }

    override fun setupView(view: View) {
        super.setupView(view)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val broker = NauticalPlugin.engine?.dataBroker
                    if (broker != null) {
                        dataJob?.cancel()
                        dataJob = mapActivity.lifecycleScope.launch(Dispatchers.Main.immediate) {
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
    }

    override fun updateIcon() {
        val iconId = getIconId()
        if (iconId != 0) {
            val color = ContextCompat.getColor(app, R.color.map_widget_icon_color)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        // Item 12: Calculate Target VMG from Polar if available
        val vmg = state.polarTargetSpeed?.let { targetSpeed ->
             NauticalPlugin.getInstance()?.tacticalProcessor?.polarDiagram?.let { pd ->
                 val tws = state.windSpeedTrue ?: 0.0
                 val twa = state.trueWindAngle ?: 0.0
                 val optimalTwa = if (abs(twa) < Math.PI / 2.0) {
                     pd.getOptimalUpwindTwaRad(tws)
                 } else {
                     pd.getOptimalDownwindTwaRad(tws)
                 }
                 targetSpeed * abs(cos(optimalTwa))
             }
        } ?: state.velocityMadeGood

        val (main, sub) = SignalKUnitConverter.formatValue(mapActivity, app.settings, vmg, SignalKPaths.PERF_VMG)
        setText(main, sub)
    }
}
