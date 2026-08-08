package net.osmand.plus.plugins.nautical

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.viewmodel.SailingPerformanceSettingsViewModel
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.discovery.SignalKDiscoveryManager
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.OnPreferenceChanged
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.settings.enums.VesselContext
import net.osmand.plus.settings.preferences.EditTextPreferenceEx

import net.osmand.plus.settings.preferences.ListPreferenceEx
import net.osmand.plus.settings.preferences.SwitchPreferenceEx
import java.util.*

class NauticalSettingsFragment : BaseSettingsFragment(), OnPreferenceChanged {

    private var discoveryManager: SignalKDiscoveryManager? = null

    override fun setupPreferences() {
        setupDisplayCategory()
        setupVesselContext()
        setupModulesCategory()
        setupConnectionCategory()
        setupVesselCategory()
        setupAutopilotTuningCategory()
        setupSafetyCategory()
        setupManeuverCategory()
        setupAnchorAdvancedCategory()
        setupMapOverlaysCategory()
        setupSailingCategory()
        setupAisCategory()
        setupVhfCategory()
        setupNavtexCategory()
        setupLogbookCategory()
        setupMaintenanceCategory()

        updateSecureSettingsVisibility(settings.NAUTICAL_USE_SECURE_CONNECTION.get())
        updateHardwareVisibility(settings.NAUTICAL_NMEA_SOURCE.get())
        updateModuleDetailsVisibility()
        updateConnectionStatusSummaries()

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { 
                updateConnectionStatusSummaries()
            }
        }
    }

    private fun updateConnectionStatusSummaries() {
        val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
        val status = if (connected) "" else " (${getString(R.string.nautical_offline_status)})"
        
        val needsConnection = listOf(
            "nautical_switch_panel",
            "nautical_boat_ai",
            "nautical_notifications",
            "nautical_server_routes",
            "nautical_server_charts"
        )
        
        needsConnection.forEach { key ->
            findPreference<Preference>(key)?.let { pref ->
                val baseSummary = when(key) {
                    "nautical_switch_panel" -> getString(R.string.nautical_switch_panel_desc)
                    "nautical_boat_ai" -> getString(R.string.nautical_boat_ai_desc)
                    "nautical_notifications" -> getString(R.string.nautical_notifications_desc)
                    "nautical_server_routes" -> getString(R.string.nautical_server_routes_desc)
                    "nautical_server_charts" -> getString(R.string.nautical_server_charts_desc)
                    else -> ""
                }
                pref.summary = "$baseSummary$status"
            }
        }
    }

    private fun setupDisplayCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_DISPLAY_MODE.id)?.apply {
            entries = arrayOf(
                getString(R.string.nautical_display_mode_normal),
                getString(R.string.nautical_display_mode_dark),
                getString(R.string.nautical_display_mode_sunlight),
            )
            entryValues = arrayOf(
                NauticalDisplayMode.NORMAL.name,
                NauticalDisplayMode.DARK.name,
                NauticalDisplayMode.SUNLIGHT.name,
            )
            val currentMode = settings.NAUTICAL_DISPLAY_MODE.get()
            value = currentMode.name
            summary = when (currentMode) {
                NauticalDisplayMode.DARK -> getString(R.string.nautical_display_mode_dark)
                NauticalDisplayMode.SUNLIGHT -> getString(R.string.nautical_display_mode_sunlight)
                else -> getString(R.string.nautical_display_mode_normal)
            }
            setIcon(R.drawable.ic_action_appearance)
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_HEAVY_WEATHER_MODE.id)?.apply {
            setIcon(R.drawable.ic_action_alert)
        }
    }

    private fun setupVesselContext() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_VESSEL_CONTEXT.id)?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            entries = VesselContext.entries.map { getString(it.titleId) }.toTypedArray()
            entryValues = VesselContext.entries.map { it.name }.toTypedArray()
            val current = settings.NAUTICAL_VESSEL_CONTEXT.get()
            value = current.name
            summary = getString(current.titleId)
        }
    }

    private fun setupModulesCategory() {
        val modules = listOf(
            settings.NAUTICAL_AIS_ENABLED,
            settings.NAUTICAL_MODULE_TIDES,
            settings.NAUTICAL_MODULE_GRIB,
            settings.NAUTICAL_VHF_ENABLED,
            settings.NAUTICAL_MODULE_LOGBOOK,
            settings.NAUTICAL_MODULE_ENC,
            settings.NAUTICAL_MODULE_RASTER,
            settings.NAUTICAL_NAVTEX_ENABLED
        )
        modules.forEach { pref ->
            findPreference<SwitchPreferenceEx>(pref.id)?.apply {
                setIcon(R.drawable.ic_action_additional_option)
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_GRIB_SOURCE_SIGNALK.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
        }
    }

    private fun setupMaintenanceCategory() {
        findPreference<Preference>("nautical_diagnostics")?.apply {
            setIcon(R.drawable.ic_action_info)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SIGNALK_DIAGNOSTICS)
                true
            }
        }

        findPreference<Preference>("nautical_advanced_tuning")?.apply {
            setIcon(R.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_ADVANCED_SETTINGS)
                true
            }
        }

        findPreference<Preference>("nautical_boat_ai")?.apply {
            setIcon(R.drawable.ic_action_settings) // Replace with AI icon if available
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.BOAT_AI)
                true
            }
        }

        findPreference<Preference>("nautical_checklists")?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_CHECKLISTS)
                true
            }
        }

        findPreference<Preference>("nautical_notifications")?.apply {
            setIcon(R.drawable.ic_action_alert)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_NOTIFICATIONS)
                true
            }
        }

        findPreference<Preference>("nautical_safety_regions")?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_SAFETY_REGIONS)
                true
            }
        }

        findPreference<Preference>("nautical_hardware_health")?.apply {
            setIcon(R.drawable.ic_action_info)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_HARDWARE_STATS)
                true
            }
        }

        findPreference<Preference>("nautical_master_telemetry_setup")?.apply {
            setIcon(R.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_MASTER_TELEMETRY)
                true
            }
        }

        findPreference<Preference>("nautical_clear_data")?.apply {
            setIcon(R.drawable.ic_action_delete_dark)
            setOnPreferenceClickListener {
                // Task 60: Add confirmation dialog to prevent accidental data loss
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.nautical_clear_marine_data)
                    .setMessage(R.string.nautical_clear_data_confirm)
                    .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                        NauticalPlugin.getInstance()?.clearMarineData()
                    }
                    .setNegativeButton(R.string.shared_string_cancel, null)
                    .show()
                true
            }
        }
        
        findPreference<Preference>("marine_raster_manager")?.setOnPreferenceClickListener {
            net.osmand.plus.plugins.nautical.raster.MarineRasterSettingsControl.show(parentFragmentManager)
            true
        }

        findPreference<Preference>("s63_permit_manager")?.setOnPreferenceClickListener {
            showInstance(requireActivity(), SettingsScreenType.S63_PERMIT_MANAGER)
            true
        }

        findPreference<Preference>("nautical_enc_manager")?.apply {
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.ENC_CHART_MANAGER)
                true
            }
            NauticalPlugin.getInstance()?.s57SpatialIndex?.let { index ->
                lifecycleScope.launch {
                    index.indexingStatus.collectLatest { status ->
                        summary = status
                    }
                }
            }
        }

        findPreference<Preference>("nautical_tide_manager")?.apply {
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.TIDE_DATA_MANAGER)
                true
            }
            lifecycleScope.launch {
                NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                    summary = state.tide?.stationName ?: getString(R.string.shared_string_none)
                }
            }
        }

        findPreference<Preference>("nautical_grib_manager")?.setOnPreferenceClickListener {
            net.osmand.plus.plugins.nautical.grib.ui.GribManagerBottomSheet.show(parentFragmentManager)
            true
        }

        findPreference<Preference>("nautical_server_routes")?.setOnPreferenceClickListener {
            showInstance(requireActivity(), SettingsScreenType.SIGNALK_SERVER_ROUTES)
            true
        }

        findPreference<Preference>("nautical_server_charts")?.setOnPreferenceClickListener {
            showInstance(requireActivity(), SettingsScreenType.SIGNALK_SERVER_CHARTS)
            true
        }
    }

    private fun setupVesselCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_VESSEL_TYPE.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            entries = arrayOf(
                getString(R.string.nautical_vessel_conventional),
                getString(R.string.nautical_vessel_proa)
            )
            entryValues = arrayOf("CONVENTIONAL", "PROA")
            val type = settings.NAUTICAL_VESSEL_TYPE.get()
            value = type.name
            summary = when (type) {
                net.osmand.plus.settings.enums.VesselType.PROA -> getString(R.string.nautical_vessel_proa)
                else -> getString(R.string.nautical_vessel_conventional)
            }
        }

        setupDepthPreference(settings.NAUTICAL_VESSEL_DRAFT.id, R.string.nautical_vessel_draft_base, R.drawable.ic_action_sail_boat_dark)
        setupDepthPreference(settings.NAUTICAL_AIR_DRAFT.id, R.string.nautical_vessel_air_draft_label, R.drawable.ic_action_altitude)
        setupDepthPreference(settings.NAUTICAL_KEEL_OFFSET.id, R.string.nautical_keel_offset_title, R.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_WIND_ALIGNMENT.id)?.apply {
            setIcon(R.drawable.ic_action_wind)
            summary = "${settings.NAUTICAL_WIND_ALIGNMENT.get()}°"
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_HEADING_REFERENCE.id)?.apply {
            setIcon(R.drawable.ic_action_compass)
            entries = arrayOf(getString(R.string.nautical_heading_reference_true), getString(R.string.nautical_heading_reference_mag))
            entryValues = arrayOf("TRUE", "MAGNETIC")
            val ref = settings.NAUTICAL_HEADING_REFERENCE.get()
            value = ref.name
            summary = if (ref == net.osmand.plus.settings.enums.HeadingReference.TRUE) getString(R.string.nautical_heading_reference_true) else getString(R.string.nautical_heading_reference_mag)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TTW_MODE.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            entries = arrayOf(getString(R.string.nautical_ttw_mode_sog), getString(R.string.nautical_ttw_mode_vmg))
            entryValues = arrayOf("SOG", "VMG")
            val mode = settings.NAUTICAL_TTW_MODE.get()
            value = mode.name
            summary = if (mode == net.osmand.plus.settings.enums.TtwMode.SOG) getString(R.string.nautical_ttw_mode_sog) else getString(R.string.nautical_ttw_mode_vmg)
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ACTIVE_SAIL_PLAN.id)?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            summary = settings.NAUTICAL_ACTIVE_SAIL_PLAN.get().ifEmpty { getString(R.string.shared_string_none) }
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SAIL_INVENTORY)
                true
            }
        }
    }

    private fun setupAutopilotTuningCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_PILOT_SEA_STATE.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            val options = (0..9).map { it.toString() }.toTypedArray()
            entries = options
            entryValues = options
            val current = settings.NAUTICAL_PILOT_SEA_STATE.get() ?: 3
            value = current.toString()
            summary = current.toString()
        }

        val tuningPrefs = listOf(
            settings.NAUTICAL_RUDDER_GAIN,
            settings.NAUTICAL_COUNTER_RUDDER,
            settings.NAUTICAL_AUTO_TRIM,
            settings.NAUTICAL_FILTER_SENSITIVITY,
            settings.NAUTICAL_RUDDER_LIMIT
        )
        tuningPrefs.forEach { pref ->
            findPreference<EditTextPreferenceEx>(pref.id)?.apply {
                setIcon(R.drawable.ic_action_settings)
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
            }
        }
    }

    private fun setupConnectionCategory() {
        findPreference<Preference>("nautical_discovery_mdns")?.apply {
            setIcon(R.drawable.ic_sensors_search)
            setOnPreferenceClickListener {
                showDiscoveryDialog()
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_IP.id)?.apply {
            setIcon(R.drawable.ic_action_world_globe)
            summary = settings.NAUTICAL_SERVER_IP.get().ifEmpty { getString(R.string.nautical_server_ip_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PORT.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            summary = settings.NAUTICAL_SERVER_PORT.get().ifEmpty { getString(R.string.nautical_server_port_desc) }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_USE_SECURE_CONNECTION.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_USERNAME.id)?.apply {
            setIcon(R.drawable.ic_action_user)
            summary = settings.NAUTICAL_SERVER_USERNAME.get().ifEmpty { getString(R.string.nautical_server_username_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PASSWORD.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            summary = if (settings.NAUTICAL_SERVER_PASSWORD.get().isEmpty()) {
                getString(R.string.nautical_server_password_desc)
            } else {
                getString(R.string.nautical_password_mask)
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            summary = if (settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get().isEmpty()) getString(R.string.shared_string_none) else getString(R.string.nautical_password_mask)
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            isChecked = settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_FORCE_WATCH_LAYOUT.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TELEMETRY_REFRESH_RATE.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            entries = arrayOf(getString(R.string.nautical_refresh_rate_1s), getString(R.string.nautical_refresh_rate_2s), getString(R.string.nautical_refresh_rate_5s))
            entryValues = arrayOf("1", "2", "5")
            val refreshRate = settings.NAUTICAL_TELEMETRY_REFRESH_RATE.get()
            value = refreshRate.toString()
            summary = when (refreshRate) {
                1 -> getString(R.string.nautical_refresh_rate_1s)
                2 -> getString(R.string.nautical_refresh_rate_2s)
                5 -> getString(R.string.nautical_refresh_rate_5s)
                else -> getString(R.string.nautical_refresh_rate_1s)
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_NMEA_SOURCE.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            entries = arrayOf(
                getString(R.string.nautical_nmea_source_signalk),
                getString(R.string.nautical_nmea_source_bluetooth),
                getString(R.string.nautical_nmea_source_usb),
                getString(R.string.nautical_nmea_source_tcp),
            )
            entryValues = arrayOf(
                net.osmand.plus.settings.enums.NmeaSource.SIGNALK.name,
                net.osmand.plus.settings.enums.NmeaSource.BLUETOOTH.name,
                net.osmand.plus.settings.enums.NmeaSource.USB.name,
                net.osmand.plus.settings.enums.NmeaSource.TCP.name
            )
            val source = settings.NAUTICAL_NMEA_SOURCE.get()
            value = source.name
            summary = getString(source.titleId)
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_BT_DEVICE_ADDRESS.id)?.apply {
            setIcon(R.drawable.ic_action_bluetooth)
            summary = settings.NAUTICAL_BT_DEVICE_ADDRESS.get().ifEmpty { getString(R.string.shared_string_none) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_USB_DEVICE_NAME.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            summary = settings.NAUTICAL_USB_DEVICE_NAME.get().ifEmpty { getString(R.string.shared_string_none) }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_NMEA_BAUD_RATE.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            val baudRates = arrayOf("4800", "9600", "19200", "38400", "57600", "115200")
            entries = baudRates
            entryValues = baudRates
            val current = settings.NAUTICAL_NMEA_BAUD_RATE.get().toString()
            value = current
            summary = current
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id)?.apply {
            setIcon(R.drawable.ic_action_play_dark)
            entries = arrayOf(getString(R.string.shared_string_yes), getString(R.string.shared_string_no))
            entryValues = arrayOf(true.toString(), false.toString())
            val isEnabled = settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
            value = isEnabled.toString()
            summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
        }

        findPreference<Preference>("nautical_hardware_health")?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_HARDWARE_STATS)
                true
            }
        }
    }

    private fun showDiscoveryDialog() {
        val manager = SignalKDiscoveryManager(requireContext())
        discoveryManager = manager
        manager.startDiscovery()

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.nautical_discovery_searching)
        
        val adapter = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        builder.setAdapter(adapter) { _, which ->
            val server = manager.discoveredServers.value[which]
            settings.NAUTICAL_SERVER_IP.set(server.host)
            settings.NAUTICAL_SERVER_PORT.set(server.port.toString())
            settings.NAUTICAL_USE_SECURE_CONNECTION.set(server.isWebSocket) // Simplified heuristic
            onPreferenceChanged(settings.NAUTICAL_SERVER_IP.id)
            manager.stopDiscovery()
        }

        val dialog = builder.create()
        dialog.setOnDismissListener { manager.stopDiscovery() }
        dialog.show()

        lifecycleScope.launch {
            manager.discoveredServers.collectLatest { servers ->
                app.runInUIThread {
                    adapter.clear()
                    if (servers.isEmpty()) {
                        dialog.setTitle(getString(R.string.nautical_discovery_searching))
                    } else {
                        dialog.setTitle(getString(R.string.nautical_discovery_select_server))
                        servers.forEach { adapter.add("${it.name} (${it.host})") }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupSafetyCategory() {
        setupDepthPreference(settings.NAUTICAL_SAFETY_MARGIN.id, R.string.nautical_safety_margin_base, R.drawable.ic_action_additional_option)

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_ENABLE_AUTO_DR.id)?.apply {
            setIcon(R.drawable.ic_action_play_dark)
        }

        setupDistancePreference(settings.NAUTICAL_XTE_THRESHOLD.id, R.string.nautical_xte_threshold_desc, R.drawable.ic_action_anchor)
        setupDistancePreference(settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id, R.string.nautical_look_ahead_radius_nm, R.drawable.ic_action_anchor)
        setupDistancePreference(settings.NAUTICAL_CORRIDOR_WIDTH.id, R.string.nautical_corridor_width, R.drawable.ic_action_additional_option)
        setupDistancePreference(settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id, R.string.nautical_safety_corridor_buffer, R.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_OFF_COURSE_ALARM.id)?.apply {
            setIcon(R.drawable.ic_action_alert)
            summary = "${settings.NAUTICAL_OFF_COURSE_ALARM.get()}°"
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.id)?.apply {
            setIcon(R.drawable.ic_action_alert)
            summary = "${settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get()}%"
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_MOB_AUDIO_GUIDANCE.id)?.apply {
            setIcon(R.drawable.ic_action_volume_up)
            val available = NauticalPlugin.getInstance()?.isAudioHardwareAvailable() == true
            isEnabled = available
            isChecked = settings.NAUTICAL_MOB_AUDIO_GUIDANCE.get() && available
        }

        findPreference<Preference>("nautical_compass_wizard")?.apply {
            setIcon(R.drawable.ic_action_compass)
            setOnPreferenceClickListener {
                net.osmand.plus.views.mapwidgets.widgets.NauticalCompassWizardDialog.show(this@NauticalSettingsFragment)
                true
            }
        }
    }

    private fun setupManeuverCategory() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.id)?.apply {
            isChecked = settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.get()
            setOnPreferenceChangeListener { _, newValue ->
                settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.set(newValue as Boolean)
                true
            }
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.id)?.apply {
            setIcon(R.drawable.ic_action_length)
            summary = "${settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()} m"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MED_MOORING_SCOPE.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_MED_MOORING_SCOPE.get()}:1"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_TACKING_WIND_LIMIT.id)?.apply {
            setIcon(R.drawable.ic_action_wind)
            summary = "${settings.NAUTICAL_TACKING_WIND_LIMIT.get()} kn"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MOB_AUDIO_INTERVAL.id)?.apply {
            setIcon(R.drawable.ic_action_volume_up)
            summary = "${settings.NAUTICAL_MOB_AUDIO_INTERVAL.get()} s"
        }
        setupDistancePreference(settings.NAUTICAL_ARRIVAL_RADIUS.id, R.string.nautical_arrival_radius, R.drawable.ic_action_anchor)
    }

    private fun setupAnchorAdvancedCategory() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_ANCHOR_LOCKED_LOCALLY.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
        }

        setupDepthPreference(settings.NAUTICAL_ANCHOR_DEPTH.id, R.string.nautical_anchor_label_depth, R.drawable.ic_action_anchor)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_TIDE_RISE.id, R.string.nautical_anchor_label_tide, R.drawable.ic_action_additional_option)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_FREEBOARD.id, R.string.nautical_anchor_label_freeboard, R.drawable.ic_action_altitude)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_BOW_OFFSET.id, R.string.nautical_anchor_label_bow_offset, R.drawable.ic_action_additional_option)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id, R.string.nautical_anchor_label_safety_margin, R.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ANCHOR_SCOPE_RATIO.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()}:1"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ANCHOR_ACCURACY_THRESHOLD.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_ANCHOR_ACCURACY_THRESHOLD.get()} m"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        }

        findPreference<Preference>("nautical_clear_anchor")?.apply {
            setIcon(R.drawable.ic_action_delete_dark)
            setOnPreferenceClickListener {
                settings.NAUTICAL_ANCHOR_LAT.set(0.0)
                settings.NAUTICAL_ANCHOR_LON.set(0.0)
                app.showToastMessage(R.string.nautical_anchor_cleared)
                true
            }
        }
    }

    private fun setupMapOverlaysCategory() {
        val plugin = NauticalPlugin.getInstance()
        // Group Telemetry Widgets
        findPreference<PreferenceCategory>("telemetry_widgets_group")?.apply {
            title = "Telemetry (Text Widgets)"
        }
        
        val overlays = listOf(
            settings.NAUTICAL_SHOW_LAYLINES,
            settings.NAUTICAL_SHOW_WIND_SHIFTS,
            settings.NAUTICAL_SHOW_TRAJECTORY,
            settings.NAUTICAL_SHOW_TIDES,
            settings.NAUTICAL_SHOW_HEADING_LINE,
            settings.NAUTICAL_SHOW_COG_LINE,
            settings.NAUTICAL_SHOW_CMG_LINE,
            settings.NAUTICAL_SHOW_CURRENT_VECTOR,
            settings.NAUTICAL_RESTRICTED_AREAS_ENABLED,
            settings.NAUTICAL_SHOW_WINDY_TILES,
            settings.NAUTICAL_SHOW_RAIN_RADAR,
            settings.NAUTICAL_SHOW_OPENMETEO_TILES,
            settings.NAUTICAL_SHOW_NOAA_TILES,
            settings.NAUTICAL_SHOW_LOGBOOK_LAYER,
            settings.NAUTICAL_SHOW_PMTILES
        )
        overlays.forEach { pref ->
            findPreference<SwitchPreferenceEx>(pref.id)?.apply {
                setIcon(R.drawable.ic_action_additional_option)
                setOnPreferenceChangeListener { _, newValue ->
                    pref.set(newValue as Boolean)
                    plugin?.requestRefresh()
                    true
                }
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_LOOK_AHEAD_TIME.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            val options = arrayOf("2", "5", "10", "20", "30", "60")
            entries = options.map { "$it ${getString(R.string.shared_string_min)}" }.toTypedArray()
            entryValues = options
            val current = settings.NAUTICAL_LOOK_AHEAD_TIME.get()
            value = current.toString()
            summary = "$current ${getString(R.string.shared_string_min)}"
        }
    }

    private fun setupDepthPreference(id: String, titleId: Int, iconId: Int) {
        val safetyManager = NauticalPlugin.getInstance()?.safetyManager
        findPreference<EditTextPreferenceEx>(id)?.apply {
            setIcon(iconId)
            
            val meters = when (id) {
                settings.NAUTICAL_VESSEL_DRAFT.id -> settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
                settings.NAUTICAL_AIR_DRAFT.id -> settings.NAUTICAL_AIR_DRAFT.get().toDouble()
                settings.NAUTICAL_KEEL_OFFSET.id -> settings.NAUTICAL_KEEL_OFFSET.get().toDouble()
                settings.NAUTICAL_SAFETY_MARGIN.id -> settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
                settings.NAUTICAL_ANCHOR_DEPTH.id -> settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
                settings.NAUTICAL_ANCHOR_TIDE_RISE.id -> settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
                settings.NAUTICAL_ANCHOR_BOW_OFFSET.id -> settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
                settings.NAUTICAL_ANCHOR_FREEBOARD.id -> settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
                settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id -> settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get().toDouble()
                else -> 0.0
            }
            val (v, u) = SignalKUnitConverter.formatValue(app, settings, meters, "depth")
            title = "${getString(titleId)} ($u)"
            dialogTitle = title
            summary = "$v $u"
            
            val multiplier = safetyManager?.getDepthSItoUserMultiplier() ?: 1.0
            text = String.format(Locale.US, "%.2f", meters * multiplier)

            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    private fun setupDistancePreference(id: String, titleId: Int, iconId: Int) {
        findPreference<EditTextPreferenceEx>(id)?.apply {
            setIcon(iconId)
            val nm = when (id) {
                settings.NAUTICAL_XTE_THRESHOLD.id -> settings.NAUTICAL_XTE_THRESHOLD.get().toDouble()
                settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id -> settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.get().toDouble()
                settings.NAUTICAL_CORRIDOR_WIDTH.id -> settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
                settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id -> settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
                else -> 0.0
            }
            val (v, u) = SignalKUnitConverter.formatValue(app, settings, nm * 1852.0, "distance")
            title = "${getString(titleId)} ($u)"
            summary = "$v $u"
            text = nm.toString()
            setOnPreferenceChangeListener { _, newValue ->
                val str = newValue?.toString()?.replace(" ", "")?.replace("\u00A0", "") ?: ""
                try {
                    str.toDouble()
                    true
                } catch (_: NumberFormatException) {
                    false
                }
            }
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        }
    }

    private fun setupSailingCategory() {
        findPreference<Preference>("sailing_performance")?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SAILING_PERFORMANCE_SETTINGS)
                true
            }
            
            // Integrate SailingPerformanceSettingsViewModel to show active polar
            val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
            if (!connected) {
                summary = getString(R.string.nautical_disconnected_performance_msg)
            } else {
                SailingDependencyContainer.performanceRepository?.let { repo ->
                    val vm = SailingPerformanceSettingsViewModel(repo)
                    lifecycleScope.launch {
                        vm.activePolarName.collectLatest { name ->
                            summary = name
                        }
                    }
                }
            }
        }

        findPreference<Preference>("nautical_polar_wizard")?.apply {
            setIcon(R.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_POLAR_WIZARD)
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LAYLINES_TACK_ANGLE.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_LAYLINES_TACK_ANGLE.get()}°"
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LEEWAY_COEFFICIENT.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = settings.NAUTICAL_LEEWAY_COEFFICIENT.get().toString()
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_PREDICTIVE_STEERING.id)?.apply {
            setIcon(R.drawable.ic_action_wind)
            isChecked = settings.NAUTICAL_PREDICTIVE_STEERING.get()
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_SHADOW_DRIVE.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            entries = arrayOf("Low (20%)", "Medium (50%)", "High (80%)")
            entryValues = arrayOf("20", "50", "80")
            val current = settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get()
            value = current.toString()
            summary = "$current%"
        }
    }

    private fun setupAisCategory() {
        val plugin = NauticalPlugin.getInstance() ?: return

        findPreference<ListPreferenceEx>(plugin.aisObjLostTimeout.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            val entryValuesArr = arrayOf(3, 5, 7, 10, 12, 15, 20)
            entries = entryValuesArr.map { "$it ${getString(R.string.shared_string_minute_lowercase)}" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisObjLostTimeout.get()
            value = current.toString()
            summary = "$current ${getString(R.string.shared_string_minute_lowercase)}"
        }

        findPreference<ListPreferenceEx>(plugin.aisShipLostTimeout.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            val entryValuesArr = arrayOf(2, 3, 4, 5, 7, 10, 15, 100)
            entries = entryValuesArr.mapIndexed { index, i ->
                if (index == (entryValuesArr.size - 1)) getString(R.string.shared_string_disabled)
                else "$i ${getString(R.string.shared_string_minute_lowercase)}"
            }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisShipLostTimeout.get()
            value = current.toString()
            summary = if (current == 100) getString(R.string.shared_string_disabled) else "$current ${getString(R.string.shared_string_minute_lowercase)}"
        }

        findPreference<ListPreferenceEx>(plugin.aisCpaWarningTime.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            val entryValuesArr = arrayOf(0, 1, 5, 10, 20, 30, 60)
            entries = entryValuesArr.map { if (it == 0) getString(R.string.shared_string_disabled) else "$it ${getString(R.string.shared_string_minute_lowercase)}" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisCpaWarningTime.get()
            value = current.toString()
            summary = if (current == 0) getString(R.string.shared_string_disabled) else "$current ${getString(R.string.shared_string_minute_lowercase)}"
            findPreference<Preference>(plugin.aisCpaWarningDistance.id)?.isEnabled = current != 0
        }

        findPreference<ListPreferenceEx>(plugin.aisCpaWarningDistance.id)?.apply {
            setIcon(R.drawable.ic_action_anchor)
            val entryValuesArr = arrayOf(0.02f, 0.05f, 0.1f, 0.2f, 0.5f, 1.0f, 2.0f)
            entries = entryValuesArr.map { "$it NM" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisCpaWarningDistance.get()
            value = current.toString()
            summary = "$current NM"
        }

        findPreference<EditTextPreferenceEx>(plugin.aisOwnMmsi.id)?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            summary = plugin.aisOwnMmsi.get().toString()
        }

        findPreference<SwitchPreferenceEx>(plugin.aisDisplayOwnPosition.id)?.apply {
            setIcon(R.drawable.ic_action_user)
            isChecked = plugin.aisDisplayOwnPosition.get()
            isEnabled = plugin.aisOwnMmsi.get() != 0
        }

        findPreference<Preference>("nautical_ais_buddies")?.apply {
            setIcon(R.drawable.ic_action_group_list)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_AIS_BUDDIES)
                true
            }
        }
    }

    private fun setupVhfCategory() {
        findPreference<Preference>("nautical_vhf_history_view")?.apply {
            setIcon(R.drawable.ic_action_group_list)
            setOnPreferenceClickListener {
                net.osmand.plus.plugins.nautical.ui.VhfHistoryBottomSheet.show(parentFragmentManager)
                true
            }
        }

        findPreference<Preference>("nautical_switch_panel")?.apply {
            setIcon(R.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_SWITCH_PANEL)
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_VHF_BACKEND_URL.id)?.apply {
            setIcon(R.drawable.ic_action_antenna)
            summary = settings.NAUTICAL_VHF_BACKEND_URL.get().ifEmpty { getString(R.string.shared_string_none) }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_VHF_AUTO_REPLAY.id)?.apply {
            setIcon(R.drawable.ic_action_play_dark)
            isChecked = settings.NAUTICAL_VHF_AUTO_REPLAY.get()
        }
    }

    private fun setupNavtexCategory() {
        findPreference<SwitchPreferenceEx>(settings.NAVTEX_ONLY_URGENT.id)?.apply {
            setIcon(R.drawable.ic_action_alert)
            isChecked = settings.NAVTEX_ONLY_URGENT.get()
        }

        findPreference<EditTextPreferenceEx>(settings.NAVTEX_SUBJECT_FILTER.id)?.apply {
            setIcon(R.drawable.ic_action_filter)
            summary = settings.NAVTEX_SUBJECT_FILTER.get().ifEmpty { getString(R.string.shared_string_none) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAVTEX_MAX_DISTANCE.id)?.apply {
            setIcon(R.drawable.ic_action_anchor)
            val dist = settings.NAVTEX_MAX_DISTANCE.get()
            summary = if (dist > 0) "$dist km" else getString(R.string.shared_string_none)
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            summary = "${settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.get()} h"
        }
    }

    private fun setupLogbookCategory() {
        findPreference<Preference>("nautical_passage_plan")?.apply {
            setIcon(R.drawable.ic_action_track_16)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
                true
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_LOGBOOK_INTERVAL.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            val values = arrayOf(0, 15, 30, 60, 120, 240, 360, 1440)
            entries = values.map { if (it == 0) "Immediate (Event-based)" else "$it min" }.toTypedArray()
            entryValues = values.map { it.toString() }.toTypedArray()
            val current = settings.NAUTICAL_LOGBOOK_INTERVAL.get()
            value = current.toString()
            summary = if (current == 0) "Immediate (Event-based)" else "$current min"
        }
        
        findPreference<Preference>("nautical_module_logbook")?.apply {
             val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
             if (!connected) {
                 summary = getString(R.string.nautical_logbook_sync_msg)
             }
        }
    }

    private fun updateModuleDetailsVisibility() {
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        
        findPreference<PreferenceCategory>("ais_details_group")?.isVisible = settings.NAUTICAL_AIS_ENABLED.get()
        findPreference<PreferenceCategory>("vhf_details_group")?.isVisible = settings.NAUTICAL_VHF_ENABLED.get()
        findPreference<PreferenceCategory>("navtex_details_group")?.isVisible = settings.NAUTICAL_NAVTEX_ENABLED.get()
        findPreference<PreferenceCategory>("logbook_details_group")?.isVisible = settings.NAUTICAL_MODULE_LOGBOOK.get()
        findPreference<PreferenceCategory>("grib_details_group")?.isVisible = settings.NAUTICAL_MODULE_GRIB.get()
        
        findPreference<PreferenceCategory>("nautical_autopilot_tuning_category")?.isVisible = caps?.hasAdvancedAutopilot == true
        findPreference<PreferenceCategory>("nautical_energy_category")?.isVisible = caps?.hasEnergyManagement == true
        
        val hasSmart = caps?.hasNavtex == true || caps?.hasMediaControl == true
        findPreference<PreferenceCategory>("nautical_smart_category")?.isVisible = hasSmart
        findPreference<Preference>("nautical_boat_ai")?.isVisible = caps?.hasMediaControl == true
        findPreference<Preference>("nautical_notifications")?.isVisible = true
        findPreference<Preference>("nautical_safety_regions")?.isVisible = true
        findPreference<Preference>("nautical_checklists")?.isVisible = caps?.hasChecklists == true
        
        // Maneuver section logic - hide if not in BOAT mode (safety)
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(net.osmand.plus.settings.backend.ApplicationMode.BOAT)
        findPreference<PreferenceCategory>("nautical_maneuver_category")?.isVisible = isBoat
    }

    private fun updateSecureSettingsVisibility(useSecure: Boolean) {
        findPreference<Preference>(settings.NAUTICAL_SERVER_USERNAME.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_SERVER_PASSWORD.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.isVisible = useSecure
    }

    private fun updateHardwareVisibility(source: net.osmand.plus.settings.enums.NmeaSource) {
        findPreference<Preference>(settings.NAUTICAL_BT_DEVICE_ADDRESS.id)?.isVisible = (source == net.osmand.plus.settings.enums.NmeaSource.BLUETOOTH)
        findPreference<Preference>(settings.NAUTICAL_USB_DEVICE_NAME.id)?.isVisible = (source == net.osmand.plus.settings.enums.NmeaSource.USB)
        findPreference<Preference>(settings.NAUTICAL_NMEA_BAUD_RATE.id)?.isVisible = (source == net.osmand.plus.settings.enums.NmeaSource.USB)
    }

    override fun onPreferenceChanged(prefId: String) {
        val plugin = NauticalPlugin.getInstance()
        when (prefId) {
            settings.NAUTICAL_DISPLAY_MODE.id -> {
                val mode = settings.NAUTICAL_DISPLAY_MODE.get()
                plugin?.applyDisplayMode(requireActivity() as net.osmand.plus.activities.MapActivity, mode)
            }
            settings.NAUTICAL_VESSEL_CONTEXT.id -> {
                val ctx = settings.NAUTICAL_VESSEL_CONTEXT.get()
                plugin?.applyVesselContext(ctx)
            }
            settings.NAUTICAL_USE_SECURE_CONNECTION.id -> {
                updateSecureSettingsVisibility(settings.NAUTICAL_USE_SECURE_CONNECTION.get())
                plugin?.reconnect()
            }
            settings.NAUTICAL_SERVER_IP.id,
            settings.NAUTICAL_SERVER_PORT.id,
            settings.NAUTICAL_SERVER_USERNAME.id,
            settings.NAUTICAL_SERVER_PASSWORD.id,
            settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id,
            settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id,
            -> {
                plugin?.reconnect()
            }
            settings.NAUTICAL_NMEA_SOURCE.id -> {
                updateHardwareVisibility(settings.NAUTICAL_NMEA_SOURCE.get())
                plugin?.updateNmeaSource()
            }
            settings.NAUTICAL_AIS_ENABLED.id,
            settings.NAUTICAL_MODULE_TIDES.id,
            settings.NAUTICAL_MODULE_GRIB.id,
            settings.NAUTICAL_VHF_ENABLED.id,
            settings.NAUTICAL_MODULE_LOGBOOK.id,
            settings.NAUTICAL_MODULE_ENC.id,
            settings.NAUTICAL_MODULE_RASTER.id,
            settings.NAUTICAL_NAVTEX_ENABLED.id -> {
                updateModuleDetailsVisibility()
                plugin?.updateFeatureLifecycle()
            }
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val changed = super.onPreferenceChange(preference, newValue)
        if (changed) {
            val key = preference.key
            val newString = newValue?.toString() ?: ""
            val plugin = NauticalPlugin.getInstance()
            val safetyManager = plugin?.safetyManager
            
            when (key) {
                settings.NAUTICAL_DISPLAY_MODE.id -> {
                    val mode = NauticalDisplayMode.valueOf(newString)
                    preference.summary = when (mode) {
                        NauticalDisplayMode.DARK -> getString(R.string.nautical_display_mode_dark)
                        NauticalDisplayMode.SUNLIGHT -> getString(R.string.nautical_display_mode_sunlight)
                        else -> getString(R.string.nautical_display_mode_normal)
                    }
                }
                settings.NAUTICAL_SERVER_IP.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_ip_desc) }
                settings.NAUTICAL_SERVER_PORT.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_port_desc) }
                settings.NAUTICAL_SERVER_USERNAME.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_username_desc) }
                settings.NAUTICAL_SERVER_PASSWORD.id -> preference.summary = if (newString.isEmpty()) getString(R.string.nautical_server_password_desc) else getString(R.string.nautical_password_mask)
                settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id -> preference.summary = if (newString.isEmpty()) getString(R.string.shared_string_none) else getString(R.string.nautical_password_mask)
                settings.NAUTICAL_USE_SECURE_CONNECTION.id -> updateSecureSettingsVisibility(newValue as Boolean)
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id -> {
                    val isEnabled = newString.toBoolean()
                    preference.summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
                }
                
                settings.NAUTICAL_XTE_THRESHOLD.id,
                settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id,
                settings.NAUTICAL_CORRIDOR_WIDTH.id,
                settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 0.1f
                    val meters = SignalKUnitConverter.getUserDistanceToMeters(floatValue.toDouble(), settings)
                    val nm = SignalKUnitConverter.metersToNm(meters).toFloat()

                    when (key) {
                        settings.NAUTICAL_XTE_THRESHOLD.id -> settings.NAUTICAL_XTE_THRESHOLD.set(nm)
                        settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id -> settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.set(nm.coerceIn(0.5f, 5.0f))
                        settings.NAUTICAL_CORRIDOR_WIDTH.id -> settings.NAUTICAL_CORRIDOR_WIDTH.set(nm)
                        settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id -> settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(nm)
                    }
                    val (v, u) = SignalKUnitConverter.formatValue(app, settings, meters, "distance")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = v
                    return false
                }

                settings.NAUTICAL_VESSEL_DRAFT.id,
                settings.NAUTICAL_AIR_DRAFT.id,
                settings.NAUTICAL_KEEL_OFFSET.id,
                settings.NAUTICAL_SAFETY_MARGIN.id,
                settings.NAUTICAL_ANCHOR_DEPTH.id,
                settings.NAUTICAL_ANCHOR_TIDE_RISE.id,
                settings.NAUTICAL_ANCHOR_BOW_OFFSET.id,
                settings.NAUTICAL_ANCHOR_FREEBOARD.id,
                settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id -> {
                    val floatValue = newString.toFloatOrNull()
                    if (floatValue == null || floatValue < 0) {
                        app.showToastMessage(R.string.shared_string_invalid_value)
                        return false
                    }
                    val multiplier = safetyManager?.getDepthUserToSIMultiplier() ?: 1.0
                    val meters = floatValue.toDouble() * multiplier
                    
                    when (key) {
                        settings.NAUTICAL_VESSEL_DRAFT.id -> settings.NAUTICAL_VESSEL_DRAFT.set(meters.toFloat())
                        settings.NAUTICAL_AIR_DRAFT.id -> settings.NAUTICAL_AIR_DRAFT.set(meters.toFloat())
                        settings.NAUTICAL_KEEL_OFFSET.id -> settings.NAUTICAL_KEEL_OFFSET.set(meters.toFloat())
                        settings.NAUTICAL_SAFETY_MARGIN.id -> settings.NAUTICAL_SAFETY_MARGIN.set(meters.toFloat())
                        settings.NAUTICAL_ANCHOR_DEPTH.id -> settings.NAUTICAL_ANCHOR_DEPTH.set(meters.toFloat())
                        settings.NAUTICAL_ANCHOR_TIDE_RISE.id -> settings.NAUTICAL_ANCHOR_TIDE_RISE.set(meters.toFloat())
                        settings.NAUTICAL_ANCHOR_BOW_OFFSET.id -> settings.NAUTICAL_ANCHOR_BOW_OFFSET.set(meters.toFloat())
                        settings.NAUTICAL_ANCHOR_FREEBOARD.id -> settings.NAUTICAL_ANCHOR_FREEBOARD.set(meters.toFloat())
                        settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id -> settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.set(meters.toFloat())
                    }
                    val (v, u) = SignalKUnitConverter.formatValue(app, settings, meters, "depth")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = String.format(Locale.US, "%.2f", floatValue)
                    return false
                }

                settings.NAUTICAL_WIND_ALIGNMENT.id -> preference.summary = "${newString}°"
                settings.NAUTICAL_LAYLINES_TACK_ANGLE.id -> preference.summary = "${newString}°"
                settings.NAUTICAL_LEEWAY_COEFFICIENT.id -> preference.summary = newString
                settings.NAUTICAL_OFF_COURSE_ALARM.id -> preference.summary = "${newString}°"
                settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.id -> preference.summary = "$newString%"
                
                settings.NAUTICAL_RUDDER_GAIN.id,
                settings.NAUTICAL_COUNTER_RUDDER.id,
                settings.NAUTICAL_AUTO_TRIM.id,
                settings.NAUTICAL_FILTER_SENSITIVITY.id,
                settings.NAUTICAL_RUDDER_LIMIT.id -> {
                    preference.summary = newString
                }

                settings.NAUTICAL_ANCHOR_SCOPE_RATIO.id -> {
                    preference.summary = "$newString:1"
                }
                settings.NAUTICAL_STW_REL_DELAY_SEC.id -> {
                    preference.summary = "$newString s"
                }
                settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.id -> {
                    preference.summary = "$newString s"
                }

                settings.NAUTICAL_VESSEL_TYPE.id -> {
                    preference.summary = when (newString) {
                        "PROA" -> getString(R.string.nautical_vessel_proa)
                        else -> getString(R.string.nautical_vessel_conventional)
                    }
                }
                settings.NAUTICAL_NMEA_SOURCE.id -> {
                    val source = net.osmand.plus.settings.enums.NmeaSource.valueOf(newString)
                    updateHardwareVisibility(source)
                    preference.summary = getString(source.titleId)
                }
                settings.NAUTICAL_BT_DEVICE_ADDRESS.id -> preference.summary = newString.ifEmpty { getString(R.string.shared_string_none) }
                settings.NAUTICAL_USB_DEVICE_NAME.id -> preference.summary = newString.ifEmpty { getString(R.string.shared_string_none) }
                settings.NAUTICAL_NMEA_BAUD_RATE.id -> preference.summary = newString
                settings.NAUTICAL_TELEMETRY_REFRESH_RATE.id -> {
                    val rate = newString.toIntOrNull() ?: 1
                    preference.summary = when (rate) {
                        1 -> getString(R.string.nautical_refresh_rate_1s)
                        2 -> getString(R.string.nautical_refresh_rate_2s)
                        5 -> getString(R.string.nautical_refresh_rate_5s)
                        else -> getString(R.string.nautical_refresh_rate_1s)
                    }
                }
                settings.NAUTICAL_LOOK_AHEAD_TIME.id -> {
                    preference.summary = "$newString ${getString(R.string.shared_string_min)}"
                }

                plugin?.aisObjLostTimeout?.id -> preference.summary = "$newString ${getString(R.string.shared_string_minute_lowercase)}"
                plugin?.aisShipLostTimeout?.id -> preference.summary = if (newString == "100") getString(R.string.shared_string_disabled) else "$newString ${getString(R.string.shared_string_minute_lowercase)}"
                plugin?.aisCpaWarningTime?.id -> {
                    val time = newString.toIntOrNull() ?: 0
                    preference.summary = if (time == 0) getString(R.string.shared_string_disabled) else "$newString ${getString(R.string.shared_string_minute_lowercase)}"
                    findPreference<Preference>(plugin.aisCpaWarningDistance.id)?.isEnabled = time != 0
                }
                plugin?.aisCpaWarningDistance?.id -> preference.summary = "$newString NM"
                plugin?.aisOwnMmsi?.id -> {
                    val mmsi = newString.toIntOrNull() ?: 0
                    preference.summary = mmsi.toString()
                    findPreference<Preference>(plugin.aisDisplayOwnPosition.id)?.isEnabled = mmsi != 0
                }
                settings.NAUTICAL_VHF_BACKEND_URL.id -> preference.summary = newString.ifEmpty { getString(R.string.shared_string_none) }
                settings.NAVTEX_SUBJECT_FILTER.id -> preference.summary = newString.ifEmpty { getString(R.string.shared_string_none) }
                settings.NAVTEX_MAX_DISTANCE.id -> {
                    val dist = newString.toFloatOrNull() ?: 0f
                    preference.summary = if (dist > 0) "$newString km" else getString(R.string.shared_string_none)
                }
                settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.id -> preference.summary = "$newString h"
                settings.NAUTICAL_LOGBOOK_INTERVAL.id -> {
                    val interval = newString.toIntOrNull() ?: 0
                    preference.summary = when (interval) {
                        1 -> getString(R.string.nautical_logbook_1h)
                        4 -> getString(R.string.nautical_logbook_4h)
                        else -> getString(R.string.nautical_logbook_disabled)
                    }
                }
                settings.NAUTICAL_HEADING_REFERENCE.id -> preference.summary = if (newString == "TRUE") getString(R.string.nautical_heading_reference_true) else getString(R.string.nautical_heading_reference_mag)
                settings.NAUTICAL_TTW_MODE.id -> preference.summary = if (newString == "SOG") getString(R.string.nautical_ttw_mode_sog) else getString(R.string.nautical_ttw_mode_vmg)
                settings.NAUTICAL_VESSEL_CONTEXT.id -> {
                    val ctx = VesselContext.valueOf(newString)
                    preference.summary = getString(ctx.titleId)
                }
                settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.id -> preference.summary = "$newString m"
                settings.NAUTICAL_MED_MOORING_SCOPE.id -> preference.summary = "$newString:1"
                settings.NAUTICAL_TACKING_WIND_LIMIT.id -> preference.summary = "$newString kn"
                settings.NAUTICAL_MOB_AUDIO_INTERVAL.id -> preference.summary = "$newString s"
                settings.NAUTICAL_ARRIVAL_RADIUS.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 35.0f
                    val meters = floatValue.toDouble()
                    settings.NAUTICAL_ARRIVAL_RADIUS.set(meters.toFloat())
                    val (v, u) = SignalKUnitConverter.formatValue(app, settings, meters, "distance")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = v
                    return false
                }
                settings.NAUTICAL_PILOT_SEA_STATE.id -> {
                    preference.summary = newString
                }
                settings.NAUTICAL_ACTIVE_SAIL_PLAN.id -> preference.summary = newString.ifEmpty { getString(R.string.shared_string_none) }
                settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.id -> preference.summary = "$newString%"
            }
        }
        return changed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (NauticalPlugin.isNightVision(app)) {
            NauticalPlugin.getInstance()?.applyNightVisionFilter(view)
        }
    }
}
