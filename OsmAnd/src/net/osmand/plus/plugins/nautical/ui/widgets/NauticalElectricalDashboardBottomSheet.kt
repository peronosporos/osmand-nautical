package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import androidx.core.content.ContextCompat
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

    private val engineAdapter = EngineAdapter()
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
        
        val app = requireActivity().application as net.osmand.plus.OsmandApplication
        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            customView.setBackgroundColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_bg))
            customView.findViewById<View>(R.id.drag_handle)?.setBackgroundColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_muted))
            customView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_power_matrix)?.apply {
                setCardBackgroundColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_bg))
                strokeColor = ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_primary)
            }
        }

        customView.findViewById<RecyclerView?>(R.id.rv_engines)?.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = engineAdapter
        }
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
                // 1. Power Matrix Updates — honest values only, "--" when telemetry is absent.
                val houseBattery = state.batteries.values.find { (it.name ?: "").contains("house", ignoreCase = true) || it.instance == "0" }
                    ?: state.batteries.values.firstOrNull()
                val houseSocPct = houseBattery?.stateOfCharge?.let { (it * 100.0).toInt().coerceIn(0, 100) }
                // Net current: only meaningful when at least one battery reports.
                val netCurrent: Double? = if (state.batteries.isNotEmpty()) state.batteries.values.sumOf { it.current ?: 0.0 } else null
                // Charging power: sum of ALL chargers (solar + shore + alternator/chargers), not just solar.
                val chargingWatts: Double? = if (state.chargers.isNotEmpty()) state.chargers.values.sumOf { (it.voltage ?: 0.0) * (it.current ?: 0.0) } else null

                val txtSoc = customView.findViewById<TextView>(R.id.txt_matrix_soc)
                val txtNetFlow = customView.findViewById<TextView>(R.id.txt_matrix_net_flow)
                val txtCharge = customView.findViewById<TextView>(R.id.txt_matrix_charge)

                txtSoc?.text = houseSocPct?.let { String.format(Locale.US, "%d%%", it) } ?: "--%"
                txtNetFlow?.text = netCurrent?.let { String.format(Locale.US, "%+.1f A", it) } ?: "-- A"
                txtCharge?.text = chargingWatts?.let { String.format(Locale.US, "%.0f W", it) } ?: "-- W"

                // Autonomy Forecaster — honest only: prefers the server-computed
                // timeRemaining; invented capacity/current defaults removed.
                val cardAutonomy = customView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_autonomy_forecaster)
                val txtAutonomyTitle = customView.findViewById<TextView>(R.id.txt_autonomy_title)
                val txtAutonomyDetails = customView.findViewById<TextView>(R.id.txt_autonomy_details)

                val ttdHours = houseBattery?.timeRemaining?.let { it / 3600.0 }
                txtAutonomyTitle?.text = when {
                    ttdHours != null && ttdHours > 0.0 ->
                        getString(R.string.nautical_autonomy_hrs_fmt, String.format(Locale.US, "%.1f", ttdHours))
                    netCurrent != null && netCurrent >= 0.0 ->
                        getString(R.string.nautical_autonomy_charging_fmt, String.format(Locale.US, "%+.1f", netCurrent))
                    netCurrent != null ->
                        getString(R.string.nautical_autonomy_discharging_fmt, String.format(Locale.US, "%.1f", kotlin.math.abs(netCurrent)))
                    else -> getString(R.string.nautical_autonomy_waiting)
                }

                if (houseSocPct == null || netCurrent == null) {
                    // No battery telemetry: disclose it instead of inventing figures.
                    txtAutonomyDetails?.text = getString(R.string.nautical_autonomy_no_telemetry_hint)
                } else {
                    val chargeW = chargingWatts?.let { String.format(Locale.US, "%.0f W", it) } ?: "-- W"
                    txtAutonomyDetails?.text = getString(R.string.nautical_autonomy_details_fmt, houseSocPct, String.format(Locale.US, "%+.1f", netCurrent), chargeW)
                }

                if (isNightVision) {
                    txtSoc?.setTextColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_primary))
                    txtNetFlow?.setTextColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_data))
                    txtCharge?.setTextColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_data))
                    cardAutonomy?.setCardBackgroundColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_bg))
                    cardAutonomy?.strokeColor = ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_primary)
                    txtAutonomyTitle?.setTextColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_primary))
                    txtAutonomyDetails?.setTextColor(ContextCompat.getColor(themedCtx, R.color.nautical_night_vision_data))
                }

                // 2. Engines & Mechanical — live data only; the old code rendered
                // two INVENTED engines (30 RPM, fake oil pressure) when none reported.
                val engines = state.engines.values.toList()
                engineAdapter.submitList(engines)
                customView.findViewById<View>(R.id.txt_engines_label)?.visibility = if (engines.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_engines)?.visibility = if (engines.isEmpty()) View.GONE else View.VISIBLE

                // 3. Batteries — live data only (previously invented "House Bank 92%").
                val batteries = state.batteries.values.toList()
                batteryAdapter.submitList(batteries)
                customView.findViewById<View>(R.id.txt_batteries_label)?.visibility = if (batteries.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_batteries)?.visibility = if (batteries.isEmpty()) View.GONE else View.VISIBLE

                // 4. Tanks — live data only (previously invented 78% fuel etc.).
                val tanks = state.tanks.values.toList()
                tankAdapter.submitList(tanks)
                customView.findViewById<View>(R.id.txt_tanks_label)?.visibility = if (tanks.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_tanks)?.visibility = if (tanks.isEmpty()) View.GONE else View.VISIBLE
                
                val conversionItems = mutableListOf<ConversionItem>()
                state.chargers.forEach { (_, charger) -> conversionItems.add(ConversionItem.ChargerItem(charger)) }
                state.inverters.forEach { (_, inverter) -> conversionItems.add(ConversionItem.InverterItem(inverter)) }
                conversionAdapter.submitList(conversionItems)
                
                customView.findViewById<View>(R.id.txt_conversion_label)?.visibility = if (conversionItems.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_conversion)?.visibility = if (conversionItems.isEmpty()) View.GONE else View.VISIBLE

                val watermakerItems = state.watermakers.values.toList()
                watermakerAdapter.submitList(watermakerItems)
                customView.findViewById<View>(R.id.txt_watermaker_label)?.visibility = if (watermakerItems.isEmpty()) View.GONE else View.VISIBLE
                customView.findViewById<View>(R.id.rv_watermakers)?.visibility = if (watermakerItems.isEmpty()) View.GONE else View.VISIBLE

                // Switches — live data only; the old code fabricated four switches
                // (nav lights ON, bilge pump OFF...) when none were discovered.
                val switches = state.switches.asSequence().map { it.toPair() }.sortedBy { it.first }.toList()
                switchAdapter.updateData(switches, state.timestamps, state.pathMeta, state.dimmers)
                customView.findViewById<View>(R.id.rv_switches)?.visibility = if (switches.isEmpty()) View.GONE else View.VISIBLE
                // Empty-state disclosure instead of invented devices.
                customView.findViewById<View>(R.id.txt_empty_switches)?.visibility = if (switches.isEmpty()) View.VISIBLE else View.GONE
                
                val showWindlass = caps?.hasWindlassControl == true
                customView.findViewById<View>(R.id.txt_windlass_label)?.visibility = if (showWindlass) View.VISIBLE else View.GONE
                customView.findViewById<View>(R.id.rv_windlass)?.visibility = if (showWindlass) View.VISIBLE else View.GONE
                if (showWindlass) {
                    windlassAdapter.updateState(state.isEngineRunning, state.rodeDeployed)
                }
            }
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private class EngineAdapter : ListAdapter<Engine, EngineViewHolder>(EngineDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EngineViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_battery, parent, false)
            return EngineViewHolder(view)
        }
        override fun onBindViewHolder(holder: EngineViewHolder, position: Int) = holder.bind(getItem(position))
    }

    private class EngineDiffCallback : DiffUtil.ItemCallback<Engine>() {
        override fun areItemsTheSame(oldItem: Engine, newItem: Engine): Boolean = oldItem.instance == newItem.instance
        override fun areContentsTheSame(oldItem: Engine, newItem: Engine): Boolean = oldItem == newItem
    }

    private class EngineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_battery_name)
        private val txtType: TextView? = view.findViewById(R.id.txt_battery_type)
        private val txtVolt: TextView = view.findViewById(R.id.txt_battery_volt)
        private val txtSoc: TextView = view.findViewById(R.id.txt_battery_soc)
        private val pbSoc: ProgressBar? = view.findViewById(R.id.pb_battery_soc)
        private val txtCurrent: TextView = view.findViewById(R.id.txt_battery_current)
        private val txtEta: TextView? = view.findViewById(R.id.txt_battery_eta)

        fun bind(engine: Engine) {
            val ctxt = itemView.context
            val engineName = if (engine.instance == "0") ctxt.getString(R.string.nautical_engine_port) else if (engine.instance == "1") ctxt.getString(R.string.nautical_engine_starboard) else ctxt.getString(R.string.nautical_engine_instance_fmt, engine.instance)
            txtName.text = engineName
            txtType?.text = ctxt.getString(R.string.nautical_engine_propulsion)

            // Honest engine readouts: show "--" instead of invented values.
            val rpm = engine.revolutions?.let { it * 60.0 }
            txtVolt.text = if (rpm != null) ctxt.getString(R.string.nautical_engine_rpm_fmt, rpm.toInt()) else "--"

            val tempC = engine.coolantTemperature?.let { if (it > 150) it - 273.15 else it }
            if (tempC != null) {
                txtSoc.text = String.format(Locale.US, "%.0f°C", tempC)
                pbSoc?.progress = ((tempC / 120.0) * 100).toInt().coerceIn(0, 100)
            } else {
                txtSoc.text = "--"
                pbSoc?.progress = 0
            }

            val bar = engine.oilPressure?.let { it / 100000.0 }
            txtCurrent.text = if (bar != null) ctxt.getString(R.string.nautical_engine_oil_fmt, String.format(Locale.US, "%.1f", bar)) else "--"

            val altV = engine.alternatorVoltage
            txtEta?.text = if (altV != null) ctxt.getString(R.string.nautical_engine_alt_fmt, String.format(Locale.US, "%.1f", altV)) else "--"

            val app = itemView.context.applicationContext as? net.osmand.plus.OsmandApplication
            val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
            if (isNightVision) {
                (itemView as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_bg))
                txtName.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtType?.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtVolt.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_primary))
                txtSoc.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_primary))
                txtCurrent.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtEta?.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
            }
        }
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
        private val txtType: TextView? = view.findViewById(R.id.txt_battery_type)
        private val txtVolt: TextView = view.findViewById(R.id.txt_battery_volt)
        private val txtSoc: TextView = view.findViewById(R.id.txt_battery_soc)
        private val pbSoc: ProgressBar? = view.findViewById(R.id.pb_battery_soc)
        private val txtCurrent: TextView = view.findViewById(R.id.txt_battery_current)
        private val txtEta: TextView? = view.findViewById(R.id.txt_battery_eta)
        private val cellText: TextView? = view.findViewById(R.id.txt_battery_cells)
        // Palette reset targets: holder reuse must never keep a stale alarm colour.
        private val socDefaultColor: Int = txtSoc.currentTextColor

        fun bind(battery: Battery) {
            val ctxt = itemView.context
            val rawName = battery.name ?: ctxt.getString(R.string.nautical_battery_instance_fmt, battery.instance)
            val lowerName = rawName.lowercase(Locale.US)
            val bankType = when {
                lowerName.contains("house") || lowerName.contains("service") || battery.instance == "0" -> ctxt.getString(R.string.nautical_battery_house)
                lowerName.contains("starter") || lowerName.contains("engine") || battery.instance == "1" -> ctxt.getString(R.string.nautical_battery_starter)
                lowerName.contains("thruster") || lowerName.contains("bow") -> ctxt.getString(R.string.nautical_battery_thruster)
                lowerName.contains("gen") -> ctxt.getString(R.string.nautical_battery_generator)
                else -> ctxt.getString(R.string.nautical_battery_aux)
            }

            txtName.text = rawName
            txtType?.text = bankType

            txtVolt.text = battery.voltage?.let { String.format(Locale.US, "%.1f V", it) } ?: "--"

            val soc = battery.stateOfCharge
            if (soc != null) {
                val pct = (soc * 100).toInt().coerceIn(0, 100)
                txtSoc.text = String.format(Locale.US, "%d%%", pct)
                pbSoc?.progress = pct

                val color = when {
                    soc > 0.60 -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_ok)
                    soc >= 0.30 -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_warning)
                    else -> androidx.core.content.ContextCompat.getColor(itemView.context, R.color.color_invalid)
                }
                txtSoc.setTextColor(color)
                pbSoc?.progressTintList = android.content.res.ColorStateList.valueOf(color)
            } else {
                txtSoc.text = "--%"
                // Reset the tint state left by a previously bound battery.
                txtSoc.setTextColor(socDefaultColor)
                pbSoc?.progressTintList = null
                pbSoc?.progress = 0
            }

            val current = battery.current
            if (current != null) {
                txtCurrent.text = String.format(Locale.US, "%+.1f A", current)
            } else {
                txtCurrent.text = "-- A"
            }

            val ttf = battery.timeToFull
            val ttr = battery.timeRemaining
            val etaStr = when {
                ttf != null && ttf > 0 -> ctxt.getString(R.string.nautical_battery_full_in_fmt, formatDuration(ttf))
                ttr != null && ttr > 0 -> ctxt.getString(R.string.nautical_battery_empty_in_fmt, formatDuration(ttr))
                current != null && current > 0.5 -> ctxt.getString(R.string.nautical_status_charging)
                current != null && current < -0.5 -> ctxt.getString(R.string.nautical_status_discharging)
                else -> ctxt.getString(R.string.nautical_status_standby)
            }
            txtEta?.text = etaStr

            if (battery.cellVoltages.isNotEmpty()) {
                cellText?.visibility = View.VISIBLE
                val cells = battery.cellVoltages.joinToString(" | ") { String.format(Locale.US, "%.2fV", it) }
                cellText?.text = itemView.context.getString(R.string.nautical_battery_cells_fmt, cells)
            } else {
                cellText?.visibility = View.GONE
            }

            val app = itemView.context.applicationContext as? net.osmand.plus.OsmandApplication
            val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
            if (isNightVision) {
                (itemView as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_bg))
                txtName.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtType?.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtVolt.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_primary))
                txtCurrent.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                txtEta?.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                cellText?.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
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
        override fun areItemsTheSame(oldItem: Tank, newItem: Tank): Boolean = oldItem.type == newItem.type && oldItem.instance == newItem.instance
        override fun areContentsTheSame(oldItem: Tank, newItem: Tank): Boolean = oldItem == newItem
    }

    private class TankViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_tank_name)
        private val txtPercent: TextView = view.findViewById(R.id.txt_tank_percent)
        private val pbLevel: ProgressBar = view.findViewById(R.id.pb_tank_level)
        // Reset target so a holder reused from a warning tank doesn't stay red.
        private val percentDefaultColor: Int = txtPercent.currentTextColor

        fun bind(tank: Tank) {
            val ctxt = itemView.context
            txtName.text = tank.name ?: formatTankDisplayName(tank.type, tank.instance)
            // Unknown level shows "--", never an invented 0%.
            val level = tank.currentLevel
            val percent = level?.let { (it * 100.0).coerceIn(0.0, 100.0) }

            val capLiters = if (tank.capacity != null && tank.capacity > 0.0) {
                if (tank.capacity < 1.0) tank.capacity * 1000.0 else tank.capacity
            } else null

            val curVolLiters = if (tank.currentVolume != null && tank.currentVolume > 0.0) {
                if (tank.currentVolume < 1.0) tank.currentVolume * 1000.0 else tank.currentVolume
            } else if (capLiters != null && level != null) {
                capLiters * level
            } else null

            val isFuel = tank.type.lowercase(Locale.US).contains("fuel")
            val isWaste = tank.type.lowercase(Locale.US).let { it.contains("black") || it.contains("waste") || it.contains("holding") }
            val isLowFuelWarning = isFuel && percent != null && percent < 15.0
            val isHighWasteWarning = isWaste && percent != null && percent > 85.0

            val warningSuffix = when {
                isLowFuelWarning -> ctxt.getString(R.string.nautical_tank_low_suffix)
                isHighWasteWarning -> ctxt.getString(R.string.nautical_tank_full_suffix)
                else -> ""
            }

            txtPercent.text = when {
                percent == null -> "--"
                curVolLiters != null && capLiters != null -> String.format(Locale.US, "%.0f%% (%.0f / %.0f L)%s", percent, curVolLiters, capLiters, warningSuffix)
                capLiters != null -> String.format(Locale.US, "%.0f%% (%.0f L)%s", percent, capLiters, warningSuffix)
                else -> String.format(Locale.US, "%.0f%%%s", percent, warningSuffix)
            }

            if (isLowFuelWarning || isHighWasteWarning) {
                txtPercent.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_status_red))
            } else {
                txtPercent.setTextColor(percentDefaultColor)
            }

            pbLevel.progress = percent?.toInt() ?: 0

            val colorRes = when {
                isLowFuelWarning || isHighWasteWarning -> R.color.nautical_status_red
                tank.type.lowercase(Locale.US) == "fuel" -> R.color.nautical_status_red
                tank.type.lowercase(Locale.US) == "freshwater" -> R.color.nautical_status_blue
                isWaste -> R.color.buttons_secondary_dark_v2
                tank.type.lowercase(Locale.US) in listOf("lubeoil", "oil") -> R.color.nautical_status_yellow
                tank.type.lowercase(Locale.US) in listOf("gas", "lpg", "cng") -> R.color.nautical_status_orange
                else -> R.color.color_ok
            }
            pbLevel.progressTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(itemView.context, colorRes))

            val app = itemView.context.applicationContext as? net.osmand.plus.OsmandApplication
            val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
            if (isNightVision) {
                txtName.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                if (!isLowFuelWarning && !isHighWasteWarning) {
                    txtPercent.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_data))
                }
            }
        }

        private fun formatTankDisplayName(type: String, instance: String): String {
            val ctxt = itemView.context
            val typeLabel = when (type.lowercase(Locale.US)) {
                "fuel" -> ctxt.getString(R.string.nautical_tank_fuel)
                "freshwater" -> ctxt.getString(R.string.nautical_tank_fresh_water)
                "blackwater" -> ctxt.getString(R.string.nautical_tank_black_water)
                "wastewater" -> ctxt.getString(R.string.nautical_tank_waste_water)
                "greywater" -> ctxt.getString(R.string.nautical_tank_grey_water)
                "lubeoil", "oil" -> ctxt.getString(R.string.nautical_tank_lube_oil)
                "gas", "lpg", "cng" -> ctxt.getString(R.string.nautical_tank_gas)
                "livewell", "baitwell" -> ctxt.getString(R.string.nautical_tank_live_well)
                "ballast" -> ctxt.getString(R.string.nautical_tank_ballast)
                "rawwater" -> ctxt.getString(R.string.nautical_tank_raw_water)
                else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
            return if (instance.isNotEmpty() && instance != "0") ctxt.getString(R.string.nautical_tank_type_instance_fmt, typeLabel, instance) else typeLabel
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
            txtVal1.text = watermaker.rate?.let { String.format(Locale.US, "%.1f L/h", it) } ?: ""
            txtVal2.text = watermaker.salinity?.let { String.format(Locale.US, "%.0f ppm", it) } ?: ""
            spinnerMode.visibility = View.GONE
        }
    }

    private class WindlassAdapter : RecyclerView.Adapter<WindlassViewHolder>() {
        private var isEngineRunning = false
        private var rodeDeployed: Double? = null

        fun updateState(running: Boolean, rode: Double?) {
            isEngineRunning = running
            rodeDeployed = rode
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

        fun bind(isEngineRunning: Boolean, rodeDeployed: Double?) {
            // Never invent a rode length: null shows the disclosed "--" placeholder.
            txtInfo.text = if (rodeDeployed != null) {
                itemView.context.getString(R.string.nautical_windlass_rode_fmt, String.format(Locale.US, "%.1f", rodeDeployed))
            } else {
                itemView.context.getString(R.string.nautical_rode_deployed_placeholder)
            }
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
                    txtName.text = charger.name ?: itemView.context.getString(R.string.nautical_charger_instance, charger.instance)
                    txtState.text = charger.state?.uppercase(Locale.getDefault()) ?: "--"
                    txtVal1.text = charger.voltage?.let { String.format(Locale.US, "%.1f V", it) } ?: ""
                    txtVal2.text = charger.current?.let { String.format(Locale.US, "%.1f A", it) } ?: ""
                    setupModeSpinner(charger.instance, charger.mode, isCharger = true)
                }
                is ConversionItem.InverterItem -> {
                    val inverter = item.inverter
                    txtName.text = inverter.name ?: itemView.context.getString(R.string.nautical_inverter_instance, inverter.instance)
                    txtState.text = inverter.state?.uppercase(Locale.getDefault()) ?: "--"
                    txtVal1.text = inverter.acVoltage?.let { String.format(Locale.US, "%.0f V", it) } ?: ""
                    txtVal2.text = inverter.acCurrent?.let { String.format(Locale.US, "%.1f A", it) } ?: ""
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

            val app = itemView.context.applicationContext as? net.osmand.plus.OsmandApplication
            val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
            if (isNightVision) {
                txtName.setTextColor(ContextCompat.getColor(itemView.context, R.color.nautical_night_vision_primary))
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
