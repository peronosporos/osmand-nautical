package net.osmand.plus.plugins.nautical.hazard.data

import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.api.SQLiteAPI
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
        // cleanupExpiredInternal reads the expiry from
        // app.settings.NAUTICAL_NAVTEX_EXPIRY_HOURS, a public final Java
        // field on OsmandSettings that mockk cannot intercept. Provide a
        // real settings object over an in-memory SettingsAPI, which yields
        // the registered default (48 hours).
        every { app.settings } returns TestOsmandSettings.create(app)
        // The database stubs below are the ones the repository logic needs.
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
        // Default answer for the repository's debounced performRefresh():
        // every NavtexRepository constructed by a test keeps a live
        // Dispatchers.IO scope, and its 500 ms-debounced refresh fires even
        // AFTER the test method ends (JUnit builds one class instance per
        // method, so each instance leaks its own refresh). Without a
        // default rawQuery answer, MockK throws "no answer found" on that
        // background thread and kotlinx-coroutines-test attributes the
        // uncaught exception to the next runTest in the suite
        // (UncaughtExceptionsBeforeTest in SignalKRepositoryTest). Per-test
        // rawQuery stubs recorded later still take precedence.
        val emptyCursor = mockk<SQLiteAPI.SQLiteCursor>()
        every { db.rawQuery(any(), any()) } returns emptyCursor
        every { emptyCursor.moveToFirst() } returns false
        every { emptyCursor.close() } just Runs
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

    @After
    fun tearDown() {
        // getNavtexRepository() caches a process-wide singleton; reset the
        // seam so this class does not leak its instance into other tests.
        SailingDependencyContainer.setNavtexRepositoryForTesting(null)
    }

    @Test
    fun testSharedRepositorySingletonAcrossContainer() {
        // Every NAVTEX construction site (multiplexer writer, view models,
        // UI overlay) must observe the same repository instance, or
        // bulletins persisted by the multiplexer never reach UI flows.
        val repo1 = SailingDependencyContainer.getNavtexRepository(app)
        val repo2 = SailingDependencyContainer.getNavtexRepository(app)
        assertSame(repo1, repo2)

        // The test seam overrides the singleton and clears it again.
        val fake = mockk<NavtexRepository>()
        SailingDependencyContainer.setNavtexRepositoryForTesting(fake)
        assertSame(fake, SailingDependencyContainer.getNavtexRepository(app))

        SailingDependencyContainer.setNavtexRepositoryForTesting(null)
        val fresh = SailingDependencyContainer.getNavtexRepository(app)
        assertNotSame(repo1, fresh)
    }

    @Test
    fun testCleanupLogic() = runBlocking {
        // Mock cursor for refreshMessages
        val cursor = mockk<SQLiteAPI.SQLiteCursor>()
        every { db.rawQuery(match { it.contains("SELECT * FROM") }, any()) } returns cursor
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } just Runs

        repository.cleanupExpired()

        // A single DELETE: the repository and the view model deliberately
        // follow one configurable expiry rule (NAUTICAL_NAVTEX_EXPIRY_HOURS,
        // default 48 hours) instead of per-subject tiers.
        verify(exactly = 1) {
            db.execSQL(match { it.contains("DELETE FROM") }, any())
        }
    }
}
