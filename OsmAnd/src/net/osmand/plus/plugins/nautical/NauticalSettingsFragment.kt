package net.osmand.plus.plugins.nautical

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.OnPreferenceChanged
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import net.osmand.plus.settings.preferences.ListPreferenceEx
import net.osmand.plus.settings.preferences.SwitchPreferenceEx

class NauticalSettingsFragment : BaseSettingsFragment(), OnPreferenceChanged {

    override fun setupPreferences() {
        setupConnectionCategory()
        setupSafetyCategory()
        setupDisplayCategory()
        setupTelemetryCategory()
        setupHardwareCategory()

        updateSecureSettingsVisibility(settings.NAUTICAL_USE_SECURE_CONNECTION.get())
    }

    private fun setupConnectionCategory() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_IP.id)?.apply {
            setIcon(R.drawable.ic_action_world_globe)
            description = getString(R.string.nautical_server_ip_desc)
            summary = settings.NAUTICAL_SERVER_IP.get().ifEmpty { getString(R.string.nautical_server_ip_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PORT.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            description = getString(R.string.nautical_server_port_desc)
            summary = settings.NAUTICAL_SERVER_PORT.get().ifEmpty { getString(R.string.nautical_server_port_desc) }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_USE_SECURE_CONNECTION.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_secure_desc)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_USERNAME.id)?.apply {
            setIcon(R.drawable.ic_action_user)
            description = getString(R.string.nautical_server_username_desc)
            summary = settings.NAUTICAL_SERVER_USERNAME.get().ifEmpty { getString(R.string.nautical_server_username_desc) }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PASSWORD.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_password_desc)
            summary = if (settings.NAUTICAL_SERVER_PASSWORD.get().isEmpty()) {
                getString(R.string.nautical_server_password_desc)
            } else {
                getString(R.string.nautical_password_mask)
            }
        }

        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            description = getString(R.string.nautical_server_trust_all_desc)
            isChecked = settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        }
    }

    private fun setupSafetyCategory() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_VESSEL_DRAFT.id)?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            summary = getString(R.string.nautical_format_meters, settings.NAUTICAL_VESSEL_DRAFT.get().toString())
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SAFETY_MARGIN.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            summary = getString(R.string.nautical_format_meters, settings.NAUTICAL_SAFETY_MARGIN.get().toString())
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_XTE_THRESHOLD.id)?.apply {
            setIcon(R.drawable.ic_action_anchor)
            description = getString(R.string.nautical_xte_threshold_desc)
            summary = getString(R.string.nautical_format_nm, settings.NAUTICAL_XTE_THRESHOLD.get().toString())
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_CORRIDOR_WIDTH.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            summary = getString(R.string.nautical_format_nm, settings.NAUTICAL_CORRIDOR_WIDTH.get().toString())
        }
    }

    private fun setupDisplayCategory() {
        findPreference<SwitchPreferenceEx>("nautical_night_vision_enabled")?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            isChecked = settings.NAUTICAL_NIGHT_VISION_ENABLED.get()
            setOnPreferenceChangeListener { _, newValue ->
                val enable = newValue as Boolean
                val plugin = NauticalPlugin.getInstance()
                plugin?.let {
                    it.toggleNightVision(requireActivity() as net.osmand.plus.activities.MapActivity, enable)
                }
                true
            }
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LOOK_AHEAD_TIME.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            description = getString(R.string.nautical_look_ahead_time_desc)
            summary = "${settings.NAUTICAL_LOOK_AHEAD_TIME.get()} ${getString(R.string.shared_string_min)}"
        }

        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LAYLINES_TACK_ANGLE.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            description = getString(R.string.nautical_laylines_tack_angle_desc)
            summary = getString(R.string.nautical_format_deg, settings.NAUTICAL_LAYLINES_TACK_ANGLE.get().toString())
        }
    }

    private fun setupTelemetryCategory() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id)?.apply {
            setIcon(R.drawable.ic_action_play_dark)
            entries = arrayOf(getString(R.string.shared_string_yes), getString(R.string.shared_string_no))
            entryValues = arrayOf(true.toString(), false.toString())
            val isEnabled = settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
            value = isEnabled.toString()
            summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
            setDescription(R.string.nautical_receive_in_background_description)
        }

        findPreference<Preference>("sailing_performance")?.apply {
            setIcon(R.drawable.ic_action_sail_boat_dark)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.SAILING_PERFORMANCE_SETTINGS)
                true
            }
        }
    }

    private fun setupHardwareCategory() {
        findPreference<Preference>("marine_raster_manager")?.apply {
            setIcon(R.drawable.ic_action_world_globe)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.MARINE_RASTER_MANAGER)
                true
            }
        }

        findPreference<Preference>("s63_permit_manager")?.apply {
            setIcon(R.drawable.ic_action_lock)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.S63_PERMIT_MANAGER)
                true
            }
        }
    }

    private fun updateSecureSettingsVisibility(useSecure: Boolean) {
        findPreference<Preference>(settings.NAUTICAL_SERVER_USERNAME.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_SERVER_PASSWORD.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.isVisible = useSecure
    }

    override fun onPreferenceChanged(prefId: String) {
        val plugin = NauticalPlugin.getInstance()
        if (prefId == settings.NAUTICAL_USE_SECURE_CONNECTION.id) {
            updateSecureSettingsVisibility(settings.NAUTICAL_USE_SECURE_CONNECTION.get())
            plugin?.reconnect()
        } else if ((prefId == settings.NAUTICAL_SERVER_IP.id) ||
            (prefId == settings.NAUTICAL_SERVER_PORT.id) ||
            (prefId == settings.NAUTICAL_SERVER_USERNAME.id) ||
            (prefId == settings.NAUTICAL_SERVER_PASSWORD.id) ||
            (prefId == settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)
        ) {
            plugin?.reconnect()
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val changed = super.onPreferenceChange(preference, newValue)
        if (changed) {
            val key = preference.key
            val newString = newValue?.toString() ?: ""
            when (key) {
                settings.NAUTICAL_SERVER_IP.id -> {
                    if (newString.isEmpty()) {
                        app.showToastMessage(R.string.nautical_invalid_ip)
                        return false
                    }
                    preference.summary = newString
                }
                settings.NAUTICAL_SERVER_PORT.id -> {
                    val port = newString.toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        app.showToastMessage(R.string.nautical_invalid_port)
                        return false
                    }
                    preference.summary = newString
                }
                settings.NAUTICAL_SERVER_USERNAME.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_username_desc) }
                settings.NAUTICAL_SERVER_PASSWORD.id -> preference.summary = if (newString.isEmpty()) getString(R.string.nautical_server_password_desc) else getString(R.string.nautical_password_mask)
                settings.NAUTICAL_USE_SECURE_CONNECTION.id -> {
                    updateSecureSettingsVisibility(newValue as Boolean)
                }
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id -> {
                    val isEnabled = newString.toBoolean()
                    preference.summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
                }
                settings.NAUTICAL_XTE_THRESHOLD.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 0.1f
                    preference.summary = getString(R.string.nautical_format_nm, floatValue.toString())
                }
                settings.NAUTICAL_VESSEL_DRAFT.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 2.0f
                    preference.summary = getString(R.string.nautical_format_meters, floatValue.toString())
                }
                settings.NAUTICAL_SAFETY_MARGIN.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 1.0f
                    preference.summary = getString(R.string.nautical_format_meters, floatValue.toString())
                }
                settings.NAUTICAL_CORRIDOR_WIDTH.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 0.5f
                    preference.summary = getString(R.string.nautical_format_nm, floatValue.toString())
                }
                settings.NAUTICAL_LAYLINES_TACK_ANGLE.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 45.0f
                    preference.summary = getString(R.string.nautical_format_deg, floatValue.toString())
                }
                settings.NAUTICAL_LOOK_AHEAD_TIME.id -> {
                    val intValue = newString.toIntOrNull()
                    if (intValue == null || intValue !in 1..60) {
                        app.showToastMessage(R.string.nautical_invalid_look_ahead)
                        return false
                    }
                    preference.summary = "$intValue ${getString(R.string.shared_string_min)}"
                }
            }
        }
        return changed
    }
}
