package net.osmand.plus.views.mapwidgets.widgets

import android.annotation.SuppressLint
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.shared.settings.enums.MetricsConstants
import net.osmand.shared.units.SpeedUnits
import java.util.*

class MarineTextWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    private var lastUpdateTime = 0L
    private val marineStateListener: (MarineState) -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > 200) { // Throttle 5Hz
            lastUpdateTime = now
            mapActivity.runOnUiThread {
                updateInfo(null)
            }
        }
    }

    override fun setupView(view: View) {
        super.setupView(view)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                NauticalPlugin.engine?.registerListener(marineStateListener)
            }

            override fun onViewDetachedFromWindow(v: View) {
                NauticalPlugin.engine?.unregisterListener(marineStateListener)
            }
        })
    }

    override fun getOnClickListener(): View.OnClickListener? {
        return View.OnClickListener {
            if (!mapActivity.isFinishing) {
                val dialog = NauticalDataBottomSheet.newInstance(this.widgetType)
                dialog.show(mapActivity.supportFragmentManager, "nautical_graph")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        updateIcon()

        if (state == null || state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.DISCONNECTED) {
            setText(mapActivity.getString(R.string.nautical_status_off), mapActivity.getString(R.string.n_a))
        } else if (state.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.STALE) {
            setText(mapActivity.getString(R.string.nautical_connection_stale), "")
        } else {
            val settings = mapActivity.app.settings
            val metrics = settings.METRIC_SYSTEM.get()

            when (widgetType) {
                WidgetType.NAUTICAL_DEPTH -> handleDepthUpdate(state, metrics)
                WidgetType.NAUTICAL_WIND -> handleWindUpdate(state, metrics)
                WidgetType.NAUTICAL_VMG -> handleVmgUpdate(state, metrics)
                WidgetType.NAUTICAL_COG -> handleCogUpdate(state)
                else -> {}
            }
        }
    }

    private fun handleVmgUpdate(state: MarineState, metrics: MetricsConstants) {
        val vmg = state.velocityMadeGood
        if (vmg != null) {
            val speedUnit = metrics.getSpeedUnit()
            val converted = vmg * speedUnit.conversionCoefficient
            val unitStr = when (speedUnit) {
                SpeedUnits.KNOTS -> mapActivity.getString(R.string.nautical_unit_knots)
                SpeedUnits.KILOMETERS_PER_HOUR -> mapActivity.getString(R.string.km_h)
                SpeedUnits.MILES_PER_HOUR -> mapActivity.getString(R.string.mile_per_hour)
                else -> mapActivity.getString(R.string.m_s)
            }
            setText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_knots))
        }
    }

    private fun handleCogUpdate(state: MarineState) {
        val cog = state.courseOverGroundTrue
        if (cog != null) {
            val cogDeg = Math.toDegrees(cog)
            setText(String.format(Locale.US, "%.0f°", cogDeg), mapActivity.getString(R.string.nautical_widget_cog_label))
        } else {
            setText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_widget_cog_label))
        }
    }

    private fun handleDepthUpdate(state: MarineState, metrics: MetricsConstants) {
        var depth = state.depthBelowTransducer
        if (depth != null) {
            val useFeet = metrics.shouldUseFeet()
            val unit = if (useFeet) mapActivity.getString(R.string.nautical_unit_feet) else mapActivity.getString(R.string.nautical_unit_meters)

            if (useFeet) depth *= 3.28084
            setText(String.format(Locale.US, "%.1f", depth), unit)
        } else {
            setText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_meters))
        }
    }

    private fun handleWindUpdate(state: MarineState, metrics: MetricsConstants) {
        val wind = state.windSpeedTrue
        if (wind != null) {
            val speedUnit = metrics.getSpeedUnit()
            val converted = wind * speedUnit.conversionCoefficient
            val unitStr = when (speedUnit) {
                SpeedUnits.KNOTS -> mapActivity.getString(R.string.nautical_unit_knots)
                SpeedUnits.KILOMETERS_PER_HOUR -> mapActivity.getString(R.string.km_h)
                SpeedUnits.MILES_PER_HOUR -> mapActivity.getString(R.string.mile_per_hour)
                else -> mapActivity.getString(R.string.m_s)
            }
            setText(String.format(Locale.US, "%.1f", converted), unitStr)
        } else {
            setText(mapActivity.getString(R.string.n_a), mapActivity.getString(R.string.nautical_unit_knots))
        }
    }
}
