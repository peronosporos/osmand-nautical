package net.osmand.plus.plugins.nautical

import androidx.preference.Preference
import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import net.osmand.plus.settings.preferences.ListPreferenceEx
import net.osmand.plus.settings.preferences.SwitchPreferenceEx

class NauticalSettingsFragment : BaseSettingsFragment() {

    override fun setupPreferences() {
        setupIpAddress()
        setupPort()
        setupSecureConnection()
        setupUsername()
        setupPassword()
        setupTrustAll()
        setupXteThreshold()
        setupLaylinesTackAngle()
        setupProjectionLines()
        setupLookAheadTime()
        setupReceiveInBackground()

        updateSecureSettingsVisibility(settings.NAUTICAL_USE_SECURE_CONNECTION.get())
    }

    private fun setupIpAddress() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_IP.id)?.apply {
            setIcon(R.drawable.ic_action_world_globe)
            description = getString(R.string.nautical_server_ip_desc)
            summary = settings.NAUTICAL_SERVER_IP.get().ifEmpty { getString(R.string.nautical_server_ip_desc) }
        }
    }

    private fun setupPort() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PORT.id)?.apply {
            setIcon(R.drawable.ic_action_settings)
            description = getString(R.string.nautical_server_port_desc)
            summary = settings.NAUTICAL_SERVER_PORT.get().ifEmpty { getString(R.string.nautical_server_port_desc) }
        }
    }

    private fun setupUsername() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_USERNAME.id)?.apply {
            setIcon(R.drawable.ic_action_user)
            description = getString(R.string.nautical_server_username_desc)
            summary = settings.NAUTICAL_SERVER_USERNAME.get().ifEmpty { getString(R.string.nautical_server_username_desc) }
        }
    }

    private fun setupPassword() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_SERVER_PASSWORD.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_password_desc)
            summary = if (settings.NAUTICAL_SERVER_PASSWORD.get().isEmpty()) {
                getString(R.string.nautical_server_password_desc)
            } else {
                getString(R.string.nautical_password_mask)
            }
        }
    }

    private fun setupSecureConnection() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_USE_SECURE_CONNECTION.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_secure_desc)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }
    }

    private fun setupTrustAll() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            description = getString(R.string.nautical_server_trust_all_desc)
            isChecked = settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        }
    }

    private fun setupXteThreshold() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_XTE_THRESHOLD.id)?.apply {
            setIcon(R.drawable.ic_action_anchor)
            description = getString(R.string.nautical_xte_threshold_desc)
            summary = "${settings.NAUTICAL_XTE_THRESHOLD.get()} ${getString(R.string.nautical_unit_nm)}"
        }
    }

    private fun setupLaylinesTackAngle() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LAYLINES_TACK_ANGLE.id)?.apply {
            setIcon(R.drawable.ic_action_additional_option)
            description = getString(R.string.nautical_laylines_tack_angle_desc)
            summary = "${settings.NAUTICAL_LAYLINES_TACK_ANGLE.get()}${getString(R.string.nautical_unit_deg)}"
        }
    }

    private fun setupProjectionLines() {
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_SHOW_HEADING_LINE.id)?.apply {
            setIcon(R.drawable.ic_action_direction_compass)
            isChecked = settings.NAUTICAL_SHOW_HEADING_LINE.get()
        }
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_SHOW_COG_LINE.id)?.apply {
            setIcon(R.drawable.ic_action_direction_movement)
            isChecked = settings.NAUTICAL_SHOW_COG_LINE.get()
        }
        findPreference<SwitchPreferenceEx>(settings.NAUTICAL_SHOW_CURRENT_VECTOR.id)?.apply {
            setIcon(R.drawable.ic_action_bearing)
            isChecked = settings.NAUTICAL_SHOW_CURRENT_VECTOR.get()
        }
    }

    private fun setupLookAheadTime() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_LOOK_AHEAD_TIME.id)?.apply {
            setIcon(R.drawable.ic_action_time)
            summary = "${settings.NAUTICAL_LOOK_AHEAD_TIME.get()} ${getString(R.string.shared_string_min)}"
        }
    }

    private fun setupReceiveInBackground() {
        findPreference<ListPreferenceEx>(settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id)?.apply {
            setIcon(R.drawable.ic_action_play_dark)
            entries = arrayOf(getString(R.string.shared_string_yes), getString(R.string.shared_string_no))
            entryValues = arrayOf(true.toString(), false.toString())
            val isEnabled = settings.NAUTICAL_RECEIVE_IN_BACKGROUND.get()
            value = isEnabled.toString()
            summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
            setDescription(R.string.nautical_receive_in_background_description)
        }
    }

    private fun updateSecureSettingsVisibility(useSecure: Boolean) {
        findPreference<Preference>(settings.NAUTICAL_SERVER_USERNAME.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_SERVER_PASSWORD.id)?.isVisible = useSecure
        findPreference<Preference>(settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id)?.isVisible = useSecure
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val changed = super.onPreferenceChange(preference, newValue)
        if (changed) {
            val key = preference.key
            val newString = newValue?.toString() ?: ""
            when (key) {
                settings.NAUTICAL_SERVER_IP.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_ip_desc) }
                settings.NAUTICAL_SERVER_PORT.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_port_desc) }
                settings.NAUTICAL_SERVER_USERNAME.id -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_username_desc) }
                settings.NAUTICAL_SERVER_PASSWORD.id -> preference.summary = if (newString.isEmpty()) getString(R.string.nautical_server_password_desc) else getString(R.string.nautical_password_mask)
                settings.NAUTICAL_USE_SECURE_CONNECTION.id -> {
                    val useSecure = newValue as Boolean
                    settings.NAUTICAL_USE_SECURE_CONNECTION.set(useSecure)
                    updateSecureSettingsVisibility(useSecure)
                }
                settings.NAUTICAL_TRUST_ALL_CERTIFICATES.id -> settings.NAUTICAL_TRUST_ALL_CERTIFICATES.set(newValue as Boolean)
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.id -> {
                    val isEnabled = newString.toBoolean()
                    settings.NAUTICAL_RECEIVE_IN_BACKGROUND.set(isEnabled)
                    preference.summary = if (isEnabled) getString(R.string.shared_string_yes) else getString(R.string.shared_string_no)
                }
                settings.NAUTICAL_XTE_THRESHOLD.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 0.1f
                    settings.NAUTICAL_XTE_THRESHOLD.set(floatValue)
                    preference.summary = "$floatValue ${getString(R.string.nautical_unit_nm)}"
                }
                settings.NAUTICAL_LAYLINES_TACK_ANGLE.id -> {
                    val floatValue = newString.toFloatOrNull() ?: 45.0f
                    settings.NAUTICAL_LAYLINES_TACK_ANGLE.set(floatValue)
                    preference.summary = "$floatValue${getString(R.string.nautical_unit_deg)}"
                }
                settings.NAUTICAL_SHOW_HEADING_LINE.id -> settings.NAUTICAL_SHOW_HEADING_LINE.set(newValue as Boolean)
                settings.NAUTICAL_SHOW_COG_LINE.id -> settings.NAUTICAL_SHOW_COG_LINE.set(newValue as Boolean)
                settings.NAUTICAL_SHOW_CURRENT_VECTOR.id -> settings.NAUTICAL_SHOW_CURRENT_VECTOR.set(newValue as Boolean)
                settings.NAUTICAL_LOOK_AHEAD_TIME.id -> {
                    val intValue = newString.toIntOrNull() ?: 10
                    settings.NAUTICAL_LOOK_AHEAD_TIME.set(intValue)
                    preference.summary = "$intValue ${getString(R.string.shared_string_min)}"
                }
            }
        }
        return changed
    }
}
