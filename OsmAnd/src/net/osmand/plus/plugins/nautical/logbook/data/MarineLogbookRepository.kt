package net.osmand.plus.plugins.nautical.logbook.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor
import net.osmand.plus.utils.AndroidDbUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MarineLogbookRepository(private val context: OsmandApplication) {
    private val log = PlatformUtil.getLog(MarineLogbookRepository::class.java)

    private val dbHelper = LogbookDbHelper(context)
    private val _logEntries = MutableStateFlow<List<LogbookEntry>>(emptyList())
    val logEntries: StateFlow<List<LogbookEntry>> = _logEntries.asStateFlow()

    suspend fun insertEntry(entry: LogbookEntry) = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(false) ?: return@withContext
        try {
            db.execSQL("BEGIN TRANSACTION")
            val values = mutableMapOf<String, Any?>()
            values[LogbookDbHelper.COL_TIMESTAMP] = entry.timestamp
            values[LogbookDbHelper.COL_LAT] = entry.latitude
            values[LogbookDbHelper.COL_LON] = entry.longitude
            values[LogbookDbHelper.COL_SOG] = entry.sog
            values[LogbookDbHelper.COL_COG] = entry.cog
            values[LogbookDbHelper.COL_HEADING] = entry.heading
            values[LogbookDbHelper.COL_TWS] = entry.tws
            values[LogbookDbHelper.COL_TWA] = entry.twa
            values[LogbookDbHelper.COL_TWD] = entry.twd
            values[LogbookDbHelper.COL_PRESSURE] = entry.pressure
            values[LogbookDbHelper.COL_WATER_DEPTH] = entry.waterDepth
            values[LogbookDbHelper.COL_WATER_TEMP] = entry.waterTemp
            values[LogbookDbHelper.COL_BATTERY_VOLTAGE] = entry.batteryVoltage
            values[LogbookDbHelper.COL_ENGINE_HOURS] = entry.engineHours
            values[LogbookDbHelper.COL_SAIL_PLAN] = entry.sailPlan
            values[LogbookDbHelper.COL_NOTES] = entry.notes

            db.execSQL(
                AndroidDbUtils.createDbInsertQuery(LogbookDbHelper.TABLE_LOGBOOK, values.keys),
                values.values.toTypedArray()
            )
            db.execSQL("COMMIT TRANSACTION")
            refreshEntries()
        } catch (e: Exception) {
            try { 
                db.execSQL("ROLLBACK TRANSACTION") 
            } catch (re: Exception) {
                log.error("Failed to rollback logbook transaction", re)
            }
            throw e
        } finally {
            db.close()
        }
    }

    suspend fun updateEntryDetails(id: Long, sailPlan: String, notes: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(false) ?: return@withContext
        try {
            db.execSQL(
                "UPDATE ${LogbookDbHelper.TABLE_LOGBOOK} SET ${LogbookDbHelper.COL_SAIL_PLAN} = ?, ${LogbookDbHelper.COL_NOTES} = ? WHERE ${LogbookDbHelper.COL_ID} = ?",
                arrayOf(sailPlan, notes, id)
            )
            refreshEntries()
        } finally {
            db.close()
        }
    }

    suspend fun refreshEntries() = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(true) ?: return@withContext
        try {
            val entries = mutableListOf<LogbookEntry>()
            val cursor = db.rawQuery("SELECT * FROM ${LogbookDbHelper.TABLE_LOGBOOK} ORDER BY ${LogbookDbHelper.COL_TIMESTAMP} DESC", null)
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    entries.add(readEntry(cursor))
                } while (cursor.moveToNext())
                cursor.close()
            }
            _logEntries.value = entries
        } finally {
            db.close()
        }
    }

    private fun readEntry(cursor: SQLiteCursor): LogbookEntry {
        return LogbookEntry(
            id = cursor.getLong(0),
            timestamp = cursor.getLong(1),
            latitude = cursor.getDouble(2),
            longitude = cursor.getDouble(3),
            sog = if (cursor.isNull(4)) null else cursor.getDouble(4),
            cog = if (cursor.isNull(5)) null else cursor.getDouble(5),
            heading = if (cursor.isNull(6)) null else cursor.getDouble(6),
            tws = if (cursor.isNull(7)) null else cursor.getDouble(7),
            twa = if (cursor.isNull(8)) null else cursor.getDouble(8),
            twd = if (cursor.isNull(9)) null else cursor.getDouble(9),
            pressure = if (cursor.isNull(10)) null else cursor.getDouble(10),
            waterDepth = if (cursor.isNull(11)) null else cursor.getDouble(11),
            waterTemp = if (cursor.isNull(12)) null else cursor.getDouble(12),
            batteryVoltage = if (cursor.isNull(13)) null else cursor.getDouble(13),
            engineHours = if (cursor.isNull(14)) null else cursor.getDouble(14),
            sailPlan = cursor.getString(15) ?: "",
            notes = cursor.getString(16) ?: ""
        )
    }
}
