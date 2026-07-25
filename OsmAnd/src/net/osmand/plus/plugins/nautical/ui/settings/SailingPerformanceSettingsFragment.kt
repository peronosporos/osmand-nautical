package net.osmand.plus.plugins.nautical.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType

class SailingPerformanceSettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setupPreferences()
    }

    override fun setupPreferences() {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        val category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.pref_sailing_performance_title)
        }
        screen.addPreference(category)

        val configurePolarsPref = Preference(requireContext()).apply {
            title = getString(R.string.pref_configure_polars_title)
            summary = getString(R.string.nautical_polar_target)
            setIcon(R.drawable.ic_action_settings)
            setOnPreferenceClickListener {
                app.showToastMessage(R.string.pref_configure_polars_title)
                true
            }
        }
        category.addPreference(configurePolarsPref)

        val viewPerformancePref = Preference(requireContext()).apply {
            title = getString(R.string.pref_see_polars_title)
            summary = getString(R.string.status_disconnected)
            setIcon(R.drawable.ic_action_world_globe)
            setOnPreferenceClickListener {
                app.showToastMessage(R.string.pref_see_polars_title)
                true
            }
        }
        category.addPreference(viewPerformancePref)

        val logbookPref = Preference(requireContext()).apply {
            title = getString(R.string.logbook_title)
            summary = getString(R.string.nautical_logbook_interval_desc)
            setIcon(R.drawable.ic_action_nautical_log)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.MARINE_LOGBOOK)
                true
            }
        }
        category.addPreference(logbookPref)

        val tidesPref = Preference(requireContext()).apply {
            title = getString(R.string.nautical_settings_tides_menu)
            summary = getString(R.string.nautical_settings_tides_summary)
            setIcon(R.drawable.ic_action_nautical_depth)
            setOnPreferenceClickListener {
                showInstance(requireActivity(), SettingsScreenType.TIDE_DATA_MANAGER)
                true
            }
        }
        category.addPreference(tidesPref)
    }
}
