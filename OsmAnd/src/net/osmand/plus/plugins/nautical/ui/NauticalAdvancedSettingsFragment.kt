package net.osmand.plus.plugins.nautical.ui

import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import kotlin.time.Duration.Companion.milliseconds

class NauticalAdvancedSettingsFragment : BaseSettingsFragment(), androidx.preference.Preference.OnPreferenceChangeListener {

    override fun setupPreferences() {
        setupTuningCategory()
        setupEmaCategory()
        setupReliabilityCategory()
        setupNetworkCategory()
    }

    override fun onPreferenceChange(preference: androidx.preference.Preference, newValue: Any?): Boolean {
        // Allow the change to be saved first, then notify the backend
        viewLifecycleOwner.lifecycleScope.launch {
            delay(100.milliseconds) // Small debounce for batch updates
            NauticalPlugin.engine?.dataBroker?.updateTuning()
        }
        return true
    }

    private fun setupTuningCategory() {
        createPreferenceCategory(R.string.nautical_telemetry_tuning)
        val tuningPrefs = listOf(
            settings.NAUTICAL_RUDDER_GAIN,
            settings.NAUTICAL_COUNTER_RUDDER,
            settings.NAUTICAL_AUTO_TRIM,
            settings.NAUTICAL_FILTER_SENSITIVITY,
            settings.NAUTICAL_RUDDER_LIMIT,
            settings.NAUTICAL_PYPILOT_P,
            settings.NAUTICAL_PYPILOT_I,
            settings.NAUTICAL_PYPILOT_D,
            settings.NAUTICAL_PYPILOT_PR,
            settings.NAUTICAL_PYPILOT_FF
        )
        tuningPrefs.forEach { pref ->
            findPreference<EditTextPreferenceEx>(pref.id)?.apply {
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
                onPreferenceChangeListener = this@NauticalAdvancedSettingsFragment
            }
        }
    }

    private fun setupEmaCategory() {
        createPreferenceCategory(R.string.nautical_ema_smoothing)
        val emaPrefs = listOf(
            settings.NAUTICAL_EMA_ALPHA_HEADING,
            settings.NAUTICAL_EMA_ALPHA_WIND_ANGLE,
            settings.NAUTICAL_EMA_ALPHA_WIND_SPEED,
            settings.NAUTICAL_EMA_ALPHA_DEPTH,
            settings.NAUTICAL_EMA_ALPHA_RUDDER,
            settings.NAUTICAL_EMA_ANGLE_THRESHOLD_DEG,
            settings.NAUTICAL_EMA_SPEED_THRESHOLD_MS,
            settings.NAUTICAL_TELEMETRY_REFRESH_BASE_MS
        )
        emaPrefs.forEach { pref ->
            findPreference<EditTextPreferenceEx>(pref.id)?.apply {
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
                onPreferenceChangeListener = this@NauticalAdvancedSettingsFragment
            }
        }
    }

    private fun setupReliabilityCategory() {
        createPreferenceCategory(R.string.nautical_reliability_thresholds)
        val relPrefs = listOf(
            settings.NAUTICAL_STW_REL_MIN_STW,
            settings.NAUTICAL_STW_REL_MIN_SOG,
            settings.NAUTICAL_STW_REL_DELAY_SEC
        )
        relPrefs.forEach { pref ->
            findPreference<EditTextPreferenceEx>(pref.id)?.apply {
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
                onPreferenceChangeListener = this@NauticalAdvancedSettingsFragment
            }
        }
    }

    private fun setupNetworkCategory() {
        createPreferenceCategory(R.string.nautical_connectivity_settings)
        val netPrefs = listOf(
            settings.NAUTICAL_WATCHDOG_TIMEOUT_SEC,
            settings.NAUTICAL_COMMAND_TIMEOUT_MS,
            settings.NAUTICAL_ACTUATOR_OVERLOAD_WINDOW_SEC
        )
        netPrefs.forEach { pref ->
            findPreference<EditTextPreferenceEx>(pref.id)?.apply {
                summary = pref.get().toString()
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
                onPreferenceChangeListener = this@NauticalAdvancedSettingsFragment
            }
        }
    }

    private fun createPreferenceCategory(titleResId: Int): androidx.preference.PreferenceCategory {
        val category = androidx.preference.PreferenceCategory(requireContext())
        category.setTitle(titleResId)
        preferenceScreen.addPreference(category)
        return category
    }
}
