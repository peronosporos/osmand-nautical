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

                val switches = state.switches.asSequence().map { it.key to it.value }.sortedBy { it.first }.toList()
                adapter.timestamps = state.timestamps
                adapter.dimmers = state.dimmers
                adapter.meta = state.pathMeta
                adapter.submitList(switches)
                
                val showEmpty = switches.isEmpty() && energyLayout.visibility != View.VISIBLE && windlassLayout.visibility != View.VISIBLE
                val txtNoSwitches = root.findViewById<View>(R.id.txt_no_switches)
                if (txtNoSwitches.visibility != (if (showEmpty) View.VISIBLE else View.GONE)) {
                    txtNoSwitches.visibility = if (showEmpty) View.VISIBLE else View.GONE
                }
            }
        }
        
        return root
    }

    private fun <T> updateEnergyControls(container: LinearLayout, items: Map<String, T>, onModeChange: (String, String) -> Unit) {
        val currentChildCount = container.childCount
        val itemKeys = items.keys.toList()

        if (currentChildCount > itemKeys.size) {
            container.removeViews(itemKeys.size, currentChildCount - itemKeys.size)
        }

        itemKeys.forEachIndexed { index, instance ->
            val item = items[instance]!!
            val view = if (index < container.childCount) {
                container.getChildAt(index)
            } else {
                val v = themedInflater.inflate(R.layout.item_nautical_charger_inverter, container, false)
                container.addView(v)
                v
            }

            val name = if (item is net.osmand.plus.plugins.nautical.engine.Charger) {
                item.name ?: getString(R.string.nautical_charger_instance, instance)
            } else {
                (item as? net.osmand.plus.plugins.nautical.engine.Inverter)?.name ?: getString(R.string.nautical_inverter_instance, instance)
            }
            view.findViewById<TextView>(R.id.txt_device_name)?.text = name
            
            val spinner = view.findViewById<android.widget.Spinner?>(R.id.spinner_device_mode)
            val path = if (item is net.osmand.plus.plugins.nautical.engine.Charger) "electrical.chargers.$instance" else "electrical.inverters.$instance"
            val meta = NauticalPlugin.engine?.getCurrentState()?.pathMeta?.get("$path.mode")
            
            @Suppress("UNCHECKED_CAST")
            val possibleValues = (meta?.get("possibleValues") as? List<String>)?.map { it.lowercase() }
            
            val modes = possibleValues ?: (if (item is net.osmand.plus.plugins.nautical.engine.Charger) listOf("off", "on", "only_eco") else listOf("off", "on", "eco"))
            
            if (spinner != null && spinner.tag != modes) {
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modes.map { it.replace("_", " ").uppercase() })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
                spinner.tag = modes
            }

            val currentMode = if (item is net.osmand.plus.plugins.nautical.engine.Charger) item.mode else (item as? net.osmand.plus.plugins.nautical.engine.Inverter)?.mode
            val targetIdx = modes.indexOf(currentMode?.lowercase())
            if (spinner != null && targetIdx != -1 && spinner.selectedItemPosition != targetIdx) {
                spinner.setSelection(targetIdx, false)
            }

            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                    val newMode = modes[position]
                    if (newMode != currentMode?.lowercase()) {
                        onModeChange(instance, newMode)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

    private fun setupWindlassButton(button: View, path: String) {
        button.setOnTouchListener { v, event ->
            if (!button.isEnabled) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    NauticalPlugin.engine?.setSwitch(path, true)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    NauticalPlugin.engine?.setSwitch(path, false)
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private class SwitchAdapter(private val onToggle: (String, Boolean) -> Unit) : ListAdapter<Pair<String, Boolean>, SwitchViewHolder>(DiffCallback()) {
        var timestamps: Map<String, Long> = emptyMap()
        var dimmers: Map<String, Double> = emptyMap()
        var meta: Map<String, Map<String, Any>> = emptyMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
            return SwitchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
            val (path, state) = getItem(position)
            val isPending = timestamps.containsKey("pending.electrical.switches.$path.state") || 
                           timestamps.containsKey("pending.electrical.switches.$path.dimmingLevel")
            val displayName = meta[path]?.get("displayName") as? String
            val dimLevel = dimmers[path]
            holder.bind(path, state, isPending, displayName, dimLevel, onToggle)
        }

        private class DiffCallback : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
            override fun areItemsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean {
                return oldItem.first == newItem.first
            }

            override fun areContentsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean {
                return oldItem == newItem
            }
        }
    }

    private class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_switch_name)
        private val switchToggle: SwitchCompat = view.findViewById(R.id.switch_toggle)
        private val sliderDimmer: com.google.android.material.slider.Slider = view.findViewById(R.id.slider_dimmer)

        fun bind(path: String, state: Boolean, isPending: Boolean, displayName: String?, dimLevel: Double?, onToggle: (String, Boolean) -> Unit) {
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
