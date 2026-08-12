package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalDataBottomSheet : BaseNauticalBottomSheet() {

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nautical_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Red filter handled by BaseNauticalBottomSheet

        val titleView = view.findViewById<TextView>(R.id.graph_title)
        graph = view.findViewById(R.id.graph_view)

        if (type == null) {
            dismiss()
            return
        }

        val name = when (type) {
            WidgetType.NAUTICAL_DEPTH -> context?.getString(R.string.nautical_widget_depth_label)
            WidgetType.NAUTICAL_WIND -> context?.getString(R.string.nautical_widget_wind_label)
            WidgetType.NAUTICAL_VMG -> context?.getString(R.string.nautical_widget_vmg_label)
            WidgetType.NAUTICAL_COG -> context?.getString(R.string.nautical_widget_cog_label)
            WidgetType.NAUTICAL_SOG -> context?.getString(R.string.nautical_sog)
            WidgetType.NAUTICAL_STW -> context?.getString(R.string.nautical_stw)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> context?.getString(R.string.nautical_heading_magnetic)
            WidgetType.NAUTICAL_LOG -> context?.getString(R.string.nautical_log)
            WidgetType.NAUTICAL_TRIP_LOG -> context?.getString(R.string.nautical_trip_log)
            WidgetType.NAUTICAL_ROLL -> context?.getString(R.string.nautical_roll)
            WidgetType.NAUTICAL_PITCH -> context?.getString(R.string.nautical_pitch)
            WidgetType.NAUTICAL_DEPTH_KEEL -> context?.getString(R.string.nautical_depth_keel)
            WidgetType.NAUTICAL_WATER_TEMP -> context?.getString(R.string.nautical_water_temp)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> context?.getString(R.string.nautical_outside_temp)
            WidgetType.NAUTICAL_PRESSURE -> context?.getString(R.string.nautical_pressure)
            WidgetType.NAUTICAL_ENGINE_RPM -> context?.getString(R.string.nautical_engine_rpm)
            WidgetType.NAUTICAL_ENGINE_TEMP -> context?.getString(R.string.nautical_engine_temp)
            WidgetType.NAUTICAL_BATTERY_VOLT -> context?.getString(R.string.nautical_battery_volt)
            WidgetType.NAUTICAL_BATTERY_SOC -> context?.getString(R.string.nautical_battery_soc)
            WidgetType.NAUTICAL_FUEL_LEVEL -> context?.getString(R.string.nautical_fuel_level)
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> context?.getString(R.string.nautical_fresh_water_level)
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> context?.getString(R.string.nautical_waste_water_level)
            WidgetType.NAUTICAL_POLAR_RATIO -> context?.getString(R.string.nautical_polar_ratio)
            WidgetType.NAUTICAL_ROT -> context?.getString(R.string.nautical_rot)
            WidgetType.NAUTICAL_XTE -> context?.getString(R.string.nautical_xte)
            WidgetType.NAUTICAL_TTW -> context?.getString(R.string.nautical_ttw)
            WidgetType.NAUTICAL_DTW -> context?.getString(R.string.nautical_dtw)
            WidgetType.NAUTICAL_ETA -> context?.getString(R.string.nautical_eta)
            WidgetType.NAUTICAL_AWA -> context?.getString(R.string.nautical_awa)
            WidgetType.NAUTICAL_AWS -> context?.getString(R.string.nautical_aws)
            WidgetType.NAUTICAL_TWA -> context?.getString(R.string.nautical_twa)
            WidgetType.NAUTICAL_TWD -> context?.getString(R.string.nautical_twd)
            WidgetType.NAUTICAL_OIL_PRESSURE -> context?.getString(R.string.nautical_oil_pressure)
            WidgetType.NAUTICAL_ENGINE_LOAD -> context?.getString(R.string.nautical_engine_load)
            WidgetType.NAUTICAL_BATTERY_CURRENT -> context?.getString(R.string.nautical_battery_current)
            WidgetType.NAUTICAL_SOLAR_CURRENT -> context?.getString(R.string.nautical_solar_current)
            WidgetType.NAUTICAL_ENGINE_RUNTIME -> context?.getString(R.string.nautical_engine_runtime)
            WidgetType.NAUTICAL_ENGINE_COOLANT -> context?.getString(R.string.nautical_engine_coolant)
            WidgetType.NAUTICAL_HUMIDITY -> context?.getString(R.string.nautical_humidity)
            WidgetType.NAUTICAL_AC_VOLTAGE -> context?.getString(R.string.nautical_ac_voltage)
            WidgetType.NAUTICAL_AC_CURRENT -> context?.getString(R.string.nautical_ac_current)
            WidgetType.NAUTICAL_AC_FREQUENCY -> context?.getString(R.string.nautical_ac_frequency)
            else -> context?.getString(R.string.nautical_data_telemetry)
        }
        if (name != null) {
            titleView?.text = context?.getString(R.string.nautical_history_title_pattern, name)
        }
    }

    override fun onStart() {
        super.onStart()

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
        val ctx = context ?: return
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
