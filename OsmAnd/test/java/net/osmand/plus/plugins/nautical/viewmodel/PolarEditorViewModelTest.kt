package net.osmand.plus.plugins.nautical.viewmodel

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
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
        Dispatchers.resetMain()
    }

    @Test
    fun testSmoothingIntensity() = runTest {
        val viewModel = PolarEditorViewModel()

        viewModel.setSmoothingIntensity(0.8f)

        viewModel.smoothedPoints.test {
            val points = awaitItem()
            assertTrue("Smoothed points should not be empty", points.isNotEmpty())
            // First point should be same as raw (boundary)
            assertEquals(5.2, points[0].second, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
