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
            else -> getString(R.string.nautical_data_telemetry)
        }
        titleView?.text = getString(R.string.nautical_history_title_pattern, name)
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
        val knotsCoeff = net.osmand.shared.units.SpeedConstants.KNOTS

        when (type) {
            WidgetType.NAUTICAL_DEPTH -> g.setData(engine.getDepthHistory(), ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_WIND -> g.setData(engine.getWindHistory().map { it * knotsCoeff }, ctx.getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_VMG -> g.setData(engine.getVmgHistory().map { it * knotsCoeff }, ctx.getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_COG -> g.setData(engine.getCogHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_SOG -> g.setData(engine.getSogHistory().map { it * knotsCoeff }, ctx.getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_STW -> g.setData(engine.getStwHistory().map { it * knotsCoeff }, ctx.getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_ENGINE_RPM -> g.setData(engine.getRpmHistory(), ctx.getString(R.string.nautical_unit_rpm))
            WidgetType.NAUTICAL_BATTERY_VOLT -> g.setData(engine.getVoltHistory(), ctx.getString(R.string.nautical_unit_volt))
            WidgetType.NAUTICAL_BATTERY_SOC -> g.setData(engine.getSocHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_ENGINE_TEMP -> g.setData(engine.getTempEngineHistory().map { it - 273.15 }, ctx.getString(R.string.nautical_unit_celsius))
            WidgetType.NAUTICAL_WATER_TEMP -> g.setData(engine.getWaterTempHistory().map { it - 273.15 }, ctx.getString(R.string.nautical_unit_celsius))
            WidgetType.NAUTICAL_OUTSIDE_TEMP -> g.setData(engine.getOutsideTempHistory().map { it - 273.15 }, ctx.getString(R.string.nautical_unit_celsius))
            WidgetType.NAUTICAL_PRESSURE -> g.setData(engine.getPressureHistory().map { it / 100.0 }, ctx.getString(R.string.nautical_unit_hpa))
            WidgetType.NAUTICAL_ROLL -> g.setData(engine.getRollHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_PITCH -> g.setData(engine.getPitchHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_ROT -> g.setData(engine.getRotHistory().map { Math.toDegrees(it) * 60.0 }, ctx.getString(R.string.nautical_unit_rot_short))
            WidgetType.NAUTICAL_XTE -> g.setData(engine.getXteHistory().map { it / 1852.0 }, ctx.getString(R.string.nautical_unit_nm))
            WidgetType.NAUTICAL_TTW -> g.setData(engine.getTtwHistory().map { it / 60.0 }, ctx.getString(R.string.nautical_unit_min_short))
            WidgetType.NAUTICAL_DTW -> g.setData(engine.getDtwHistory().map { it / 1852.0 }, ctx.getString(R.string.nautical_unit_nm))
            WidgetType.NAUTICAL_AWA -> g.setData(engine.getAwaHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_AWS -> g.setData(engine.getAwsHistory().map { it * knotsCoeff }, ctx.getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_TWA -> g.setData(engine.getTwaHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_POLAR_RATIO -> g.setData(engine.getPolarRatioHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_HEADING_MAGNETIC -> g.setData(engine.getMagHdgHistory().map { Math.toDegrees(it) }, "°")
            WidgetType.NAUTICAL_LOG -> g.setData(engine.getLogHistory().map { it / 1852.0 }, ctx.getString(R.string.nautical_unit_nm))
            WidgetType.NAUTICAL_TRIP_LOG -> g.setData(engine.getTripLogHistory().map { it / 1852.0 }, ctx.getString(R.string.nautical_unit_nm))
            WidgetType.NAUTICAL_DEPTH_KEEL -> g.setData(engine.getDepthKeelHistory(), ctx.getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_FUEL_LEVEL -> g.setData(engine.getFuelHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL -> g.setData(engine.getFreshWaterHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL -> g.setData(engine.getWasteHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_OIL_PRESSURE -> g.setData(engine.getOilPressureHistory().map { it / 100000.0 }, ctx.getString(R.string.nautical_unit_bar))
            WidgetType.NAUTICAL_ENGINE_LOAD -> g.setData(engine.getEngineLoadHistory().map { it * 100.0 }, ctx.getString(R.string.nautical_unit_percent))
            WidgetType.NAUTICAL_BATTERY_CURRENT -> g.setData(engine.getBatteryCurrentHistory(), ctx.getString(R.string.nautical_unit_ampere))
            WidgetType.NAUTICAL_SOLAR_CURRENT -> g.setData(engine.getSolarCurrentHistory(), ctx.getString(R.string.nautical_unit_ampere))
            WidgetType.NAUTICAL_TWD -> g.setData(engine.getTwdHistory().map { Math.toDegrees(it) }, "°")
            else -> {}
        }
    }
}
