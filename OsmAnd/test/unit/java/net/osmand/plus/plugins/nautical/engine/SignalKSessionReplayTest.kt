package net.osmand.plus.plugins.nautical.engine

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Replay-first regression harness: replays recorded Signal K delta sessions through the
 * real engine (ingest -> parser -> broker -> finalize) and asserts the aggregated golden
 * state. New sessions are added to [RecordedSignalSessions]; every previously-fixed
 * ingestion defect is pinned by at least one assertion here so it cannot silently return.
 */
class SignalKSessionReplayTest {

    private val testDispatcher = StandardTestDispatcher()

    // The engine schedules periodic maintenance (5-minute autosave) on the
    // scope it is given. On the shared test scheduler runTest would chase
    // those re-arming delays forever after the body completes, so keep the
    // engine off the virtual clock. The tests wait with awaitPipeline
    // real-time polls, matching the engine's Default-dispatcher processing.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var app: OsmandApplication
    private lateinit var engine: SignalKEngine
    private lateinit var aisManager: NauticalAisManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        app = mockk<OsmandApplication>(relaxed = true)
        // OsmandSettings preferences are public final Java fields, so mockk
        // cannot intercept them. Use a real settings object backed by an
        // in-memory SettingsAPI instead.
        val settings = TestOsmandSettings.create(app)
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

        val aisManager = NauticalAisManager(app)
        engine = SignalKEngine(app, engineScope)
        engine.registerAisListener { target -> aisManager.onAisObjectReceived(target) }
        this.aisManager = aisManager
        // AIS contacts in the recorded sessions route through
        // NauticalPlugin.getInstance()?.aisManager before reaching the engine
        // listeners; install a mock plugin (objenesis mock, no Android plugin
        // lifecycle) that hands the routing to this suite's manager.
        val plugin = mockk<NauticalPlugin>(relaxed = true)
        every { plugin.aisManager } returns aisManager
        NauticalPlugin.setInstanceForTesting(plugin)

        val aggregator = SailingDataAggregator()
        val parser = NmeaSentenceParser(app)
        val multiplexer = DirectNmeaMultiplexer(app, aggregator, engineScope, parser)
        multiplexer.deltaConsumer = { delta -> engine.handleDelta(delta) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Stops the engine maintenance and multiplexer worker threads
        // (their IO loops would otherwise leak across tests).
        engineScope.cancel()
        NauticalPlugin.setInstanceForTesting(null)
    }

    @Test
    fun `recorded passage produces golden end state`() = runTest(testDispatcher) {
        RecordedSignalSessions.SHORT_PASSAGE.forEach { engine.handleIncomingMessage(it) }
        assertTrue("recorded passage did not settle in time", awaitPipeline {
            val state = engine.getCurrentState()
            state.timestamps.containsKey(RecordedSignalSessions.IGNORED_DEPTH_PATH) &&
                aisManager.getAisObjects().isNotEmpty()
        })

        val state = engine.getCurrentState()

        // Position and velocity from the LAST valid fix in the recording.
        assertEquals(37.7802, state.latitude!!, 0.0001)
        assertEquals(-122.4245, state.longitude!!, 0.0001)
        assertEquals(6.9, state.speedOverGround!!, 0.001)

        // Depth keeps the last VALID value; the textual "about ten" was rejected.
        assertEquals(10.5, state.depthBelowTransducer!!, 0.001)

        // Heading/wind/COG as recorded.
        assertEquals(1.22, state.headingTrue!!, 0.001)
        assertEquals(1.309, state.courseOverGroundTrue!!, 0.001)
        assertEquals(0.7854, state.windDirectionApparent!!, 0.001)
        assertEquals(8.0, state.windSpeedApparent!!, 0.001)
        assertEquals(5.8, state.speedThroughWater!!, 0.001)

        // Session-order regressions:
        // 1. "self" in a message must not suppress updates processing (old isHello bug):
        //    proven by every non-null assertion above, all from self-bearing messages.
        // 2. The malformed depth value at 12:00:04 must NOT have refreshed the depth
        //    timestamp, so staleness checks would flag a dead sensor instead of trusting it.
        val depthStampedAt = state.timestamps[RecordedSignalSessions.IGNORED_DEPTH_PATH]
        assertNotNull("valid depth sample must stamp its path", depthStampedAt)
        assertEquals(
            "malformed value must not refresh the path timestamp",
            net.osmand.plus.plugins.nautical.utils.TemporalUtils.parseIso8601(RecordedSignalSessions.IGNORED_DEPTH_SENT_AT),
            depthStampedAt
        )

        // AIS contact captured through the JSON ingest path (whole delta stream, not NMEA).
        val contacts = aisManager.getAisObjects()
        assertEquals("golden session must yield exactly one AIS contact", 1, contacts.size)
        assertEquals(244010952, contacts.first().mmsi)
    }

    @Test
    fun `passthrough of self identity and mmsi extraction`() = runTest(testDispatcher) {
        RecordedSignalSessions.SHORT_PASSAGE.forEach { engine.handleIncomingMessage(it) }
        assertTrue("self identity did not settle in time", awaitPipeline {
            engine.sessionManager.trueSelfContext != "vessels.self"
        })

        // The "self" of the first message must become the true context.
        assertEquals(
            "vessels.urn:mrn:imo:mmsi:230111111",
            engine.sessionManager.trueSelfContext
        )
    }

    @Test
    fun `stw only stream does not mask sog staleness`() = runTest(testDispatcher) {
        RecordedSignalSessions.STW_ONLY.forEach { engine.handleIncomingMessage(it) }
        assertTrue("STW-only stream did not settle in time", awaitPipeline {
            engine.getCurrentState().speedThroughWater != null
        })

        val state = engine.getCurrentState()
        assertEquals("STW must be ingested", 5.5, state.speedThroughWater!!, 0.001)
        assertTrue(
            "STW must record its own fix time",
            state.timeOfStwFix > 0L
        )
        assertEquals(
            "STW must not refresh the GPS/SOG channel staleness",
            0L,
            state.timeOfSogFix
        )
        assertNull(state.speedOverGround)
    }
}
