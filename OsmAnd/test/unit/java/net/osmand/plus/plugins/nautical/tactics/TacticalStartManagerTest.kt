package net.osmand.plus.plugins.nautical.tactics

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.maneuvers.TacticalStartManager
import net.osmand.shared.util.KMapUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TacticalStartManagerTest {

    private val app: OsmandApplication = mockk(relaxed = true)
    private val mockEngine: SignalKEngine = mockk(relaxed = true)
    private lateinit var startManager: TacticalStartManager

    @Before
    fun setup() {
        mockkObject(NauticalAudioArbiter)
        every { NauticalAudioArbiter.getInstance(any()) } returns mockk(relaxed = true)

        NauticalPlugin.engine = mockEngine

        startManager = TacticalStartManager(app)
    }

    @After
    fun teardown() {
        startManager.stopTimer()
        NauticalPlugin.engine = null
        unmockkAll()
    }

    @Test
    fun testSetAndClearPins() {
        assertFalse(startManager.isPortPinSet())
        assertFalse(startManager.isStarboardPinSet())
        assertFalse(startManager.isLineSet())
        assertNull(startManager.portPin)
        assertNull(startManager.starboardPin)

        // Set port pin
        startManager.setPortPin(50.8000, -1.3000)
        assertTrue(startManager.isPortPinSet())
        assertFalse(startManager.isStarboardPinSet())
        assertFalse(startManager.isLineSet())
        assertEquals(50.8000, startManager.portPin!!.first, 1e-6)
        assertEquals(-1.3000, startManager.portPin!!.second, 1e-6)

        // Set starboard pin
        startManager.setStarboardPin(50.8010, -1.2980)
        assertTrue(startManager.isPortPinSet())
        assertTrue(startManager.isStarboardPinSet())
        assertTrue(startManager.isLineSet())
        assertEquals(50.8010, startManager.starboardPin!!.first, 1e-6)
        assertEquals(-1.2980, startManager.starboardPin!!.second, 1e-6)

        // Clear port pin
        startManager.clearPortPin()
        assertFalse(startManager.isPortPinSet())
        assertTrue(startManager.isStarboardPinSet())
        assertFalse(startManager.isLineSet())

        // Set port pin again, then clear starboard pin
        startManager.setPortPin(50.8000, -1.3000)
        startManager.clearStarboardPin()
        assertTrue(startManager.isPortPinSet())
        assertFalse(startManager.isStarboardPinSet())
        assertFalse(startManager.isLineSet())

        // Set both and call clear()
        startManager.setStarboardPin(50.8010, -1.2980)
        assertTrue(startManager.isLineSet())
        startManager.clear()
        assertFalse(startManager.isPortPinSet())
        assertFalse(startManager.isStarboardPinSet())
        assertFalse(startManager.isLineSet())
    }

    @Test
    fun testStartLineGeometryAndDistance() {
        val portLat = 50.8000
        val portLon = -1.3000
        val stbLat = 50.8000
        val stbLon = -1.2900

        startManager.setPortPin(portLat, portLon)
        startManager.setStarboardPin(stbLat, stbLon)

        val lineLength = KMapUtils.getDistance(portLat, portLon, stbLat, stbLon)
        assertTrue("Line length should be positive", lineLength > 100.0)

        // Boat right on the line midpoint
        val boatMidLat = 50.8000
        val boatMidLon = -1.2950
        val distMid = startManager.getDistanceToLine(boatMidLat, boatMidLon)
        assertNotNull(distMid)
        assertEquals(0.0, distMid!!, 1.0)

        // Boat 0.001 deg North of the line midpoint (~111m)
        val boatNorthLat = 50.8010
        val boatNorthLon = -1.2950
        val distNorth = startManager.getDistanceToLine(boatNorthLat, boatNorthLon)
        assertNotNull(distNorth)
        assertTrue("Distance should be approximately 111m", distNorth!! in 100.0..125.0)

        // Returns null when line is incomplete
        startManager.clearPortPin()
        assertNull(startManager.getDistanceToLine(boatNorthLat, boatNorthLon))
    }

    @Test
    fun testLineBiasAndFavoredPinDetermination() {
        // Line oriented East-West: Port at (0, 0), Starboard at (0, 0.01) -> Bearing 90°
        // Perpendicular pointing North is (90 + 90) = 180°
        startManager.setPortPin(0.0, 0.0)
        startManager.setStarboardPin(0.0, 0.01) // Starboard is directly East (90°)

        // When True Wind Direction (TWD) is 180° (South wind, pointing straight down the line perpendicular)
        val stateSquare = MarineState(windDirectionTrue = Math.toRadians(180.0))
        every { mockEngine.getCurrentState() } returns stateSquare

        val squareBias = startManager.getLineBias()
        assertNotNull(squareBias)
        assertEquals(0.0, squareBias!!, 0.5)

        val neutralAdvantage = startManager.getFavoredEndAdvantage(boatLengthMeters = 10.0)
        assertEquals("Square Line (Neutral)", neutralAdvantage)

        // Wind shifted right (TWD = 170° -> wind coming from 170°, bias is +10°)
        val stateStarboardFavored = MarineState(windDirectionTrue = Math.toRadians(170.0))
        every { mockEngine.getCurrentState() } returns stateStarboardFavored

        val stbBias = startManager.getLineBias()
        assertNotNull(stbBias)
        assertTrue("Bias should be positive (favoring Starboard)", stbBias!! > 0.0)

        val stbAdvantage = startManager.getFavoredEndAdvantage(boatLengthMeters = 10.0)
        assertNotNull(stbAdvantage)
        assertTrue("Should favor Starboard", stbAdvantage!!.contains("Favored: Starboard"))

        // Wind shifted left (TWD = 190° -> wind coming from 190°, bias is -10°)
        val statePortFavored = MarineState(windDirectionTrue = Math.toRadians(190.0))
        every { mockEngine.getCurrentState() } returns statePortFavored

        val portBias = startManager.getLineBias()
        assertNotNull(portBias)
        assertTrue("Bias should be negative (favoring Port)", portBias!! < 0.0)

        val portAdvantage = startManager.getFavoredEndAdvantage(boatLengthMeters = 10.0)
        assertNotNull(portAdvantage)
        assertTrue("Should favor Port", portAdvantage!!.contains("Favored: Port"))
    }

    @Test
    fun testTimerSyncAndStateTransitions() {
        // Initial state
        assertEquals(300.0, startManager.remainingSeconds.value, 1e-6)
        assertFalse(startManager.isTimerRunning.value)

        // Start timer with 5 minutes (300s)
        startManager.startTimer(300.0)
        assertTrue(startManager.isTimerRunning.value)
        assertEquals(300.0, startManager.remainingSeconds.value, 1e-6)

        // Stop timer
        startManager.stopTimer()
        assertFalse(startManager.isTimerRunning.value)

        // Reset timer to 4 minutes (240s)
        startManager.resetTimer(240.0)
        assertEquals(240.0, startManager.remainingSeconds.value, 1e-6)
        assertFalse(startManager.isTimerRunning.value)

        // Test 1-Tap Sync rounding
        // Case 1: 288s (4:48) -> snaps up to 300s (5:00)
        startManager.resetTimer(288.0)
        startManager.syncTimer()
        assertEquals(300.0, startManager.remainingSeconds.value, 1e-6)

        // Case 2: 252s (4:12) -> snaps down to 240s (4:00)
        startManager.resetTimer(252.0)
        startManager.syncTimer()
        assertEquals(240.0, startManager.remainingSeconds.value, 1e-6)

        // Case 3: 25s -> snaps down to 0s
        startManager.resetTimer(25.0)
        startManager.syncTimer()
        assertEquals(0.0, startManager.remainingSeconds.value, 1e-6)
    }

    @Test
    fun testTimeToBurnCalculation() {
        startManager.setPortPin(50.8000, -1.3000)
        startManager.setStarboardPin(50.8000, -1.2900)

        // Boat 200m away, traveling towards line at 5 m/s (~10 kn), 60s remaining on timer
        val marineState = MarineState(
            speedOverGround = 5.0,
            courseOverGroundTrue = Math.toRadians(0.0), // Heading North straight to line
            racingTimer = 60.0
        )
        every { mockEngine.getCurrentState() } returns marineState

        // Boat at 50.7980 (~222m South)
        val ttb = startManager.getTimeToBurn(50.7980, -1.2950)
        assertNotNull(ttb)
        // Time to line = ~222m / 5m/s = ~44.4s. Time to burn = 60s - 44.4s = ~15.6s
        assertTrue("TTB should be positive", ttb!! > 0.0)
    }
}
