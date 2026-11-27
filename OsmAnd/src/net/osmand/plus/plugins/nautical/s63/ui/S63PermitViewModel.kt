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
import net.osmand.plus.plugins.nautical.s63.crypto.CellPermitStatus
import net.osmand.plus.plugins.nautical.s63.crypto.S63AuthVerifier
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
                // S-63 10.6.2: a Scheme Administrator certificate or public
                // key, or another data server certificate, is imported through
                // the same file picker as PERMIT.TXT.
                val certificateMessage = withContext(Dispatchers.IO) {
                    importCertificateOrPublicKey(uri)
                }
                if (certificateMessage != null) {
                    _uiState.value = _uiState.value.copy(toastMessage = certificateMessage)
                    return@launch
                }
                val (validPermits, expiredPermits, errorCount) = withContext(Dispatchers.IO) {
                    val hwid = store.getHwid()
                    var content = ""
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        content = input.bufferedReader().use { it.readText() }
                    }
                    val results = S63PermitGenerator.parseAndValidatePermits(content, hwid)
                    val valid = results.filterIsInstance<CellPermitStatus.Valid>()
                    val expired = results.filterIsInstance<CellPermitStatus.Expired>()
                    val errors = results.count { it is CellPermitStatus.ChecksumError || it is CellPermitStatus.Malformed }
                    Triple(valid, expired, errors)
                }

                if (validPermits.isNotEmpty()) {
                    val permitsMap = mutableMapOf<String, S63PermitGenerator.PermitInfo>()
                    validPermits.forEach {
                        permitsMap[it.cellName] = S63PermitGenerator.PermitInfo(
                            cellKey = it.keyHex,
                            cellKey2 = it.key2Hex,
                            expiryDate = it.expiryDate
                        )
                    }
                    expiredPermits.forEach {
                        permitsMap[it.cellName] = S63PermitGenerator.PermitInfo(
                            cellKey = "",
                            cellKey2 = "",
                            expiryDate = it.expiryDate
                        )
                    }
                    store.savePermits(permitsMap)

                    val summary = app.getString(R.string.nautical_s63_import_summary_fmt, validPermits.size, expiredPermits.size, errorCount)
                    _uiState.value = _uiState.value.copy(
                        loadedCellCount = store.getLoadedCellCount(),
                        toastMessage = summary,
                        errorMessage = if (errorCount > 0) app.getString(R.string.nautical_s63_errors_fmt, errorCount) else null
                    )
                } else {
                    val detail = if (expiredPermits.isNotEmpty()) {
                        app.getString(R.string.s63_all_expired_fmt, expiredPermits.size)
                    } else if (errorCount > 0) {
                        app.getString(R.string.s63_crc_failed_fmt, errorCount)
                    } else {
                        app.getString(R.string.s63_invalid_permit)
                    }
                    _uiState.value = _uiState.value.copy(
                        toastMessage = detail,
                        errorMessage = detail
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = app.getString(R.string.nautical_s63_error_prefix, e.message ?: ""),
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Imports an S-63 public key or certificate (S-63 10.6.2): an ASCII
     * public key file (IHO.PUB format), or a DER encoded X.509 certificate
     * such as IHO.CRT or a data server certificate. Returns the user
     * message when the file was recognized as a key or certificate, or
     * null when the content is a PERMIT.TXT and permit parsing applies.
     */
    private fun importCertificateOrPublicKey(uri: Uri): String? {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val text = bytes.toString(Charsets.ISO_8859_1)
        val trimmed = text.trimStart { it == ' ' || it == '\r' || it == '\n' || it == '\t' }
        val fileName = getFileName(uri) ?: ""
        if (trimmed.startsWith("//")) {
            if (trimmed.contains("signature part", ignoreCase = true)) {
                // An ENC signature file (.SF) carries the data server key of
                // one chart; installing it as the Scheme Administrator key
                // would break authentication of every other chart.
                return null
            }
            val key = S63AuthVerifier.dsaKeyFromAsciiPublicKey(trimmed)
                ?: return app.getString(R.string.s63_certificate_invalid)
            store.saveSaKey(key, null)
            return app.getString(R.string.s63_sa_key_imported)
        }
        if (isDerSequence(bytes)) {
            val pair = S63AuthVerifier.dsaKeyFromX509(bytes)
                ?: return app.getString(R.string.s63_certificate_invalid)
            val (key, certificate) = pair
            val now = System.currentTimeMillis()
            if (certificate.notAfter.time in 1 until now) {
                return app.getString(R.string.s63_sa_cert_expired)
            }
            val isSchemeAdministrator = fileName.uppercase().contains("IHO") ||
                    certificate.subjectX500Principal.name.contains("Scheme Administrator", ignoreCase = true)
            return if (isSchemeAdministrator) {
                store.saveSaKey(key, certificate.notAfter.time)
                app.getString(R.string.s63_sa_key_imported)
            } else {
                store.addTrustedKey(key, certificate.notAfter.time)
                app.getString(R.string.s63_trusted_key_imported, fileName)
            }
        }
        return null
    }

    /**
     * True when the bytes are a DER SEQUENCE whose declared length covers
     * the whole file. The leading 0x30 alone is not sufficient: the ASCII
     * character '0' is also 0x30, so a text file starting with the digit
     * zero must fall through to permit parsing.
     */
    private fun isDerSequence(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes[0] != 0x30.toByte()) {
            return false
        }
        return when {
            bytes[1] == 0x81.toByte() -> 3 + (bytes[2].toInt() and 0xFF) == bytes.size
            bytes[1] == 0x82.toByte() ->
                4 + (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)) == bytes.size
            bytes[1] >= 0 && bytes[1] < 0x80.toByte() -> 2 + (bytes[1].toInt() and 0xFF) == bytes.size
            else -> false
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
