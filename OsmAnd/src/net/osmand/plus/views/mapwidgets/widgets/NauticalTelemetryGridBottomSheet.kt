package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalMenuBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalMiniRoseView
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.plugins.nautical.ui.NauticalSparklineView
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalTelemetryGridBottomSheet : NauticalMenuBottomSheetDialogFragment() {

    private var recyclerView: RecyclerView? = null
    private var adapter: TelemetryAdapter? = null
    private var stateListener: ((MarineState) -> Unit)? = null
    private var widgetId: String? = null

    companion object {
        const val KEY_WIDGET_ID = "widget_id"

        fun show(manager: FragmentManager, widgetId: String?) {
            val fragment = NauticalTelemetryGridBottomSheet()
            val args = Bundle()
            args.putString(KEY_WIDGET_ID, widgetId)
            fragment.arguments = args
            fragment.show(manager, "nautical_telemetry_grid")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = arguments?.getString(KEY_WIDGET_ID)
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as OsmandApplication
        
        var itemIdsString = app.settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.get()
        if (itemIdsString.isEmpty()) {
            itemIdsString = "nautical_sog,nautical_cog,nautical_depth_keel,nautical_wind,nautical_vmg,nautical_battery_volt"
            app.settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(itemIdsString)
        }
        val itemIds = itemIdsString.split(",").filter { it.isNotEmpty() }
        val widgets = itemIds.mapNotNull { WidgetType.getById(it) }

        val gridView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_nautical_telemetry_grid, null)
        recyclerView = gridView.findViewById(R.id.recycler_view)
        recyclerView?.layoutManager = GridLayoutManager(requireContext(), 3)
        
        adapter = TelemetryAdapter(widgets, app)
        recyclerView?.adapter = adapter
        
        gridView.findViewById<View>(R.id.title).visibility = View.GONE
        val btnSettings = gridView.findViewById<View>(R.id.btn_settings)
        btnSettings.visibility = View.VISIBLE
        btnSettings.setOnClickListener { onRightBottomButtonClick() }

        items.add(net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem.Builder().setCustomView(gridView).create())
    }

    override fun getRightBottomButtonTextId(): Int = 0
    override fun getDismissButtonTextId(): Int = 0

    override fun onRightBottomButtonClick() {
        if (widgetId != null) {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(
                requireActivity(), 
                SettingsScreenType.NAUTICAL_MASTER_TELEMETRY,
                null,
                Bundle().apply { putString(net.osmand.plus.views.mapwidgets.configure.settings.WidgetInfoBaseFragment.KEY_WIDGET_ID, widgetId) },
                null
            )
        } else {
            // Fallback if no widgetId
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.NAUTICAL_MASTER_TELEMETRY)
        }
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        if (stateListener == null) {
            stateListener = { _ ->
                view?.post {
                    if (isAdded) {
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
        stateListener?.let { NauticalPlugin.engine?.registerListener(it) }
    }

    override fun onStop() {
        stateListener?.let { NauticalPlugin.engine?.unregisterListener(it) }
        super.onStop()
    }

    private inner class TelemetryAdapter(
        private val widgets: List<WidgetType>,
        private val app: OsmandApplication,
    ) : RecyclerView.Adapter<TelemetryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TelemetryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_telemetry_grid, parent, false)
            return TelemetryViewHolder(view)
        }

        override fun onBindViewHolder(holder: TelemetryViewHolder, position: Int) {
            val widget = widgets[position]
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            val isNight = app.daynightHelper.isNightMode(app.settings.APPLICATION_MODE.get(), net.osmand.plus.settings.enums.ThemeUsageContext.APP)
            
            holder.icon.setImageResource(widget.getIconId(isNight))
            
            val (value, unit) = formatTelemetry(widget, state)
            holder.value.text = value
            holder.unit.text = unit

            // Color Coding
            val color = getSemanticColor(widget, state)
            holder.value.setTextColor(color)
            
            holder.miniRose.visibility = View.GONE
            holder.sparkline.visibility = View.GONE
            holder.icon.visibility = View.VISIBLE

            // Graphical activation
            when (widget) {
                WidgetType.NAUTICAL_HEADING_MAGNETIC, WidgetType.NAUTICAL_COG -> {
                    val angle = if (widget == WidgetType.NAUTICAL_HEADING_MAGNETIC) state?.headingMagnetic else state?.courseOverGroundTrue
                    if (angle != null) {
                        holder.miniRose.visibility = View.VISIBLE
                        holder.icon.visibility = View.GONE
                        holder.miniRose.setAngle(angle, color, relative = false)
                    }
                }
                WidgetType.NAUTICAL_AWA, WidgetType.NAUTICAL_TWA -> {
                    val angle = if (widget == WidgetType.NAUTICAL_AWA) state?.windDirectionApparent else state?.trueWindAngle
                    if (angle != null) {
                        holder.miniRose.visibility = View.VISIBLE
                        holder.icon.visibility = View.GONE
                        holder.miniRose.setAngle(angle, color, relative = true)
                    }
                }
                WidgetType.NAUTICAL_DEPTH_KEEL, WidgetType.NAUTICAL_SOG, WidgetType.NAUTICAL_STW, WidgetType.NAUTICAL_WIND -> {
                    val path = when(widget) {
                        WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKPaths.ENV_DEPTH_BELOW_KEEL
                        WidgetType.NAUTICAL_SOG -> SignalKPaths.NAV_SPEED_OVER_GROUND
                        WidgetType.NAUTICAL_STW -> SignalKPaths.NAV_SPEED_THROUGH_WATER
                        WidgetType.NAUTICAL_WIND -> SignalKPaths.ENV_WIND_SPEED_TRUE
                        else -> ""
                    }
                    if (path.isNotEmpty()) {
                        val history = engine?.getHistory(path)
                        if (!history.isNullOrEmpty()) {
                            holder.sparkline.visibility = View.VISIBLE
                            holder.sparkline.setData(history.map { it.first }, color)
                        }
                    }
                }
                else -> {}
            }

            holder.itemView.setOnClickListener {
                NauticalDataBottomSheet.newInstance(widget).show(parentFragmentManager, "nautical_data")
            }
        }

        private fun getSemanticColor(widget: WidgetType, state: MarineState?): Int {
            if (state == null) return NauticalColorResolver.getColor(requireContext(), NauticalSemanticColor.PRIMARY)
            
            val safety = net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager.getInstance(app)
            return when (widget) {
                WidgetType.NAUTICAL_DEPTH_KEEL -> {
                    val d = state.depthBelowKeel ?: 100.0
                    if (d < (safety.getVesselDraft() + 0.5)) NauticalColorResolver.getColor(requireContext(), NauticalSemanticColor.STATUS_ERROR)
                    else if (d < safety.getMinSafeDepth()) NauticalColorResolver.getColor(requireContext(), NauticalSemanticColor.STATUS_WARNING)
                    else NauticalColorResolver.getColor(requireContext(), NauticalSemanticColor.PRIMARY)
                }
                else -> NauticalColorResolver.getColor(requireContext(), NauticalSemanticColor.PRIMARY)
            }
        }

        override fun getItemCount(): Int = widgets.size

        private fun formatTelemetry(widget: WidgetType, state: MarineState?): Pair<String, String> {
            if (state == null) return getString(R.string.n_a) to ""
            
            val context = requireContext()
            val settings = app.settings
            
            return when (widget) {
                WidgetType.NAUTICAL_SOG -> SignalKUnitConverter.formatValue(context, settings, state.speedOverGround, "speed")
                WidgetType.NAUTICAL_STW -> SignalKUnitConverter.formatValue(context, settings, state.speedThroughWater, "speed")
                WidgetType.NAUTICAL_COG -> SignalKUnitConverter.formatValue(context, settings, state.courseOverGroundTrue, "course")
                WidgetType.NAUTICAL_HEADING_MAGNETIC -> SignalKUnitConverter.formatValue(context, settings, state.headingMagnetic, "heading")
                WidgetType.NAUTICAL_DEPTH_KEEL -> SignalKUnitConverter.formatValue(context, settings, state.depthBelowKeel, "depth")
                WidgetType.NAUTICAL_WIND -> SignalKUnitConverter.formatValue(context, settings, state.windSpeedTrue, "speed")
                WidgetType.NAUTICAL_VMG -> SignalKUnitConverter.formatValue(context, settings, state.velocityMadeGood, "speed")
                WidgetType.NAUTICAL_BATTERY_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.batteries["0"]?.voltage, "voltage")
                WidgetType.NAUTICAL_BATTERY_SOC -> SignalKUnitConverter.formatValue(context, settings, state.batteries["0"]?.stateOfCharge, "stateOfCharge")
                WidgetType.NAUTICAL_WATER_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.waterTemperature, "temperature")
                WidgetType.NAUTICAL_OUTSIDE_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.outsideTemperature, "temperature")
                WidgetType.NAUTICAL_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.outsidePressure, "pressure")
                WidgetType.NAUTICAL_ROLL -> SignalKUnitConverter.formatValue(context, settings, state.roll, "roll")
                WidgetType.NAUTICAL_PITCH -> SignalKUnitConverter.formatValue(context, settings, state.pitch, "pitch")
                WidgetType.NAUTICAL_LOG -> SignalKUnitConverter.formatValue(context, settings, state.log, "log")
                WidgetType.NAUTICAL_TRIP_LOG -> SignalKUnitConverter.formatValue(context, settings, state.tripLog, "log")
                WidgetType.NAUTICAL_ENGINE_RUNTIME -> SignalKUnitConverter.formatValue(context, settings, state.engineHours?.let { it * 3600.0 } ?: state.engines["0"]?.runTime, "runTime")
                WidgetType.NAUTICAL_XTE -> SignalKUnitConverter.formatValue(context, settings, state.crossTrackError, "distance")
                WidgetType.NAUTICAL_DTW -> SignalKUnitConverter.formatValue(context, settings, state.distanceToWaypoint, "distance")
                WidgetType.NAUTICAL_BOOST_PRESSURE -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.boostPressure, "pressure")
                WidgetType.NAUTICAL_EXHAUST_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.exhaustTemperature, "temperature")
                WidgetType.NAUTICAL_ALTERNATOR_VOLT -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.alternatorVoltage, "voltage")
                WidgetType.NAUTICAL_ALTERNATOR_CURR -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.alternatorCurrent, "current")
                WidgetType.NAUTICAL_TRANS_GEAR -> (state.engines["0"]?.transmissionGear ?: getString(R.string.n_a)) to ""
                WidgetType.NAUTICAL_TRANS_PRESS -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.transmissionPressure, "pressure")
                WidgetType.NAUTICAL_TRANS_OIL_TEMP -> SignalKUnitConverter.formatValue(context, settings, state.engines["0"]?.transmissionOilTemperature, "temperature")
                WidgetType.NAUTICAL_INV_STATE -> (state.inverters["0"]?.state ?: getString(R.string.n_a)) to ""
                WidgetType.NAUTICAL_CHG_STATE -> (state.chargers["0"]?.state ?: getString(R.string.n_a)) to ""
                WidgetType.NAUTICAL_WATERMAKER_RATE -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.rate, "watermakerRate")
                WidgetType.NAUTICAL_WATERMAKER_TOTAL -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.totalProduction, "volume")
                WidgetType.NAUTICAL_WATERMAKER_SALINITY -> SignalKUnitConverter.formatValue(context, settings, state.watermakers["0"]?.salinity, "salinity")
                WidgetType.NAUTICAL_REEFS -> (state.reefs?.toString() ?: getString(R.string.n_a)) to ""
                else -> getString(R.string.n_a) to ""
            }
        }
    }

    private class TelemetryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val value: TextView = view.findViewById(R.id.value)
        val unit: TextView = view.findViewById(R.id.unit)
        val miniRose: NauticalMiniRoseView = view.findViewById(R.id.mini_rose)
        val sparkline: NauticalSparklineView = view.findViewById(R.id.sparkline)
    }
}
