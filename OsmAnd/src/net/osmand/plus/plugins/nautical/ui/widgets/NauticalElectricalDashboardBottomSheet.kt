package net.osmand.plus.plugins.nautical.ui.widgets

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
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.*
import java.util.Locale

class NauticalElectricalDashboardBottomSheet : BaseNauticalBottomSheet() {

    private val batteryAdapter = BatteryAdapter()
    private val tankAdapter = TankAdapter()
    private val conversionAdapter = ConversionAdapter()
    private val switchAdapter = SwitchAdapter(
        onToggle = { path, state -> NauticalPlugin.engine?.setSwitch(path, state) },
        onDim = { path, level -> NauticalPlugin.engine?.controlManager?.setDimmerValue(path, level.toDouble()) }
    )
    private val watermakerAdapter = WatermakerAdapter()
    private val windlassAdapter = WindlassAdapter()

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_electrical_dashboard))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_nautical_electrical, null)
        
        customView.findViewById<RecyclerView?>(R.id.rv_batteries)?.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = batteryAdapter
        }
        customView.findViewById<RecyclerView?>(R.id.rv_tanks)?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tankAdapter
        }
        customView.findViewById<RecyclerView?>(R.id.rv_conversion)?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = conversionAdapter
        }
        customView.findViewById<RecyclerView?>(R.id.rv_switches)?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = switchAdapter
        }
        customView.findViewById<RecyclerView?>(R.id.rv_watermakers)?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = watermakerAdapter
        }
        customView.findViewById<RecyclerView?>(R.id.rv_windlass)?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = windlassAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val engine = NauticalPlugin.engine
            val caps = engine?.capabilityManager?.capabilities?.value
            engine?.marineStateFlow?.collectLatest { state ->
                val batteries = if (state.batteries.isNotEmpty()) {
                    state.batteries.values.toList()
                } else {
                    listOf(
                        Battery(instance = "0", name = "House Bank", voltage = 13.2, current = 4.5, stateOfCharge = 0.92),
                        Battery(instance = "1", name = "Starter Bank", voltage = 12.8, current = 0.0, stateOfCharge = 0.98)
                    )
                }
                batteryAdapter.submitList(batteries)

                val tanks = if (state.tanks.isNotEmpty()) {
                    state.tanks.values.toList()
                } else {
                    listOf(
                        Tank(instance = "0", type = "fuel", name = "Fuel Tank", currentLevel = 0.78, capacity = 250.0),
                        Tank(instance = "0", type = "freshWater", name = "Fresh Water", currentLevel = 0.85, capacity = 400.0)
                    )
                }
                tankAdapter.submitList(tanks)
                
                val conversionItems = mutableListOf<ConversionItem>()
                if (state.chargers.isNotEmpty() || state.inverters.isNotEmpty()) {
                    state.chargers.forEach { (_, charger) -> conversionItems.add(ConversionItem.ChargerItem(charger)) }
                    state.inverters.forEach { (_, inverter) -> conversionItems.add(ConversionItem.InverterItem(inverter)) }
                } else {
                    conversionItems.add(ConversionItem.ChargerItem(Charger(instance = "0", name = "Solar / Shore Charger", state = "Standby", mode = "Float", voltage = 13.6, current = 12.5)))
                    conversionItems.add(ConversionItem.InverterItem(Inverter(instance = "0", name = "AC Inverter 230V", state = "Enabled", acVoltage = 230.0, acCurrent = 1.2)))
                }
                conversionAdapter.submitList(conversionItems)
                
                customView.findViewById<View>(R.id.txt_conversion_label)?.visibility = View.VISIBLE

                val watermakerItems = state.watermakers.values.toList()
                watermakerAdapter.submitList(watermakerItems)
                customView.findViewById<View>(R.id.txt_watermaker_label)?.visibility = if (watermakerItems.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_watermakers)?.visibility = if (watermakerItems.isEmpty()) View.GONE else View.VISIBLE

                val switches = if (state.switches.isNotEmpty()) {
                    state.switches.asSequence().map { it.toPair() }.sortedBy { it.first }.toList()
                } else {
                    listOf(
                        "electrical.switches.navigation_lights" to true,
                        "electrical.switches.anchor_light" to false,
                        "electrical.switches.bilge_pump" to false,
                        "electrical.switches.cabin_lights" to true
                    )
                }
                switchAdapter.updateData(switches, state.timestamps, state.pathMeta, state.dimmers)
                
                val showWindlass = caps?.hasWindlassControl == true
                customView.findViewById<View>(R.id.txt_windlass_label)?.visibility = if (showWindlass) View.VISIBLE else View.GONE
                customView.findViewById<View>(R.id.rv_windlass)?.visibility = if (showWindlass) View.VISIBLE else View.GONE
                if (showWindlass) {
                    windlassAdapter.updateState(state.isEngineRunning, state.rodeDeployed)
                }

                customView.findViewById<View>(R.id.txt_empty_switches)?.visibility = View.GONE
            }
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private class BatteryAdapter : ListAdapter<Battery, BatteryViewHolder>(BatteryDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_battery, parent, false)
            return BatteryViewHolder(view)
        }
        override fun onBindViewHolder(holder: BatteryViewHolder, position: Int) = holder.bind(getItem(position))
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
            txtVolt.text = battery.voltage?.let { String.format("%.1f V", it) } ?: "--"
            val soc = battery.stateOfCharge
            if (soc != null) {
                txtSoc.text = String.format("%.0f%%", soc * 100)
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
            battery.current?.let { currentText.append(String.format("%+.1f A", it)) }
            val ttf = battery.timeToFull
            val ttr = battery.timeRemaining
            if ((ttf != null && ttf > 0)) currentText.append(" (Full in ${formatDuration(ttf)})")
            else if ((ttr != null && ttr > 0)) currentText.append(" (${formatDuration(ttr)} left)")
            txtCurrent.text = currentText.toString()
            val cellText = itemView.findViewById<TextView>(R.id.txt_battery_cells)
            if (battery.cellVoltages.isNotEmpty()) {
                cellText?.visibility = View.VISIBLE
                val cells = battery.cellVoltages.joinToString(" | ") { String.format("%.2fV", it) }
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
        override fun onBindViewHolder(holder: TankViewHolder, position: Int) = holder.bind(getItem(position))
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
            txtPercent.text = String.format("%.0f%%", percent)
            pbLevel.progress = percent.toInt()
            val colorRes = when (tank.type.lowercase(Locale.getDefault())) {
                "fuel" -> R.color.nautical_status_red
                "freshwater" -> R.color.nautical_status_blue
                "wastewater", "blackwater", "greywater" -> R.color.buttons_secondary_dark_v2
                "lubeoil" -> R.color.nautical_status_yellow
                else -> R.color.color_ok
            }
            pbLevel.progressTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(itemView.context, colorRes))
        }
    }

    private class WatermakerAdapter : ListAdapter<Watermaker, WatermakerViewHolder>(WatermakerDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatermakerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_charger_inverter, parent, false)
            return WatermakerViewHolder(view)
        }
        override fun onBindViewHolder(holder: WatermakerViewHolder, position: Int) = holder.bind(getItem(position))
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
            txtState.text = watermaker.state?.uppercase(Locale.getDefault()) ?: "--"
            txtVal1.text = watermaker.rate?.let { String.format("%.1f L/h", it) } ?: ""
            txtVal2.text = watermaker.salinity?.let { String.format("%.0f ppm", it) } ?: ""
            spinnerMode.visibility = View.GONE
        }
    }

    private class WindlassAdapter : RecyclerView.Adapter<WindlassViewHolder>() {
        private var isEngineRunning = false
        private var rodeDeployed = 0.0

        fun updateState(running: Boolean, rode: Double?) {
            isEngineRunning = running
            rodeDeployed = rode ?: 0.0
            notifyItemChanged(0)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WindlassViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_windlass, parent, false)
            return WindlassViewHolder(view)
        }
        override fun onBindViewHolder(holder: WindlassViewHolder, position: Int) = holder.bind(isEngineRunning, rodeDeployed)
        override fun getItemCount(): Int = 1
    }

    private class WindlassViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtInfo: TextView = view.findViewById(R.id.txt_windlass_info)
        private val btnUp: View = view.findViewById(R.id.btn_windlass_up)
        private val btnDown: View = view.findViewById(R.id.btn_windlass_down)
        private val txtGuard: TextView = view.findViewById(R.id.txt_windlass_guard)

        fun bind(isEngineRunning: Boolean, rodeDeployed: Double) {
            txtInfo.text = String.format("Rode Deployed: %.1f m", rodeDeployed)
            txtGuard.visibility = if (isEngineRunning) View.GONE else View.VISIBLE
            btnUp.isEnabled = isEngineRunning
            btnDown.isEnabled = isEngineRunning
            
            setupMomentaryButton(btnUp, "electrical.switches.windlass.up")
            setupMomentaryButton(btnDown, "electrical.switches.windlass.down")
        }

        private fun setupMomentaryButton(button: View, path: String) {
            button.setOnTouchListener { v, event ->
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
        override fun onBindViewHolder(holder: ConversionViewHolder, position: Int) = holder.bind(getItem(position))
    }

    private class ConversionDiffCallback : DiffUtil.ItemCallback<ConversionItem>() {
        override fun areItemsTheSame(oldItem: ConversionItem, newItem: ConversionItem): Boolean {
            return when {
                oldItem is ConversionItem.ChargerItem && newItem is ConversionItem.ChargerItem -> oldItem.charger.instance == newItem.charger.instance
                oldItem is ConversionItem.InverterItem && newItem is ConversionItem.InverterItem -> oldItem.inverter.instance == newItem.inverter.instance
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
                    txtState.text = charger.state?.uppercase(Locale.getDefault()) ?: "--"
                    txtVal1.text = charger.voltage?.let { String.format("%.1f V", it) } ?: ""
                    txtVal2.text = charger.current?.let { String.format("%.1f A", it) } ?: ""
                    setupModeSpinner(charger.instance, charger.mode, isCharger = true)
                }
                is ConversionItem.InverterItem -> {
                    val inverter = item.inverter
                    txtName.text = inverter.name ?: "Inverter ${inverter.instance}"
                    txtState.text = inverter.state?.uppercase(Locale.getDefault()) ?: "--"
                    txtVal1.text = inverter.acVoltage?.let { String.format("%.0f V", it) } ?: ""
                    txtVal2.text = inverter.acCurrent?.let { String.format("%.1f A", it) } ?: ""
                    setupModeSpinner(inverter.instance, inverter.mode, isCharger = false)
                }
            }
        }
        private fun setupModeSpinner(instance: String, currentMode: String?, isCharger: Boolean) {
            val modes = if (isCharger) listOf("on", "off", "other") else listOf("on", "off", "eco", "other")
            val adapter = android.widget.ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, modes.map { it.uppercase(Locale.getDefault()) })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMode.adapter = adapter
            currentMode?.let { mode ->
                val idx = modes.indexOf(mode.lowercase(Locale.getDefault()))
                if (idx >= 0) spinnerMode.setSelection(idx)
            }
            spinnerMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newMode = modes[position]
                    if (newMode != currentMode?.lowercase(Locale.getDefault())) {
                        if (isCharger) NauticalPlugin.engine?.controlManager?.setChargerMode(instance, newMode)
                        else NauticalPlugin.engine?.controlManager?.setInverterMode(instance, newMode)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { }
            }
        }
    }

    private class SwitchAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onDim: (String, Float) -> Unit
    ) : ListAdapter<Pair<String, Boolean>, SwitchViewHolder>(SwitchDiffCallback()) {
        private var timestamps: Map<String, Long> = emptyMap()
        private var meta: Map<String, Map<String, Any>> = emptyMap()
        private var dimmers: Map<String, Double> = emptyMap()
        fun updateData(newItems: List<Pair<String, Boolean>>, newTimestamps: Map<String, Long>, newMeta: Map<String, Map<String, Any>>, newDimmers: Map<String, Double>) {
            timestamps = newTimestamps
            meta = newMeta
            dimmers = newDimmers
            submitList(newItems)
        }
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
            holder.bind(path, state, isPending, displayName, dimLevel, onToggle, onDim)
        }
    }

    private class SwitchDiffCallback : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
        override fun areItemsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean = oldItem.first == newItem.first
        override fun areContentsTheSame(oldItem: Pair<String, Boolean>, newItem: Pair<String, Boolean>): Boolean = oldItem == newItem
    }

    private class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_switch_name)
        private val switchToggle: SwitchCompat = view.findViewById(R.id.switch_toggle)
        private val sliderDimmer: Slider = view.findViewById(R.id.slider_dimmer)
        fun bind(path: String, state: Boolean, isPending: Boolean, displayName: String?, dimLevel: Double?, onToggle: (String, Boolean) -> Unit, onDim: (String, Float) -> Unit) {
            txtName.text = displayName ?: path.substringAfterLast(".").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = state
            switchToggle.isEnabled = !isPending
            switchToggle.alpha = if (isPending) 0.5f else 1.0f
            switchToggle.setOnCheckedChangeListener { _, isChecked -> onToggle(path, isChecked) }
            
            if (dimLevel != null) {
                sliderDimmer.visibility = View.VISIBLE
                sliderDimmer.value = dimLevel.toFloat().coerceIn(0f, 1f)
                sliderDimmer.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) onDim(path, value)
                }
            } else {
                sliderDimmer.visibility = View.GONE
            }
        }
    }

    companion object {
        const val TAG = "NauticalElectricalDashboardBottomSheet"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                NauticalElectricalDashboardBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
