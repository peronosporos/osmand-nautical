package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.telemetry.AngleSide
import net.osmand.plus.plugins.nautical.telemetry.FilteredMetricState
import net.osmand.plus.plugins.nautical.telemetry.TelemetryRegistry
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalMenuBottomSheetDialogFragment
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType

class MasterTelemetryBottomSheet : NauticalMenuBottomSheetDialogFragment() {

    private var recyclerView: RecyclerView? = null
    private var adapter: MasterTelemetryAdapter? = null
    private var widgetId: String? = null

    companion object {
        const val TAG = "nautical_master_telemetry"
        const val KEY_WIDGET_ID = "widget_id"

        fun show(manager: FragmentManager, widgetId: String? = null) {
            if (manager.isStateSaved) return
            if (manager.findFragmentByTag(TAG) == null) {
                val fragment = MasterTelemetryBottomSheet()
                val args = Bundle().apply {
                    putString(KEY_WIDGET_ID, widgetId)
                }
                fragment.arguments = args
                fragment.show(manager, TAG)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = arguments?.getString(KEY_WIDGET_ID)
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as OsmandApplication
        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val layout = LayoutInflater.from(themedCtx)
            .inflate(R.layout.bottom_sheet_nautical_master_telemetry, null)

        val btnSettings = layout.findViewById<ImageView>(R.id.btn_settings)
        btnSettings.setOnClickListener {
            onRightBottomButtonClick()
        }

        recyclerView = layout.findViewById(R.id.telemetry_recycler_view)
        val isLandscapeOrTablet = resources.configuration.screenWidthDp >= 600 ||
                resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscapeOrTablet) 3 else 2

        val gridLayoutManager = GridLayoutManager(requireContext(), spanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val metricKey = adapter?.getItemKey(position) ?: return 1
                val metricDef = TelemetryRegistry.getMetric(metricKey) ?: return 1
                return if (metricDef.spanSize >= 2) spanCount else 1
            }
        }
        recyclerView?.layoutManager = gridLayoutManager

        adapter = MasterTelemetryAdapter(
            context = requireContext(),
            app = app,
            onItemClicked = { key ->
                MetricGraphBottomSheet.show(parentFragmentManager, key)
            }
        )
        recyclerView?.adapter = adapter

        // Preset Chips
        val chipSailing = layout.findViewById<TextView>(R.id.chip_preset_sailing)
        val chipPilotage = layout.findViewById<TextView>(R.id.chip_preset_pilotage)
        val chipAnchorage = layout.findViewById<TextView>(R.id.chip_preset_anchorage)
        val chipPassage = layout.findViewById<TextView>(R.id.chip_preset_passage)

        val chips = listOf(chipSailing, chipPilotage, chipAnchorage, chipPassage)
        val activeColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)
        val cardBgColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.card_and_list_background_basic)
        val textColorSecondary = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorSecondary)

        fun selectPreset(chip: TextView, presetId: String) {
            val presetKeys = TelemetryRegistry.getPresetKeys(presetId)
            app.settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(presetKeys.joinToString(","))
            for (c in chips) {
                if (c == chip) {
                    c.setBackgroundResource(R.drawable.btn_active_light)
                    c.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                    c.setTextColor(Color.WHITE)
                } else {
                    c.setBackgroundResource(R.drawable.btn_active_light)
                    c.backgroundTintList = android.content.res.ColorStateList.valueOf(cardBgColor)
                    c.setTextColor(textColorSecondary)
                }
            }
            adapter?.loadMetricsFromSettings()
        }

        chipSailing.setOnClickListener { selectPreset(chipSailing, TelemetryRegistry.PRESET_SAILING) }
        chipPilotage.setOnClickListener { selectPreset(chipPilotage, TelemetryRegistry.PRESET_PILOTAGE) }
        chipAnchorage.setOnClickListener { selectPreset(chipAnchorage, TelemetryRegistry.PRESET_ANCHORAGE) }
        chipPassage.setOnClickListener { selectPreset(chipPassage, TelemetryRegistry.PRESET_PASSAGE) }

        // Initial preset styling
        for (c in chips) {
            c.setBackgroundResource(R.drawable.btn_active_light)
            c.backgroundTintList = android.content.res.ColorStateList.valueOf(cardBgColor)
            c.setTextColor(textColorSecondary)
        }
        chipSailing.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
        chipSailing.setTextColor(Color.WHITE)

        adapter?.loadMetricsFromSettings()

        items.add(BaseBottomSheetItem.Builder().setCustomView(layout).create())

        observeData()
    }

    private fun observeData() {
        val filterEngine = NauticalPlugin.getInstance()?.telemetryFilterEngine
        if (filterEngine != null) {
            lifecycleScope.launch {
                filterEngine.filteredMetrics.collectLatest { metrics ->
                    adapter?.updateMetricStates(metrics)
                }
            }
        }
    }

    override fun onRightBottomButtonClick() {
        BaseSettingsFragment.showInstance(
            requireActivity(),
            SettingsScreenType.NAUTICAL_TELEMETRY_CONFIG
        )
        dismiss()
    }

    class MasterTelemetryAdapter(
        private val context: Context,
        private val app: OsmandApplication,
        private val onItemClicked: (String) -> Unit
    ) : RecyclerView.Adapter<MasterTelemetryAdapter.ViewHolder>() {

        private val displayedKeys = mutableListOf<String>()
        private var metricStates: Map<String, FilteredMetricState> = emptyMap()

        // Cached semantic colors
        private val colorTextPrimary = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(context, android.R.attr.textColorPrimary)
        private val colorTextSecondary = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(context, android.R.attr.textColorSecondary)
        private val colorPort = ContextCompat.getColor(context, R.color.nautical_status_red)
        private val colorStarboard = ContextCompat.getColor(context, R.color.nautical_status_green)

        private val colorNav = Color.rgb(2, 136, 209)
        private val colorWind = Color.rgb(0, 137, 123)
        private val colorEnv = Color.rgb(57, 73, 171)
        private val colorVessel = Color.rgb(245, 124, 0)
        private val colorPower = Color.rgb(216, 155, 0)

        fun loadMetricsFromSettings() {
            var raw = app.settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.get()
            if (raw.isNullOrEmpty()) {
                raw = TelemetryRegistry.getPresetKeys(TelemetryRegistry.PRESET_SAILING).joinToString(",")
                app.settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(raw)
            }
            val keys = raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("!") }
                .map { key ->
                    val metric = TelemetryRegistry.getMetric(key)
                    metric?.key ?: key
                }

            displayedKeys.clear()
            displayedKeys.addAll(keys)
            notifyDataSetChanged()
        }

        fun updateMetricStates(newStates: Map<String, FilteredMetricState>) {
            metricStates = newStates
            notifyDataSetChanged()
        }

        fun getItemKey(position: Int): String? = displayedKeys.getOrNull(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_nautical_telemetry_tile, parent, false)
            return ViewHolder(view)
        }

        private fun getCategoryColor(category: net.osmand.plus.plugins.nautical.telemetry.MetricCategory?): Int {
            return when (category) {
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.NAVIGATION -> colorNav
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.WIND -> colorWind
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.ENVIRONMENT -> colorEnv
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.VESSEL -> colorVessel
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.POWER -> colorPower
                null -> colorTextSecondary
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val key = displayedKeys[position]
            val metricDef = TelemetryRegistry.getMetric(key)
            val state = metricStates[key]

            if (metricDef != null) {
                holder.txtHeader.text = context.getString(metricDef.titleRes)
                holder.txtHeader.setTextColor(getCategoryColor(metricDef.category))
            } else {
                holder.txtHeader.text = key
                holder.txtHeader.setTextColor(colorTextSecondary)
            }

            if (state != null) {
                holder.itemView.alpha = state.alpha
                holder.txtValue.text = state.formatted.primaryText
                holder.txtUnit.text = state.formatted.unitText
                holder.txtUnit.setTextColor(colorTextSecondary)

                if (state.formatted.secondaryText.isNotEmpty()) {
                    holder.txtSecondary.visibility = View.VISIBLE
                    holder.txtSecondary.text = state.formatted.secondaryText
                    holder.txtSecondary.setTextColor(colorTextSecondary)
                } else {
                    holder.txtSecondary.visibility = View.GONE
                }

                if (state.formatted.isPortStarboard && state.formatted.angleSide != AngleSide.NONE) {
                    holder.badgePortStarboard.visibility = View.VISIBLE
                    if (state.formatted.angleSide == AngleSide.STARBOARD) {
                        holder.badgePortStarboard.text = context.getString(R.string.nautical_starboard_indicator)
                        holder.badgePortStarboard.setBackgroundColor(colorStarboard)
                        holder.txtValue.setTextColor(colorStarboard)
                    } else {
                        holder.badgePortStarboard.text = context.getString(R.string.nautical_port_indicator)
                        holder.badgePortStarboard.setBackgroundColor(colorPort)
                        holder.txtValue.setTextColor(colorPort)
                    }
                } else {
                    holder.badgePortStarboard.visibility = View.GONE
                    holder.txtValue.setTextColor(colorTextPrimary)
                }
            } else {
                holder.itemView.alpha = 0.4f
                holder.txtValue.text = "---"
                holder.txtValue.setTextColor(colorTextSecondary)
                holder.txtUnit.text = ""
                holder.txtSecondary.visibility = View.GONE
                holder.badgePortStarboard.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClicked(key)
            }
        }

        override fun getItemCount(): Int = displayedKeys.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtHeader: TextView = view.findViewById(R.id.txt_header)
            val badgePortStarboard: TextView = view.findViewById(R.id.badge_port_starboard)
            val txtValue: TextView = view.findViewById(R.id.txt_value)
            val txtUnit: TextView = view.findViewById(R.id.txt_unit)
            val txtSecondary: TextView = view.findViewById(R.id.txt_secondary)
        }
    }
}
