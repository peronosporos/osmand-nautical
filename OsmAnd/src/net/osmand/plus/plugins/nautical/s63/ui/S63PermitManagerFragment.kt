package net.osmand.plus.plugins.nautical.s63.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.OnPreferenceChanged
import net.osmand.plus.settings.preferences.EditTextPreferenceEx
import org.json.JSONObject

class S63PermitManagerFragment : BaseSettingsFragment(), OnPreferenceChanged {

    private lateinit var store: S63CredentialStore

    private val configPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importConfig(uri)
            }
        }
    }

    private val permitTxtPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadPermitTxt(uri)
            }
        }
    }

    override fun setupPreferences() {
        store = S63CredentialStore(app)

        setupMId()
        setupMKey()
        updateUserPermit()
        setupImportConfig()
        setupLoadPermitTxt()
    }

    private fun setupMId() {
        findPreference<EditTextPreferenceEx>("s63_m_id")?.apply {
            summary = store.manufacturerId.ifEmpty { getString(R.string.shared_string_none) }
            setText(store.manufacturerId)
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as String
                store.manufacturerId = value
                summary = value.ifEmpty { getString(R.string.shared_string_none) }
                updateUserPermit()
                true
            }
        }
    }

    private fun setupMKey() {
        findPreference<EditTextPreferenceEx>("s63_m_key")?.apply {
            summary = if (store.manufacturerKey.isEmpty()) getString(R.string.shared_string_none) else "********"
            setText(store.manufacturerKey)
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as String
                store.manufacturerKey = value
                summary = if (value.isEmpty()) getString(R.string.shared_string_none) else "********"
                updateUserPermit()
                true
            }
        }
    }

    private fun updateUserPermit() {
        val mid = store.manufacturerId
        val mkey = store.manufacturerKey
        val pref = findPreference<Preference>("s63_user_permit") ?: return

        if (mid.length == 2 && mkey.length == 5) {
            try {
                val hwid = store.getHwid()
                val permit = S63PermitGenerator.calculateUserPermit(hwid, mid, mkey.toByteArray(Charsets.US_ASCII))
                
                pref.summary = permit
                pref.setOnPreferenceClickListener {
                    copyToClipboard(permit)
                    true
                }
            } catch (e: Exception) {
                pref.summary = getString(R.string.nautical_s63_error_prefix, e.message ?: "")
            }
        } else {
            pref.summary = getString(R.string.shared_string_none)
            pref.setOnPreferenceClickListener(null)
        }
    }

    private fun setupImportConfig() {
        findPreference<Preference>("s63_import_config")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            configPicker.launch(intent)
            true
        }
    }

    private fun setupLoadPermitTxt() {
        findPreference<Preference>("s63_load_permit_txt")?.apply {
            val count = store.getLoadedCellCount()
            summary = if (count > 0) getString(R.string.s63_keys_loaded, count) else getString(R.string.shared_string_none)
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "text/plain"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                permitTxtPicker.launch(intent)
                true
            }
        }
    }

    private fun importConfig(uri: android.net.Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val json = JSONObject(input.bufferedReader().use { it.readText() })
                val mid = json.optString("m_id")
                val mkey = json.optString("m_key")
                
                if (mid.isNotEmpty() && mkey.isNotEmpty()) {
                    store.manufacturerId = mid
                    store.manufacturerKey = mkey
                    setupPreferences() // Refresh UI
                    app.showToastMessage(R.string.s63_config_imported)
                } else {
                    app.showToastMessage(R.string.s63_invalid_config)
                }
            }
        } catch (e: Exception) {
            app.showToastMessage(getString(R.string.nautical_s63_error_prefix, e.message ?: ""))
        }
    }

    private fun loadPermitTxt(uri: android.net.Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val content = input.bufferedReader().use { it.readText() }
                val hwid = store.getHwid()
                
                val keys = S63PermitGenerator.extractCellKeys(content, hwid)
                if (keys.isNotEmpty()) {
                    store.saveCellKeys(keys)
                    setupPreferences() // Refresh UI
                    app.showToastMessage(R.string.s63_keys_loaded, keys.size)
                }
            }
        } catch (e: Exception) {
            app.showToastMessage(getString(R.string.nautical_s63_error_prefix, e.message ?: ""))
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.s63_user_permit_label), text)
        clipboard.setPrimaryClip(clip)
        app.showToastMessage(R.string.s63_permit_copied)
    }

    override fun onPreferenceChanged(prefId: String) {
        // Handle generic changes if needed
    }
}
