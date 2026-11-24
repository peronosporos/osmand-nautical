package net.osmand.plus.plugins.nautical.hazard.viewmodel

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osmand.Location
import net.osmand.plus.OsmandApplication
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.util.MapUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavtexViewModelTest {

    private val app = mockk<OsmandApplication>()
    private val repository = mockk<NavtexRepository>()
    private val locationProvider = mockk<OsmAndLocationProvider>()
    private val messagesFlow = MutableStateFlow<List<NavtexMessage>>(emptyList())
    
    private val viewModel by lazy { NavtexViewModel(app, repository) }

    @Before
    fun setUp() {
        every { app.locationProvider } returns locationProvider
        every { locationProvider.lastKnownLocation } returns null
        every { locationProvider.addLocationListener(any()) } just Runs
        every { locationProvider.removeLocationListener(any()) } just Runs
        every { repository.messages } returns messagesFlow
        coEvery { repository.refreshMessages() } just Runs
        
        mockkStatic(MapUtils::class)
    }

    @Test
    fun testUrgentFiltering() = runTest {
        val msg1 = NavtexMessage("1", 'A', NavtexSubject.NAVTEX_WARNING, 1, 0, "", isUrgent = true)
        val msg2 = NavtexMessage("2", 'A', NavtexSubject.UNKNOWN, 2, 0, "", isUrgent = false)
        messagesFlow.value = listOf(msg1, msg2)

        viewModel.setUrgentOnly(true)
        val state = viewModel.uiState.first()
        
        assertEquals(1, state.messages.size)
        assertEquals("1", state.messages[0].id)
    }

    @Test
    fun testProximityFiltering() = runTest {
        val msg1 = NavtexMessage("1", 'A', NavtexSubject.NAVTEX_WARNING, 1, 0, "", 
            coordinates = net.osmand.data.LatLon(10.0, 10.0))
        messagesFlow.value = listOf(msg1)

        val loc = mockk<Location>()
        every { loc.latitude } returns 0.0
        every { loc.longitude } returns 0.0
        every { locationProvider.lastKnownLocation } returns loc

        // Mock distance to be 200km
        every { MapUtils.getDistance(0.0, 0.0, 10.0, 10.0) } returns 200000.0

        viewModel.setMaxDistance(100.0) // Filter for 100km
        val state = viewModel.uiState.first()
        assertEquals(0, state.messages.size)

        viewModel.setMaxDistance(300.0) // Filter for 300km
        val state2 = viewModel.uiState.first()
        assertEquals(1, state2.messages.size)
    }
}
