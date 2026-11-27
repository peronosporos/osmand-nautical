package net.osmand.plus.plugins.nautical.hazard.data

import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.backend.preferences.CommonPreference
import org.junit.Before
import org.junit.Test

class NavtexRepositoryTest {

    private val app = mockk<OsmandApplication>()
    private val sqliteAPI = mockk<SQLiteAPI>()
    private val db = mockk<SQLiteAPI.SQLiteConnection>()
    private val settings = mockk<OsmandSettings>()
    private val expiryPreference = mockk<CommonPreference<Int>>()
    private val repository = NavtexRepository(app)

    @Before
    fun setUp() {
        every { app.getSQLiteAPI() } returns sqliteAPI
        // The navtex expiry preference is a public final Java field on
        // OsmandSettings, which mockk cannot intercept, so inject the
        // preference mock reflectively and route getSettings() to it.
        OsmandSettings::class.java
            .getField("NAUTICAL_NAVTEX_EXPIRY_HOURS")
            .set(settings, expiryPreference)
        every { app.getSettings() } returns settings
        every { expiryPreference.get() } returns 48
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
