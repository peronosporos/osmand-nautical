package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalSwitchPanelFragment : BaseOsmAndFragment() {

    private lateinit var adapter: SwitchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = themedInflater.inflate(R.layout.fragment_nautical_switch_panel, container, false)
        
        val windlassLayout = root.findViewById<View>(R.id.layout_windlass_control)
        val btnUp = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_windlass_up)
        val btnDown = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_windlass_down)
        val txtGuard = root.findViewById<TextView>(R.id.txt_windlass_guard)

        val energyLayout = root.findViewById<LinearLayout>(R.id.layout_energy_control)
        val chargerGroup = root.findViewById<LinearLayout>(R.id.group_chargers)
        val inverterGroup = root.findViewById<LinearLayout>(R.id.group_inverters)

        val recyclerView = root.findViewById<RecyclerView>(R.id.recycler_switches)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SwitchAdapter { path, state ->
            NauticalPlugin.engine?.setSwitch(path, state)
        }
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
                
                windlassLayout.visibility = if (caps?.hasWindlassControl == true) View.VISIBLE else View.GONE
                val engineOk = state.isEngineRunning
                btnUp.isEnabled = engineOk
                btnDown.isEnabled = engineOk
                txtGuard.visibility = if (engineOk) View.GONE else View.VISIBLE

                setupWindlassButton(btnUp, "electrical.switches.windlass.up")
                setupWindlassButton(btnDown, "electrical.switches.windlass.down")

                energyLayout.visibility = if (state.chargers.isNotEmpty() || state.inverters.isNotEmpty()) View.VISIBLE else View.GONE
                updateEnergyControls(chargerGroup, state.chargers) { instance, mode ->
                    NauticalPlugin.engine?.controlManager?.setChargerMode(instance, mode)
                }
                updateEnergyControls(inverterGroup, state.inverters) { instance, mode ->
                    NauticalPlugin.engine?.controlManager?.setInverterMode(instance, mode)
                }

                val defaultSwitches = linkedMapOf(
                    "electrical.switches.navigationLights" to false,
                    "electrical.switches.anchorLight" to false,
                    "electrical.switches.deckLights" to false,
                    "electrical.switches.bilgePumpAuto" to true,
                    "electrical.switches.freshWaterPump" to true,
                    "electrical.switches.cabinLights" to false,
                    "electrical.switches.refrigerator" to true
                )

                val effectiveSwitches = if (state.switches.isNotEmpty()) state.switches else defaultSwitches
                
                val categorized = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()
                effectiveSwitches.forEach { (path, swState) ->
                    val category = getCategoryForSwitch(path, state.pathMeta[path]?.get("displayName") as? String)
                    categorized.getOrPut(category) { mutableListOf() }.add(path to swState)
                }

                val itemsList = mutableListOf<SwitchItem>()
                val categoryOrder = listOf(
                    "Navigation & Deck Lighting",
                    "Bilge & Pumps",
                    "Deck & Ground Tackle",
                    "Domestic & Cabin",
                    "Auxiliary Power & Relays"
                )

                val sortedCategories = categorized.keys.sortedBy { cat ->
                    val idx = categoryOrder.indexOf(cat)
                    if (idx != -1) idx else 99
                }

                sortedCategories.forEach { cat ->
                    itemsList.add(SwitchItem.Header(cat))
                    categorized[cat]?.sortedBy { it.first }?.forEach { (path, swState) ->
                        val isPending = state.timestamps.containsKey("pending.electrical.switches.$path.state") || 
                                       state.timestamps.containsKey("pending.electrical.switches.$path.dimmingLevel")
                        val displayName = state.pathMeta[path]?.get("displayName") as? String
                        val dimLevel = state.dimmers[path]
                        itemsList.add(SwitchItem.SwitchEntry(path, swState, isPending, displayName, dimLevel))
                    }
                }

                adapter.submitList(itemsList)
                
                val txtNoSwitches = root.findViewById<View>(R.id.txt_no_switches)
                txtNoSwitches.visibility = View.GONE
            }
        }
        
        return root
    }

    private fun getCategoryForSwitch(path: String, displayName: String?): String {
        val key = (path + " " + (displayName ?: "")).lowercase(java.util.Locale.ROOT)
        return when {
            key.contains("nav") || key.contains("anchor") || key.contains("steaming") || key.contains("mast") || key.contains("deck_light") || key.contains("spreader") || key.contains("underwater") -> "Navigation & Deck Lighting"
            key.contains("bilge") || key.contains("pump") || key.contains("wash") || key.contains("macerator") || key.contains("freshwater") -> "Bilge & Pumps"
            key.contains("windlass") || key.contains("thruster") || key.contains("winch") || key.contains("ground") -> "Deck & Ground Tackle"
            key.contains("cabin") || key.contains("salon") || key.contains("galley") || key.contains("fridge") || key.contains("refrigerator") || key.contains("freezer") || key.contains("water_heater") || key.contains("heater") || key.contains("light") -> "Domestic & Cabin"
            else -> "Auxiliary Power & Relays"
        }
    }

    private fun <T> updateEnergyControls(container: LinearLayout?, items: Map<String, T>, onModeChange: (String, String) -> Unit) {
        if (container == null) return
        container.removeAllViews()
        for ((instance, obj) in items) {
            val tv = TextView(context).apply {
                text = "$instance: $obj"
                textSize = 14f
                setTextColor(net.osmand.plus.utils.AndroidUtils.getColorFromAttr(context, android.R.attr.textColorPrimary))
                setPadding(0, 8, 0, 8)
            }
            container.addView(tv)
        }
    }

    private fun setupWindlassButton(button: com.google.android.material.button.MaterialButton?, path: String) {
        button?.setOnTouchListener { v, event ->
            if (!button.isEnabled) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    NauticalPlugin.engine?.setSwitch(path, true)
                    v.isPressed = true
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    NauticalPlugin.engine?.setSwitch(path, false)
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private sealed class SwitchItem {
        data class Header(val title: String) : SwitchItem()
        data class SwitchEntry(
            val path: String,
            val state: Boolean,
            val isPending: Boolean,
            val displayName: String?,
            val dimLevel: Double?
        ) : SwitchItem()
    }

    private class SwitchAdapter(private val onToggle: (String, Boolean) -> Unit) : ListAdapter<SwitchItem, RecyclerView.ViewHolder>(DiffCallback()) {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_SWITCH = 1
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is SwitchItem.Header -> TYPE_HEADER
                is SwitchItem.SwitchEntry -> TYPE_SWITCH
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, (16 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
                    }
                    setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.icon_color_osmand_light))
                }
                HeaderViewHolder(tv)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
                SwitchViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is SwitchItem.Header -> (holder as HeaderViewHolder).bind(item.title)
                is SwitchItem.SwitchEntry -> (holder as SwitchViewHolder).bind(item, onToggle)
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<SwitchItem>() {
            override fun areItemsTheSame(oldItem: SwitchItem, newItem: SwitchItem): Boolean {
                return when {
                    oldItem is SwitchItem.Header && newItem is SwitchItem.Header -> oldItem.title == newItem.title
                    oldItem is SwitchItem.SwitchEntry && newItem is SwitchItem.SwitchEntry -> oldItem.path == newItem.path
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: SwitchItem, newItem: SwitchItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(title: String) {
            (itemView as? TextView)?.text = title
        }
    }

    private class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_switch_name)
        private val switchToggle: SwitchCompat = view.findViewById(R.id.switch_toggle)
        private val sliderDimmer: com.google.android.material.slider.Slider = view.findViewById(R.id.slider_dimmer)

        fun bind(entry: SwitchItem.SwitchEntry, onToggle: (String, Boolean) -> Unit) {
            val path = entry.path
            val state = entry.state
            val isPending = entry.isPending
            val displayName = entry.displayName
            val dimLevel = entry.dimLevel

            txtName.text = displayName ?: path.substringAfterLast(".").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = state
            switchToggle.isEnabled = !isPending
            switchToggle.alpha = if (isPending) 0.5f else 1.0f
            switchToggle.setOnCheckedChangeListener { _, isChecked ->
                onToggle(path, isChecked)
            }

            if (dimLevel != null) {
                sliderDimmer.visibility = View.VISIBLE
                sliderDimmer.isEnabled = !isPending
                sliderDimmer.value = dimLevel.toFloat().coerceIn(0f, 1f)
                sliderDimmer.clearOnChangeListeners()
                sliderDimmer.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        NauticalPlugin.engine?.controlManager?.setDimmerValue(path, value.toDouble())
                    }
                }
            } else {
                sliderDimmer.visibility = View.GONE
            }
        }
    }
}
