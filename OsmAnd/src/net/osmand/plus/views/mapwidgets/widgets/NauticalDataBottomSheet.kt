package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalDataBottomSheet : BottomSheetDialogFragment() {

    private var type: WidgetType? = null
    private var graph: NauticalGraphView? = null
    private var myListener: ((MarineState) -> Unit)? = null

    companion object {
        private const val WIDGET_TYPE = "widget_type"

        @JvmStatic
        fun newInstance(type: WidgetType): NauticalDataBottomSheet {
            val fragment = NauticalDataBottomSheet()
            val args = Bundle()
            args.putSerializable(WIDGET_TYPE, type)
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

        val plugin = NauticalPlugin.getInstance()
        plugin?.applyNightVisionFilter(view)

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
            titleView?.text = getString(R.string.nautical_history_title_pattern, name)
        }
    }

    override fun onStart() {
        super.onStart()

        myListener = { _ ->
            view?.post {
                if (!isAdded) return@post
                updateGraphData()
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

        val knotsCoeff = net.osmand.shared.units.SpeedUnits.KNOTS.conversionCoefficient

        when (type) {
            WidgetType.NAUTICAL_DEPTH -> g.setData(engine.getDepthHistory(), ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_WIND -> g.setData(engine.getWindHistory(), ctx.getString(R.string.nautical_unit_knots), knotsCoeff)
            WidgetType.NAUTICAL_VMG -> g.setData(engine.getVmgHistory(), ctx.getString(R.string.nautical_unit_knots), knotsCoeff)
            WidgetType.NAUTICAL_COG -> g.setData(engine.getCogHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_SOG -> g.setData(engine.getSogHistory(), ctx.getString(R.string.nautical_unit_knots), knotsCoeff)
            WidgetType.NAUTICAL_STW -> g.setData(engine.getStwHistory(), ctx.getString(R.string.nautical_unit_knots), knotsCoeff)
            WidgetType.NAUTICAL_ENGINE_RPM -> g.setData(engine.getRpmHistory(), "RPM")
            WidgetType.NAUTICAL_BATTERY_VOLT -> g.setData(engine.getVoltHistory(), "V")
            WidgetType.NAUTICAL_BATTERY_SOC -> g.setData(engine.getSocHistory(), "%", 100.0)
            WidgetType.NAUTICAL_ENGINE_TEMP -> g.setData(engine.getTempEngineHistory(), ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_TO_CELSIUS)
            WidgetType.NAUTICAL_WATER_TEMP -> g.setData(engine.getWaterTempHistory(), ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_TO_CELSIUS)
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> g.setData(engine.getOutsideTempHistory(), ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_TO_CELSIUS)
            WidgetType.NAUTICAL_PRESSURE -> g.setData(engine.getPressureHistory(), ctx.getString(R.string.nautical_unit_hpa), SignalKUnitConverter.PASCAL_TO_HPA)
            WidgetType.NAUTICAL_ROLL -> g.setData(engine.getRollHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_PITCH -> g.setData(engine.getPitchHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_ROT -> g.setData(engine.getRotHistory(), ctx.getString(R.string.nautical_unit_rot_short), Math.toDegrees(1.0) * 60.0)
            WidgetType.NAUTICAL_XTE -> g.setData(engine.getXteHistory(), ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_DTW -> g.setData(engine.getDtwHistory(), ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_AWA -> g.setData(engine.getAwaHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_AWS -> g.setData(engine.getAwsHistory(), ctx.getString(R.string.nautical_unit_knots), knotsCoeff)
            WidgetType.NAUTICAL_TWA -> g.setData(engine.getTwaHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_POLAR_RATIO -> g.setData(engine.getPolarRatioHistory(), "%", 100.0)
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> g.setData(engine.getMagHdgHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_LOG -> g.setData(engine.getLogHistory(), ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_TRIP_LOG -> g.setData(engine.getTripLogHistory(), ctx.getString(R.string.nautical_unit_nm), SignalKUnitConverter.METERS_TO_NM)
            WidgetType.NAUTICAL_DEPTH_KEEL -> g.setData(engine.getDepthKeelHistory(), ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_FUEL_LEVEL -> g.setData(engine.getFuelHistory(), "%", 100.0)
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> g.setData(engine.getFreshWaterHistory(), "%", 100.0)
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> g.setData(engine.getWasteHistory(), "%", 100.0)
            WidgetType.NAUTICAL_OIL_PRESSURE -> g.setData(engine.getOilPressureHistory(), ctx.getString(R.string.nautical_unit_bar), SignalKUnitConverter.PASCAL_TO_BAR)
            WidgetType.NAUTICAL_ENGINE_LOAD -> g.setData(engine.getEngineLoadHistory(), "%", 100.0)
            WidgetType.NAUTICAL_BATTERY_CURRENT -> g.setData(engine.getBatteryCurrentHistory(), "A")
            WidgetType.NAUTICAL_ENGINE_COOLANT -> g.setData(engine.getCoolantTempHistory(), ctx.getString(R.string.nautical_unit_celsius), 1.0, SignalKUnitConverter.KELVIN_TO_CELSIUS)
            WidgetType.NAUTICAL_SOLAR_CURRENT -> g.setData(engine.getSolarCurrentHistory(), "A")
            WidgetType.NAUTICAL_TWD -> g.setData(engine.getTwdHistory(), "°", Math.toDegrees(1.0))
            WidgetType.NAUTICAL_HUMIDITY -> g.setData(engine.getHumidityHistory(), "%", 100.0)
            else -> {}
        }
    }
}
