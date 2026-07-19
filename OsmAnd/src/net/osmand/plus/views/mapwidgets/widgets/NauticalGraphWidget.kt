package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

class NauticalGraphWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?,
) : MapWidget(mapActivity, widgetType, customId, panel) {

    private var graphView: NauticalGraphView? = null
    private var lastUpdateTime = 0L

    private val marineStateListener: (MarineState) -> Unit = {
        val now = System.currentTimeMillis()
        if ((now - lastUpdateTime) > 500) { // Throttle 2Hz
            lastUpdateTime = now
            mapActivity.runOnUiThread {
                updateInfo(view, null)
            }
        }
    }

    override fun getLayoutId(): Int = R.layout.widget_nautical_graph

    override fun setupView(view: View) {
        graphView = view.findViewById(R.id.graph_view)

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
            val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
            dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
        }
    }

    override fun updateInfo(view: View, drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val g = graphView ?: return

        when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> {
                val unit = mapActivity.getString(R.string.nautical_unit_meters)
                g.setData(engine.getDepthHistory(), unit)
            }
            WidgetType.NAUTICAL_WIND -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = 1.94384
                g.setData(engine.getWindHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_VMG -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = 1.94384
                g.setData(engine.getVmgHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_SOG -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = 1.94384
                g.setData(engine.getSogHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_STW -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = 1.94384
                g.setData(engine.getStwHistory().map { it * coeff }, unit)
            }
            else -> {}
        }
    }
}
