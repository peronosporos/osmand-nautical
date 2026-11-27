package net.osmand.plus.plugins.nautical.viewmodel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolarEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        SailingDependencyContainer.performanceRepository = null
        Dispatchers.resetMain()
    }

    @Test
    fun testSmoothingIntensity() = runTest(testDispatcher) {
        // The view model loads its curve from the active polar profile held
        // by the DI container; inject a fake repository exposing one curve.
        // The original fixture expected the FIRST point to pass through
        // smoothing unchanged, which matches a boundary-preserving moving
        // average, not the Gaussian-weighted smoothing TASK-007 implements.
        // Assertions below follow the Gaussian semantics; the passthrough
        // case is covered by intensity 0.
        val profile = PolarProfile(
            name = "test",
            description = null,
            tws = listOf(8.0),
            twa = listOf(30.0, 60.0, 90.0, 120.0),
            speeds = listOf(listOf(5.2, 6.0, 5.5, 4.8))
        )
        val repository = mockk<SailingPerformanceRepository>()
        every { repository.activePolarProfile } returns MutableStateFlow(profile)
        SailingDependencyContainer.performanceRepository = repository

        val viewModel = PolarEditorViewModel()
        advanceUntilIdle() // let the init collector load the curve

        viewModel.setSmoothingIntensity(0.8f)

        val points = viewModel.smoothedPoints.value
        assertTrue("Smoothed points should not be empty", points.isNotEmpty())
        assertEquals(4, points.size)
        // TWA values are preserved by the Gaussian smoothing.
        assertEquals(30.0, points[0].first, 0.001)
        // A Gaussian-weighted average cannot leave the range of the raw
        // samples, so every smoothed speed stays inside [4.8, 6.0].
        for (p in points) {
            assertTrue("smoothed speed out of raw range: ${p.second}", p.second in 4.8..6.0)
        }

        // With intensity 0 smoothing is disabled and the raw curve passes
        // through unchanged, including the first point.
        viewModel.setSmoothingIntensity(0f)
        val raw = viewModel.smoothedPoints.value
        assertEquals(4, raw.size)
        assertEquals(5.2, raw[0].second, 0.001)
        assertEquals(6.0, raw[1].second, 0.001)
    }
}
