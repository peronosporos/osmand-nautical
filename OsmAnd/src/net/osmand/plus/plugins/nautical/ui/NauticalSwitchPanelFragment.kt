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
import androidx.core.view.isGone

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
                adapter.submitList(switches)
                adapter.timestamps = state.timestamps
                root.findViewById<View>(R.id.txt_no_switches).visibility = if (switches.isEmpty() && energyLayout.isGone) View.VISIBLE else View.GONE
            }
        }
        
        return root
    }

    private fun <T> updateEnergyControls(container: LinearLayout, items: Map<String, T>, onModeChange: (String, String) -> Unit) {
        container.removeAllViews()
        items.forEach { (instance, item) ->
            val view = themedInflater.inflate(R.layout.item_nautical_charger_inverter, container, false)
            val name = if (item is net.osmand.plus.plugins.nautical.engine.Charger) {
                item.name ?: getString(R.string.nautical_charger_instance, instance)
            } else {
                (item as net.osmand.plus.plugins.nautical.engine.Inverter).name ?: getString(R.string.nautical_inverter_instance, instance)
            }
            view.findViewById<TextView>(R.id.txt_device_name).text = name
            val spinner = view.findViewById<android.widget.Spinner>(R.id.spinner_device_mode)
            val modes = if (item is net.osmand.plus.plugins.nautical.engine.Charger) listOf("off", "on", "only_eco") else listOf("off", "on", "eco")
            val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modes)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            val currentMode = if (item is net.osmand.plus.plugins.nautical.engine.Charger) item.mode else (item as net.osmand.plus.plugins.nautical.engine.Inverter).mode
            spinner.setSelection(modes.indexOf(currentMode?.lowercase()))
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newMode = modes[position]
                    if (newMode != currentMode?.lowercase()) {
                        onModeChange(instance, newMode)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            container.addView(view)
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
            return SwitchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
            val (path, state) = getItem(position)
            val isPending = timestamps.containsKey("pending.electrical.switches.$path.state")
            holder.bind(path, state, isPending, onToggle)
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

        fun bind(path: String, state: Boolean, isPending: Boolean, onToggle: (String, Boolean) -> Unit) {
            txtName.text = path.substringAfterLast(".").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = state
            switchToggle.isEnabled = !isPending
            switchToggle.alpha = if (isPending) 0.5f else 1.0f
            switchToggle.setOnCheckedChangeListener { _, isChecked ->
                onToggle(path, isChecked)
            }
        }
    }
}
