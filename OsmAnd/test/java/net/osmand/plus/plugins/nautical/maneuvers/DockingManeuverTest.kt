package net.osmand.plus.plugins.nautical.maneuvers

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator
import net.osmand.util.MapUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DockingManeuverTest {

    private val app: OsmandApplication = mockk(relaxed = true)
    private lateinit var maneuver: DockingManeuver

    @Before
    fun setup() {
        mockkStatic(MapUtils::class)
        mockkObject(NauticalHelmArbitrator)
        every { NauticalHelmArbitrator.getInstance(any()) } returns mockk(relaxed = true)
        
        maneuver = DockingManeuver(app)
        maneuver.setTarget(10.0, 10.0)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testProgressCalculation() {
        // Mocking distance: initial 100m, then 50m, then 0m
        every { MapUtils.getDistance(any(), any(), 10.0, 10.0) } returnsMany listOf(100.0, 50.0, 0.0)

        maneuver.transitionToExecuting()
        
        // 1st update: initial distance set to 100, progress 0
        maneuver.onStateUpdate(MarineState(latitude = 1.0, longitude = 1.0))
        assertEquals(0, maneuver.progressFlow.value)

        // 2nd update: current distance 50, progress 50%
        maneuver.onStateUpdate(MarineState(latitude = 1.1, longitude = 1.1))
        assertEquals(50, maneuver.progressFlow.value)

        // 3rd update: current distance 0, progress 100%
        maneuver.onStateUpdate(MarineState(latitude = 1.2, longitude = 1.2))
        assertEquals(100, maneuver.progressFlow.value)
    }

    @Test
    fun testSpeedThresholdAbort() {
        // Close distance (5m) but high speed (3.0 kn)
        every { MapUtils.getDistance(any(), any(), 10.0, 10.0) } returns 5.0
        
        maneuver.transitionToExecuting()
        maneuver.onStateUpdate(MarineState(latitude = 10.0, longitude = 10.0, speedOverGround = 3.0))
        
        assertEquals(ManeuverStateMachine.State.ABORTED, maneuver.currentState)
    }

    @Test
    fun testSuccessfulCompletion() {
        // Close distance (2m) and low speed (0.1 kn)
        every { MapUtils.getDistance(any(), any(), 10.0, 10.0) } returns 2.0
        
        maneuver.transitionToExecuting()
        maneuver.onStateUpdate(MarineState(latitude = 10.0, longitude = 10.0, speedOverGround = 0.1))
        
        assertEquals(ManeuverStateMachine.State.COMPLETED, maneuver.currentState)
    }

    @Test
    fun testSignalLostAbort() {
        maneuver.transitionToExecuting()
        // State with null coordinates
        maneuver.onStateUpdate(MarineState(latitude = null, longitude = null))
        
        assertEquals(ManeuverStateMachine.State.ABORTED, maneuver.currentState)
    }
}
