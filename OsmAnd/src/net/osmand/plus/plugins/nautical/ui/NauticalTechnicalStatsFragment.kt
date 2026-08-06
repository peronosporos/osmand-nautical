package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import java.util.Locale

class NauticalTechnicalStatsFragment : BaseOsmAndFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = themedInflater.inflate(R.layout.fragment_nautical_technical_stats, container, false)
        
        setupClickListeners(root)

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                updateStats(root, state)
            }
        }
        
        return root
    }

    private fun setupClickListeners(root: View) {
        val identity = root.findViewById<View>(R.id.grid_identity)
        
        // MMSI
        identity.findViewById<View>(R.id.txt_label_1_3).setOnClickListener {
             showEditDesignDialog(getString(R.string.nautical_vessel_mmsi_label), "mmsi")
        }
        
        // Dimensions (Length / Beam)
        identity.findViewById<View>(R.id.txt_label_2_1).setOnClickListener {
             showDimensionEditDialog()
        }
        
        // Air Draft
        identity.findViewById<View>(R.id.txt_label_2_2).setOnClickListener {
             showEditDesignDialog(getString(R.string.nautical_vessel_air_draft_label), "design.airDraft")
        }

        // Displacement
        identity.findViewById<View>(R.id.txt_label_2_3).setOnClickListener {
             showEditDesignDialog(getString(R.string.nautical_vessel_displacement_label), "design.displacement")
        }
    }

    private fun showDimensionEditDialog() {
        val context = context ?: return
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val lengthInput = EditText(context).apply {
            hint = getString(R.string.nautical_vessel_length_val)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val beamInput = EditText(context).apply {
            hint = getString(R.string.nautical_vessel_beam_val)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        
        layout.addView(lengthInput)
        layout.addView(beamInput)
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_vessel_dimensions)
            .setView(layout)
            .setPositiveButton(R.string.shared_string_save) { _, _ ->
                lengthInput.text.toString().toDoubleOrNull()?.let {
                    NauticalPlugin.engine?.controlManager?.updateVesselDesign("design.length.overall", it)
                }
                beamInput.text.toString().toDoubleOrNull()?.let {
                    NauticalPlugin.engine?.controlManager?.updateVesselDesign("design.beam", it)
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showEditDesignDialog(label: String, path: String) {
        val context = context ?: return
        val input = EditText(context).apply {
            inputType = if (path == "mmsi") InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        
        AlertDialog.Builder(context)
            .setTitle(label)
            .setView(input)
            .setPositiveButton(R.string.shared_string_save) { _, _ ->
                val value: Any? = if (path == "mmsi") input.text.toString().toIntOrNull() else input.text.toString().toDoubleOrNull()
                value?.let {
                    NauticalPlugin.engine?.controlManager?.updateVesselDesign(path, it)
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }


    private fun updateStats(root: View, state: MarineState) {

        root.findViewById<TextView>(R.id.txt_vessel_title).text = state.vesselName ?: getString(R.string.nautical_vessel_details)
        root.findViewById<TextView>(R.id.txt_vessel_uuid).text = state.vesselUuid ?: ""

        // Identity & Design
        val identity = root.findViewById<View>(R.id.grid_identity)
        fillCell(identity, 11, R.drawable.ic_action_flag, getString(R.string.nautical_vessel_flag_port), "${state.vesselFlag ?: "N/A"} / ${state.vesselPort ?: "N/A"}")
        fillCell(identity, 12, R.drawable.ic_action_user, getString(R.string.nautical_vessel_callsign), state.vesselCallSign ?: "N/A")
        fillCell(identity, 13, R.drawable.ic_action_info, getString(R.string.nautical_vessel_mmsi_label), state.vesselMmsi?.toString() ?: "N/A")
        fillCell(identity, 21, R.drawable.ic_action_length, getString(R.string.nautical_vessel_dimensions), "L:${state.vesselLength ?: "N/A"}m\nB:${state.vesselBeam ?: "N/A"}m")
        fillCell(identity, 22, R.drawable.ic_action_altitude, getString(R.string.nautical_vessel_air_draft_label), "${state.airDraft ?: "N/A"}m")
        fillCell(identity, 23, R.drawable.ic_action_weight_limit, getString(R.string.nautical_vessel_displacement_label), "${state.displacement ?: "N/A"}kg")

        // Engine & Tanks
        val systems = root.findViewById<View>(R.id.grid_systems)
        val mainBattery = state.batteries.values.firstOrNull()
        val cellInfo = mainBattery?.cellVoltages?.takeIf { it.isNotEmpty() }?.joinToString("/") { String.format(Locale.US, "%.2f", it) } ?: ""
        
        val mainEngine = state.engines.values.firstOrNull()
        val wasteTank = state.tanks.values.find { it.type == "wasteWater" }
        val greyTank = state.tanks.values.find { it.type == "greyWater" }
        val oilTank = state.tanks.values.find { it.type == "lubeOil" }
        val gasTank = state.tanks.values.find { it.type == "gas" }

        fillCell(systems, 11, R.drawable.ic_action_nautical_battery_volt, getString(R.string.nautical_vessel_main_bms), "${mainBattery?.voltage ?: "N/A"}V\n$cellInfo")
        fillCell(systems, 12, R.drawable.ic_action_settings, getString(R.string.nautical_vessel_transmission_label), "G:${mainEngine?.transmissionGear ?: "N/A"}\nP:${mainEngine?.transmissionPressure ?: "N/A"}Pa")
        fillCell(systems, 13, R.drawable.ic_action_nautical_battery_volt, getString(R.string.nautical_vessel_alternator_label), "${mainEngine?.alternatorVoltage ?: "N/A"}V\n${mainEngine?.alternatorCurrent ?: "N/A"}A")
        fillCell(systems, 21, R.drawable.ic_action_nautical_waste_tank, getString(R.string.nautical_vessel_waste_grey), "${formatPercent(wasteTank?.currentLevel)} / ${formatPercent(greyTank?.currentLevel)}")
        fillCell(systems, 22, R.drawable.ic_action_nautical_oil_pressure, getString(R.string.nautical_vessel_lube_oil_label), formatPercent(oilTank?.currentLevel))
        fillCell(systems, 23, R.drawable.ic_action_fuel_tank, getString(R.string.nautical_vessel_gas_label), formatPercent(gasTank?.currentLevel))

        // AC Power & Sails
        val power = root.findViewById<View>(R.id.grid_power)
        val mainInverter = state.inverters.values.firstOrNull()
        val mainCharger = state.chargers.values.firstOrNull()

        fillCell(power, 11, R.drawable.ic_action_nautical_battery_volt, getString(R.string.nautical_vessel_ac_voltage_label), "${mainInverter?.acVoltage ?: "N/A"}V\n${mainInverter?.acFrequency ?: "N/A"}Hz")
        fillCell(power, 12, R.drawable.ic_action_nautical_battery_current, getString(R.string.nautical_vessel_ac_current_label), "${mainInverter?.acCurrent ?: "N/A"}A\nSrc:${mainInverter?.state ?: "N/A"}")
        fillCell(power, 13, R.drawable.ic_action_settings, getString(R.string.nautical_vessel_inv_chg), "I:${mainInverter?.state ?: "OFF"}\nC:${mainCharger?.state ?: "OFF"}")
        fillCell(power, 21, R.drawable.ic_action_sail_boat_dark, getString(R.string.nautical_vessel_sails_label), "Reefs: ${state.reefs ?: 0}")
        fillCell(power, 22, R.drawable.ic_action_sail_boat_dark, getString(R.string.nautical_vessel_active_plan_label), state.activeSailPlan ?: "N/A")
        fillCell(power, 23, R.drawable.ic_action_user, getString(R.string.nautical_vessel_crew_label), state.crewNames.joinToString(", ").takeIf { it.isNotEmpty() } ?: "None")

        // Environment & GNSS
        val environment = root.findViewById<View>(R.id.grid_environment)
        val gnss = state.gnss
        fillCell(environment, 11, R.drawable.ic_action_device_location, getString(R.string.nautical_vessel_gnss_label), "Sats:${gnss?.satellites ?: 0}\nHDOP:${gnss?.horizontalDilution ?: "N/A"}")
        fillCell(environment, 12, R.drawable.ic_action_sun, getString(R.string.nautical_vessel_illum_label), "${state.outsideIlluminance ?: "N/A"} lux")
        fillCell(environment, 13, R.drawable.ic_action_nautical_water_temp, getString(R.string.nautical_vessel_salinity_label), "${state.waterSalinity ?: "N/A"}‰")
        fillCell(environment, 21, R.drawable.ic_action_nautical_water_temp, getString(R.string.nautical_vessel_dew_point_label), "${state.airDewPoint ?: "N/A"}K")
        fillCell(environment, 22, R.drawable.ic_action_nautical_oil_pressure, getString(R.string.nautical_vessel_humidity_label), formatPercent(state.outsideHumidity))
        fillCell(environment, 23, R.drawable.ic_action_info, getString(R.string.nautical_vessel_status_label), gnss?.integrity ?: "N/A")

        // Rigging & Loads
        val riggingHeader = root.findViewById<View>(R.id.header_rigging)
        val riggingGrid = root.findViewById<View>(R.id.grid_rigging)
        if (state.riggingLoads.isNotEmpty()) {
            riggingHeader.visibility = View.VISIBLE
            riggingGrid.visibility = View.VISIBLE
            val items = state.riggingLoads.toList()
            for (i in 0 until 6) {
                if (i < items.size) {
                    val (key, value) = items[i]
                    fillCell(riggingGrid, getCellIdx(i), R.drawable.ic_action_settings, key, "${value}N")
                } else {
                    fillCell(riggingGrid, getCellIdx(i), 0, "", "")
                }
            }
        } else {
            riggingHeader.visibility = View.GONE
            riggingGrid.visibility = View.GONE
        }

        // Pypilot Health (Phase 9)
        val pypilotHeader = root.findViewById<View>(R.id.header_pypilot)
        val pypilotGrid = root.findViewById<View>(R.id.grid_pypilot)
        val pypilot = state.pypilotServo
        if (pypilot != null) {
            pypilotHeader.visibility = View.VISIBLE
            pypilotGrid.visibility = View.VISIBLE
            fillCell(pypilotGrid, 11, R.drawable.ic_action_nautical_battery_volt, getString(R.string.nautical_pypilot_servo_volt), "${pypilot.voltage ?: "N/A"}V")
            fillCell(pypilotGrid, 12, R.drawable.ic_action_nautical_battery_current, getString(R.string.nautical_pypilot_servo_curr), "${pypilot.current ?: "N/A"}A")
            fillCell(pypilotGrid, 13, R.drawable.ic_action_nautical_engine_temp, getString(R.string.nautical_pypilot_ctrl_temp), "${pypilot.controllerTemp ?: "N/A"}K")
            fillCell(pypilotGrid, 21, R.drawable.ic_action_time, getString(R.string.nautical_pypilot_amp_hours), "${pypilot.ampHours ?: "N/A"}Ah")
            fillCell(pypilotGrid, 22, R.drawable.ic_action_time, getString(R.string.nautical_vessel_runtime_label), "${pypilot.runtime ?: "N/A"}${getString(R.string.nautical_unit_sec_short)}")
            fillCell(pypilotGrid, 23, R.drawable.ic_action_info, getString(R.string.nautical_pypilot_engaged), pypilot.engagement ?: "N/A")
        } else {
            pypilotHeader.visibility = View.GONE
            pypilotGrid.visibility = View.GONE
        }
    }

    private fun getCellIdx(i: Int): Int = when(i) {
        0 -> 11
        1 -> 12
        2 -> 13
        3 -> 21
        4 -> 22
        5 -> 23
        else -> 11
    }

    private fun fillCell(root: View, cellIdx: Int, iconId: Int, label: String, value: String) {
        val icon: ImageView? = when(cellIdx) {
            11 -> root.findViewById(R.id.img_icon_1_1)
            12 -> root.findViewById(R.id.img_icon_1_2)
            13 -> root.findViewById(R.id.img_icon_1_3)
            21 -> root.findViewById(R.id.img_icon_2_1)
            22 -> root.findViewById(R.id.img_icon_2_2)
            23 -> root.findViewById(R.id.img_icon_2_3)
            else -> null
        }
        val lbl: TextView? = when(cellIdx) {
            11 -> root.findViewById(R.id.txt_label_1_1)
            12 -> root.findViewById(R.id.txt_label_1_2)
            13 -> root.findViewById(R.id.txt_label_1_3)
            21 -> root.findViewById(R.id.txt_label_2_1)
            22 -> root.findViewById(R.id.txt_label_2_2)
            23 -> root.findViewById(R.id.txt_label_2_3)
            else -> null
        }
        val valTxt: TextView? = when(cellIdx) {
            11 -> root.findViewById(R.id.txt_value_1_1)
            12 -> root.findViewById(R.id.txt_value_1_2)
            13 -> root.findViewById(R.id.txt_value_1_3)
            21 -> root.findViewById(R.id.txt_value_2_1)
            22 -> root.findViewById(R.id.txt_value_2_2)
            23 -> root.findViewById(R.id.txt_value_2_3)
            else -> null
        }

        icon?.setImageResource(iconId)
        lbl?.text = label
        valTxt?.text = value
    }

    private fun formatPercent(value: Double?): String {
        return if (value != null) String.format(Locale.US, "%.0f%%", value * 100.0) else "N/A"
    }
}
