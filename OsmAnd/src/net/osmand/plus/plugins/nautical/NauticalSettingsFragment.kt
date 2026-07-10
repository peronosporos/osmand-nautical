package net.osmand.plus.plugins.nautical

import androidx.preference.EditTextPreference
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.PluginsHelper

class NauticalSettingsFragment : BaseSettingsFragment() {

    override fun setupPreferences() {
        // 1. setPreferences prevents duplicates by clearing the screen before inflating
        setPreferencesFromResource(R.xml.nautical_settings, null)

        val plugin = PluginsHelper.requirePlugin(NauticalPlugin::class.java)

        // 2. Wire the IP Address dynamically
        val ipPref = findPreference<EditTextPreference>("server_ip")
        ipPref?.let { pref ->
            val currentIp = plugin.nauticalServerIp.get()
            pref.summary = currentIp.ifEmpty { "Enter your SignalK server IP" }
            pref.text = currentIp

            pref.setOnPreferenceChangeListener { _, newValue ->
                val newString = newValue.toString()
                pref.summary = newString.ifEmpty { "Enter your SignalK server IP" }
                plugin.nauticalServerIp.set(newString)
                true
            }
        }

        // 3. Wire the Port dynamically
        val portPref = findPreference<EditTextPreference>("server_port")
        portPref?.let { pref ->
            val currentPort = plugin.nauticalServerPort.get()
            pref.summary = currentPort.ifEmpty { "Enter port (default 3000)" }
            pref.text = currentPort

            pref.setOnPreferenceChangeListener { _, newValue ->
                val newString = newValue.toString()
                pref.summary = newString.ifEmpty { "Enter port (default 3000)" }
                plugin.nauticalServerPort.set(newString)
                true
            }
        }
    }
}