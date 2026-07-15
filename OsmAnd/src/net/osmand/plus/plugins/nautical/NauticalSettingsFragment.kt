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
        findPreference<EditTextPreferenceEx>("server_ip")?.apply {
            setIcon(R.drawable.ic_action_world_globe)
            description = getString(R.string.nautical_server_ip_desc)
            summary = settings.NAUTICAL_SERVER_IP.get().ifEmpty { getString(R.string.nautical_server_ip_desc) }
        }
    }

    private fun setupPort() {
        findPreference<EditTextPreferenceEx>("server_port")?.apply {
            setIcon(R.drawable.ic_action_settings)
            description = getString(R.string.nautical_server_port_desc)
            summary = settings.NAUTICAL_SERVER_PORT.get().ifEmpty { getString(R.string.nautical_server_port_desc) }
        }
    }

    private fun setupUsername() {
        findPreference<EditTextPreferenceEx>("server_username")?.apply {
            setIcon(R.drawable.ic_action_user)
            description = getString(R.string.nautical_server_username_desc)
            summary = settings.NAUTICAL_SERVER_USERNAME.get().ifEmpty { getString(R.string.nautical_server_username_desc) }
        }
    }

    private fun setupPassword() {
        findPreference<EditTextPreferenceEx>("server_password")?.apply {
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
        findPreference<net.osmand.plus.settings.preferences.SwitchPreferenceEx>("server_secure")?.apply {
            setIcon(R.drawable.ic_action_lock)
            description = getString(R.string.nautical_server_secure_desc)
            isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        }
    }

    private fun setupXteThreshold() {
        findPreference<EditTextPreferenceEx>("nautical_xte_threshold")?.apply {
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
            setDescription(R.string.nautical_receive_in_background_description)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val changed = super.onPreferenceChange(preference, newValue)
        if (changed) {
            val key = preference.key
            val newString = newValue?.toString() ?: ""
            when (key) {
                "server_ip" -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_ip_desc) }
                "server_port" -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_port_desc) }
                "server_username" -> preference.summary = newString.ifEmpty { getString(R.string.nautical_server_username_desc) }
                "server_password" -> preference.summary = if (newString.isEmpty()) getString(R.string.nautical_server_password_desc) else getString(R.string.nautical_password_mask)
                "server_secure" -> settings.NAUTICAL_USE_SECURE_CONNECTION.set(newValue as Boolean)
                "nautical_show_laylines" -> settings.NAUTICAL_SHOW_LAYLINES.set(newValue as Boolean)
                "nautical_show_wind_shifts" -> settings.NAUTICAL_SHOW_WIND_SHIFTS.set(newValue as Boolean)
                "nautical_show_trajectory" -> settings.NAUTICAL_SHOW_TRAJECTORY.set(newValue as Boolean)
                "nautical_xte_threshold" -> {
                    val floatValue = newString.toFloatOrNull() ?: 0.1f
                    settings.NAUTICAL_XTE_THRESHOLD.set(floatValue)
                    preference.summary = "$floatValue ${getString(R.string.nautical_unit_nm)}"
                }
            }
        }
        return changed
    }
}
