package net.osmand.plus.plugins.nautical.hazard.data

import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import org.junit.Before
import org.junit.Test

class NavtexRepositoryTest {

    private val app = mockk<OsmandApplication>()
    private val sqliteAPI = mockk<SQLiteAPI>()
    private val db = mockk<SQLiteAPI.SQLiteConnection>()
    private val repository = NavtexRepository(app)

    @Before
    fun setUp() {
        every { app.getSQLiteAPI() } returns sqliteAPI
        every { sqliteAPI.getOrCreateDatabase(any(), any()) } returns db
        every { db.version } returns 1
        every { db.close() } just Runs
        every { db.execSQL(any(), any()) } just Runs
    }

    @Test
    fun testUpsertMessage() = runBlocking {
        val message = NavtexMessage(
            id = "OA00",
            stationLetter = 'O',
            subject = NavtexSubject.NAVTEX_WARNING,
            sequenceNumber = 0,
            timestamp = System.currentTimeMillis(),
            body = "TEST BODY",
            isUrgent = true
        )

        // Mock existing message check
        val existingCursor = mockk<SQLiteAPI.SQLiteCursor>()
        every { db.rawQuery(match { it.contains("SELECT timestamp, body") }, any()) } returns existingCursor
        every { existingCursor.moveToFirst() } returns false
        every { existingCursor.close() } just Runs

        // Mock cursor for refreshMessages (triggered by emit)
        val refreshCursor = mockk<SQLiteAPI.SQLiteCursor>()
        every { db.rawQuery(match { it.contains("SELECT * FROM") }, any()) } returns refreshCursor
        every { refreshCursor.moveToFirst() } returns false
        every { refreshCursor.close() } just Runs

        repository.upsertMessage(message)

        verify {
            db.execSQL(match { it.contains("INSERT OR REPLACE") }, any())
        }
    }

    @Test
    fun testCleanupLogic() = runBlocking {
        // Mock cursor for refreshMessages
        val cursor = mockk<SQLiteAPI.SQLiteCursor>()
        every { db.rawQuery(match { it.contains("SELECT * FROM") }, any()) } returns cursor
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } just Runs

        repository.cleanupExpired()

        // Verify two DELETE calls: one for 72h, one for 24h met warnings
        verify(exactly = 2) {
            db.execSQL(match { it.contains("DELETE FROM") }, any())
        }
    }
}
