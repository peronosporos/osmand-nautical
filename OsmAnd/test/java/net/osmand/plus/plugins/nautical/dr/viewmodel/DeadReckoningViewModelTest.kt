package net.osmand.plus.plugins.nautical.dr.viewmodel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import net.osmand.plus.plugins.nautical.dr.engine.FixSource
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeadReckoningViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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
        
        val viewModel = DeadReckoningViewModel(repo)
        
        // Advance until onEach is triggered
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(FixSource.GPS, state.source)
        assertEquals(50.0, state.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `viewModel should transition to DR after 3 seconds of GPS loss`() = runTest(testDispatcher) {
        val repo = mockk<SailingPerformanceRepository>()
        val now = System.currentTimeMillis()
        val flow = MutableStateFlow(LivePerformanceData(latitude = 50.0, longitude = 0.0, timestamp = now))
        every { repo.livePerformanceData } returns flow
        
        val viewModel = DeadReckoningViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(FixSource.GPS, viewModel.uiState.value.source)

        // Simulate GPS loss by updating flow with null coordinates and an old timestamp
        flow.value = LivePerformanceData(latitude = null, longitude = null, timestamp = now - 4000)
        
        // Advance time to trigger the periodic watchdog loop
        testDispatcher.scheduler.advanceTimeBy(2000)
        testDispatcher.scheduler.runCurrent()

        assertEquals(FixSource.DEAD_RECKONING, viewModel.uiState.value.source)
    }
}
