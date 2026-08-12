package net.osmand.plus.plugins.nautical.hazard.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor
import net.osmand.plus.plugins.nautical.utils.use
import net.osmand.plus.plugins.nautical.NauticalIOQueue
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository for NAVTEX messages with local SQLite persistence.
 * survival: Survive app restarts and screen wakeups.
 * expiry: 48 hours for all messages.
 */
@OptIn(FlowPreview::class)
class NavtexRepository(private val app: OsmandApplication) {

    private val dbHelper = NavtexDatabaseHelper(app)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _messages = MutableStateFlow<List<NavtexMessage>>(emptyList())
    val messages: StateFlow<List<NavtexMessage>> = _messages.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    init {
        scope.launch {
            refreshTrigger
                .debounce(500.milliseconds)
                .collect {
                    performRefresh()
                }
        }
        refreshMessages()
    }

    private var lastCleanupTime = 0L
    private val CLEANUP_INTERVAL = 60L * 60L * 1000L // 1 hour

    suspend fun upsertMessage(message: NavtexMessage) = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            val db = dbHelper.openConnection(false) ?: return@withLock
            try {
                db.beginTransactionNonExclusive()
                val sql = """
                    INSERT OR REPLACE INTO ${NavtexDatabaseHelper.TABLE_NAVTEX} (
                        ${NavtexDatabaseHelper.COL_ID}, ${NavtexDatabaseHelper.COL_STATION}, 
                        ${NavtexDatabaseHelper.COL_SUBJECT}, ${NavtexDatabaseHelper.COL_SEQUENCE}, 
                        ${NavtexDatabaseHelper.COL_TIMESTAMP}, ${NavtexDatabaseHelper.COL_BODY}, 
                        ${NavtexDatabaseHelper.COL_POINTS}, ${NavtexDatabaseHelper.COL_URGENT}
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                db.execSQL(sql, arrayOf(
                    message.id,
                    message.stationLetter.toString(),
                    message.subject.code.toString(),
                    message.sequenceNumber,
                    message.timestamp,
                    message.body,
                    pointsToString(message.points),
                    if (message.isUrgent) 1 else 0
                ))
                
                val now = System.currentTimeMillis()
                if (now - lastCleanupTime > CLEANUP_INTERVAL) {
                    cleanupExpiredInternal(db)
                    lastCleanupTime = now
                }
                
                db.setTransactionSuccessful()
                refreshTrigger.emit(Unit)
            } finally {
                db.endTransaction()
                db.close()
            }
        }
    }

    private fun pointsToString(points: List<LatLon>): String {
        return points.joinToString(";") { "${it.latitude},${it.longitude}" }
    }

    private fun stringToPoints(data: String?): List<LatLon> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) LatLon(lat, lon) else null
            } else null
        }
    }

    fun refreshMessages() {
        refreshTrigger.tryEmit(Unit)
    }

    private suspend fun performRefresh() = withContext(Dispatchers.IO) {
        val db = dbHelper.openConnection(true) ?: return@withContext
        try {
            val list = mutableListOf<NavtexMessage>()
            db.rawQuery(
                "SELECT * FROM ${NavtexDatabaseHelper.TABLE_NAVTEX} ORDER BY ${NavtexDatabaseHelper.COL_TIMESTAMP} DESC", 
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        list.add(readMessage(cursor))
                    } while (cursor.moveToNext())
                }
            }
            _messages.value = list
        } finally {
            db.close()
        }
    }

    private fun cleanupExpiredInternal(db: net.osmand.plus.api.SQLiteAPI.SQLiteConnection) {
        val now = System.currentTimeMillis()
        val expiryHours = maxOf(1L, app.settings.NAUTICAL_NAVTEX_EXPIRY_HOURS.get().toLong())
        val expiryMs = expiryHours * 60L * 60L * 1000L

        db.execSQL(
            "DELETE FROM ${NavtexDatabaseHelper.TABLE_NAVTEX} WHERE ${NavtexDatabaseHelper.COL_TIMESTAMP} < ?",
            arrayOf(now - expiryMs)
        )
    }

    suspend fun cleanupExpired() = withContext(Dispatchers.IO) {
        NauticalIOQueue.writeMutex.withLock {
            val db = dbHelper.openConnection(false) ?: return@withLock
            try {
                db.beginTransactionNonExclusive()
                cleanupExpiredInternal(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }
        }
    }

    private fun readMessage(cursor: SQLiteCursor): NavtexMessage {
        return NavtexMessage(
            id = cursor.getString(cursor.getColumnIndex(NavtexDatabaseHelper.COL_ID)),
            stationLetter = cursor.getString(cursor.getColumnIndex(NavtexDatabaseHelper.COL_STATION)).getOrNull(0) ?: ' ',
            subject = NavtexSubject.fromCode(cursor.getString(cursor.getColumnIndex(NavtexDatabaseHelper.COL_SUBJECT)).getOrNull(0) ?: ' '),
            sequenceNumber = cursor.getInt(cursor.getColumnIndex(NavtexDatabaseHelper.COL_SEQUENCE)),
            timestamp = cursor.getLong(cursor.getColumnIndex(NavtexDatabaseHelper.COL_TIMESTAMP)),
            body = cursor.getString(cursor.getColumnIndex(NavtexDatabaseHelper.COL_BODY)),
            points = stringToPoints(cursor.getString(cursor.getColumnIndex(NavtexDatabaseHelper.COL_POINTS))),
            isUrgent = cursor.getInt(cursor.getColumnIndex(NavtexDatabaseHelper.COL_URGENT)) == 1
        )
    }
}
