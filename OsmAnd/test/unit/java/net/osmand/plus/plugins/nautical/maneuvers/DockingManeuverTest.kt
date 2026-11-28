package net.osmand.plus.plugins.nautical.maneuvers

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.NauticalHelmArbitrator
import net.osmand.plus.settings.backend.TestOsmandSettings
import net.osmand.plus.utils.OsmAndFormatter
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
        // ManeuverEngine launches its timeout timer on Dispatchers.Main,
        // which does not exist on a plain JVM and makes engineScope.launch
        // throw MissingMainDispatcherException (an IllegalStateException).
        Dispatchers.setMain(StandardTestDispatcher())

        mockkStatic(MapUtils::class)
        mockkObject(NauticalHelmArbitrator)
        every { NauticalHelmArbitrator.getInstance(any()) } returns mockk(relaxed = true)

        // speak() reaches NauticalAudioArbiter, whose real constructor needs
        // an AudioManager from the OS; substitute a relaxed mock.
        mockkObject(NauticalAudioArbiter)
        every { NauticalAudioArbiter.getInstance(any()) } returns mockk(relaxed = true)

        // onStateUpdate formats instructions through OsmAndFormatter, which
        // reads METRIC_SYSTEM and SPEED_SYSTEM preference fields on the
        // app's settings. Those are public final Java fields that mockk
        // cannot intercept, so provide a real settings object over an
        // in-memory SettingsAPI.
        every { app.settings } returns TestOsmandSettings.create(app)

        // getFormattedSpeed also derives the current application mode, whose
        // getDefaultSpeed reaches the static OsmandApplication reference that
        // is only assigned by the Android app startup and is null on a plain
        // JVM. Formatting is a UI concern for this test, so stub the two
        // formatter entry points the maneuver uses.
        // OsmAndFormatter's static initializer calls its own statics (the
        // day-name table via getLettersStringArray, setTwelveHoursFormatting
        // for the time formatters). If that initialization runs while the
        // static mock is recording, the intercepted initializer calls land
        // inside the every-block and MockK's recording determinism check
        // aborts with "every/verify {} block were run several times".
        // Initialize the class before the mock is installed so only the
        // stubbed call is recorded on every run of the block.
        Class.forName("net.osmand.plus.utils.OsmAndFormatter")
        mockkStatic(OsmAndFormatter::class)
        every { OsmAndFormatter.getFormattedDistance(any(), any<OsmandApplication>()) } returns "100 m"
        every { OsmAndFormatter.getFormattedSpeed(any(), any<OsmandApplication>()) } returns "5 kn"

        maneuver = DockingManeuver(app)
        maneuver.setTarget(10.0, 10.0)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
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

        // 3rd update: distance 0 with zero speed is an arrival, and the
        // completionDist gate in DockingManeuver.onStateUpdate auto-completes
        // the maneuver on arrival instead of pushing a final progress value,
        // so the progress flow keeps the last approach value. Pin that
        // behavior explicitly.
        maneuver.onStateUpdate(MarineState(latitude = 1.2, longitude = 1.2))
        assertEquals(ManeuverStateMachine.State.COMPLETED, maneuver.currentState)
        assertEquals(50, maneuver.progressFlow.value)
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
