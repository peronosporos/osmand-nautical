package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.*
import java.util.Locale

class NauticalElectricalDashboardBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var batteryAdapter: BatteryAdapter
    private lateinit var tankAdapter: TankAdapter
    private lateinit var switchAdapter: SwitchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = themedInflater.inflate(R.layout.bottom_sheet_nautical_electrical, container, false)

        val rvBatteries = root.findViewById<RecyclerView>(R.id.rv_batteries)
        rvBatteries.layoutManager = GridLayoutManager(context, 2)
        batteryAdapter = BatteryAdapter()
        rvBatteries.adapter = batteryAdapter

        val rvTanks = root.findViewById<RecyclerView>(R.id.rv_tanks)
        rvTanks.layoutManager = LinearLayoutManager(context)
        tankAdapter = TankAdapter()
        rvTanks.adapter = tankAdapter

        val rvConversion = root.findViewById<RecyclerView>(R.id.rv_conversion)
        rvConversion.layoutManager = LinearLayoutManager(context)
        val conversionAdapter = ConversionAdapter()
        rvConversion.adapter = conversionAdapter

        val rvSwitches = root.findViewById<RecyclerView>(R.id.rv_switches)
        rvSwitches.layoutManager = LinearLayoutManager(context)
        switchAdapter = SwitchAdapter(
            onToggle = { path, state -> NauticalPlugin.engine?.setSwitch(path, state) }
        ) { path -> showDimmerDialog(path) }
        rvSwitches.adapter = switchAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            val engine = NauticalPlugin.engine
            val caps = engine?.capabilityManager?.capabilities?.value
            engine?.marineStateFlow?.collectLatest { state ->
                batteryAdapter.submitList(state.batteries.values.toList())
                tankAdapter.submitList(state.tanks.values.toList())
                
                val conversionItems = mutableListOf<ConversionItem>()
                state.chargers.forEach { (_, charger) -> conversionItems.add(ConversionItem.ChargerItem(charger)) }
                state.inverters.forEach { (_, inverter) -> conversionItems.add(ConversionItem.InverterItem(inverter)) }
                conversionAdapter.submitList(conversionItems)
                
                root.findViewById<View>(R.id.txt_conversion_label).visibility = if (conversionItems.isEmpty()) View.GONE else View.VISIBLE

                val watermakerItems = state.watermakers.values.toList()
                val rvWatermakers = root.findViewById<RecyclerView>(R.id.rv_watermakers)
                rvWatermakers.layoutManager = LinearLayoutManager(context)
                val watermakerAdapter = WatermakerAdapter()
                rvWatermakers.adapter = watermakerAdapter
                watermakerAdapter.submitList(watermakerItems)
                root.findViewById<View>(R.id.txt_watermaker_label).visibility = if (watermakerItems.isEmpty()) View.GONE else View.VISIBLE

                val switches = state.switches.asSequence().map { it.toPair() }.sortedBy { it.first }.toList()
                switchAdapter.updateData(switches, state.timestamps, state.pathMeta)
                
                val rvWindlass = root.findViewById<RecyclerView>(R.id.rv_windlass)
                rvWindlass.layoutManager = LinearLayoutManager(context)
                val windlassAdapter = WindlassAdapter()
                rvWindlass.adapter = windlassAdapter
                // Show if hasWindlassControl is true or if paths exist
                root.findViewById<View>(R.id.txt_windlass_label).visibility = if (caps?.hasWindlassControl == true) View.VISIBLE else View.GONE
                rvWindlass.visibility = if (caps?.hasWindlassControl == true) View.VISIBLE else View.GONE

                root.findViewById<View>(R.id.txt_empty_switches).visibility = 
                    if (switches.isEmpty() && state.batteries.isEmpty() && state.tanks.isEmpty() && conversionItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return root
    }

    private fun showDimmerDialog(path: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.nautical_adjust_dimmer)
        
        val slider = android.widget.SeekBar(requireContext())
        slider.max = 100
        val engine = NauticalPlugin.engine
        val current = engine?.getCurrentState()?.customValues?.get("electrical.switches.$path.dimmingLevel") ?: 1.0
        slider.progress = (current * 100).toInt()
        
        builder.setView(slider)
        builder.setPositiveButton(R.string.shared_string_ok) { _, _ ->
            engine?.controlManager?.setDimmerValue(path, slider.progress / 100.0)
        }
        builder.show()
    }

    private class BatteryAdapter : ListAdapter<Battery, BatteryViewHolder>(BatteryDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_battery, parent, false)
            return BatteryViewHolder(view)
        }

        override fun onBindViewHolder(holder: BatteryViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class BatteryDiffCallback : DiffUtil.ItemCallback<Battery>() {
        override fun areItemsTheSame(oldItem: Battery, newItem: Battery): Boolean = oldItem.instance == newItem.instance
        override fun areContentsTheSame(oldItem: Battery, newItem: Battery): Boolean = oldItem == newItem
    }

    private class BatteryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_battery_name)
        private val txtVolt: TextView = view.findViewById(R.id.txt_battery_volt)
        private val txtSoc: TextView = view.findViewById(R.id.txt_battery_soc)
        private val txtCurrent: TextView = view.findViewById(R.id.txt_battery_current)

        fun bind(battery: Battery) {
            txtName.text = battery.name ?: "Battery ${battery.instance}"
            txtVolt.text = battery.voltage?.let { String.format(Locale.US, "%.1f V", it) } ?: "--"
            val soc = battery.stateOfCharge
            if (soc != null) {
                txtSoc.text = String.format(Locale.US, "%.0f%%", soc * 100)
                val color = when {
                    soc > 0.7 -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_ok)
                    soc > 0.3 -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_warning)
                    else -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_invalid)
                }
                txtSoc.setTextColor(color)
            } else {
                txtSoc.text = ""
            }
            
            val currentText = StringBuilder()
            battery.current?.let { currentText.append(String.format(Locale.US, "%+.1f A", it)) }
            
            val ttf = battery.timeToFull
            val ttr = battery.timeRemaining
            if ((ttf != null && ttf > 0)) {
                currentText.append(" (Full in ${formatDuration(ttf)})")
            } else if ((ttr != null && ttr > 0)) {
                currentText.append(" (${formatDuration(ttr)} left)")
            }
            
            txtCurrent.text = currentText.toString()
            
            // Cell Voltages
            val cellText = itemView.findViewById<TextView>(R.id.txt_battery_cells)
            if (battery.cellVoltages.isNotEmpty()) {
                cellText?.visibility = View.VISIBLE
                val cells = battery.cellVoltages.joinToString(" | ") { String.format(Locale.US, "%.2fV", it) }
                cellText?.text = itemView.context.getString(R.string.nautical_battery_cells_fmt, cells)
            } else {
                cellText?.visibility = View.GONE
            }
        }

        private fun formatDuration(seconds: Double): String {
            val h = (seconds / 3600).toInt()
            val m = ((seconds % 3600) / 60).toInt()
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }

    private class TankAdapter : ListAdapter<Tank, TankViewHolder>(TankDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TankViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_tank, parent, false)
            return TankViewHolder(view)
        }

        override fun onBindViewHolder(holder: TankViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class TankDiffCallback : DiffUtil.ItemCallback<Tank>() {
        override fun areItemsTheSame(oldItem: Tank, newItem: Tank): Boolean = oldItem.instance == newItem.instance
        override fun areContentsTheSame(oldItem: Tank, newItem: Tank): Boolean = oldItem == newItem
    }

    private class TankViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_tank_name)
        private val txtPercent: TextView = view.findViewById(R.id.txt_tank_percent)
        private val pbLevel: ProgressBar = view.findViewById(R.id.pb_tank_level)

        fun bind(tank: Tank) {
            txtName.text = tank.name ?: (tank.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + " " + tank.instance)
            val percent = (tank.currentLevel ?: 0.0) * 100
            txtPercent.text = String.format(Locale.US, "%.0f%%", percent)
            pbLevel.progress = percent.toInt()
            
            // Set color based on type
            val colorRes = when (tank.type.lowercase(Locale.US)) {
                "fuel" -> R.color.nautical_status_red
                "freshwater" -> R.color.nautical_status_blue
                "wastewater", "blackwater", "greywater" -> R.color.buttons_secondary_dark_v2
                "lubeoil" -> R.color.nautical_status_yellow
                else -> R.color.color_ok
            }
            val color = androidx.core.content.ContextCompat.getColor(itemView.context, colorRes)
            pbLevel.progressTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private class WatermakerAdapter : ListAdapter<Watermaker, WatermakerViewHolder>(WatermakerDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatermakerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_charger_inverter, parent, false)
            return WatermakerViewHolder(view)
        }

        override fun onBindViewHolder(holder: WatermakerViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class WatermakerDiffCallback : DiffUtil.ItemCallback<Watermaker>() {
        override fun areItemsTheSame(oldItem: Watermaker, newItem: Watermaker): Boolean = oldItem.instance == newItem.instance
        override fun areContentsTheSame(oldItem: Watermaker, newItem: Watermaker): Boolean = oldItem == newItem
    }

    private class WatermakerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_device_name)
        private val txtState: TextView = view.findViewById(R.id.txt_device_state)
        private val txtVal1: TextView = view.findViewById(R.id.txt_device_val1)
        private val txtVal2: TextView = view.findViewById(R.id.txt_device_val2)
        private val spinnerMode: android.widget.Spinner = view.findViewById(R.id.spinner_device_mode)

        fun bind(watermaker: Watermaker) {
            txtName.text = itemView.context.getString(R.string.nautical_watermaker_instance_name, watermaker.instance)
            txtState.text = watermaker.state?.uppercase(Locale.US) ?: "--"
            txtVal1.text = watermaker.rate?.let { String.format(Locale.US, "%.1f L/h", it) } ?: ""
            txtVal2.text = watermaker.salinity?.let { String.format(Locale.US, "%.0f ppm", it) } ?: ""
            spinnerMode.visibility = View.GONE
        }
    }

    private class WindlassAdapter : RecyclerView.Adapter<WindlassViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WindlassViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_charger_inverter, parent, false)
            return WindlassViewHolder(view)
        }

        override fun onBindViewHolder(holder: WindlassViewHolder, position: Int) {
            holder.bind()
        }

        override fun getItemCount(): Int = 1
    }

    private class WindlassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_device_name)
        private val txtState: TextView = view.findViewById(R.id.txt_device_state)
        private val txtVal1: TextView = view.findViewById(R.id.txt_device_val1)
        private val txtVal2: TextView = view.findViewById(R.id.txt_device_val2)
        private val spinnerMode: android.widget.Spinner = view.findViewById(R.id.spinner_device_mode)

        fun bind() {
            txtName.text = itemView.context.getString(R.string.nautical_windlass_title)
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState()
            val rode = state?.rodeDeployed ?: 0.0
            txtVal1.text = String.format(Locale.US, "%.1f m", rode)
            txtState.text = if (state?.isEngineRunning == true) "READY" else "LOCKED"
            txtVal2.text = if (state?.isEngineRunning == false) itemView.context.getString(R.string.nautical_windlass_engine_guard) else ""
            
            spinnerMode.visibility = View.GONE
            
            itemView.setOnClickListener {
                if (state?.isEngineRunning == true) {
                    showWindlassControls()
                } else {
                    NauticalPlugin.hudManager?.get()?.showBanner(itemView.context.getString(R.string.nautical_windlass_engine_guard), 3000, isWarning = true)
                }
            }
        }

        private fun showWindlassControls() {
            val builder = androidx.appcompat.app.AlertDialog.Builder(itemView.context)
            builder.setTitle(R.string.nautical_windlass_control)
            builder.setItems(arrayOf(itemView.context.getString(R.string.nautical_windlass_up), itemView.context.getString(R.string.nautical_windlass_down))) { _, which ->
                val command = if (which == 0) "up" else "down"
                NauticalPlugin.engine?.sendDelta("electrical.switches.windlass.state", command)
            }
            builder.show()
        }
    }

    private sealed class ConversionItem {
        data class ChargerItem(val charger: Charger) : ConversionItem()
        data class InverterItem(val inverter: Inverter) : ConversionItem()
    }

    private class ConversionAdapter : ListAdapter<ConversionItem, ConversionViewHolder>(ConversionDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_charger_inverter, parent, false)
            return ConversionViewHolder(view)
        }

        override fun onBindViewHolder(holder: ConversionViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class ConversionDiffCallback : DiffUtil.ItemCallback<ConversionItem>() {
        override fun areItemsTheSame(oldItem: ConversionItem, newItem: ConversionItem): Boolean {
            return when (oldItem) {
                is ConversionItem.ChargerItem if newItem is ConversionItem.ChargerItem -> oldItem.charger.instance == newItem.charger.instance
                is ConversionItem.InverterItem if newItem is ConversionItem.InverterItem -> oldItem.inverter.instance == newItem.inverter.instance
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: ConversionItem, newItem: ConversionItem): Boolean = oldItem == newItem
    }

    private class ConversionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_device_name)
        private val txtState: TextView = view.findViewById(R.id.txt_device_state)
        private val txtVal1: TextView = view.findViewById(R.id.txt_device_val1)
        private val txtVal2: TextView = view.findViewById(R.id.txt_device_val2)
        private val spinnerMode: android.widget.Spinner = view.findViewById(R.id.spinner_device_mode)

        fun bind(item: ConversionItem) {
            when (item) {
                is ConversionItem.ChargerItem -> {
                    val charger = item.charger
                    txtName.text = charger.name ?: "Charger ${charger.instance}"
                    txtState.text = charger.state?.uppercase(Locale.US) ?: "--"
                    txtVal1.text = charger.voltage?.let { String.format(Locale.US, "%.1f V", it) } ?: ""
                    txtVal2.text = charger.current?.let { String.format(Locale.US, "%.1f A", it) } ?: ""
                    
                    setupModeSpinner(charger.instance, charger.mode, isCharger = true)
                }
                is ConversionItem.InverterItem -> {
                    val inverter = item.inverter
                    txtName.text = inverter.name ?: "Inverter ${inverter.instance}"
                    txtState.text = inverter.state?.uppercase(Locale.US) ?: "--"
                    txtVal1.text = inverter.acVoltage?.let { String.format(Locale.US, "%.0f V", it) } ?: ""
                    txtVal2.text = inverter.acCurrent?.let { String.format(Locale.US, "%.1f A", it) } ?: ""
                    
                    setupModeSpinner(inverter.instance, inverter.mode, isCharger = false)
                }
            }
        }

        private fun setupModeSpinner(instance: String, currentMode: String?, isCharger: Boolean) {
            val modes = if (isCharger) listOf("on", "off", "other") else listOf("on", "off", "eco", "other")
            val adapter = android.widget.ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, modes.map { it.uppercase(Locale.US) })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMode.adapter = adapter
            
            currentMode?.let { mode ->
                val idx = modes.indexOf(mode.lowercase(Locale.US))
                if (idx >= 0) spinnerMode.setSelection(idx)
            }
            
            spinnerMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newMode = modes[position]
                    if (newMode != currentMode?.lowercase(Locale.US)) {
                        if (isCharger) {
                            NauticalPlugin.autopilot?.let { 
                                // Actually we need to call SignalKControlManager. NauticalPlugin has electrical but we didn't expose setChargerMode there yet.
                                // Let's use the engine directly or expose it.
                                NauticalPlugin.engine?.controlManager?.setChargerMode(instance, newMode)
                            }
                        } else {
                            NauticalPlugin.engine?.controlManager?.setInverterMode(instance, newMode)
                        }
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
    }

    private class SwitchAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onLongClick: (String) -> Unit
    ) : ListAdapter<Pair<String, Boolean>, SwitchViewHolder>(SwitchDiffCallback()) {
        private var timestamps: Map<String, Long> = emptyMap()
        private var meta: Map<String, Map<String, Any>> = emptyMap()

        fun updateData(newItems: List<Pair<String, Boolean>>, newTimestamps: Map<String, Long>, newMeta: Map<String, Map<String, Any>>) {
            timestamps = newTimestamps
            meta = newMeta
            submitList(newItems)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
            return SwitchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
            val (path, state) = getItem(position)
            val isPending = timestamps.containsKey("pending.electrical.switches.$path.state")
            val displayName = meta[path]?.get("displayName") as? String
            holder.bind(path, state, isPending, displayName, onToggle, onLongClick)
        }
    }

    private class SwitchDiffCallback : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
        override fun areItemsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean = oldItem.first == newItem.first
        override fun areContentsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean = oldItem == newItem
    }

    private class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_switch_name)
        private val switchToggle: SwitchCompat = view.findViewById(R.id.switch_toggle)

        fun bind(path: String, state: Boolean, isPending: Boolean, displayName: String?, onToggle: (String, Boolean) -> Unit, onLongClick: (String) -> Unit) {
            txtName.text = displayName ?: path.substringAfterLast(".").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = state
            switchToggle.isEnabled = !isPending
            switchToggle.alpha = if (isPending) 0.5f else 1.0f
            switchToggle.setOnCheckedChangeListener { _, isChecked ->
                onToggle(path, isChecked)
            }
            
            itemView.setOnLongClickListener {
                onLongClick(path)
                true
            }
        }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            NauticalElectricalDashboardBottomSheet().show(fragmentManager, "NauticalElectricalDashboardBottomSheet")
        }
    }
}
