package net.osmand.plus.plugins.nautical.telemetry

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.utils.AndroidUtils

class TelemetryWidgetSettingsFragment : BaseOsmAndFragment() {

    private lateinit var reorderAdapter: TelemetryReorderAdapter
    private val activeItems = mutableListOf<TelemetryItemConfig>()
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = themedInflater.inflate(R.layout.fragment_nautical_telemetry_settings, container, false)
        setupViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        if (::reorderAdapter.isInitialized) {
            reorderAdapter.notifyDataSetChanged()
        }
    }

    private fun setupViews(rootView: View) {
        loadSettings()

        val toolbar = rootView.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.title = getString(R.string.nautical_telemetry_widget_config)
        toolbar?.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar?.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        rootView.findViewById<View>(R.id.close_button)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        rootView.findViewById<TextView>(R.id.toolbar_title)?.text = getString(R.string.nautical_telemetry_widget_config)

        val recyclerView: RecyclerView = rootView.findViewById(R.id.recycler_reorder)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        reorderAdapter = TelemetryReorderAdapter(
            items = activeItems,
            onStartDragListener = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onItemsChangedListener = {
                saveSettings()
            }
        )
        recyclerView.adapter = reorderAdapter
        reorderAdapter.notifyDataSetChanged()

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    reorderAdapter.moveItem(from, to)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    reorderAdapter.removeItem(pos)
                }
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)

        // Presets
        val btnSailing = rootView.findViewById<TextView>(R.id.btn_preset_sailing)
        val btnPilotage = rootView.findViewById<TextView>(R.id.btn_preset_pilotage)
        val btnAnchorage = rootView.findViewById<TextView>(R.id.btn_preset_anchorage)
        val btnPassage = rootView.findViewById<TextView>(R.id.btn_preset_passage)

        val presetButtons = listOf(btnSailing, btnPilotage, btnAnchorage, btnPassage)
        val activeColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)
        val cardBgColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.card_and_list_background_basic)
        val textColorSecondary = AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorSecondary)

        fun updatePresetButtons(activeBtn: TextView?) {
            for (btn in presetButtons) {
                if (btn == activeBtn) {
                    btn.backgroundTintList = ColorStateList.valueOf(activeColor)
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(cardBgColor)
                    btn.setTextColor(textColorSecondary)
                }
            }
        }

        // Initialize preset button styling
        updatePresetButtons(null)

        btnSailing.setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_SAILING)
            updatePresetButtons(btnSailing)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        btnPilotage.setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_PILOTAGE)
            updatePresetButtons(btnPilotage)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        btnAnchorage.setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_ANCHORAGE)
            updatePresetButtons(btnAnchorage)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        btnPassage.setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_PASSAGE)
            updatePresetButtons(btnPassage)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Add Metric Button
        rootView.findViewById<View>(R.id.btn_add_metric).setOnClickListener {
            showAddMetricDialog()
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun loadSettings() {
        var raw = settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.get()
        if (raw.isNullOrEmpty()) {
            raw = TelemetryRegistry.getPresetKeys(TelemetryRegistry.PRESET_SAILING).joinToString(",")
            settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(raw)
        }
        val keys = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        activeItems.clear()
        for (k in keys) {
            val isHidden = k.startsWith("!")
            val actualKey = if (isHidden) k.substring(1) else k
            val metric = TelemetryRegistry.getMetric(actualKey)
            val resolvedKey = metric?.key ?: actualKey
            if (activeItems.none { it.key == resolvedKey }) {
                activeItems.add(TelemetryItemConfig(resolvedKey, isVisible = !isHidden))
            }
        }
        if (activeItems.isEmpty()) {
            val defaultKeys = TelemetryRegistry.getPresetKeys(TelemetryRegistry.PRESET_SAILING)
            for (dk in defaultKeys) {
                activeItems.add(TelemetryItemConfig(dk, isVisible = true))
            }
            saveSettings()
        }
    }

    private fun saveSettings() {
        val current = if (::reorderAdapter.isInitialized) reorderAdapter.getItems() else activeItems
        val serialized = current.joinToString(",") { item ->
            if (item.isVisible) item.key else "!${item.key}"
        }
        settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(serialized)
    }

    private fun applyPreset(presetId: String) {
        val presetKeys = TelemetryRegistry.getPresetKeys(presetId)
        val newConfigs = presetKeys.map { TelemetryItemConfig(it, isVisible = true) }
        reorderAdapter.setItems(newConfigs)
        saveSettings()
    }

    private fun showAddMetricDialog() {
        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val dialogView = LayoutInflater.from(themedCtx).inflate(R.layout.dialog_nautical_add_metric, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_add_metrics)
        recycler.layoutManager = LinearLayoutManager(themedCtx)

        val activeColor = AndroidUtils.getColorFromAttr(themedCtx, R.attr.active_color_primary)
        val cardBgColor = AndroidUtils.getColorFromAttr(themedCtx, R.attr.card_and_list_background_basic)
        val textColorSecondary = AndroidUtils.getColorFromAttr(themedCtx, android.R.attr.textColorSecondary)

        var selectedCategory: MetricCategory? = null
        val activeKeys = reorderAdapter.getItems().map { it.key }.toSet()
        val allMetrics = TelemetryRegistry.getAllMetrics().filter { it.key !in activeKeys }

        var dialog: AlertDialog? = null

        val addMetricAdapter = AddMetricAdapter(
            onItemClicked = { metricKey ->
                reorderAdapter.addItem(metricKey)
                saveSettings()
                dialog?.dismiss()
            }
        )
        recycler.adapter = addMetricAdapter

        val chipAll = dialogView.findViewById<TextView>(R.id.chip_cat_all)
        val chipNav = dialogView.findViewById<TextView>(R.id.chip_cat_nav)
        val chipWind = dialogView.findViewById<TextView>(R.id.chip_cat_wind)
        val chipEnv = dialogView.findViewById<TextView>(R.id.chip_cat_env)
        val chipVessel = dialogView.findViewById<TextView>(R.id.chip_cat_vessel)
        val chipPower = dialogView.findViewById<TextView>(R.id.chip_cat_power)
        val chips = listOf(chipAll, chipNav, chipWind, chipEnv, chipVessel, chipPower)

        fun updateList(activeChip: TextView) {
            for (c in chips) {
                if (c == activeChip) {
                    c.backgroundTintList = ColorStateList.valueOf(activeColor)
                    c.setTextColor(Color.WHITE)
                } else {
                    c.backgroundTintList = ColorStateList.valueOf(cardBgColor)
                    c.setTextColor(textColorSecondary)
                }
            }

            val filtered = if (selectedCategory == null) {
                allMetrics
            } else {
                allMetrics.filter { it.category == selectedCategory }
            }
            addMetricAdapter.setItems(filtered)
        }

        chipAll.setOnClickListener {
            selectedCategory = null
            updateList(chipAll)
        }
        chipNav.setOnClickListener {
            selectedCategory = MetricCategory.NAVIGATION
            updateList(chipNav)
        }
        chipWind.setOnClickListener {
            selectedCategory = MetricCategory.WIND
            updateList(chipWind)
        }
        chipEnv.setOnClickListener {
            selectedCategory = MetricCategory.ENVIRONMENT
            updateList(chipEnv)
        }
        chipVessel.setOnClickListener {
            selectedCategory = MetricCategory.VESSEL
            updateList(chipVessel)
        }
        chipPower.setOnClickListener {
            selectedCategory = MetricCategory.POWER
            updateList(chipPower)
        }

        updateList(chipAll)

        dialog = AlertDialog.Builder(themedCtx)
            .setView(dialogView)
            .setNegativeButton(R.string.shared_string_cancel, null)
            .create()
        dialog.show()
    }

    private class AddMetricAdapter(
        private val onItemClicked: (String) -> Unit
    ) : RecyclerView.Adapter<AddMetricAdapter.ViewHolder>() {

        private val items = mutableListOf<TelemetryMetricDefinition>()

        fun setItems(newItems: List<TelemetryMetricDefinition>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_telemetry_reorder, parent, false)
            v.findViewById<View>(R.id.btn_drag_handle).visibility = View.GONE
            v.findViewById<View>(R.id.btn_visibility_toggle).visibility = View.GONE
            v.findViewById<View>(R.id.btn_delete_metric).visibility = View.GONE
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val metric = items[position]
            holder.titleView.text = holder.itemView.context.getString(metric.titleRes)
            holder.catView.text = holder.itemView.context.getString(metric.category.titleRes)
            holder.itemView.setOnClickListener {
                onItemClicked(metric.key)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleView: TextView = view.findViewById(R.id.txt_metric_name)
            val catView: TextView = view.findViewById(R.id.txt_metric_category)
        }
    }
}
