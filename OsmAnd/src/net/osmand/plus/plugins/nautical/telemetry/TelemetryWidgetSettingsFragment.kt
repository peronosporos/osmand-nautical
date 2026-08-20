package net.osmand.plus.plugins.nautical.telemetry

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

    private fun setupViews(rootView: View) {
        loadSettings()

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

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
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

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)

        // Presets
        rootView.findViewById<View>(R.id.btn_preset_sailing).setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_SAILING)
        }
        rootView.findViewById<View>(R.id.btn_preset_pilotage).setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_PILOTAGE)
        }
        rootView.findViewById<View>(R.id.btn_preset_anchorage).setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_ANCHORAGE)
        }
        rootView.findViewById<View>(R.id.btn_preset_passage).setOnClickListener {
            applyPreset(TelemetryRegistry.PRESET_PASSAGE)
        }

        // Add Metric Button
        rootView.findViewById<View>(R.id.btn_add_metric).setOnClickListener {
            showAddMetricDialog()
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
            activeItems.add(TelemetryItemConfig(actualKey, isVisible = !isHidden))
        }
    }

    private fun saveSettings() {
        val serialized = activeItems.joinToString(",") { item ->
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
        val context = requireContext()
        val dialogView = themedInflater.inflate(R.layout.dialog_nautical_add_metric, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_add_metrics)
        recycler.layoutManager = LinearLayoutManager(context)

        var selectedCategory: MetricCategory? = null
        val allMetrics = TelemetryRegistry.getAllMetrics()
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setNegativeButton(R.string.shared_string_cancel, null)
            .create()

        fun updateList() {
            val filtered = if (selectedCategory == null) {
                allMetrics
            } else {
                allMetrics.filter { it.category == selectedCategory }
            }

            recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val v = themedInflater.inflate(R.layout.item_nautical_telemetry_reorder, parent, false)
                    v.findViewById<View>(R.id.btn_drag_handle).visibility = View.GONE
                    v.findViewById<View>(R.id.btn_visibility_toggle).visibility = View.GONE
                    v.findViewById<View>(R.id.btn_delete_metric).visibility = View.GONE
                    return object : RecyclerView.ViewHolder(v) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val metric = filtered[position]
                    val titleView = holder.itemView.findViewById<TextView>(R.id.txt_metric_name)
                    val catView = holder.itemView.findViewById<TextView>(R.id.txt_metric_category)
                    titleView.text = context.getString(metric.titleRes)
                    catView.text = context.getString(metric.category.titleRes)

                    holder.itemView.setOnClickListener {
                        reorderAdapter.addItem(metric.key)
                        saveSettings()
                        dialog.dismiss()
                    }
                }

                override fun getItemCount(): Int = filtered.size
            }
        }

        dialogView.findViewById<View>(R.id.chip_cat_all).setOnClickListener {
            selectedCategory = null
            updateList()
        }
        dialogView.findViewById<View>(R.id.chip_cat_nav).setOnClickListener {
            selectedCategory = MetricCategory.NAVIGATION
            updateList()
        }
        dialogView.findViewById<View>(R.id.chip_cat_wind).setOnClickListener {
            selectedCategory = MetricCategory.WIND
            updateList()
        }
        dialogView.findViewById<View>(R.id.chip_cat_env).setOnClickListener {
            selectedCategory = MetricCategory.ENVIRONMENT
            updateList()
        }
        dialogView.findViewById<View>(R.id.chip_cat_vessel).setOnClickListener {
            selectedCategory = MetricCategory.VESSEL
            updateList()
        }
        dialogView.findViewById<View>(R.id.chip_cat_power).setOnClickListener {
            selectedCategory = MetricCategory.POWER
            updateList()
        }

        updateList()
        dialog.show()
    }
}
