package net.osmand.plus.plugins.nautical.engine

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.backend.preferences.CommonPreference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SignalKEnginePipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var app: OsmandApplication
    private lateinit var settings: OsmandSettings
    private lateinit var engine: SignalKEngine
    private lateinit var aisManager: NauticalAisManager
    private lateinit var multiplexer: DirectNmeaMultiplexer
    private lateinit var aggregator: SailingDataAggregator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        app = mockk<OsmandApplication>(relaxed = true)
        settings = mockk<OsmandSettings>(relaxed = true)
        every { app.settings } returns settings

        val xtePref = mockk<CommonPreference<Float>>(relaxed = true)
        every { xtePref.get() } returns 0.1f
        every { settings.NAUTICAL_XTE_THRESHOLD } returns xtePref

        val draftPref = mockk<CommonPreference<Float>>(relaxed = true)
        every { draftPref.get() } returns 1.5f
        every { settings.NAUTICAL_VESSEL_DRAFT } returns draftPref

        val corridorPref = mockk<CommonPreference<Float>>(relaxed = true)
        every { corridorPref.get() } returns 0.5f
        every { settings.NAUTICAL_CORRIDOR_WIDTH } returns corridorPref

        val bufferPref = mockk<CommonPreference<Float>>(relaxed = true)
        every { bufferPref.get() } returns 0.1f
        every { settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER } returns bufferPref

        val arrivalPref = mockk<CommonPreference<Float>>(relaxed = true)
        every { arrivalPref.get() } returns 50.0f
        every { settings.NAUTICAL_ARRIVAL_RADIUS } returns arrivalPref

        engine = SignalKEngine(app, testScope)
        aisManager = NauticalAisManager(app)
        engine.registerAisListener { target ->
            aisManager.onAisObjectReceived(target)
        }

        aggregator = SailingDataAggregator()
        val parser = NmeaSentenceParser(app)
        multiplexer = DirectNmeaMultiplexer(app, aggregator, testScope, parser)
        multiplexer.deltaConsumer = { delta -> engine.handleDelta(delta) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testNmeaRmcPipelineUpdatesSpeedAndCourse() = runTest(testDispatcher) {
        val rmcSentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        multiplexer.injectSentence(rmcSentence)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = engine.getCurrentState()
        // SOG: 22.4 knots * 0.514444 = 11.5235456 m/s
        assertNotNull(state.speedOverGround)
        assertEquals(11.5235, state.speedOverGround!!, 0.001)

        // COG: 84.4 deg in radians
        assertNotNull(state.courseOverGroundTrue)
        assertEquals(Math.toRadians(84.4), state.courseOverGroundTrue!!, 0.001)
    }

    @Test
    fun testNmeaDbtPipelineUpdatesDepth() = runTest(testDispatcher) {
        val dbtSentence = "\$IIDBT,033.1,f,010.1,M,005.4,F*25"
        multiplexer.injectSentence(dbtSentence)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = engine.getCurrentState()
        assertNotNull(state.depthBelowTransducer)
        assertEquals(10.1, state.depthBelowTransducer!!, 0.001)
    }

    @Test
    fun testSignalKDeltaJsonPipelineUpdatesPositionAndSpeed() = runTest(testDispatcher) {
        val jsonDelta = """
            {
              "context": "vessels.self",
              "updates": [
                {
                  "timestamp": "2026-08-15T12:00:00.000Z",
                  "values": [
                    { "path": "navigation.position", "value": { "latitude": 37.7749, "longitude": -122.4194 } },
                    { "path": "navigation.speedOverGround", "value": 6.5 }
                  ]
                }
              ]
            }
        """.trimIndent()

        engine.handleIncomingMessage(jsonDelta)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = engine.getCurrentState()
        assertNotNull(state.latitude)
        assertEquals(37.7749, state.latitude!!, 0.0001)
        assertNotNull(state.longitude)
        assertEquals(-122.4194, state.longitude!!, 0.0001)
        assertNotNull(state.speedOverGround)
        assertEquals(6.5, state.speedOverGround!!, 0.001)
    }

    @Test
    fun testAisDeltaPipelineUpdatesAisManager() = runTest(testDispatcher) {
        val aisDelta = DeltaMessage(
            context = "vessels.urn:mrn:imo:mmsi:244010952",
            updates = listOf(
                Update(
                    timestamp = "2026-08-15T12:00:00.000Z",
                    source = null,
                    values = listOf(
                        Value("navigation.position", mapOf("latitude" to 52.3702, "longitude" to 4.8952)),
                        Value("name", "TEST_VESSEL")
                    )
                )
            )
        )

        engine.handleDelta(aisDelta)

        testDispatcher.scheduler.advanceUntilIdle()

        val aisObjects = aisManager.getAisObjects()
        assertEquals(1, aisObjects.size)

        val target = aisObjects.first()
        assertEquals(244010952, target.mmsi)
        assertEquals(52.3702, target.position?.latitude ?: 0.0, 0.0001)
        assertEquals(4.8952, target.position?.longitude ?: 0.0, 0.0001)
        assertEquals("TEST_VESSEL", target.shipName)
    }

    @Test
    fun testSignalKDeltaConnectedPaths() = runTest(testDispatcher) {
        val jsonDelta = """
            {
              "context": "vessels.self",
              "updates": [
                {
                  "timestamp": "2026-08-15T12:00:00.000Z",
                  "values": [
                    { "path": "navigation.courseRhumbline.crossTrackError", "value": 15.5 },
                    { "path": "navigation.state.flags", "value": ["moored", "anchored"] },
                    { "path": "navigation.anchor.rodeDeployed", "value": 45.0 },
                    { "path": "environment.moon.phase", "value": 0.5 },
                    { "path": "environment.sunlight.mode", "value": "night" },
                    { "path": "steering.autopilot.seaState", "value": 3 },
                    { "path": "design.length.overall", "value": 12.5 },
                    { "path": "design.beam", "value": 3.8 },
                    { "path": "entertainment.device.fusion.title", "value": "Nautical Song" },
                    { "path": "entertainment.device.fusion.artist", "value": "Sea Band" }
                  ]
                }
              ]
            }
        """.trimIndent()

        engine.handleIncomingMessage(jsonDelta)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = engine.getCurrentState()
        assertEquals(15.5, state.crossTrackError!!, 0.001)
        assertTrue(state.flags.contains("moored"))
        assertEquals(45.0, state.rodeDeployed!!, 0.001)
        assertEquals(0.5, state.moonPhase!!, 0.001)
        assertEquals("night", state.sunlightMode)
        assertEquals(3, state.seaState)
        assertEquals(12.5, state.vesselLength!!, 0.001)
        assertEquals(3.8, state.vesselBeam!!, 0.001)
        assertEquals("Nautical Song", state.mediaInfo?.title)
        assertEquals("Sea Band", state.mediaInfo?.artist)
    }
}
