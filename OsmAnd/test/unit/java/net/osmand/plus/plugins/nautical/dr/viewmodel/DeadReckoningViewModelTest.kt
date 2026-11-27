package net.osmand.plus.plugins.nautical.dr.viewmodel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.dr.engine.FixSource
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeadReckoningViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun testApp(): OsmandApplication {
        val app = mockk<OsmandApplication>(relaxed = true)
        // The view model reads persisted DR state through OsmandSettings
        // preference fields, which mockk cannot intercept; a real settings
        // object over an in-memory SettingsAPI provides the defaults.
        every { app.settings } returns TestOsmandSettings.create(app)
        return app
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel should start in GPS mode when repository has data`() = runTest(testDispatcher) {
        val repo = mockk<SailingPerformanceRepository>()
        val flow = MutableStateFlow(LivePerformanceData(latitude = 50.0, longitude = 0.0))
        every { repo.livePerformanceData } returns flow
        
        val app = testApp()
        val viewModel = DeadReckoningViewModel(app, repo)
        
        // Run the queued collectors without fast-forwarding virtual time:
        // the watchdog and projection loops reschedule forever, so
        // advanceUntilIdle would never return.
        testDispatcher.scheduler.runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(FixSource.GPS, state.source)
        assertEquals(50.0, state.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `viewModel should transition to DR after 3 seconds of GPS loss`() = runTest(testDispatcher) {
        val repo = mockk<SailingPerformanceRepository>()
        val now = System.currentTimeMillis()
        // The last valid fix is already older than the 3 second staleness
        // window. The watchdog compares real timestamps, so virtual time
        // cannot be used to age the fix.
        val flow = MutableStateFlow(
            LivePerformanceData(latitude = 50.0, longitude = 0.0, timestamp = now - 5000)
        )
        every { repo.livePerformanceData } returns flow
        
        val app = testApp()
        val viewModel = DeadReckoningViewModel(app, repo)
        testDispatcher.scheduler.runCurrent()
        assertEquals(FixSource.GPS, viewModel.uiState.value.source)

        // Simulate GPS loss: coordinates stop arriving.
        flow.value = LivePerformanceData(latitude = null, longitude = null, timestamp = now - 4000)

        // The staleness branch fires on the next collect pass (the last
        // valid fix is more than 3 seconds old) and the projection
        // coroutine publishes the dead reckoning state.
        testDispatcher.scheduler.runCurrent()

        assertEquals(FixSource.DEAD_RECKONING, viewModel.uiState.value.source)
    }
}
