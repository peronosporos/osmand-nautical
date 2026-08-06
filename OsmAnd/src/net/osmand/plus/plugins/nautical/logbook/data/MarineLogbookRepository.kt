package net.osmand.plus.plugins.nautical.logbook.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.NauticalIOQueue
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.plus.utils.AndroidDbUtils

class MarineLogbookRepository(context: OsmandApplication) {
    private val log = PlatformUtil.getLog(MarineLogbookRepository::class.java)

    private val dbHelper = LogbookDbHelper(context)
    private val _logEntries = MutableStateFlow<List<LogbookEntry>>(emptyList())
    val logEntries: StateFlow<List<LogbookEntry>> = _logEntries.asStateFlow()

    suspend fun insertEntry(entry: LogbookEntry) = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            insertEntrySync(entry)
        }
    }

    suspend fun upsertTacticalState(key: String, value: String) = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            val db = dbHelper.openConnection(readonly = false) ?: return@withLock
            try {
                db.beginTransaction()
                db.execSQL(
                    "INSERT OR REPLACE INTO ${LogbookDbHelper.TABLE_TACTICAL_STATE} " +
                            "(${LogbookDbHelper.COL_STATE_KEY}, ${LogbookDbHelper.COL_STATE_VALUE}) VALUES (?, ?)",
                    arrayOf(key, value),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }
        }
    }

    suspend fun getTacticalState(key: String): String? = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(readonly = true) ?: return@withContext null
        try {
            var value: String? = null
            val cursor = db.rawQuery(
                "SELECT ${LogbookDbHelper.COL_STATE_VALUE} FROM ${LogbookDbHelper.TABLE_TACTICAL_STATE} " +
                        "WHERE ${LogbookDbHelper.COL_STATE_KEY} = ?",
                arrayOf(key),
            )
            try {
                if (((cursor != null) && cursor.moveToFirst())) {
                    value = cursor.getString(0)
                }
            } finally {
                cursor?.close()
            }
            value
        } finally {
            db.close()
        }
    }

    suspend fun deleteTacticalState(key: String) = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            val db = dbHelper.openConnection(readonly = false) ?: return@withLock
            try {
                db.execSQL(
                "DELETE FROM ${LogbookDbHelper.TABLE_TACTICAL_STATE} WHERE ${LogbookDbHelper.COL_STATE_KEY} = ?",
                arrayOf(key),
            )
            } finally {
                db.close()
            }
        }
    }

    /**
     * Synchronous insert for emergency/crash logging where coroutines are not available.
     */
    fun insertEntrySync(entry: LogbookEntry) {
        val db = dbHelper.openConnection(readonly = false) ?: return
        try {
            db.beginTransaction()
            val values = mutableMapOf<String, Any?>()
            values[LogbookDbHelper.COL_TIMESTAMP] = TemporalUtils.validate(entry.timestamp)
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
            values[LogbookDbHelper.COL_SERVER_UUID] = entry.serverUuid

            db.execSQL(
                AndroidDbUtils.createDbInsertQuery(LogbookDbHelper.TABLE_LOGBOOK, values.keys),
                values.values.toTypedArray(),
            )
            db.setTransactionSuccessful()
            // Can't refresh state-flow synchronously easily here without causing issues on Main thread
        } catch (e: Exception) {
            log.error("Sync insert failed", e)
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    suspend fun updateEntryDetails(id: Long, sailPlan: String, notes: String) = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            val db = dbHelper.openConnection(readonly = false) ?: return@withLock
            try {
                db.execSQL(
                    "UPDATE ${LogbookDbHelper.TABLE_LOGBOOK} SET ${LogbookDbHelper.COL_SAIL_PLAN} = ?, ${LogbookDbHelper.COL_NOTES} = ? WHERE ${LogbookDbHelper.COL_ID} = ?",
                    arrayOf<Any>(sailPlan, notes, id),
                )
                
                // Task: Synergy Sync-Back
                val cursor = db.rawQuery("SELECT * FROM ${LogbookDbHelper.TABLE_LOGBOOK} WHERE ${LogbookDbHelper.COL_ID} = ?", arrayOf(id.toString()))
                try {
                    if (((cursor != null) && cursor.moveToFirst())) {
                        val entry = readEntry(cursor)
                        NauticalPlugin.engine?.resourceManager?.pushNoteToServer(
                            entry.latitude, entry.longitude, 
                            "Log Edit: ${entry.sailPlan}", 
                            entry.notes,
                            entry.serverUuid,
                        )
                    }
                } finally {
                    cursor?.close()
                }

                refreshEntries()
            } finally {
                db.close()
            }
        }
    }

    suspend fun refreshEntries(limit: Int = 100, offset: Int = 0, append: Boolean = false) = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(readonly = true) ?: return@withContext
        try {
            val entries = mutableListOf<LogbookEntry>()
            val cursor = db.rawQuery(
                "SELECT * FROM ${LogbookDbHelper.TABLE_LOGBOOK} ORDER BY ${LogbookDbHelper.COL_TIMESTAMP} DESC LIMIT ? OFFSET ?", 
                arrayOf(limit.toString(), offset.toString()),
            )
            try {
                if (((cursor != null) && cursor.moveToFirst())) {
                    do {
                        entries.add(readEntry(cursor))
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
            
            if (append) {
                _logEntries.value += entries
            } else {
                _logEntries.value = entries
            }
        } finally {
            db.close()
        }
    }

    private fun readEntry(cursor: SQLiteCursor): LogbookEntry {
        val colId = cursor.getColumnIndex(LogbookDbHelper.COL_ID)
        val colTs = cursor.getColumnIndex(LogbookDbHelper.COL_TIMESTAMP)
        val colLat = cursor.getColumnIndex(LogbookDbHelper.COL_LAT)
        val colLon = cursor.getColumnIndex(LogbookDbHelper.COL_LON)
        val colSog = cursor.getColumnIndex(LogbookDbHelper.COL_SOG)
        val colCog = cursor.getColumnIndex(LogbookDbHelper.COL_COG)
        val colHdg = cursor.getColumnIndex(LogbookDbHelper.COL_HEADING)
        val colTws = cursor.getColumnIndex(LogbookDbHelper.COL_TWS)
        val colTwa = cursor.getColumnIndex(LogbookDbHelper.COL_TWA)
        val colTwd = cursor.getColumnIndex(LogbookDbHelper.COL_TWD)
        val colPrs = cursor.getColumnIndex(LogbookDbHelper.COL_PRESSURE)
        val colDep = cursor.getColumnIndex(LogbookDbHelper.COL_WATER_DEPTH)
        val colTmp = cursor.getColumnIndex(LogbookDbHelper.COL_WATER_TEMP)
        val colBat = cursor.getColumnIndex(LogbookDbHelper.COL_BATTERY_VOLTAGE)
        val colEng = cursor.getColumnIndex(LogbookDbHelper.COL_ENGINE_HOURS)
        val colSail = cursor.getColumnIndex(LogbookDbHelper.COL_SAIL_PLAN)
        val colNotes = cursor.getColumnIndex(LogbookDbHelper.COL_NOTES)
        val colUuid = cursor.getColumnIndex(LogbookDbHelper.COL_SERVER_UUID)

        return LogbookEntry(
            id = if (colId != -1) cursor.getLong(colId) else 0,
            timestamp = if (colTs != -1) cursor.getLong(colTs) else 0,
            latitude = if (colLat != -1) cursor.getDouble(colLat) else 0.0,
            longitude = if (colLon != -1) cursor.getDouble(colLon) else 0.0,
            sog = if ((colSog != -1) && (!cursor.isNull(colSog))) cursor.getDouble(colSog) else null,
            cog = if ((colCog != -1) && (!cursor.isNull(colCog))) cursor.getDouble(colCog) else null,
            heading = if ((colHdg != -1) && (!cursor.isNull(colHdg))) cursor.getDouble(colHdg) else null,
            tws = if ((colTws != -1) && (!cursor.isNull(colTws))) cursor.getDouble(colTws) else null,
            twa = if ((colTwa != -1) && (!cursor.isNull(colTwa))) cursor.getDouble(colTwa) else null,
            twd = if ((colTwd != -1) && (!cursor.isNull(colTwd))) cursor.getDouble(colTwd) else null,
            pressure = if ((colPrs != -1) && (!cursor.isNull(colPrs))) cursor.getDouble(colPrs) else null,
            waterDepth = if ((colDep != -1) && (!cursor.isNull(colDep))) cursor.getDouble(colDep) else null,
            waterTemp = if ((colTmp != -1) && (!cursor.isNull(colTmp))) cursor.getDouble(colTmp) else null,
            batteryVoltage = if ((colBat != -1) && (!cursor.isNull(colBat))) cursor.getDouble(colBat) else null,
            engineHours = if ((colEng != -1) && (!cursor.isNull(colEng))) cursor.getDouble(colEng) else null,
            sailPlan = if (colSail != -1) cursor.getString(colSail) ?: "" else "",
            notes = if (colNotes != -1) cursor.getString(colNotes) ?: "" else "",
            serverUuid = if (colUuid != -1) cursor.getString(colUuid) else null,
        )
    }
}
