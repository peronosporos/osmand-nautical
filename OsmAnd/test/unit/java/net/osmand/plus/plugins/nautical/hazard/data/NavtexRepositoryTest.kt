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
        // Remaining blocker: cleanupExpiredInternal reads the expiry from
        // app.settings.NAUTICAL_NAVTEX_EXPIRY_HOURS, a public final Java
        // field on OsmandSettings. Neither mockk interception nor reflective
        // Field.set can supply a final field (JDK 17 rejects it), so these
        // tests stay red until the settings injection round provides an
        // OsmandSettings test double. The database stubs below are the ones
        // the repository logic itself needs and stay valid for that round.
        every { sqliteAPI.getOrCreateDatabase(any(), any()) } returns db
        every { db.version } returns 1
        every { db.setVersion(any()) } just Runs
        every { db.close() } just Runs
        // The helper migrates a version-1 database (DROP TABLE + CREATE) via
        // the single-argument execSQL before any repository statement runs.
        every { db.execSQL(any()) } just Runs
        every { db.execSQL(any(), any()) } just Runs
        every { db.beginTransactionNonExclusive() } just Runs
        every { db.setTransactionSuccessful() } just Runs
        every { db.endTransaction() } just Runs
    }

    @Test
    fun testUpsertMessage() = runBlocking {
        val message = NavtexMessage(
            id = "OA00",
            stationLetter = 'O',
            subject = NavtexSubject.NAVIGATIONAL_WARNING,
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
