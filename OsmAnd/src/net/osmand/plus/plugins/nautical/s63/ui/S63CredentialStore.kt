package net.osmand.plus.plugins.nautical.s63.ui

import android.content.Context
import net.osmand.plus.OsmandApplication

/**
 * Stores S-63 credentials and cell keys.
 * Uses OsmAnd's default SharedPreferences for simplicity, 
 * but ensures M_KEY is treated with care.
 */
class S63CredentialStore(private val app: OsmandApplication) {

    private val prefs = app.getSharedPreferences("s63_credentials", Context.MODE_PRIVATE)
    
    // Memory cache to avoid repeated SharedPreferences reads during high-frequency map events
    private val cellKeyCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var cachedHwid: ByteArray? = null

    var manufacturerId: String
        get() = prefs.getString(KEY_M_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_M_ID, value).apply()

    var manufacturerKey: String
        get() = prefs.getString(KEY_M_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_M_KEY, value).apply()

    /**
     * Retrieves or generates the 5-byte HWID for this device.
     * Caches the result in memory for the duration of the app session.
     */
    fun getHwid(): ByteArray {
        cachedHwid?.let { return it }
        val androidId = android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val hwid = net.osmand.plus.plugins.nautical.s63.crypto.S63PermitGenerator.generateHWID(androidId ?: "fallback_seed")
        cachedHwid = hwid
        return hwid
    }

    /**
     * Stores a map of Cell Name to Decrypted Cell Key.
     */
    fun saveCellKeys(keys: Map<String, String>) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX_CELL_KEY) }.forEach { editor.remove(it) }
        
        keys.forEach { (cell, key) ->
            editor.putString(PREFIX_CELL_KEY + cell, key)
            cellKeyCache[cell] = key
        }
        editor.apply()
    }

    fun getCellKey(cellName: String): String? {
        cellKeyCache[cellName]?.let { return it }
        val key = prefs.getString(PREFIX_CELL_KEY + cellName, null)
        if (key != null) {
            cellKeyCache[cellName] = key
        }
        return key
    }

    fun getLoadedCellCount(): Int {
        return prefs.all.keys.count { it.startsWith(PREFIX_CELL_KEY) }
    }

    companion object {
        private const val KEY_M_ID = "m_id"
        private const val KEY_M_KEY = "m_key"
        private const val PREFIX_CELL_KEY = "cell_key_"
    }
}
