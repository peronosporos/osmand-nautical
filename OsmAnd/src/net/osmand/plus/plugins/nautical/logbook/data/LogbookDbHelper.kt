package net.osmand.plus.plugins.nautical.logbook.data

import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection

class LogbookDbHelper(private val context: OsmandApplication) {

    companion object {
        const val DB_NAME = "marine_logbook_db"
        const val DB_VERSION = 2
        
        const val TABLE_LOGBOOK = "logbook_entries"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_LAT = "latitude"
        const val COL_LON = "longitude"
        const val COL_SOG = "sog"
        const val COL_COG = "cog"
        const val COL_HEADING = "heading"
        const val COL_TWS = "tws"
        const val COL_TWA = "twa"
        const val COL_TWD = "twd"
        const val COL_PRESSURE = "pressure"
        const val COL_WATER_DEPTH = "water_depth"
        const val COL_WATER_TEMP = "water_temp"
        const val COL_BATTERY_VOLTAGE = "battery_voltage"
        const val COL_ENGINE_HOURS = "engine_hours"
        const val COL_SAIL_PLAN = "sail_plan"
        const val COL_NOTES = "notes"

        private const val TABLE_CREATE = """
            CREATE TABLE IF NOT EXISTS $TABLE_LOGBOOK (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP LONG NOT NULL,
                $COL_LAT DOUBLE NOT NULL,
                $COL_LON DOUBLE NOT NULL,
                $COL_SOG DOUBLE,
                $COL_COG DOUBLE,
                $COL_HEADING DOUBLE,
                $COL_TWS DOUBLE,
                $COL_TWA DOUBLE,
                $COL_TWD DOUBLE,
                $COL_PRESSURE DOUBLE,
                $COL_WATER_DEPTH DOUBLE,
                $COL_WATER_TEMP DOUBLE,
                $COL_BATTERY_VOLTAGE DOUBLE,
                $COL_ENGINE_HOURS DOUBLE,
                $COL_SAIL_PLAN TEXT,
                $COL_NOTES TEXT
            );
        """
        
        private const val INDEX_CREATE = "CREATE INDEX IF NOT EXISTS logbook_ts_idx ON $TABLE_LOGBOOK ($COL_TIMESTAMP);"
    }

    fun openConnection(readonly: Boolean): SQLiteConnection? {
        val db = context.sqliteAPI.getOrCreateDatabase(DB_NAME, readonly) ?: return null
        if (db.version < DB_VERSION) {
            val finalDb = if (readonly) {
                db.close()
                context.sqliteAPI.getOrCreateDatabase(DB_NAME, false) ?: return null
            } else {
                db
            }
            val version = finalDb.version
            finalDb.version = DB_VERSION
            if (version == 0) {
                onCreate(finalDb)
            } else {
                onUpgrade(finalDb, version, DB_VERSION)
            }
            return finalDb
        }
        return db
    }

    private fun onCreate(db: SQLiteConnection) {
        db.execSQL(TABLE_CREATE)
        db.execSQL(INDEX_CREATE)
    }

    private fun onUpgrade(db: SQLiteConnection, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_LOGBOOK ADD COLUMN $COL_TWD DOUBLE")
            db.execSQL("ALTER TABLE $TABLE_LOGBOOK ADD COLUMN $COL_WATER_DEPTH DOUBLE")
            db.execSQL("ALTER TABLE $TABLE_LOGBOOK ADD COLUMN $COL_WATER_TEMP DOUBLE")
            db.execSQL("ALTER TABLE $TABLE_LOGBOOK ADD COLUMN $COL_BATTERY_VOLTAGE DOUBLE")
            db.execSQL(INDEX_CREATE)
        }
    }
}
