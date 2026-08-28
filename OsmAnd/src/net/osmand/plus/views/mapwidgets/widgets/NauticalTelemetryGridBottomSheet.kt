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
import net.osmand.plus.plugins.nautical.ui.NauticalWidgetHelper
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalMenuBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
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
            net.osmand.plus.plugins.nautical.ui.MasterTelemetryBottomSheet.show(manager, widgetId)
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

            holder.header.text = getHeaderLabel(widget)

            val (value, unit) = NauticalWidgetHelper.formatTelemetry(requireContext(), app.settings, widget, state)
            holder.value.text = value
            holder.unit.text = unit

            val color = getSemanticColor(widget, state)
            holder.value.setTextColor(color)

            holder.itemView.setOnClickListener {
                NauticalDataBottomSheet.newInstance(widget).show(parentFragmentManager, "nautical_data")
            }
        }

        private fun getHeaderLabel(widget: WidgetType): String {
            return when (widget) {
                WidgetType.NAUTICAL_SOG -> "SOG"
                WidgetType.NAUTICAL_STW -> "STW"
                WidgetType.NAUTICAL_COG -> "COG"
                WidgetType.NAUTICAL_HEADING_MAGNETIC -> "HEADING"
                WidgetType.NAUTICAL_DEPTH_KEEL -> "DEPTH"
                WidgetType.NAUTICAL_WIND -> "WIND"
                WidgetType.NAUTICAL_AWA -> "AWA"
                WidgetType.NAUTICAL_AWS -> "AWS"
                WidgetType.NAUTICAL_TWA -> "TWA"
                WidgetType.NAUTICAL_VMG -> "VMG"
                WidgetType.NAUTICAL_BATTERY_VOLT -> "BATTERY"
                WidgetType.NAUTICAL_BATTERY_SOC -> "SOC"
                WidgetType.NAUTICAL_WATER_TEMP -> "SEA TEMP"
                WidgetType.NAUTICAL_OUTSIDE_TEMP -> "AIR TEMP"
                WidgetType.NAUTICAL_PRESSURE -> "BARO"
                WidgetType.NAUTICAL_ROLL -> "ROLL"
                WidgetType.NAUTICAL_PITCH -> "PITCH"
                WidgetType.NAUTICAL_LOG -> "LOG"
                WidgetType.NAUTICAL_TRIP_LOG -> "TRIP"
                WidgetType.NAUTICAL_ENGINE_RUNTIME -> "ENGINE"
                WidgetType.NAUTICAL_XTE -> "XTE"
                WidgetType.NAUTICAL_DTW -> "DTW"
                else -> runCatching { getString(widget.titleId).uppercase() }.getOrNull() ?: widget.id.uppercase()
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
    }

    private class TelemetryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.header)
        val value: TextView = view.findViewById(R.id.value)
        val unit: TextView = view.findViewById(R.id.unit)
    }
}
