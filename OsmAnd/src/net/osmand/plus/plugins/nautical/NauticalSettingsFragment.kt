package net.osmand.plus.plugins.nautical

import androidx.preference.Preference
import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import net.osmand.plus.settings.preferences.ListPreferenceEx

class NauticalSettingsFragment : BaseSettingsFragment() {

    override fun setupPreferences() {
        setupIpAddress()
        setupPort()
        setupUsername()
        setupPassword()
        setupSecureConnection()
        setupOverlayToggles()
        setupXteThreshold()
        setupReceiveInBackground()
    }

    private fun setupOverlayToggles() {
        findPreference<net.osmand.plus.settings.preferences.SwitchPreferenceEx>("nautical_show_laylines")?.apply {
            description = getString(R.string.nautical_show_laylines)
            isChecked = settings.NAUTICAL_SHOW_LAYLINES.get()
        }
        findPreference<net.osmand.plus.settings.preferences.SwitchPreferenceEx>("nautical_show_wind_shifts")?.apply {
            description = getString(R.string.nautical_show_wind_shifts)
            isChecked = settings.NAUTICAL_SHOW_WIND_SHIFTS.get()
        }
        findPreference<net.osmand.plus.settings.preferences.SwitchPreferenceEx>("nautical_show_trajectory")?.apply {
            description = getString(R.string.nautical_show_trajectory)
            isChecked = settings.NAUTICAL_SHOW_TRAJECTORY.get()
        }
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
        findPreference<net.osmand.plus.settings.preferences.SwitchPreferenceEx>(settings.NAUTICAL_USE_SECURE_CONNECTION.id)?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_secure_desc)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }
    }

    private fun setupXteThreshold() {
        findPreference<EditTextPreferenceEx>(settings.NAUTICAL_XTE_THRESHOLD.id)?.apply {
            setIcon(R.drawable.ic_action_anchor)
            description = getString(R.string.nautical_xte_threshold_desc)
            summary = "${settings.NAUTICAL_XTE_THRESHOLD.get()} ${getString(R.string.nautical_unit_nm)}"
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
                settings.NAUTICAL_USE_SECURE_CONNECTION.id -> settings.NAUTICAL_USE_SECURE_CONNECTION.set(newValue as Boolean)
                settings.NAUTICAL_SHOW_LAYLINES.id -> settings.NAUTICAL_SHOW_LAYLINES.set(newValue as Boolean)
                settings.NAUTICAL_SHOW_WIND_SHIFTS.id -> settings.NAUTICAL_SHOW_WIND_SHIFTS.set(newValue as Boolean)
                settings.NAUTICAL_SHOW_TRAJECTORY.id -> settings.NAUTICAL_SHOW_TRAJECTORY.set(newValue as Boolean)
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
            }
        }
        return changed
    }
}
