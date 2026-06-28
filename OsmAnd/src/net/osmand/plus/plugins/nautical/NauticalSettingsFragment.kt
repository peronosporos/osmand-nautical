package net.osmand.plus.plugins.nautical

import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.R

class NauticalSettingsFragment : BaseSettingsFragment() {

    override fun setupPreferences() {
        // This is the required method in the current BaseSettingsFragment
        addPreferencesFromResource(R.xml.nautical_settings)
    }
}