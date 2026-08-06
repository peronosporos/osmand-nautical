package net.osmand.plus.plugins.nautical.s63.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
import org.json.JSONObject

class S63PermitViewModel(
    private val app: OsmandApplication,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val store = S63CredentialStore(app)
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var calculationJob: Job? = null
    private var importJob: Job? = null

    data class UiState(
        val manufacturerId: String = "",
        val manufacturerKey: String = "",
        val userPermit: String = "",
        val loadedCellCount: Int = 0,
        val isCalculating: Boolean = false,
        val errorMessage: String? = null,
        val toastMessage: String? = null
    )

    init {
        val mid = savedStateHandle.get<String>("m_id") ?: store.manufacturerId
        val mkey = savedStateHandle.get<String>("m_key") ?: store.manufacturerKey
        _uiState.value = _uiState.value.copy(
            manufacturerId = mid,
            manufacturerKey = mkey,
            loadedCellCount = store.getLoadedCellCount()
        )
        calculatePermit()
    }

    fun setManufacturerId(id: String) {
        store.manufacturerId = id
        savedStateHandle["m_id"] = id
        _uiState.value = _uiState.value.copy(manufacturerId = id)
        calculatePermit()
    }

    fun setManufacturerKey(key: String) {
        store.manufacturerKey = key
        savedStateHandle["m_key"] = key
        _uiState.value = _uiState.value.copy(manufacturerKey = key)
        calculatePermit()
    }

    private fun calculatePermit() {
        calculationJob?.cancel()
        val mid = _uiState.value.manufacturerId
        val mkey = _uiState.value.manufacturerKey

        if (mid.length == 2 && mkey.length == 5) {
            calculationJob = viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isCalculating = true, errorMessage = null)
                try {
                    val permit = withContext(Dispatchers.IO) {
                        val hwid = store.getHwid()
                        S63PermitGenerator.calculateUserPermit(hwid, mid, mkey.toByteArray(Charsets.US_ASCII))
                    }
                    _uiState.value = _uiState.value.copy(userPermit = permit, isCalculating = false)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        userPermit = "",
                        isCalculating = false,
                        errorMessage = e.message
                    )
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(userPermit = "", isCalculating = false)
        }
    }

    fun importConfig(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val json = JSONObject(input.bufferedReader().use { it.readText() })
                        val mid = json.optString("m_id")
                        val mkey = json.optString("m_key")
                        if (mid.isNotEmpty() && mkey.isNotEmpty()) {
                            mid to mkey
                        } else null
                    }
                }
                
                if (result != null) {
                    setManufacturerId(result.first)
                    setManufacturerKey(result.second)
                    _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.s63_config_imported))
                } else {
                    _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.s63_invalid_config))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.nautical_s63_error_prefix, e.message ?: ""))
            }
        }
    }

    fun loadPermitTxt(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            try {
                val keys = withContext(Dispatchers.IO) {
                    val hwid = store.getHwid()
                    val resultKeys = mutableMapOf<String, String>()
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val parts = line.split(",")
                                if (parts.size >= 5 && parts[0].trim().uppercase() == "PERMIT") {
                                    val encKey1 = parts[3].trim()
                                    if (encKey1.length == 16) {
                                        resultKeys.putAll(S63PermitGenerator.extractCellKeys(line, hwid))
                                    }
                                }
                            }
                        }
                    }
                    resultKeys
                }

                if (keys.isNotEmpty()) {
                    store.saveCellKeys(keys)
                    _uiState.value = _uiState.value.copy(
                        loadedCellCount = store.getLoadedCellCount(),
                        toastMessage = app.getString(R.string.s63_keys_loaded, keys.size)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.nautical_s63_error_prefix, e.message ?: ""))
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun importCharts(uris: List<Uri>) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val destDir = app.getAppPath("nautical/enc")
                    if (!destDir.exists()) {
                        destDir.mkdirs()
                    }
                    var imported = 0
                    uris.forEach { uri ->
                        val fileName = getFileName(uri)
                        if (fileName != null) {
                            val destFile = java.io.File(destDir, fileName)
                            app.contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                                imported++
                            }
                        }
                    }
                    imported
                }
                _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.s63_charts_imported, count))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = app.getString(R.string.nautical_s63_error_prefix, e.message ?: ""))
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor = app.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return it.getString(index)
                    }
                }
            }
        }
        return uri.path?.let { java.io.File(it).name }
    }
}
