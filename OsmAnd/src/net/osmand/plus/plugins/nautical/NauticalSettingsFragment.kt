package net.osmand.plus.plugins.nautical

import net.osmand.plus.OsmandApplication
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import net.osmand.plus.R

class NauticalSettingsFragment : BaseSettingsFragment() {

    override fun setupPreferences() {
        // 1. setPreferences prevents duplicates by clearing the screen before inflating
        setPreferencesFromResource(R.xml.nautical_settings, null)

        val app = requireActivity().application as OsmandApplication
        val settings = app.settings

        // 2. Wire the IP Address dynamically
        val ipPref = findPreference<EditTextPreferenceEx>("server_ip")
        if (ipPref != null) {
            val currentIp = settings.NAUTICAL_SERVER_IP.get()
            ipPref.summary = if (currentIp.isNullOrEmpty()) "Enter your SignalK server IP" else currentIp

            ipPref.setOnPreferenceChangeListener { _, newValue ->
                val newString = newValue.toString()
                ipPref.summary = if (newString.isEmpty()) "Enter your SignalK server IP" else newString
                true // Returns true to allow saving
            }
        }

        // 3. Wire the Port dynamically
        val portPref = findPreference<EditTextPreferenceEx>("server_port")
        if (portPref != null) {
            val currentPort = settings.NAUTICAL_SERVER_PORT.get()
            portPref.summary = if (currentPort.isNullOrEmpty()) "Enter port (default 3000)" else currentPort

            portPref.setOnPreferenceChangeListener { _, newValue ->
                val newString = newValue.toString()
                portPref.summary = if (newString.isEmpty()) "Enter port (default 3000)" else newString
                true
            }
        }
    }
}