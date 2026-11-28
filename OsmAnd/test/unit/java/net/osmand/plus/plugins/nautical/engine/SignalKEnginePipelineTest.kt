package net.osmand.plus.plugins.nautical.engine

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SignalKEnginePipelineTest {

    private val testDispatcher = StandardTestDispatcher()

    // The engine schedules periodic maintenance (5-minute autosave) on the
    // scope it is given. On the shared test scheduler runTest would chase
    // those re-arming delays forever after the body completes and the
    // worker would never go idle, so keep the engine off the virtual clock.
    // The engine consumes sentences on its own Default-dispatcher scopes
    // and the tests wait for outcomes with awaitPipeline real-time polls.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        // OsmandSettings preferences are public final Java fields, so mockk
        // cannot intercept them. Use a real settings object backed by an
        // in-memory SettingsAPI instead.
        settings = TestOsmandSettings.create(app)
        every { app.settings } returns settings
        // WearOsNauticalManager.isWatchMode() (reached from every telemetry
        // buffer access) reads settings through context.applicationContext;
        // on a bare relaxed mock that is a fresh mock whose final settings
        // fields are null, so the delta batch would die on an NPE and be
        // silently dropped. Route it back to the stubbed app instance.
        every { app.applicationContext } returns app
        // Pin the values this suite was written against so it does not
        // depend on the defaults registered in OsmandSettings.
        settings.NAUTICAL_XTE_THRESHOLD.set(0.1f)
        settings.NAUTICAL_VESSEL_DRAFT.set(1.5f)
        settings.NAUTICAL_CORRIDOR_WIDTH.set(0.5f)
        settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.set(0.1f)
        settings.NAUTICAL_ARRIVAL_RADIUS.set(50.0f)

        engine = SignalKEngine(app, engineScope)
        aisManager = NauticalAisManager(app)
        engine.registerAisListener { target ->
            aisManager.onAisObjectReceived(target)
        }
        // AIS deltas route through NauticalPlugin.getInstance()?.aisManager
        // before they reach the engine listeners; install a mock plugin
        // (objenesis mock, no Android plugin lifecycle) that hands the
        // routing to this suite's manager.
        val plugin = mockk<NauticalPlugin>(relaxed = true)
        every { plugin.aisManager } returns aisManager
        NauticalPlugin.setInstanceForTesting(plugin)

        aggregator = SailingDataAggregator()
        val parser = NmeaSentenceParser(app)
        multiplexer = DirectNmeaMultiplexer(app, aggregator, engineScope, parser)
        multiplexer.deltaConsumer = { delta -> engine.handleDelta(delta) }
        // Deltas that arrive as Signal K "source.sentence" or "raw" updates
        // are routed through the container's multiplexer rather than this
        // instance, so register it for the duration of the suite.
        SailingDependencyContainer.setNmeaMultiplexer(multiplexer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Stops the engine maintenance and multiplexer worker threads
        // (their IO loops would otherwise leak across tests).
        engineScope.cancel()
        SailingDependencyContainer.setNmeaMultiplexer(null)
        NauticalPlugin.setInstanceForTesting(null)
    }

    @Test
    fun testNmeaRmcPipelineUpdatesSpeedAndCourse() = runTest(testDispatcher) {
        val rmcSentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        multiplexer.injectSentence(rmcSentence)

        assertTrue("RMC pipeline did not settle in time", awaitPipeline {
            val state = engine.getCurrentState()
            state.speedOverGround != null && state.courseOverGroundTrue != null
        })

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
        val dbtSentence = "\$IIDBT,033.1,f,010.1,M,005.4,F*11"
        multiplexer.injectSentence(dbtSentence)

        assertTrue("DBT pipeline did not settle in time", awaitPipeline {
            engine.getCurrentState().depthBelowTransducer != null
        })

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

        assertTrue("position delta pipeline did not settle in time", awaitPipeline {
            val state = engine.getCurrentState()
            state.latitude != null && state.speedOverGround != null
        })

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

        // Settle on the target's position, not on the object's existence:
        // the object is registered before its values are applied, and the
        // assertion below needs the position to have landed.
        assertTrue("AIS delta pipeline did not settle in time", awaitPipeline {
            aisManager.getAisObjects().firstOrNull()?.position != null
        })

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
        assertTrue("connected-paths delta did not settle in time", awaitPipeline {
            engine.getCurrentState().crossTrackError != null
        })

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

    @Test
    fun testSourceSentenceWithoutValuesArrayRoutesToMultiplexer() = runTest(testDispatcher) {
        val jsonDelta = """
            {
              "context": "vessels.self",
              "updates": [
                {
                  "timestamp": "2026-08-15T12:00:00.000Z",
                  "source": { "sentence": "MWV", "talker": "II" },
                  "raw": "${'$'}IIMWV,045.0,R,12.5,N,A*0A"
                }
              ]
            }
        """.trimIndent()

        engine.handleIncomingMessage(jsonDelta)

        assertTrue("source-sentence delta did not settle in time", awaitPipeline {
            engine.getCurrentState().windSpeedApparent != null
        })

        val state = engine.getCurrentState()
        // 12.5 knots * 0.514444 = 6.43055 m/s
        assertNotNull(state.windSpeedApparent)
        assertEquals(6.4305, state.windSpeedApparent!!, 0.01)
        assertNotNull(state.windDirectionApparent)
        assertEquals(Math.toRadians(45.0), state.windDirectionApparent!!, 0.001)
    }

    @Test
    fun testDecomposedKeysInUpdateObjectDirectly() = runTest(testDispatcher) {
        val jsonDelta = """
            {
              "context": "vessels.self",
              "updates": [
                {
                  "timestamp": "2026-08-15T12:00:00.000Z",
                  "sog": 7.2,
                  "depth": 14.5,
                  "latitude": 54.321,
                  "longitude": 10.123
                }
              ]
            }
        """.trimIndent()

        engine.handleIncomingMessage(jsonDelta)

        assertTrue("decomposed-keys delta did not settle in time", awaitPipeline {
            val state = engine.getCurrentState()
            state.speedOverGround != null && state.latitude != null
        })

        val state = engine.getCurrentState()
        assertNotNull(state.speedOverGround)
        assertEquals(7.2, state.speedOverGround!!, 0.001)
        assertNotNull(state.depthBelowTransducer)
        assertEquals(14.5, state.depthBelowTransducer!!, 0.001)
        assertNotNull(state.latitude)
        assertEquals(54.321, state.latitude!!, 0.0001)
        assertNotNull(state.longitude)
        assertEquals(10.123, state.longitude!!, 0.0001)
    }

    @Test
    fun testVwtAndVtgSentenceParsing() = runTest(testDispatcher) {
        // VWT: True wind 30 deg starboard, 15.0 knots -> 7.71666 m/s
        val vwtSentence = "${'$'}IIVWT,030.0,R,15.0,N,07.7,M,027.8,K*45"
        multiplexer.injectSentence(vwtSentence)
        assertTrue("VWT pipeline did not settle in time", awaitPipeline {
            engine.getCurrentState().windSpeedTrue != null
        })

        val state = engine.getCurrentState()
        assertNotNull(state.windSpeedTrue)
        assertEquals(7.7, state.windSpeedTrue!!, 0.05)

        // VTG: COG 120.5 deg, SOG 8.5 knots -> 4.37277 m/s
        val vtgSentence = "${'$'}GPVTG,120.5,T,118.0,M,08.5,N,015.7,K,A*13"
        multiplexer.injectSentence(vtgSentence)
        assertTrue("VTG pipeline did not settle in time", awaitPipeline {
            val state = engine.getCurrentState()
            state.courseOverGroundTrue != null && state.speedOverGround != null
        })

        val state2 = engine.getCurrentState()
        assertNotNull(state2.courseOverGroundTrue)
        assertEquals(Math.toRadians(120.5), state2.courseOverGroundTrue!!, 0.001)
        assertNotNull(state2.speedOverGround)
        assertEquals(4.3727, state2.speedOverGround!!, 0.01)
    }
}

