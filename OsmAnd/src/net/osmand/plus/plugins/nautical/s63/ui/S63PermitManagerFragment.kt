package net.osmand.plus.plugins.nautical.s63.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.preference.Preference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.OnPreferenceChanged
import net.osmand.plus.settings.preferences.EditTextPreferenceEx

class S63PermitManagerFragment : BaseSettingsFragment(), OnPreferenceChanged {

    private lateinit var viewModel: S63PermitViewModel

    private val configPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importConfig(uri)
            }
        }
    }

    private val permitTxtPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.loadPermitTxt(uri)
            }
        }
    }

    private val chartPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importCharts(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val savedStateHandle = extras.createSavedStateHandle()
                return S63PermitViewModel(app, savedStateHandle) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[S63PermitViewModel::class.java]
    }

    override fun setupPreferences() {
        setupMId()
        setupMKey()
        setupImportConfig()
        setupLoadPermitTxt()
        setupImportCharts()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: S63PermitViewModel.UiState) {
        findPreference<EditTextPreferenceEx>("s63_m_id")?.apply {
            summary = state.manufacturerId.ifEmpty { getString(R.string.shared_string_none) }
            if (text != state.manufacturerId) {
                text = state.manufacturerId
            }
        }

        findPreference<EditTextPreferenceEx>("s63_m_key")?.apply {
            summary = if (state.manufacturerKey.isEmpty()) getString(R.string.shared_string_none) else "********"
            if (text != state.manufacturerKey) {
                text = state.manufacturerKey
            }
        }

        findPreference<Preference>("s63_user_permit")?.apply {
            summary = when {
                state.isCalculating -> getString(R.string.calculating_indication_message)
                state.errorMessage != null -> getString(R.string.nautical_s63_error_prefix, state.errorMessage)
                state.userPermit.isNotEmpty() -> state.userPermit
                else -> getString(R.string.shared_string_none)
            }
            if (state.userPermit.isNotEmpty()) {
                setOnPreferenceClickListener {
                    copyToClipboard(state.userPermit)
                    true
                }
            } else {
                onPreferenceClickListener = null
            }
        }

        findPreference<Preference>("s63_load_permit_txt")?.apply {
            summary = if (state.loadedCellCount > 0) {
                getString(R.string.s63_keys_loaded, state.loadedCellCount)
            } else {
                getString(R.string.shared_string_none)
            }
        }

        state.toastMessage?.let {
            app.showToastMessage(it)
            viewModel.clearToast()
        }
    }

    private fun setupMId() {
        findPreference<EditTextPreferenceEx>("s63_m_id")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setManufacturerId(newValue as String)
                true
            }
        }
    }

    private fun setupMKey() {
        findPreference<EditTextPreferenceEx>("s63_m_key")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setManufacturerKey(newValue as String)
                true
            }
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
        findPreference<Preference>("s63_load_permit_txt")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            permitTxtPicker.launch(intent)
            true
        }
    }

    private fun setupImportCharts() {
        findPreference<Preference>("s63_import_charts")?.setOnPreferenceClickListener {
            chartPicker.launch(arrayOf("*/*"))
            true
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.s63_user_permit_label), text)
        clipboard.setPrimaryClip(clip)
        app.showToastMessage(R.string.s63_permit_copied)
    }

    override fun onPreferenceChanged(prefId: String) {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Ensure preference dialog states are explicitly captured if any
        findPreference<EditTextPreferenceEx>("s63_m_id")?.let {
            outState.putString("s63_m_id_pending", it.text)
        }
        findPreference<EditTextPreferenceEx>("s63_m_key")?.let {
            outState.putString("s63_m_key_pending", it.text)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            it.getString("s63_m_id_pending")?.let { id ->
                findPreference<EditTextPreferenceEx>("s63_m_id")?.text = id
            }
            it.getString("s63_m_key_pending")?.let { key ->
                findPreference<EditTextPreferenceEx>("s63_m_key")?.text = key
            }
        }
    }
}
