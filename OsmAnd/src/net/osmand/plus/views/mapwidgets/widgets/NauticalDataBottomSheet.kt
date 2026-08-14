package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalMenuBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalDataBottomSheet : NauticalMenuBottomSheetDialogFragment() {

    private var type: WidgetType? = null
    private var graph: NauticalGraphView? = null
    private var myListener: ((MarineState) -> Unit)? = null
    private var lastUpdateTime: Long = 0
    private val throttleMs = 500L

    companion object {
        private const val WIDGET_TYPE = "widget_type"
        private const val WIDGET_INSTANCE = "widget_instance"

        @JvmStatic
        fun newInstance(type: WidgetType, instance: String = "0"): NauticalDataBottomSheet {
            val fragment = NauticalDataBottomSheet()
            val args = Bundle()
            args.putSerializable(WIDGET_TYPE, type)
            args.putString(WIDGET_INSTANCE, instance)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arguments?.getSerializable(WIDGET_TYPE, WidgetType::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(WIDGET_TYPE) as? WidgetType
        }
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        if (type == null) {
            dismiss()
            return
        }

        val name = getWidgetName()
        if (name != null) {
            addTitleItem(getString(R.string.nautical_history_title_pattern, name))
        }

        val graphView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_nautical_data, null)
        graph = graphView.findViewById(R.id.graph_view)
        
        // Hide standard header in graphView since we use addTitleItem
        graphView.findViewById<View>(R.id.graph_title).visibility = View.GONE

        items.add(net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem.Builder().setCustomView(graphView).create())
    }

    private fun getWidgetName(): String? {
        return when (type) {
            WidgetType.NAUTICAL_DEPTH -> getString(R.string.nautical_widget_depth_label)
            WidgetType.NAUTICAL_WIND -> getString(R.string.nautical_widget_wind_label)
            WidgetType.NAUTICAL_VMG -> getString(R.string.nautical_widget_vmg_label)
            WidgetType.NAUTICAL_COG -> getString(R.string.nautical_widget_cog_label)
            WidgetType.NAUTICAL_SOG -> getString(R.string.nautical_sog)
            WidgetType.NAUTICAL_STW -> getString(R.string.nautical_stw)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> getString(R.string.nautical_heading_magnetic)
            WidgetType.NAUTICAL_LOG -> getString(R.string.nautical_log)
            WidgetType.NAUTICAL_TRIP_LOG -> getString(R.string.nautical_trip_log)
            WidgetType.NAUTICAL_ROLL -> getString(R.string.nautical_roll)
            WidgetType.NAUTICAL_PITCH -> getString(R.string.nautical_pitch)
            WidgetType.NAUTICAL_DEPTH_KEEL -> getString(R.string.nautical_depth_keel)
            WidgetType.NAUTICAL_WATER_TEMP -> getString(R.string.nautical_water_temp)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> getString(R.string.nautical_outside_temp)
            WidgetType.NAUTICAL_PRESSURE -> getString(R.string.nautical_pressure)
            WidgetType.NAUTICAL_ENGINE_RPM -> getString(R.string.nautical_engine_rpm)
            WidgetType.NAUTICAL_ENGINE_TEMP -> getString(R.string.nautical_engine_temp)
            WidgetType.NAUTICAL_BATTERY_VOLT -> getString(R.string.nautical_battery_volt)
            WidgetType.NAUTICAL_BATTERY_SOC -> getString(R.string.nautical_battery_soc)
            WidgetType.NAUTICAL_FUEL_LEVEL -> getString(R.string.nautical_fuel_level)
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> getString(R.string.nautical_fresh_water_level)
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> getString(R.string.nautical_waste_water_level)
            WidgetType.NAUTICAL_POLAR_RATIO -> getString(R.string.nautical_polar_ratio)
            WidgetType.NAUTICAL_ROT -> getString(R.string.nautical_rot)
            WidgetType.NAUTICAL_XTE -> getString(R.string.nautical_xte)
            WidgetType.NAUTICAL_TTW -> getString(R.string.nautical_ttw)
            WidgetType.NAUTICAL_DTW -> getString(R.string.nautical_dtw)
            WidgetType.NAUTICAL_ETA -> getString(R.string.nautical_eta)
            WidgetType.NAUTICAL_AWA -> getString(R.string.nautical_awa)
            WidgetType.NAUTICAL_AWS -> getString(R.string.nautical_aws)
            WidgetType.NAUTICAL_TWA -> getString(R.string.nautical_twa)
            WidgetType.NAUTICAL_TWD -> getString(R.string.nautical_twd)
            WidgetType.NAUTICAL_OIL_PRESSURE -> getString(R.string.nautical_oil_pressure)
            WidgetType.NAUTICAL_ENGINE_LOAD -> getString(R.string.nautical_engine_load)
            WidgetType.NAUTICAL_BATTERY_CURRENT -> getString(R.string.nautical_battery_current)
            WidgetType.NAUTICAL_SOLAR_CURRENT -> getString(R.string.nautical_solar_current)
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> getString(R.string.nautical_engine_runtime)
            WidgetType.NAUTICAL_ENGINE_COOLANT -> getString(R.string.nautical_engine_coolant)
            WidgetType.NAUTICAL_HUMIDITY -> getString(R.string.nautical_humidity)
            WidgetType.NAUTICAL_AC_VOLTAGE -> getString(R.string.nautical_ac_voltage)
            WidgetType.NAUTICAL_AC_CURRENT -> getString(R.string.nautical_ac_current)
            WidgetType.NAUTICAL_AC_FREQUENCY -> getString(R.string.nautical_ac_frequency)
            else -> getString(R.string.nautical_data_telemetry)
        }
    }

    override fun onStart() {
        super.onStart()

        if (myListener == null) {
            myListener = { _ ->
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime > throttleMs) {
                    lastUpdateTime = now
                    view?.post {
                        if (!isAdded) return@post
                        updateGraphData()
                    }
                }
            }
        }

        myListener?.let { NauticalPlugin.engine?.registerListener(it) }
    }

    override fun onStop() {
        myListener?.let { NauticalPlugin.engine?.unregisterListener(it) }
        myListener = null
        super.onStop()
    }

    private fun updateGraphData() {
        if (!isAdded) return
        val engine = NauticalPlugin.engine ?: return
        val g = graph ?: return
        val ctx = requireContext()
        val instance = arguments?.getString(WIDGET_INSTANCE) ?: "0"

        val path = when (type) {
            WidgetType.NAUTICAL_DEPTH -> SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER
            WidgetType.NAUTICAL_WIND -> SignalKPaths.ENV_WIND_SPEED_TRUE
            WidgetType.NAUTICAL_VMG -> SignalKPaths.PERF_VMG
            WidgetType.NAUTICAL_COG -> SignalKPaths.NAV_COURSE_OVER_GROUND
            WidgetType.NAUTICAL_SOG -> SignalKPaths.NAV_SPEED_OVER_GROUND
            WidgetType.NAUTICAL_STW -> SignalKPaths.NAV_SPEED_THROUGH_WATER
            WidgetType.NAUTICAL_ENGINE_RPM -> "${SignalKPaths.PROPULSION_PREFIX}$instance.revolutions"
            WidgetType.NAUTICAL_BATTERY_VOLT -> "${SignalKPaths.BATTERIES_PREFIX}$instance.voltage"
            WidgetType.NAUTICAL_BATTERY_SOC -> "${SignalKPaths.BATTERIES_PREFIX}$instance.capacity.stateOfCharge"
            WidgetType.NAUTICAL_ENGINE_TEMP -> "${SignalKPaths.PROPULSION_PREFIX}$instance.temperature"
            WidgetType.NAUTICAL_WATER_TEMP -> SignalKPaths.ENV_WATER_TEMP
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKPaths.ENV_OUTSIDE_TEMP
            WidgetType.NAUTICAL_PRESSURE -> SignalKPaths.ENV_OUTSIDE_PRESSURE
            WidgetType.NAUTICAL_ROLL -> "${SignalKPaths.NAV_ATTITUDE}.roll"
            WidgetType.NAUTICAL_PITCH -> "${SignalKPaths.NAV_ATTITUDE}.pitch"
            WidgetType.NAUTICAL_ROT -> SignalKPaths.NAV_RATE_OF_TURN
            WidgetType.NAUTICAL_XTE -> SignalKPaths.NAV_XTE
            WidgetType.NAUTICAL_DTW -> SignalKPaths.NAV_DTW
            WidgetType.NAUTICAL_AWA -> SignalKPaths.ENV_WIND_ANGLE_APPARENT
            WidgetType.NAUTICAL_AWS -> SignalKPaths.ENV_WIND_SPEED_APPARENT
            WidgetType.NAUTICAL_TWA -> SignalKPaths.ENV_WIND_ANGLE_TRUE
            WidgetType.NAUTICAL_POLAR_RATIO -> SignalKPaths.PERF_POLAR_RATIO
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKPaths.NAV_HEADING_MAG
            WidgetType.NAUTICAL_LOG -> SignalKPaths.NAV_LOG
            WidgetType.NAUTICAL_TRIP_LOG -> SignalKPaths.NAV_TRIP_LOG
            WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKPaths.ENV_DEPTH_BELOW_KEEL
            WidgetType.NAUTICAL_FUEL_LEVEL -> "${SignalKPaths.TANKS_PREFIX}fuel.$instance.currentLevel"
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> "${SignalKPaths.TANKS_PREFIX}freshWater.$instance.currentLevel"
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> "${SignalKPaths.TANKS_PREFIX}wasteWater.$instance.currentLevel"
            WidgetType.NAUTICAL_OIL_PRESSURE -> "${SignalKPaths.PROPULSION_PREFIX}$instance.oilPressure"
            WidgetType.NAUTICAL_ENGINE_LOAD -> "${SignalKPaths.PROPULSION_PREFIX}$instance.engineLoad"
            WidgetType.NAUTICAL_BATTERY_CURRENT -> "${SignalKPaths.BATTERIES_PREFIX}$instance.current"
            WidgetType.NAUTICAL_ENGINE_COOLANT -> "${SignalKPaths.PROPULSION_PREFIX}$instance.coolantTemperature"
            WidgetType.NAUTICAL_SOLAR_CURRENT -> "electrical.solar.$instance.current"
            WidgetType.NAUTICAL_TWD -> SignalKPaths.NAV_TWD
            WidgetType.NAUTICAL_HUMIDITY -> SignalKPaths.ENV_OUTSIDE_HUMIDITY
            else -> null
        } ?: return

        val history = engine.getHistory(path)

        when (type) {
            WidgetType.NAUTICAL_DEPTH -> g.setData(history, ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_WIND -> g.setData(history, ctx.getString(R.string.nautical_unit_knots), SignalKUnitConverter.MS_TO_KNOTS)
            WidgetType.NAUTICAL_VMG -> g.setData(history, ctx.getString(R.string.nautical_unit_knots), SignalKUnitConverter.MS_TO_KNOTS)
            WidgetType.NAUTICAL_COG -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_SOG -> g.setData(history, ctx.getString(R.string.nautical_unit_knots), SignalKUnitConverter.MS_TO_KNOTS)
            WidgetType.NAUTICAL_STW -> g.setData(history, ctx.getString(R.string.nautical_unit_knots), SignalKUnitConverter.MS_TO_KNOTS)
            WidgetType.NAUTICAL_ENGINE_RPM -> g.setData(history, "RPM", 60.0)
            WidgetType.NAUTICAL_BATTERY_VOLT -> g.setData(history, "V")
            WidgetType.NAUTICAL_BATTERY_SOC -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_ENGINE_TEMP -> g.setData(history, ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_OFFSET_CELSIUS)
            WidgetType.NAUTICAL_WATER_TEMP -> g.setData(history, ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_OFFSET_CELSIUS)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> g.setData(history, ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_OFFSET_CELSIUS)
            WidgetType.NAUTICAL_PRESSURE -> g.setData(history, ctx.getString(R.string.nautical_unit_hpa), SignalKUnitConverter.PASCAL_TO_HPA)
            WidgetType.NAUTICAL_ROLL -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_PITCH -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_ROT -> g.setData(history, ctx.getString(R.string.nautical_unit_rot_short), Math.toDegrees(1.0) * 60.0)
            WidgetType.NAUTICAL_XTE -> g.setData(history, ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_DTW -> g.setData(history, ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_AWA -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_AWS -> g.setData(history, ctx.getString(R.string.nautical_unit_knots), SignalKUnitConverter.MS_TO_KNOTS)
            WidgetType.NAUTICAL_TWA -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_POLAR_RATIO -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_LOG -> g.setData(history, ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_TRIP_LOG -> g.setData(history, ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_DEPTH_KEEL -> g.setData(history, ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_FUEL_LEVEL -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_OIL_PRESSURE -> g.setData(history, ctx.getString(R.string.nautical_unit_bar), SignalKUnitConverter.PASCAL_TO_BAR)
            WidgetType.NAUTICAL_ENGINE_LOAD -> g.setData(history, "%", 100.0)
            WidgetType.NAUTICAL_BATTERY_CURRENT -> g.setData(history, "A")
            WidgetType.NAUTICAL_ENGINE_COOLANT -> g.setData(history, ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_OFFSET_CELSIUS)
            WidgetType.NAUTICAL_SOLAR_CURRENT -> g.setData(history, "A")
            WidgetType.NAUTICAL_TWD -> g.setData(history, "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_HUMIDITY -> g.setData(history, "%", 100.0)
            else -> {}
        }
    }
}
