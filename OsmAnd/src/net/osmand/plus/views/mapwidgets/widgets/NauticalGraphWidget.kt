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

    private val marineStateListener: (MarineState) -> Unit = {
        mapActivity.runOnUiThread {
            updateInfo(view, null)
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
        val engine = NauticalPlugin.engine
        if (engine == null) {
            updateVisibility(true)
            return
        }
        val state = engine.getCurrentState()
        val g = graphView ?: return
        val badge = view.findViewById<View>(R.id.stale_badge)

        val path = when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> "environment.depth.belowTransducer"
            WidgetType.NAUTICAL_WIND -> "environment.wind.speedTrue"
            WidgetType.NAUTICAL_VMG -> "performance.velocityMadeGood"
            WidgetType.NAUTICAL_SOG -> "navigation.speedOverGround"
            WidgetType.NAUTICAL_STW -> "navigation.speedThroughWater"
            WidgetType.NAUTICAL_COG -> "navigation.courseOverGroundTrue"
            WidgetType.NAUTICAL_ENGINE_RPM -> "propulsion.0.revolutions"
            WidgetType.NAUTICAL_BATTERY_VOLT -> "electrical.batteries.0.voltage"
            WidgetType.NAUTICAL_BATTERY_SOC -> "electrical.batteries.0.capacity.stateOfCharge"
            WidgetType.NAUTICAL_ENGINE_TEMP -> "propulsion.0.temperature"
            WidgetType.NAUTICAL_WATER_TEMP -> "environment.water.temperature"
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> "environment.outside.temperature"
            WidgetType.NAUTICAL_PRESSURE -> "environment.outside.pressure"
            WidgetType.NAUTICAL_ROLL -> "navigation.attitude.roll"
            WidgetType.NAUTICAL_PITCH -> "navigation.attitude.pitch"
            WidgetType.NAUTICAL_ROT -> "navigation.rateOfTurn"
            WidgetType.NAUTICAL_XTE -> "navigation.crossTrackError"
            WidgetType.NAUTICAL_TTW -> "navigation.timeToWaypoint"
            WidgetType.NAUTICAL_DTW -> "navigation.distanceToWaypoint"
            WidgetType.NAUTICAL_AWA -> "environment.wind.angleApparent"
            WidgetType.NAUTICAL_AWS -> "environment.wind.speedApparent"
            WidgetType.NAUTICAL_TWA -> "environment.wind.angleTrue"
            WidgetType.NAUTICAL_POLAR_RATIO -> "performance.polarSpeedRatio"
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> "navigation.headingMagnetic"
            WidgetType.NAUTICAL_LOG -> "navigation.log"
            WidgetType.NAUTICAL_TRIP_LOG -> "navigation.trip.log"
            WidgetType.NAUTICAL_DEPTH_KEEL -> "environment.depth.belowKeel"
            WidgetType.NAUTICAL_FUEL_LEVEL -> "tanks.fuel.0.currentLevel"
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> "tanks.freshWater.0.currentLevel"
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> "tanks.wasteWater.0.currentLevel"
            WidgetType.NAUTICAL_OIL_PRESSURE -> "propulsion.0.oilPressure"
            WidgetType.NAUTICAL_ENGINE_COOLANT -> "propulsion.0.coolantTemperature"
            WidgetType.NAUTICAL_ENGINE_LOAD -> "propulsion.0.engineLoad"
            WidgetType.NAUTICAL_BATTERY_CURRENT -> "electrical.batteries.0.current"
            WidgetType.NAUTICAL_SOLAR_CURRENT -> "electrical.solar.0.current"
            WidgetType.NAUTICAL_SET_DRIFT -> "navigation.drift"
            WidgetType.NAUTICAL_TWD -> "navigation.trueWindDirection"
            else -> null
        }

        val isStale = path != null && state.stalePaths.contains(path)
        if (isStale) {
            g.alpha = 0.35f
            badge?.visibility = View.VISIBLE
        } else {
            g.alpha = 1.0f
            badge?.visibility = View.GONE
        }

        when (widgetType) {
            WidgetType.NAUTICAL_DEPTH -> {
                g.setData(engine.getDepthHistory(), mapActivity.getString(R.string.nautical_unit_meters))
            }
            WidgetType.NAUTICAL_WIND -> {
                g.setData(engine.getWindHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_VMG -> {
                g.setData(engine.getVmgHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_SOG -> {
                g.setData(engine.getSogHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_STW -> {
                g.setData(engine.getStwHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_COG -> {
                g.setData(engine.getCogHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_ENGINE_RPM -> {
                g.setData(engine.getRpmHistory(), mapActivity.getString(R.string.nautical_unit_rpm))
            }
            WidgetType.NAUTICAL_BATTERY_VOLT -> {
                g.setData(engine.getVoltHistory(), mapActivity.getString(R.string.nautical_unit_volt))
            }
            WidgetType.NAUTICAL_BATTERY_SOC -> {
                g.setData(engine.getSocHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_ENGINE_TEMP -> {
                g.setData(engine.getTempEngineHistory(), mapActivity.getString(R.string.nautical_unit_celsius), 1.0, -273.15)
            }
            WidgetType.NAUTICAL_WATER_TEMP -> {
                g.setData(engine.getWaterTempHistory(), mapActivity.getString(R.string.nautical_unit_celsius), 1.0, -273.15)
            }
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> {
                g.setData(engine.getOutsideTempHistory(), mapActivity.getString(R.string.nautical_unit_celsius), 1.0, -273.15)
            }
            WidgetType.NAUTICAL_PRESSURE -> {
                g.setData(engine.getPressureHistory(), mapActivity.getString(R.string.nautical_unit_hpa), 0.01)
            }
            WidgetType.NAUTICAL_ROLL -> {
                g.setData(engine.getRollHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_PITCH -> {
                g.setData(engine.getPitchHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_ROT -> {
                g.setData(engine.getRotHistory(), mapActivity.getString(R.string.nautical_unit_rot_short), Math.toDegrees(1.0) * 60.0)
            }
            WidgetType.NAUTICAL_XTE -> {
                g.setData(engine.getXteHistory(), mapActivity.getString(R.string.nautical_unit_nm), 1.0 / 1852.0)
            }
            WidgetType.NAUTICAL_TTW -> {
                g.setData(engine.getTtwHistory(), mapActivity.getString(R.string.nautical_unit_min_short), 1.0 / 60.0)
            }
            WidgetType.NAUTICAL_DTW -> {
                g.setData(engine.getDtwHistory(), mapActivity.getString(R.string.nautical_unit_nm), 1.0 / 1852.0)
            }
            WidgetType.NAUTICAL_AWA -> {
                g.setData(engine.getAwaHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_AWS -> {
                g.setData(engine.getAwsHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_TWA -> {
                g.setData(engine.getTwaHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_POLAR_RATIO -> {
                g.setData(engine.getPolarRatioHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> {
                g.setData(engine.getMagHdgHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            WidgetType.NAUTICAL_LOG -> {
                g.setData(engine.getLogHistory(), mapActivity.getString(R.string.nautical_unit_nm), 1.0 / 1852.0)
            }
            WidgetType.NAUTICAL_TRIP_LOG -> {
                g.setData(engine.getTripLogHistory(), mapActivity.getString(R.string.nautical_unit_nm), 1.0 / 1852.0)
            }
            WidgetType.NAUTICAL_DEPTH_KEEL -> {
                g.setData(engine.getDepthKeelHistory(), mapActivity.getString(R.string.nautical_unit_meters))
            }
            WidgetType.NAUTICAL_FUEL_LEVEL -> {
                g.setData(engine.getFuelHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> {
                g.setData(engine.getFreshWaterHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> {
                g.setData(engine.getWasteHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_OIL_PRESSURE -> {
                g.setData(engine.getOilPressureHistory(), mapActivity.getString(R.string.nautical_unit_bar), 1.0 / 100000.0)
            }
            WidgetType.NAUTICAL_ENGINE_COOLANT -> {
                g.setData(engine.getCoolantTempHistory(), mapActivity.getString(R.string.nautical_unit_celsius), 1.0, -273.15)
            }
            WidgetType.NAUTICAL_ENGINE_LOAD -> {
                g.setData(engine.getEngineLoadHistory(), mapActivity.getString(R.string.nautical_unit_percent), 100.0)
            }
            WidgetType.NAUTICAL_BATTERY_CURRENT -> {
                g.setData(engine.getBatteryCurrentHistory(), mapActivity.getString(R.string.nautical_unit_ampere))
            }
            WidgetType.NAUTICAL_SOLAR_CURRENT -> {
                g.setData(engine.getSolarCurrentHistory(), mapActivity.getString(R.string.nautical_unit_ampere))
            }
            WidgetType.NAUTICAL_SET_DRIFT -> {
                g.setData(engine.getDriftHistory(), mapActivity.getString(R.string.nautical_unit_knots), net.osmand.shared.units.SpeedConstants.KNOTS)
            }
            WidgetType.NAUTICAL_TWD -> {
                g.setData(engine.getTwdHistory(), mapActivity.getString(R.string.nautical_unit_deg), Math.toDegrees(1.0))
            }
            else -> {}
        }
    }
}
