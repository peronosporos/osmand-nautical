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
                val coeff = net.osmand.shared.units.SpeedConstants.KNOTS
                g.setData(engine.getWindHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_VMG -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = net.osmand.shared.units.SpeedConstants.KNOTS
                g.setData(engine.getVmgHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_SOG -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = net.osmand.shared.units.SpeedConstants.KNOTS
                g.setData(engine.getSogHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_STW -> {
                val unit = mapActivity.getString(R.string.nautical_unit_knots)
                val coeff = net.osmand.shared.units.SpeedConstants.KNOTS
                g.setData(engine.getStwHistory().map { it * coeff }, unit)
            }
            WidgetType.NAUTICAL_COG -> {
                g.setData(engine.getCogHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_ENGINE_RPM -> {
                g.setData(engine.getRpmHistory(), "rpm")
            }
            WidgetType.NAUTICAL_BATTERY_VOLT -> {
                g.setData(engine.getVoltHistory(), "V")
            }
            WidgetType.NAUTICAL_BATTERY_SOC -> {
                g.setData(engine.getSocHistory().map { it * 100.0 }, "%")
            }
            WidgetType.NAUTICAL_ENGINE_TEMP -> {
                g.setData(engine.getTempEngineHistory().map { it - 273.15 }, "°C")
            }
            WidgetType.NAUTICAL_WATER_TEMP -> {
                g.setData(engine.getWaterTempHistory().map { it - 273.15 }, "°C")
            }
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> {
                g.setData(engine.getOutsideTempHistory().map { it - 273.15 }, "°C")
            }
            WidgetType.NAUTICAL_PRESSURE -> {
                g.setData(engine.getPressureHistory().map { it / 100.0 }, "hPa")
            }
            WidgetType.NAUTICAL_ROLL -> {
                g.setData(engine.getRollHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_PITCH -> {
                g.setData(engine.getPitchHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_ROT -> {
                g.setData(engine.getRotHistory().map { Math.toDegrees(it) * 60.0 }, "°/m")
            }
            WidgetType.NAUTICAL_XTE -> {
                g.setData(engine.getXteHistory().map { it / 1852.0 }, "nm")
            }
            WidgetType.NAUTICAL_TTW -> {
                g.setData(engine.getTtwHistory().map { it / 60.0 }, "min")
            }
            WidgetType.NAUTICAL_DTW -> {
                g.setData(engine.getDtwHistory().map { it / 1852.0 }, "nm")
            }
            WidgetType.NAUTICAL_AWA -> {
                g.setData(engine.getAwaHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_AWS -> {
                val knotsCoeff = net.osmand.shared.units.SpeedConstants.KNOTS
                g.setData(engine.getAwsHistory().map { it * knotsCoeff }, mapActivity.getString(R.string.nautical_unit_knots))
            }
            WidgetType.NAUTICAL_TWA -> {
                g.setData(engine.getTwaHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_POLAR_RATIO -> {
                g.setData(engine.getPolarRatioHistory().map { it * 100.0 }, mapActivity.getString(R.string.nautical_unit_percent))
            }
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> {
                g.setData(engine.getMagHdgHistory().map { Math.toDegrees(it) }, "°")
            }
            WidgetType.NAUTICAL_LOG -> {
                g.setData(engine.getLogHistory().map { it / 1852.0 }, mapActivity.getString(R.string.nautical_unit_nm))
            }
            WidgetType.NAUTICAL_TRIP_LOG -> {
                g.setData(engine.getTripLogHistory().map { it / 1852.0 }, mapActivity.getString(R.string.nautical_unit_nm))
            }
            WidgetType.NAUTICAL_DEPTH_KEEL -> {
                g.setData(engine.getDepthKeelHistory(), mapActivity.getString(R.string.nautical_unit_meters))
            }
            WidgetType.NAUTICAL_FUEL_LEVEL -> {
                g.setData(engine.getFuelHistory().map { it * 100.0 }, mapActivity.getString(R.string.nautical_unit_percent))
            }
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> {
                g.setData(engine.getFreshWaterHistory().map { it * 100.0 }, mapActivity.getString(R.string.nautical_unit_percent))
            }
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> {
                g.setData(engine.getWasteHistory().map { it * 100.0 }, mapActivity.getString(R.string.nautical_unit_percent))
            }
            WidgetType.NAUTICAL_OIL_PRESSURE -> {
                g.setData(engine.getOilPressureHistory().map { it / 100000.0 }, mapActivity.getString(R.string.nautical_unit_bar))
            }
            WidgetType.NAUTICAL_ENGINE_LOAD -> {
                g.setData(engine.getEngineLoadHistory().map { it * 100.0 }, mapActivity.getString(R.string.nautical_unit_percent))
            }
            WidgetType.NAUTICAL_BATTERY_CURRENT -> {
                g.setData(engine.getBatteryCurrentHistory(), mapActivity.getString(R.string.nautical_unit_ampere))
            }
            WidgetType.NAUTICAL_SOLAR_CURRENT -> {
                g.setData(engine.getSolarCurrentHistory(), mapActivity.getString(R.string.nautical_unit_ampere))
            }
            WidgetType.NAUTICAL_TWD -> {
                g.setData(engine.getTwdHistory().map { Math.toDegrees(it) }, "°")
            }
            else -> {}
        }
    }
}
