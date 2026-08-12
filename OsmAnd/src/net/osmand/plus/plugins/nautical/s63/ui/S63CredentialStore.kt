@file:Suppress("DEPRECATION")

package net.osmand.plus.plugins.nautical.s63.ui

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.osmand.plus.OsmandApplication
import androidx.core.content.edit
import net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator
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
            prefs.all.keys.filter { it.startsWith(PREFIX_CELL_KEY) || it.startsWith(PREFIX_CELL_EXPIRY) }.forEach { remove(it) }

            permits.forEach { (cell, info) ->
                putString(PREFIX_CELL_KEY + cell, info.cellKey)
                putString(PREFIX_CELL_EXPIRY + cell, info.expiryDate)
                cellKeyCache[cell] = info.cellKey
                expiryCache[cell] = info.expiryDate
            }
        }
    }

    @Deprecated("Use savePermits", ReplaceWith("savePermits(keys.mapValues { S63PermitGenerator.PermitInfo(it.value, \"\") })"))
    fun saveCellKeys(keys: Map<String, String>) {
        savePermits(keys.mapValues { S63PermitGenerator.PermitInfo(it.value, "") })
    }

    fun getCellKey(cellName: String): String? {
        cellKeyCache[cellName]?.let { return it }
        val key = prefs.getString(PREFIX_CELL_KEY + cellName, null)
        if (key != null) {
            cellKeyCache[cellName] = key
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

    companion object {
        private const val KEY_M_ID = "m_id"
        private const val KEY_M_KEY = "m_key"
        private const val PREFIX_CELL_KEY = "cell_key_"
        private const val PREFIX_CELL_EXPIRY = "cell_expiry_"
    }
}
