@file:Suppress("DEPRECATION")

package net.osmand.plus.plugins.nautical.s63.ui

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.osmand.plus.OsmandApplication
import androidx.core.content.edit
import net.osmand.plus.plugins.nautical.s63.crypto.S63AuthVerifier
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores S-63 credentials and cell keys.
 * Uses EncryptedSharedPreferences backed by Android KeyStore for security.
 */
class S63CredentialStore(private val app: OsmandApplication) {

    private val masterKey = MasterKey.Builder(app)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        app,
        "s63_credentials_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val legacyPrefs = app.getSharedPreferences("s63_credentials", Context.MODE_PRIVATE)
    
    // Memory cache to avoid repeated SharedPreferences reads during high-frequency map events
    private val cellKeyCache = ConcurrentHashMap<String, String>()
    private val cellKey2Cache = ConcurrentHashMap<String, String>()
    private val expiryCache = ConcurrentHashMap<String, String>()
    private var cachedHwid: ByteArray? = null

    init {
        migrateLegacyCredentials()
    }

    private fun migrateLegacyCredentials() {
        if (legacyPrefs.all.isNotEmpty()) {
            val editor = prefs.edit()
            legacyPrefs.all.forEach { (key, value) ->
                (value as? String)?.let {
                    editor.putString(key, it)
                }
            }
            if (editor.commit()) {
                legacyPrefs.edit { clear() }
            }
        }
    }

    var manufacturerId: String
        get() = prefs.getString(KEY_M_ID, "") ?: ""
        set(value) = prefs.edit { putString(KEY_M_ID, value) }

    var manufacturerKey: String
        get() = prefs.getString(KEY_M_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_M_KEY, value) }

    /**
     * Retrieves or generates the 5-byte HWID for this device.
     * Uses a combination of hardware properties for increased stability across factory resets
     * if ANDROID_ID changes, but primarily relies on ANDROID_ID as the standard seed.
     */
    @SuppressLint("HardwareIds")
    fun getHwid(): ByteArray {
        cachedHwid?.let { return it }
        
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        val hardwareSeed = "${androidId}_${android.os.Build.BOARD}_${android.os.Build.BRAND}_${android.os.Build.MANUFACTURER}"
        
        val hwid = S63PermitGenerator.generateHWID(hardwareSeed)
        cachedHwid = hwid
        return hwid
    }

    /**
     * Stores a map of Cell Name to Permit Information.
     */
    fun savePermits(permits: Map<String, S63PermitGenerator.PermitInfo>) {
        prefs.edit {
            prefs.all.keys.filter {
                it.startsWith(PREFIX_CELL_KEY) || it.startsWith(PREFIX_CELL_KEY2) || it.startsWith(PREFIX_CELL_EXPIRY)
            }.forEach { remove(it) }

            permits.forEach { (cell, info) ->
                putString(PREFIX_CELL_KEY + cell, info.cellKey)
                putString(PREFIX_CELL_KEY2 + cell, info.cellKey2)
                putString(PREFIX_CELL_EXPIRY + cell, info.expiryDate)
                cellKeyCache[cell] = info.cellKey
                cellKey2Cache[cell] = info.cellKey2
                expiryCache[cell] = info.expiryDate
            }
        }
    }

    @Deprecated("Use savePermits", ReplaceWith("savePermits(keys.mapValues { S63PermitGenerator.PermitInfo(it.value, \"\", \"\") })"))
    fun saveCellKeys(keys: Map<String, String>) {
        savePermits(keys.mapValues { S63PermitGenerator.PermitInfo(it.value, "", "") })
    }

    fun getCellKey(cellName: String): String? {
        cellKeyCache[cellName]?.let { return it }
        val key = prefs.getString(PREFIX_CELL_KEY + cellName, null)
        if (key != null) {
            cellKeyCache[cellName] = key
        }
        return key
    }

    /**
     * The second cell key (CK2) of the permit, used as fallback when the
     * cell key has been iterated by the data server (S-63 10.7.3 step c).
     */
    fun getCellKey2(cellName: String): String? {
        cellKey2Cache[cellName]?.let { return it }
        val key = prefs.getString(PREFIX_CELL_KEY2 + cellName, null)
        if (key != null) {
            cellKey2Cache[cellName] = key
        }
        return key
    }

    fun getExpiryDate(cellName: String): String? {
        expiryCache[cellName]?.let { return it }
        val expiry = prefs.getString(PREFIX_CELL_EXPIRY + cellName, null)
        if (expiry != null) {
            expiryCache[cellName] = expiry
        }
        return expiry
    }

    fun getLoadedCellCount(): Int {
        return prefs.all.keys.count { it.startsWith(PREFIX_CELL_KEY) }
    }

    /**
     * The installed Scheme Administrator public key used to authenticate the
     * data server certificates in ENC signature files (S-63 10.6.2). A user
     * installed IHO certificate or public key takes precedence; otherwise
     * the genuine IHO Scheme Administrator key that ships with the scheme
     * is used.
     */
    fun getInstalledSaKey(): S63AuthVerifier.DsaKey {
        return S63AuthVerifier.DsaKey.fromHexString(prefs.getString(KEY_SA_PARAMS, null))
            ?: S63AuthVerifier.IHO_SCHEME_ADMINISTRATOR_KEY
    }

    fun isSaKeyUserInstalled(): Boolean {
        return prefs.getString(KEY_SA_PARAMS, null) != null
    }

    /**
     * Expiry of the installed SA certificate, or null when the SA key is a
     * public key file or the built in key (no expiry, S-63 10.6.2 step 3
     * applies to certificates only).
     */
    fun getSaCertificateNotAfter(): Long? {
        val value = prefs.getString(KEY_SA_CERT_NOT_AFTER, null) ?: return null
        return value.toLongOrNull()
    }

    fun saveSaKey(key: S63AuthVerifier.DsaKey, certificateNotAfter: Long?) {
        prefs.edit {
            putString(KEY_SA_PARAMS, key.toHexString())
            putString(KEY_SA_CERT_NOT_AFTER, certificateNotAfter?.toString() ?: "")
        }
    }

    /**
     * Additional installed certificates and public keys (for example a data
     * server certificate such as UKHO.CRT). ENC data whose data server
     * certificate authenticates only with one of these keys still loads,
     * with the SSE 26 warning of S-63 10.6.2.1. Expired X.509 certificates
     * are not returned.
     */
    fun getTrustedKeys(now: Long = System.currentTimeMillis()): List<S63AuthVerifier.DsaKey> {
        val entries = prefs.getString(KEY_TRUSTED_KEYS, null) ?: return emptyList()
        val keys = ArrayList<S63AuthVerifier.DsaKey>()
        try {
            val array = JSONArray(entries)
            for (i in 0 until array.length()) {
                val entry = array.optString(i, "").split("|")
                if (entry.size == 2) {
                    val notAfter = entry[1].toLongOrNull()
                    if (notAfter != null && notAfter in 1 until now) {
                        continue
                    }
                    S63AuthVerifier.DsaKey.fromHexString(entry[0])?.let { keys.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        return keys
    }

    fun addTrustedKey(key: S63AuthVerifier.DsaKey, certificateNotAfter: Long?): Boolean {
        if (getTrustedKeys().any { it.y == key.y }) {
            return false
        }
        val array = JSONArray(prefs.getString(KEY_TRUSTED_KEYS, "[]"))
        array.put("${key.toHexString()}|${certificateNotAfter ?: 0}")
        prefs.edit { putString(KEY_TRUSTED_KEYS, array.toString()) }
        return true
    }

    fun getTrustedKeyCount(): Int {
        val entries = prefs.getString(KEY_TRUSTED_KEYS, null) ?: return 0
        return try {
            JSONArray(entries).length()
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        private const val KEY_M_ID = "m_id"
        private const val KEY_M_KEY = "m_key"
        private const val PREFIX_CELL_KEY = "cell_key_"
        private const val PREFIX_CELL_KEY2 = "cell_key2_"
        private const val PREFIX_CELL_EXPIRY = "cell_expiry_"
        private const val KEY_SA_PARAMS = "sa_key_params"
        private const val KEY_SA_CERT_NOT_AFTER = "sa_cert_not_after"
        private const val KEY_TRUSTED_KEYS = "trusted_keys"
    }
}
