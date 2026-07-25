package net.osmand.plus.plugins.nautical.hazard.data

import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection

class NavtexDatabaseHelper(private val app: OsmandApplication) {

    companion object {
        const val DB_NAME = "navtex_hazard_db"
        const val DB_VERSION = 2

        const val TABLE_NAVTEX = "navtex_messages"
        const val COL_ID = "id"
        const val COL_STATION = "station_letter"
        const val COL_SUBJECT = "subject_code"
        const val COL_SEQUENCE = "sequence_number"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_BODY = "body"
        const val COL_POINTS = "points_data"
        const val COL_URGENT = "is_urgent"

        private const val TABLE_CREATE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAVTEX (
                $COL_ID TEXT PRIMARY KEY,
                $COL_STATION TEXT NOT NULL,
                $COL_SUBJECT TEXT NOT NULL,
                $COL_SEQUENCE INTEGER NOT NULL,
                $COL_TIMESTAMP LONG NOT NULL,
                $COL_BODY TEXT NOT NULL,
                $COL_POINTS TEXT,
                $COL_URGENT INTEGER NOT NULL DEFAULT 0
            );
        """
    }

    fun openConnection(readonly: Boolean): SQLiteConnection? {
        val db = app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, readonly) ?: return null
        if (db.version < DB_VERSION) {
            val finalDb = if (readonly) {
                db.close()
                app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, false) ?: return null
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_navtex_timestamp ON $TABLE_NAVTEX ($COL_TIMESTAMP)")
    }

    private fun onUpgrade(db: SQLiteConnection, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAVTEX")
            onCreate(db)
        }
    }
}
