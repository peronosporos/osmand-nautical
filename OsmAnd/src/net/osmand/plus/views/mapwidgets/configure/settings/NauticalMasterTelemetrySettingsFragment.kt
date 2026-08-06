package net.osmand.plus.views.mapwidgets.configure.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import net.osmand.plus.R
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalMasterTelemetrySettingsFragment : BaseSimpleWidgetInfoFragment() {

    private lateinit var itemsContainer: LinearLayout
    private var recyclerView: RecyclerView? = null
    private var adapter: ItemsAdapter? = null
    private val selectedItems = mutableListOf<WidgetType>()
    private var currentMode = 0 // 0: Passage, 1: Docking, 2: Anchored

    override fun getWidget(): WidgetType = WidgetType.NAUTICAL_MASTER_TELEMETRY

    override fun setupMainContent(container: ViewGroup) {
        super.setupMainContent(container)
        
        val inflater = LayoutInflater.from(requireContext())
        inflater.inflate(R.layout.map_marker_side_widget_settings_fragment, container)
        itemsContainer = view.findViewById(R.id.items_container)
        
        setupModeSwitcher()
        loadItemsForMode()
        
        val context = requireContext()

        // Auto-switch Toggle
        val autoSwitchRow = inflater.inflate(R.layout.configure_screen_list_item, itemsContainer, false)
        autoSwitchRow.findViewById<TextView>(R.id.title).text = getString(R.string.nautical_master_telemetry_auto_switch)
        val switchView = androidx.appcompat.widget.SwitchCompat(context)
        switchView.isChecked = settings.NAUTICAL_MASTER_TELEMETRY_AUTO_SWITCH.get()
        switchView.setOnCheckedChangeListener { _, isChecked ->
            settings.NAUTICAL_MASTER_TELEMETRY_AUTO_SWITCH.set(isChecked)
        }
        (autoSwitchRow as ViewGroup).addView(switchView)
        itemsContainer.addView(autoSwitchRow)
        
        // Header
        val header = TextView(context)
        header.text = getString(R.string.nautical_telemetry_items)
        header.setPadding(net.osmand.plus.utils.AndroidUtils.dpToPx(app, 16f), net.osmand.plus.utils.AndroidUtils.dpToPx(app, 8f), 0, 0)
        itemsContainer.addView(header)

        // Items List
        recyclerView = RecyclerView(context)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        adapter = ItemsAdapter()
        recyclerView?.adapter = adapter
        
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                java.util.Collections.swap(selectedItems, from, to)
                adapter?.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(recyclerView)
        
        itemsContainer.addView(recyclerView)

        // Add Button
        val addButton = inflater.inflate(R.layout.configure_screen_list_item, itemsContainer, false)
        addButton.findViewById<TextView>(R.id.title).text = getString(R.string.nautical_add_telemetry_item)
        addButton.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(R.drawable.ic_action_plus)
            AndroidUiHelper.updateVisibility(this, true)
        }
        addButton.findViewById<View>(R.id.button_container).setOnClickListener {
            showAddItemDialog()
        }
        itemsContainer.addView(addButton)
    }

    private fun showAddItemDialog() {
        val available = WidgetType.entries.filter { 
            it.id.startsWith("nautical_") && 
            it != WidgetType.NAUTICAL_MASTER_TELEMETRY && 
            !selectedItems.contains(it) &&
            it.isAllowed
        }
        val names = available.map { getString(it.titleId) }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_select_telemetry_item)
            .setItems(names) { _, which ->
                selectedItems.add(available[which])
                adapter?.notifyItemInserted(selectedItems.size - 1)
            }
            .show()
    }

    private fun setupModeSwitcher() {
        val tabLayout = TabLayout(requireContext())
        tabLayout.addTab(tabLayout.newTab().setText(R.string.nautical_workflow_tactical))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.nautical_workflow_close_quarters))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.nautical_workflow_anchored))
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                saveItemsForMode()
                currentMode = tab?.position ?: 0
                loadItemsForMode()
                @Suppress("NotifyDataSetChanged")
                adapter?.notifyDataSetChanged()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        itemsContainer.addView(tabLayout, 0)
    }

    private fun loadItemsForMode() {
        val pref = getPrefForMode()
        val itemIds = pref.get().split(",").filter { it.isNotEmpty() }
        selectedItems.clear()
        selectedItems.addAll(itemIds.mapNotNull { WidgetType.getById(it) })
    }

    private fun saveItemsForMode() {
        val pref = getPrefForMode()
        pref.set(selectedItems.joinToString(",") { it.id })
    }

    private fun getPrefForMode(): CommonPreference<String> {
        return when (currentMode) {
            1 -> settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_DOCKING
            2 -> settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_ANCHORED
            else -> settings.NAUTICAL_MASTER_TELEMETRY_ITEMS_PASSAGE
        }
    }

    override fun applySettings() {
        saveItemsForMode()
        // Sync the main item set with current selection for immediate widget update if not in auto-workflow
        settings.NAUTICAL_MASTER_TELEMETRY_ITEMS.set(selectedItems.joinToString(",") { it.id })
        super.applySettings()
    }

    private inner class ItemsAdapter : RecyclerView.Adapter<ItemViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_telemetry_settings, parent, false)
            return ItemViewHolder(v)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = selectedItems[position]
            holder.title.text = getString(item.titleId)
            holder.icon.setImageResource(item.getIconId(nightMode))
            holder.delete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    selectedItems.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }

        override fun getItemCount(): Int = selectedItems.size
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val icon: ImageView = view.findViewById(R.id.icon)
        val delete: ImageView = view.findViewById(R.id.delete)
    }
}
