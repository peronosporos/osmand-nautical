package net.osmand.plus.plugins.nautical

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R as OsmAndR
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.discovery.SignalKDiscoveryManager
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.viewmodel.SailingPerformanceSettingsViewModel
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.settings.enums.VesselContext
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.OnPreferenceChanged
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import net.osmand.plus.settings.preferences.ListPreferenceEx
import net.osmand.plus.settings.preferences.SwitchPreferenceEx
import java.util.Locale

class NauticalSettingsFragment : BaseSettingsFragment(), OnPreferenceChanged {

    private var discoveryManager: SignalKDiscoveryManager? = null

    private fun getSafeThemedContext(): Context {
        val base = activity ?: runCatching { requireActivity() }.getOrNull()
            ?: context ?: runCatching { requireContext() }.getOrNull()
        return if (base != null) {
            net.osmand.plus.utils.UiUtilities.getThemedContext(base, isNightMode)
        } else {
            app
        }
    }

    private fun Preference.setThemedIcon(resId: Int) {
        val themedCtx = getSafeThemedContext()
        try {
            icon = AppCompatResources.getDrawable(themedCtx, resId)
                ?: ContextCompat.getDrawable(themedCtx, resId)
        } catch (_: Exception) {
            try {
                icon = getContentIcon(resId)
            } catch (_: Exception) {
            }
        }
    }


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
        val plugin = NauticalPlugin.getInstance()
        val connected = plugin?.isSignalKConnected() == true
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        
        val needsConnection = mapOf(
            "nautical_switch_panel" to Pair(caps?.hasDigitalSwitching ?: false, "Install signalk-digital-switching"),
            "nautical_boat_ai" to Pair(caps?.hasMediaControl ?: false, "Install signalk-ai-bridge"),
            "nautical_notifications" to Pair(true, ""),
            "nautical_server_routes" to Pair(caps?.hasNavicoSync ?: true, "Check server resources"),
            "nautical_server_charts" to Pair(caps?.hasCharts ?: true, "Install signalk-charts-plugin"),
            "nautical_module_logbook" to Pair(caps?.hasLogging ?: false, "Install signalk-logbook"),
            "nautical_module_vhf" to Pair(caps?.hasNavtex ?: true, "Check backend URL"),
            "sailing_performance" to Pair(caps?.hasPolarPerformance ?: false, "Install signalk-polar-performance")
        )
        
        needsConnection.forEach { (key, info) ->
            findPreference<Preference>(key)?.let { pref ->
                val hasCap = info.first
                val guidance = info.second
                val baseSummary = when(key) {
                    "nautical_switch_panel" -> getString(OsmAndR.string.nautical_switch_panel_desc)
                    "nautical_boat_ai" -> getString(OsmAndR.string.nautical_boat_ai_desc)
                    "nautical_notifications" -> getString(OsmAndR.string.nautical_notifications_desc)
                    "nautical_server_routes" -> getString(OsmAndR.string.nautical_server_routes_desc)
                    "nautical_server_charts" -> getString(OsmAndR.string.nautical_server_charts_desc)
                    "nautical_module_logbook" -> getString(OsmAndR.string.nautical_logbook_sync_msg)
                    "nautical_module_vhf" -> "Connect to VHF radio backend"
                    "sailing_performance" -> getString(OsmAndR.string.wizard_polar_title)
                    else -> ""
                }
                
                pref.summary = when {
                    !connected -> "$baseSummary (${getString(OsmAndR.string.nautical_offline_status)})"
                    !hasCap -> "$baseSummary (Plugin missing: $guidance)"
                    else -> baseSummary
                }
            }
        }
    }

    private fun setupDisplayCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_DISPLAY_MODE.id)?.apply {
            entries = arrayOf(
                getString(OsmAndR.string.nautical_display_mode_normal),
                getString(OsmAndR.string.nautical_display_mode_dark),
                getString(OsmAndR.string.nautical_display_mode_sunlight),
            )
            entryValues = arrayOf(
                NauticalDisplayMode.NORMAL.name,
                NauticalDisplayMode.DARK.name,
                NauticalDisplayMode.SUNLIGHT.name,
            )
            val currentMode = settings.NAUTICAL_DISPLAY_MODE.get()
            value = currentMode.name
            summary = when (currentMode) {
                NauticalDisplayMode.DARK -> getString(OsmAndR.string.nautical_display_mode_dark)
                NauticalDisplayMode.SUNLIGHT -> getString(OsmAndR.string.nautical_display_mode_sunlight)
                else -> getString(OsmAndR.string.nautical_display_mode_normal)
            }
            setThemedIcon(OsmAndR.drawable.ic_action_appearance)
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_HEAVY_WEATHER_MODE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_alert)
        }
    }

    private fun setupVesselContext() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_VESSEL_CONTEXT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_sail_boat_dark)
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
                setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_GRIB_SOURCE_SIGNALK.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
        }
    }

    private fun setupMaintenanceCategory() {
        findPreference<Preference>("nautical_diagnostics")?.setThemedIcon(OsmAndR.drawable.ic_action_info)
        findPreference<Preference>("nautical_advanced_tuning")?.setThemedIcon(OsmAndR.drawable.ic_action_settings)
        findPreference<Preference>("nautical_boat_ai")?.setThemedIcon(OsmAndR.drawable.ic_action_android)
        findPreference<Preference>("nautical_checklists")?.setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
        findPreference<Preference>("nautical_notifications")?.setThemedIcon(OsmAndR.drawable.ic_action_alert)
        findPreference<Preference>("nautical_safety_regions")?.setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
        findPreference<Preference>("nautical_replay_manager")?.setThemedIcon(OsmAndR.drawable.ic_action_play_dark)
        findPreference<Preference>("nautical_hardware_health")?.setThemedIcon(OsmAndR.drawable.ic_action_info)
        findPreference<Preference>("nautical_master_telemetry_setup")?.setThemedIcon(OsmAndR.drawable.ic_action_settings)
        findPreference<Preference>("nautical_clear_data")?.setThemedIcon(OsmAndR.drawable.ic_action_delete_dark)

        findPreference<Preference>("nautical_enc_manager")?.apply {
            NauticalPlugin.getInstance()?.s57SpatialIndex?.let { index ->
                lifecycleScope.launch {
                    index.indexingStatus.collectLatest { status ->
                        summary = status
                    }
                }
            }
        }

        findPreference<Preference>("nautical_tide_manager")?.apply {
            lifecycleScope.launch {
                NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                    summary = state.tide?.stationName ?: getString(OsmAndR.string.shared_string_none)
                }
            }
        }
    }

    private fun setupVesselCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_VESSEL_TYPE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            entries = arrayOf(
                getString(OsmAndR.string.nautical_vessel_conventional),
                getString(OsmAndR.string.nautical_vessel_proa)
            )
            entryValues = arrayOf("CONVENTIONAL", "PROA")
            val type = settings.NAUTICAL_VESSEL_TYPE.get()
            value = type.name
            summary = when (type) {
                net.osmand.plus.settings.enums.VesselType.PROA -> getString(OsmAndR.string.nautical_vessel_proa)
                else -> getString(OsmAndR.string.nautical_vessel_conventional)
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_MULTIHULL_SHUNTING.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_sail_boat_dark)
            isChecked = settings.NAUTICAL_MULTIHULL_SHUNTING.get()
        }

        setupDepthPreference(settings.NAUTICAL_VESSEL_DRAFT.id, OsmAndR.string.nautical_vessel_draft_base, OsmAndR.drawable.ic_action_sail_boat_dark)
        setupDepthPreference(settings.NAUTICAL_AIR_DRAFT.id, OsmAndR.string.nautical_vessel_air_draft_label, OsmAndR.drawable.ic_action_altitude)
        setupDepthPreference(settings.NAUTICAL_KEEL_OFFSET.id, OsmAndR.string.nautical_keel_offset_title, OsmAndR.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_WIND_ALIGNMENT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_wind)
            summary = "${settings.NAUTICAL_WIND_ALIGNMENT.get()}°"
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_HEADING_REFERENCE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_compass)
            entries = arrayOf(getString(OsmAndR.string.nautical_heading_reference_true), getString(OsmAndR.string.nautical_heading_reference_mag))
            entryValues = arrayOf("TRUE", "MAGNETIC")
            val ref = settings.NAUTICAL_HEADING_REFERENCE.get()
            value = ref.name
            summary = if (ref == net.osmand.plus.settings.enums.HeadingReference.TRUE) getString(OsmAndR.string.nautical_heading_reference_true) else getString(OsmAndR.string.nautical_heading_reference_mag)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TTW_MODE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            entries = arrayOf(getString(OsmAndR.string.nautical_ttw_mode_sog), getString(OsmAndR.string.nautical_ttw_mode_vmg))
            entryValues = arrayOf("SOG", "VMG")
            val mode = settings.NAUTICAL_TTW_MODE.get()
            value = mode.name
            summary = if (mode == net.osmand.plus.settings.enums.TtwMode.SOG) getString(OsmAndR.string.nautical_ttw_mode_sog) else getString(OsmAndR.string.nautical_ttw_mode_vmg)
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ACTIVE_SAIL_PLAN.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_sail_boat_dark)
            summary = settings.NAUTICAL_ACTIVE_SAIL_PLAN.get().ifEmpty { getString(OsmAndR.string.shared_string_none) }
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SAIL_INVENTORY)
                true
            }
        }
    }

    private fun setupAutopilotTuningCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_PILOT_SEA_STATE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
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
                setThemedIcon(OsmAndR.drawable.ic_action_settings)
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
            }
        }
    }

    private fun setupConnectionCategory() {
        findPreference<Preference>("nautical_discovery_mdns")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_sensors_search)
            setOnPreferenceClickListener {
                showDiscoveryDialog()
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_IP.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_world_globe)
            summary = settings.NAUTICAL_SERVER_IP.get().ifEmpty { getString(OsmAndR.string.nautical_server_ip_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PORT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            summary = settings.NAUTICAL_SERVER_PORT.get().ifEmpty { getString(OsmAndR.string.nautical_server_port_desc) }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_USE_SECURE_CONNECTION.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_lock)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_USERNAME.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_user)
            summary = settings.NAUTICAL_SERVER_USERNAME.get().ifEmpty { getString(OsmAndR.string.nautical_server_username_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PASSWORD.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_lock)
            summary = if (settings.NAUTICAL_SERVER_PASSWORD.get().isEmpty()) {
                getString(OsmAndR.string.nautical_server_password_desc)
            } else {
                getString(OsmAndR.string.nautical_password_mask)
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_lock)
            summary = if (settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get().isEmpty()) getString(OsmAndR.string.shared_string_none) else getString(OsmAndR.string.nautical_password_mask)
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            isChecked = settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_FORCE_WATCH_LAYOUT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            isEnabled = !WearOsNauticalManager(requireContext()).isWatchMode() || settings.NAUTICAL_FORCE_WATCH_LAYOUT.get()
            if (!isEnabled) {
                summary = getString(OsmAndR.string.nautical_force_watch_layout_desc) + " (Watch Mode Active)"
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_location_navigation)
            isChecked = settings.NAUTICAL_AUTO_SWITCH_LOCATION_SOURCE.get()
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TELEMETRY_REFRESH_RATE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            entries = arrayOf(getString(OsmAndR.string.nautical_refresh_rate_1s), getString(OsmAndR.string.nautical_refresh_rate_2s), getString(OsmAndR.string.nautical_refresh_rate_5s))
            entryValues = arrayOf("1", "2", "5")
            val refreshRate = settings.NAUTICAL_TELEMETRY_REFRESH_RATE.get()
            value = refreshRate.toString()
            summary = when (refreshRate) {
                1 -> getString(OsmAndR.string.nautical_refresh_rate_1s)
                2 -> getString(OsmAndR.string.nautical_refresh_rate_2s)
                5 -> getString(OsmAndR.string.nautical_refresh_rate_5s)
                else -> getString(OsmAndR.string.nautical_refresh_rate_1s)
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_NMEA_SOURCE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            entries = arrayOf(
                getString(OsmAndR.string.nautical_nmea_source_signalk),
                getString(OsmAndR.string.nautical_nmea_source_bluetooth),
                getString(OsmAndR.string.nautical_nmea_source_usb),
                getString(OsmAndR.string.nautical_nmea_source_tcp),
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
            setThemedIcon(OsmAndR.drawable.ic_action_bluetooth)
            summary = settings.NAUTICAL_BT_DEVICE_ADDRESS.get().ifEmpty { getString(OsmAndR.string.shared_string_none) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_USB_DEVICE_NAME.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            summary = settings.NAUTICAL_USB_DEVICE_NAME.get().ifEmpty { getString(OsmAndR.string.shared_string_none) }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_NMEA_BAUD_RATE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            val baudRates = arrayOf("4800", "9600", "19200", "38400", "57600", "115200")
            entries = baudRates
            entryValues = baudRates
            val current = settings.NAUTICAL_NMEA_BAUD_RATE.get().toString()
            value = current
            summary = current
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_play_dark)
            entries = arrayOf(getString(OsmAndR.string.shared_string_yes), getString(OsmAndR.string.shared_string_no))
            entryValues = arrayOf(true.toString(), false.toString())
            val isEnabled = settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
            value = isEnabled.toString()
            summary = if (isEnabled) getString(OsmAndR.string.shared_string_yes) else getString(OsmAndR.string.shared_string_no)
        }

        findPreference<Preference>("nautical_hardware_health")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
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
        builder.setTitle(OsmAndR.string.nautical_discovery_searching)
        
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
                        dialog.setTitle(getString(OsmAndR.string.nautical_discovery_searching))
                    } else {
                        dialog.setTitle(getString(OsmAndR.string.nautical_discovery_select_server))
                        servers.forEach { adapter.add("${it.name} (${it.host})") }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupSafetyCategory() {
        setupDepthPreference(settings.NAUTICAL_SAFETY_MARGIN.id, OsmAndR.string.nautical_safety_margin_base, OsmAndR.drawable.ic_action_additional_option)

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_ENABLE_AUTO_DR.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_play_dark)
        }

        setupDistancePreference(settings.NAUTICAL_XTE_THRESHOLD.id, OsmAndR.string.nautical_xte_threshold_desc, OsmAndR.drawable.ic_action_anchor)
        setupDistancePreference(settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id, OsmAndR.string.nautical_look_ahead_radius_nm, OsmAndR.drawable.ic_action_anchor)
        setupDistancePreference(settings.NAUTICAL_CORRIDOR_WIDTH.id, OsmAndR.string.nautical_corridor_width, OsmAndR.drawable.ic_action_additional_option)
        setupDistancePreference(settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id, OsmAndR.string.nautical_safety_corridor_buffer, OsmAndR.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_OFF_COURSE_ALARM.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_alert)
            summary = "${settings.NAUTICAL_OFF_COURSE_ALARM.get()}°"
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_alert)
            summary = "${settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.get()}%"
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_MOB_AUDIO_GUIDANCE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_volume_up)
            val available = NauticalPlugin.getInstance()?.isAudioHardwareAvailable() == true
            isEnabled = available
            isChecked = settings.NAUTICAL_MOB_AUDIO_GUIDANCE.get() && available
        }

        findPreference<Preference>("nautical_test_alarm")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_volume_up)
            setOnPreferenceClickListener {
                net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app).dispatchAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.MAP_HAZARD, loop = false)
                true
            }
        }

        findPreference<Preference>("nautical_test_tts")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_volume_up)
            setOnPreferenceClickListener {
                net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app).dispatchTts(getString(OsmAndR.string.nautical_test_tts_msg))
                true
            }
        }

        findPreference<Preference>("nautical_compass_wizard")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_compass)
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
                settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.set((newValue as? Boolean) ?: false)
                true
            }
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_length)
            summary = "${settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()} m"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MED_MOORING_SCOPE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_MED_MOORING_SCOPE.get()}:1"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_TACKING_WIND_LIMIT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_wind)
            summary = "${settings.NAUTICAL_TACKING_WIND_LIMIT.get()} kn"
        }
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MOB_AUDIO_INTERVAL.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_volume_up)
            summary = "${settings.NAUTICAL_MOB_AUDIO_INTERVAL.get()} s"
        }
        setupDistancePreference(settings.NAUTICAL_ARRIVAL_RADIUS.id, OsmAndR.string.nautical_arrival_radius, OsmAndR.drawable.ic_action_anchor)
    }

    private fun setupAnchorAdvancedCategory() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_ANCHOR_LOCKED_LOCALLY.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_lock)
        }

        setupDepthPreference(settings.NAUTICAL_ANCHOR_DEPTH.id, OsmAndR.string.nautical_anchor_label_depth, OsmAndR.drawable.ic_action_anchor)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_TIDE_RISE.id, OsmAndR.string.nautical_anchor_label_tide, OsmAndR.drawable.ic_action_additional_option)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_FREEBOARD.id, OsmAndR.string.nautical_anchor_label_freeboard, OsmAndR.drawable.ic_action_altitude)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_BOW_OFFSET.id, OsmAndR.string.nautical_anchor_label_bow_offset, OsmAndR.drawable.ic_action_additional_option)
        setupDepthPreference(settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id, OsmAndR.string.nautical_anchor_label_safety_margin, OsmAndR.drawable.ic_action_additional_option)

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ANCHOR_SCOPE_RATIO.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()}:1"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_ANCHOR_ACCURACY_THRESHOLD.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_ANCHOR_ACCURACY_THRESHOLD.get()} m"
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        }

        findPreference<Preference>("nautical_clear_anchor")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_delete_dark)
            setOnPreferenceClickListener {
                settings.NAUTICAL_ANCHOR_LAT.set(0.0)
                settings.NAUTICAL_ANCHOR_LON.set(0.0)
                NauticalPlugin.getInstance()?.anchorWatchdog?.stop()
                app.showToastMessage(OsmAndR.string.nautical_anchor_cleared)
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

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TRAJECTORY_COLOR.id)?.apply {
            entries = arrayOf("Magenta", "Red", "Green", "Blue", "Yellow", "Cyan", "White", "Black")
            entryValues = arrayOf(
                0xFFFF00FF.toInt().toString(),
                0xFFFF0000.toInt().toString(),
                0xFF00FF00.toInt().toString(),
                0xFF0000FF.toInt().toString(),
                0xFFFFFF00.toInt().toString(),
                0xFF00FFFF.toInt().toString(),
                0xFFFFFFFF.toInt().toString(),
                0xFF000000.toInt().toString()
            )
            val current = settings.NAUTICAL_TRAJECTORY_COLOR.get()
            value = current.toString()
            summary = entries.getOrNull(entryValues.indexOf(value)) ?: "Custom"
            setThemedIcon(OsmAndR.drawable.ic_action_appearance)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_TRAJECTORY_THICKNESS.id)?.apply {
            val values = arrayOf("4", "6", "8", "10", "12", "16", "20")
            entries = values.map { "$it px" }.toTypedArray()
            entryValues = values
            val current = settings.NAUTICAL_TRAJECTORY_THICKNESS.get()
            value = current.toInt().toString()
            summary = "$value px"
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
        }
        
        val overlays = listOf(
            settings.NAUTICAL_SHOW_LAYLINES,
            settings.NAUTICAL_SHOW_INFINITE_LAYLINES,
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
            settings.NAUTICAL_SHOW_SMHI_TILES,
            settings.NAUTICAL_SHOW_NOAA_TILES,
            settings.NAUTICAL_SHOW_LOGBOOK_LAYER,
            settings.NAUTICAL_SHOW_PMTILES
        )
        overlays.forEach { pref ->
            findPreference<SwitchPreferenceEx>(pref.id)?.apply {
                setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
                setOnPreferenceChangeListener { _, newValue ->
                    pref.set((newValue as? Boolean) ?: false)
                    plugin?.requestRefresh()
                    true
                }
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_LOOK_AHEAD_TIME.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            val options = arrayOf("2", "5", "10", "20", "30", "60")
            entries = options.map { "$it ${getString(OsmAndR.string.shared_string_min)}" }.toTypedArray()
            entryValues = options
            val current = settings.NAUTICAL_LOOK_AHEAD_TIME.get()
            value = current.toString()
            summary = "$current ${getString(OsmAndR.string.shared_string_min)}"
        }
    }

    private fun setupDepthPreference(id: String, titleId: Int, iconId: Int) {
        val safetyManager = NauticalPlugin.getInstance()?.safetyManager
        findPreference<EditTextPreferenceEx>(id)?.apply {
            setThemedIcon(iconId)
            
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
                settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.id -> settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.get().toDouble()
                else -> 0.0
            }
            val (v, u) = SignalKUnitConverter.formatValue(requireContext(), settings, meters, "depth")
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
            setThemedIcon(iconId)
            val nm = when (id) {
                settings.NAUTICAL_XTE_THRESHOLD.id -> settings.NAUTICAL_XTE_THRESHOLD.get().toDouble()
                settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.id -> settings.NAUTICAL_LOOK_AHEAD_RADIUS_NM.get().toDouble()
                settings.NAUTICAL_CORRIDOR_WIDTH.id -> settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
                settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id -> settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
                else -> 0.0
            }
            val meters = nm * 1852.0
            val (v, u) = SignalKUnitConverter.formatValue(requireContext(), settings, meters, "distance")
            val baseTitle = getString(titleId).replace(" (NM)", "")
            title = "$baseTitle ($u)"
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
            setThemedIcon(OsmAndR.drawable.ic_action_sail_boat_dark)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SAILING_PERFORMANCE_SETTINGS)
                true
            }
            
            // Integrate SailingPerformanceSettingsViewModel to show active polar
            val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
            if (!connected) {
                summary = getString(OsmAndR.string.nautical_disconnected_performance_msg)
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
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_POLAR_WIZARD)
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LAYLINES_TACK_ANGLE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_LAYLINES_TACK_ANGLE.get()}°"
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LEEWAY_COEFFICIENT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = settings.NAUTICAL_LEEWAY_COEFFICIENT.get().toString()
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
            summary = "${settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.get()}°"
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_PREDICTIVE_STEERING.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_wind)
            isChecked = settings.NAUTICAL_PREDICTIVE_STEERING.get()
            
            // Item 14 & 15: Enhance summary with status warning
            val grib = SailingDependencyContainer.gribRepository?.gridData
            val hasWaves = grib?.timeSteps?.any { it.waveHeightGrid != null } ?: false
            
            if (!hasWaves) {
                summary = "${getString(OsmAndR.string.nautical_predictive_steering_desc)} (${getString(OsmAndR.string.nautical_wave_data_missing)})"
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_SHADOW_DRIVE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_additional_option)
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            entries = arrayOf("Low (20%)", "Medium (50%)", "High (80%)")
            entryValues = arrayOf("20", "50", "80")
            val current = settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.get()
            value = current.toString()
            summary = "$current%"
        }

        setupDepthPreference(settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.id, OsmAndR.string.nautical_wave_nudge_threshold, OsmAndR.drawable.ic_action_additional_option)
    }

    private fun setupAisCategory() {
        val plugin = NauticalPlugin.getInstance() ?: return

        findPreference<ListPreferenceEx>(plugin.aisObjLostTimeout.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            val entryValuesArr = arrayOf(3, 5, 7, 10, 12, 15, 20)
            entries = entryValuesArr.map { "$it ${getString(OsmAndR.string.shared_string_minute_lowercase)}" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisObjLostTimeout.get()
            value = current.toString()
            summary = "$current ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
        }

        findPreference<ListPreferenceEx>(plugin.aisShipLostTimeout.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            val entryValuesArr = arrayOf(2, 3, 4, 5, 7, 10, 15, 100)
            entries = entryValuesArr.mapIndexed { index, i ->
                if (index == (entryValuesArr.size - 1)) getString(OsmAndR.string.shared_string_disabled)
                else "$i ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
            }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisShipLostTimeout.get()
            value = current.toString()
            summary = if (current == 100) getString(OsmAndR.string.shared_string_disabled) else "$current ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
        }

        findPreference<ListPreferenceEx>(plugin.aisCpaWarningTime.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            val entryValuesArr = arrayOf(0, 1, 5, 10, 20, 30, 60)
            entries = entryValuesArr.map { if (it == 0) getString(OsmAndR.string.shared_string_disabled) else "$it ${getString(OsmAndR.string.shared_string_minute_lowercase)}" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisCpaWarningTime.get()
            value = current.toString()
            summary = if (current == 0) getString(OsmAndR.string.shared_string_disabled) else "$current ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
            findPreference<Preference>(plugin.aisCpaWarningDistance.id)?.isEnabled = current != 0
        }

        findPreference<ListPreferenceEx>(plugin.aisCpaWarningDistance.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_anchor)
            val entryValuesArr = arrayOf(0.02f, 0.05f, 0.1f, 0.2f, 0.5f, 1.0f, 2.0f)
            entries = entryValuesArr.map { "$it NM" }.toTypedArray()
            entryValues = entryValuesArr.map { it.toString() }.toTypedArray()
            val current = plugin.aisCpaWarningDistance.get()
            value = current.toString()
            summary = "$current NM"
        }

        findPreference<EditTextPreferenceEx>(plugin.aisOwnMmsi.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_sail_boat_dark)
            summary = plugin.aisOwnMmsi.get().toString()
        }

        findPreference<SwitchPreferenceEx>(plugin.aisDisplayOwnPosition.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_user)
            isChecked = plugin.aisDisplayOwnPosition.get()
            isEnabled = plugin.aisOwnMmsi.get() != 0
        }

        findPreference<Preference>("nautical_ais_buddies")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_group_list)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_AIS_BUDDIES)
                true
            }
        }
    }

    private fun setupVhfCategory() {
        findPreference<Preference>("nautical_vhf_history_view")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_group_list)
            setOnPreferenceClickListener {
                val url = settings.NAUTICAL_VHF_BACKEND_URL.get()
                if (url.isEmpty()) {
                    app.showToastMessage(OsmAndR.string.nautical_vhf_url_missing)
                } else {
                    net.osmand.plus.plugins.nautical.ui.VhfHistoryBottomSheet.show(parentFragmentManager)
                }
                true
            }
        }

        findPreference<Preference>("nautical_switch_panel")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_SWITCH_PANEL)
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_VHF_BACKEND_URL.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_antenna)
            summary = settings.NAUTICAL_VHF_BACKEND_URL.get().ifEmpty { getString(OsmAndR.string.shared_string_none) }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_VHF_AUTO_REPLAY.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_play_dark)
            isChecked = settings.NAUTICAL_VHF_AUTO_REPLAY.get()
        }
    }

    private fun setupNavtexCategory() {
        findPreference<SwitchPreferenceEx>(settings.NAVTEX_ONLY_URGENT.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_alert)
            isChecked = settings.NAVTEX_ONLY_URGENT.get()
        }

        findPreference<EditTextPreferenceEx>(settings.NAVTEX_SUBJECT_FILTER.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_filter)
            summary = settings.NAVTEX_SUBJECT_FILTER.get().ifEmpty { getString(OsmAndR.string.shared_string_none) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAVTEX_MAX_DISTANCE.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_anchor)
            val dist = settings.NAVTEX_MAX_DISTANCE.get()
            summary = if (dist > 0) "$dist km" else getString(OsmAndR.string.shared_string_none)
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
            summary = "${settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.get()} h"
        }
    }

    private fun setupLogbookCategory() {
        findPreference<Preference>("nautical_passage_plan")?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_track_16)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
                true
            }
        }

        findPreference<ListPreferenceEx>(settings.NAUTICAL_LOGBOOK_INTERVAL.id)?.apply {
            setThemedIcon(OsmAndR.drawable.ic_action_time)
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
                 summary = getString(OsmAndR.string.nautical_logbook_sync_msg)
             }
        }
    }

    private fun updateModuleDetailsVisibility() {
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        val isWatch = WearOsNauticalManager(requireContext()).isWatchMode()
        
        findPreference<PreferenceCategory>("ais_details_group")?.isVisible = settings.NAUTICAL_AIS_ENABLED.get() && !isWatch
        findPreference<PreferenceCategory>("vhf_details_group")?.isVisible = settings.NAUTICAL_VHF_ENABLED.get() && !isWatch
        findPreference<PreferenceCategory>("navtex_details_group")?.isVisible = settings.NAUTICAL_NAVTEX_ENABLED.get() && !isWatch
        findPreference<PreferenceCategory>("logbook_details_group")?.isVisible = settings.NAUTICAL_MODULE_LOGBOOK.get() && !isWatch
        findPreference<PreferenceCategory>("grib_details_group")?.isVisible = settings.NAUTICAL_MODULE_GRIB.get() && !isWatch
        
        findPreference<PreferenceCategory>("nautical_autopilot_tuning_category")?.isVisible = caps?.hasAdvancedAutopilot == true && !isWatch
        findPreference<PreferenceCategory>("nautical_energy_category")?.isVisible = caps?.hasEnergyManagement == true && !isWatch
        
        val hasSmart = (caps?.hasNavtex == true || caps?.hasAiBridge == true) && !isWatch
        findPreference<PreferenceCategory>("nautical_smart_category")?.isVisible = hasSmart
        findPreference<Preference>("nautical_boat_ai")?.isVisible = caps?.hasAiBridge == true && !isWatch
        findPreference<Preference>("nautical_notifications")?.isVisible = true
        findPreference<Preference>("nautical_safety_regions")?.isVisible = !isWatch
        findPreference<Preference>("nautical_checklists")?.isVisible = caps?.hasChecklists == true && !isWatch
        
        // Maneuver section logic - hide if not in BOAT mode (safety)
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(net.osmand.plus.settings.backend.ApplicationMode.BOAT)
        findPreference<PreferenceCategory>("nautical_maneuver_category")?.isVisible = isBoat && !isWatch
        
        // Hide advanced maintenance on watch
        findPreference<Preference>("nautical_advanced_tuning")?.isVisible = !isWatch
        findPreference<Preference>("nautical_diagnostics")?.isVisible = !isWatch
        findPreference<Preference>("nautical_master_telemetry_setup")?.isVisible = !isWatch
        findPreference<Preference>("nautical_discovery_mdns")?.isVisible = !isWatch
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

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        when (key) {
            "nautical_diagnostics" -> {
                showInstance(requireActivity(), SettingsScreenType.SIGNALK_DIAGNOSTICS)
                return true
            }
            "nautical_advanced_tuning" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_ADVANCED_SETTINGS)
                return true
            }
            "nautical_boat_ai" -> {
                showInstance(requireActivity(), SettingsScreenType.BOAT_AI)
                return true
            }
            "nautical_checklists" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_CHECKLISTS)
                return true
            }
            "nautical_notifications" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_NOTIFICATIONS)
                return true
            }
            "nautical_safety_regions" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_SAFETY_REGIONS)
                return true
            }
            "nautical_replay_manager" -> {
                net.osmand.plus.plugins.nautical.replay.NmeaPlaybackControlBottomSheet.show(requireActivity().supportFragmentManager)
                return true
            }
            "nautical_hardware_health" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_HARDWARE_STATS)
                return true
            }
            "nautical_master_telemetry_setup" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_MASTER_TELEMETRY)
                return true
            }
            "nautical_clear_data" -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(OsmAndR.string.nautical_clear_marine_data)
                    .setMessage(OsmAndR.string.nautical_clear_data_confirm)
                    .setPositiveButton(OsmAndR.string.shared_string_delete) { _, _ ->
                        NauticalPlugin.getInstance()?.clearMarineData()
                    }
                    .setNegativeButton(OsmAndR.string.shared_string_cancel, null)
                    .show()
                return true
            }
            "marine_raster_manager" -> {
                showInstance(requireActivity(), SettingsScreenType.MARINE_RASTER_MANAGER)
                return true
            }
            "s63_permit_manager" -> {
                showInstance(requireActivity(), SettingsScreenType.S63_PERMIT_MANAGER)
                return true
            }
            "nautical_enc_manager" -> {
                showInstance(requireActivity(), SettingsScreenType.ENC_CHART_MANAGER)
                return true
            }
            "nautical_tide_manager" -> {
                showInstance(requireActivity(), SettingsScreenType.TIDE_DATA_MANAGER)
                return true
            }
            "nautical_grib_manager" -> {
                net.osmand.plus.plugins.nautical.grib.ui.GribManagerBottomSheet.show(requireActivity().supportFragmentManager)
                return true
            }
            "nautical_server_routes" -> {
                showInstance(requireActivity(), SettingsScreenType.SIGNALK_SERVER_ROUTES)
                return true
            }
            "nautical_server_charts" -> {
                showInstance(requireActivity(), SettingsScreenType.SIGNALK_SERVER_CHARTS)
                return true
            }
            "nautical_discovery_mdns" -> {
                showDiscoveryDialog()
                return true
            }
            "nautical_test_alarm" -> {
                net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app).dispatchAlarm(net.osmand.plus.plugins.nautical.audio.AlarmType.MAP_HAZARD, loop = false)
                return true
            }
            "nautical_test_tts" -> {
                net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter.getInstance(app).dispatchTts(getString(OsmAndR.string.nautical_test_tts_msg))
                return true
            }
            "nautical_compass_wizard" -> {
                net.osmand.plus.views.mapwidgets.widgets.NauticalCompassWizardDialog.show(this)
                return true
            }
            "nautical_clear_anchor" -> {
                settings.NAUTICAL_ANCHOR_LAT.set(0.0)
                settings.NAUTICAL_ANCHOR_LON.set(0.0)
                NauticalPlugin.getInstance()?.anchorWatchdog?.stop()
                app.showToastMessage(OsmAndR.string.nautical_anchor_cleared)
                return true
            }
            "nautical_vhf_history_view" -> {
                val url = settings.NAUTICAL_VHF_BACKEND_URL.get()
                if (url.isEmpty()) {
                    app.showToastMessage(OsmAndR.string.nautical_vhf_url_missing)
                } else {
                    net.osmand.plus.plugins.nautical.ui.VhfHistoryBottomSheet.show(requireActivity().supportFragmentManager)
                }
                return true
            }
            "nautical_switch_panel" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_SWITCH_PANEL)
                return true
            }
            "nautical_passage_plan" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
                return true
            }
            settings.NAUTICAL_ACTIVE_SAIL_PLAN.id -> {
                showInstance(requireActivity(), SettingsScreenType.SAIL_INVENTORY)
                return true
            }
            "sailing_performance" -> {
                showInstance(requireActivity(), SettingsScreenType.SAILING_PERFORMANCE_SETTINGS)
                return true
            }
            "nautical_polar_wizard" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_POLAR_WIZARD)
                return true
            }
            "nautical_ais_buddies" -> {
                showInstance(requireActivity(), SettingsScreenType.NAUTICAL_AIS_BUDDIES)
                return true
            }
        }
        return super.onPreferenceClick(preference)
    }

    override fun onPreferenceChanged(prefId: String) {
        val plugin = NauticalPlugin.getInstance()
        when (prefId) {
            settings.NAUTICAL_DISPLAY_MODE.id -> {
                val mode = settings.NAUTICAL_DISPLAY_MODE.get()
                val mapActivity = (activity as? net.osmand.plus.activities.MapActivity) ?: app.osmandMap?.mapView?.mapActivity
                mapActivity?.let { plugin?.applyDisplayMode(it, mode) }
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
                        NauticalDisplayMode.DARK -> getString(OsmAndR.string.nautical_display_mode_dark)
                        NauticalDisplayMode.SUNLIGHT -> getString(OsmAndR.string.nautical_display_mode_sunlight)
                        else -> getString(OsmAndR.string.nautical_display_mode_normal)
                    }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_SERVER_IP.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.nautical_server_ip_desc) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_SERVER_PORT.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.nautical_server_port_desc) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_SERVER_USERNAME.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.nautical_server_username_desc) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_SERVER_PASSWORD.id -> {
                    preference.summary = if (newString.isEmpty()) getString(OsmAndR.string.nautical_server_password_desc) else getString(OsmAndR.string.nautical_password_mask)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.id -> {
                    preference.summary = if (newString.isEmpty()) getString(OsmAndR.string.shared_string_none) else getString(OsmAndR.string.nautical_password_mask)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_USE_SECURE_CONNECTION.id -> {
                    updateSecureSettingsVisibility(newValue as Boolean)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id -> {
                    val isEnabled = newString.toBoolean()
                    preference.summary = if (isEnabled) getString(OsmAndR.string.shared_string_yes) else getString(OsmAndR.string.shared_string_no)
                    onPreferenceChanged(key)
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
                    val (v, u) = SignalKUnitConverter.formatValue(requireContext(), settings, meters, "distance")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = v
                    onPreferenceChanged(key)
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
                settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.id,
                settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.id -> {
                    val floatValue = newString.toFloatOrNull()
                    if (floatValue == null || floatValue < 0) {
                        app.showToastMessage(OsmAndR.string.shared_string_invalid_value)
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
                        settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.id -> settings.NAUTICAL_WAVE_NUDGE_THRESHOLD.set(meters.toFloat())
                    }
                    val (v, u) = SignalKUnitConverter.formatValue(requireContext(), settings, meters, "depth")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = String.format(Locale.US, "%.2f", floatValue)
                    onPreferenceChanged(key)
                    return false
                }

                settings.NAUTICAL_WIND_ALIGNMENT.id -> {
                    preference.summary = "${newString}°"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_LAYLINES_TACK_ANGLE.id -> {
                    preference.summary = "${newString}°"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_LEEWAY_COEFFICIENT.id -> {
                    preference.summary = newString
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.id -> {
                    preference.summary = "${newString}°"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_OFF_COURSE_ALARM.id -> {
                    preference.summary = "${newString}°"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_ACTUATOR_ALARM_THRESHOLD.id -> {
                    preference.summary = "$newString%"
                    onPreferenceChanged(key)
                }
                
                settings.NAUTICAL_RUDDER_GAIN.id,
                settings.NAUTICAL_COUNTER_RUDDER.id,
                settings.NAUTICAL_AUTO_TRIM.id,
                settings.NAUTICAL_FILTER_SENSITIVITY.id,
                settings.NAUTICAL_RUDDER_LIMIT.id -> {
                    preference.summary = newString
                    onPreferenceChanged(key)
                }

                settings.NAUTICAL_ANCHOR_SCOPE_RATIO.id -> {
                    preference.summary = "$newString:1"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_STW_REL_DELAY_SEC.id -> {
                    preference.summary = "$newString s"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC.id -> {
                    preference.summary = "$newString s"
                    onPreferenceChanged(key)
                }

                settings.NAUTICAL_VESSEL_TYPE.id -> {
                    preference.summary = when (newString) {
                        "PROA" -> getString(OsmAndR.string.nautical_vessel_proa)
                        else -> getString(OsmAndR.string.nautical_vessel_conventional)
                    }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_NMEA_SOURCE.id -> {
                    val source = net.osmand.plus.settings.enums.NmeaSource.valueOf(newString)
                    updateHardwareVisibility(source)
                    preference.summary = getString(source.titleId)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_BT_DEVICE_ADDRESS.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.shared_string_none) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_USB_DEVICE_NAME.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.shared_string_none) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_NMEA_BAUD_RATE.id -> {
                    preference.summary = newString
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_TELEMETRY_REFRESH_RATE.id -> {
                    val rate = newString.toIntOrNull() ?: 1
                    preference.summary = when (rate) {
                        1 -> getString(OsmAndR.string.nautical_refresh_rate_1s)
                        2 -> getString(OsmAndR.string.nautical_refresh_rate_2s)
                        5 -> getString(OsmAndR.string.nautical_refresh_rate_5s)
                        else -> getString(OsmAndR.string.nautical_refresh_rate_1s)
                    }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_LOOK_AHEAD_TIME.id -> {
                    preference.summary = "$newString ${getString(OsmAndR.string.shared_string_min)}"
                    onPreferenceChanged(key)
                }

                settings.NAUTICAL_TRAJECTORY_COLOR.id -> {
                    val listPref = preference as? ListPreferenceEx
                    preference.summary = listPref?.entries?.getOrNull(listPref.entryValues.indexOf(newString)) ?: "Custom"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_TRAJECTORY_THICKNESS.id -> {
                    preference.summary = "$newString px"
                    onPreferenceChanged(key)
                }

                plugin?.aisObjLostTimeout?.id -> {
                    preference.summary = "$newString ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
                    onPreferenceChanged(key)
                }
                plugin?.aisShipLostTimeout?.id -> {
                    preference.summary = if (newString == "100") getString(OsmAndR.string.shared_string_disabled) else "$newString ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
                    onPreferenceChanged(key)
                }
                plugin?.aisCpaWarningTime?.id -> {
                    val time = newString.toIntOrNull() ?: 0
                    preference.summary = if (time == 0) getString(OsmAndR.string.shared_string_disabled) else "$newString ${getString(OsmAndR.string.shared_string_minute_lowercase)}"
                    findPreference<Preference>(plugin.aisCpaWarningDistance.id)?.isEnabled = time != 0
                    onPreferenceChanged(key)
                }
                plugin?.aisCpaWarningDistance?.id -> {
                    preference.summary = "$newString NM"
                    onPreferenceChanged(key)
                }
                plugin?.aisOwnMmsi?.id -> {
                    val mmsi = newString.toIntOrNull() ?: 0
                    preference.summary = mmsi.toString()
                    findPreference<Preference>(plugin.aisDisplayOwnPosition.id)?.isEnabled = mmsi != 0
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_VHF_BACKEND_URL.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.shared_string_none) }
                    plugin?.vhfManager?.start()
                    onPreferenceChanged(key)
                }
                settings.NAVTEX_SUBJECT_FILTER.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.shared_string_none) }
                    onPreferenceChanged(key)
                }
                settings.NAVTEX_MAX_DISTANCE.id -> {
                    val dist = newString.toFloatOrNull() ?: 0f
                    preference.summary = if (dist > 0) "$newString km" else getString(OsmAndR.string.shared_string_none)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.id -> {
                    preference.summary = "$newString h"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_LOGBOOK_INTERVAL.id -> {
                    val interval = newString.toIntOrNull() ?: 0
                    preference.summary = when (interval) {
                        0 -> "Immediate (Event-based)"
                        else -> "$newString min"
                    }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_HEADING_REFERENCE.id -> {
                    preference.summary = if (newString == "TRUE") getString(OsmAndR.string.nautical_heading_reference_true) else getString(OsmAndR.string.nautical_heading_reference_mag)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_TTW_MODE.id -> {
                    preference.summary = if (newString == "SOG") getString(OsmAndR.string.nautical_ttw_mode_sog) else getString(OsmAndR.string.nautical_ttw_mode_vmg)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_VESSEL_CONTEXT.id -> {
                    val ctx = VesselContext.valueOf(newString)
                    preference.summary = getString(ctx.titleId)
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.id -> {
                    preference.summary = "$newString m"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_MED_MOORING_SCOPE.id -> {
                    preference.summary = "$newString:1"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_TACKING_WIND_LIMIT.id -> {
                    preference.summary = "$newString kn"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_MOB_AUDIO_INTERVAL.id -> {
                    preference.summary = "$newString s"
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_ARRIVAL_RADIUS.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 35.0f
                    val meters = floatValue.toDouble()
                    settings.NAUTICAL_ARRIVAL_RADIUS.set(meters.toFloat())
                    val (v, u) = SignalKUnitConverter.formatValue(requireContext(), settings, meters, "distance")
                    preference.summary = "$v $u"
                    (preference as? EditTextPreferenceEx)?.text = v
                    onPreferenceChanged(key)
                    return false
                }
                settings.NAUTICAL_PILOT_SEA_STATE.id -> {
                    preference.summary = newString
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_ACTIVE_SAIL_PLAN.id -> {
                    preference.summary = newString.ifEmpty { getString(OsmAndR.string.shared_string_none) }
                    onPreferenceChanged(key)
                }
                settings.NAUTICAL_WAVE_BIAS_SENSITIVITY.id -> {
                    preference.summary = "$newString%"
                    onPreferenceChanged(key)
                }
                
                // Toggle-only items with no summary logic
                settings.NAUTICAL_AIS_ENABLED.id,
                settings.NAUTICAL_MODULE_TIDES.id,
                settings.NAUTICAL_MODULE_GRIB.id,
                settings.NAUTICAL_VHF_ENABLED.id,
                settings.NAUTICAL_MODULE_LOGBOOK.id,
                settings.NAUTICAL_MODULE_ENC.id,
                settings.NAUTICAL_MODULE_RASTER.id,
                settings.NAUTICAL_NAVTEX_ENABLED.id,
                settings.NAUTICAL_HEAVY_WEATHER_MODE.id,
                settings.NAUTICAL_MULTIHULL_SHUNTING.id,
                settings.NAUTICAL_PREDICTIVE_STEERING.id,
                settings.NAUTICAL_SHADOW_DRIVE.id,
                settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id,
                settings.NAUTICAL_FORCE_WATCH_LAYOUT.id,
                settings.NAUTICAL_MOB_AUDIO_GUIDANCE.id,
                settings.NAUTICAL_LOCK_TOUCH_DURING_MANEUVERS.id,
                settings.NAUTICAL_SHOW_LAYLINES.id,
                settings.NAUTICAL_SHOW_INFINITE_LAYLINES.id,
                settings.NAUTICAL_SHOW_WIND_SHIFTS.id,
                settings.NAUTICAL_SHOW_TRAJECTORY.id,
                settings.NAUTICAL_SHOW_TIDES.id,
                settings.NAUTICAL_SHOW_HEADING_LINE.id,
                settings.NAUTICAL_SHOW_COG_LINE.id,
                settings.NAUTICAL_SHOW_CMG_LINE.id,
                settings.NAUTICAL_SHOW_CURRENT_VECTOR.id,
                settings.NAUTICAL_RESTRICTED_AREAS_ENABLED.id,
                settings.NAUTICAL_SHOW_WINDY_TILES.id,
                settings.NAUTICAL_SHOW_RAIN_RADAR.id,
                settings.NAUTICAL_SHOW_OPENMETEO_TILES.id,
                settings.NAUTICAL_SHOW_SMHI_TILES.id,
                settings.NAUTICAL_SHOW_NOAA_TILES.id,
                settings.NAUTICAL_SHOW_PMTILES.id,
                settings.NAUTICAL_SHOW_LOGBOOK_LAYER.id,
                settings.NAUTICAL_GRIB_SOURCE_SIGNALK.id -> {
                    onPreferenceChanged(key)
                }
            }
        }
        return changed
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Activity decorView filter handles scotopic rendering
    }
}
