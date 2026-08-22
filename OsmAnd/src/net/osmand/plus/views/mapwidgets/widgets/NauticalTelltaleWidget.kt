package net.osmand.plus.views.mapwidgets.widgets

import android.graphics.Color
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

/**
 * Visual representation of electronic telltales.
 * Shows flow state (STALLED, LAMINAR) for Port and Starboard.
 */
class NauticalTelltaleWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private var dataJob: Job? = null
    private val laminarColor = Color.GREEN
    private val stalledColor = Color.RED
    private val neutralColor = Color.GRAY

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

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val state = NauticalPlugin.engine?.getCurrentState()
        if (state == null) {
            setText("--", "")
            return
        }

        val portState = state.customValues["performance.telltales.port.state"]
        val stbdState = state.customValues["performance.telltales.starboard.state"]

        if (portState == null && stbdState == null) {
            setText("Telltale", "No Data")
            view.alpha = 0.5f
            return
        }

        val main = if ((portState ?: 0.5) > 0.5) "OK" else "STALL"
        val sub = if ((stbdState ?: 0.5) > 0.5) "OK" else "STALL"

        setText("P:$main", "S:$sub")
        
        val color = if (((portState ?: 0.5) < 0.5 || (stbdState ?: 0.5) < 0.5)) stalledColor else laminarColor
        val iconColor = if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) neutralColor else color
        view.alpha = if (iconColor == neutralColor) 0.5f else 1.0f
    }
}
