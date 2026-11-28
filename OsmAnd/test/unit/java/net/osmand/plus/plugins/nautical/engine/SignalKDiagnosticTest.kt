package net.osmand.plus.plugins.nautical.engine

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * TEMPORARY diagnostic suite: it dumps the pipeline state at each stage into
 * assertion messages, so the CI failure report shows exactly where the
 * Signal K handoff stalls. To be removed once the pipeline failures are
 * understood and fixed.
 */
class SignalKDiagnosticTest {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: OsmandApplication
    private lateinit var engine: SignalKEngine
    private lateinit var multiplexer: DirectNmeaMultiplexer
    private lateinit var aggregator: SailingDataAggregator
    private var consumerCalled = false

    @Before
    fun setup() {
        app = mockk<OsmandApplication>(relaxed = true)
        every { app.settings } returns TestOsmandSettings.create(app)
        engine = SignalKEngine(app, engineScope)
        aggregator = SailingDataAggregator()
        multiplexer = DirectNmeaMultiplexer(app, aggregator, engineScope, NmeaSentenceParser(app))
        multiplexer.deltaConsumer = { delta ->
            consumerCalled = true
            engine.handleDelta(delta)
        }
    }

    @After
    fun tearDown() {
        engineScope.cancel()
    }

    private fun dump(label: String): Nothing {
        val agg = aggregator.aggregatedData.value
        val st = engine.getCurrentState()
        assertFalse(
            "$label consumerCalled=$consumerCalled" +
                " agg=[sog=${agg.speedOverGround} cog=${agg.courseOverGround} lat=${agg.latitude}]" +
                " engine=[sog=${st.speedOverGround} cog=${st.courseOverGroundTrue}" +
                " conn=${st.connectionStatus} xte=${st.crossTrackError} lastMsg=${st.lastMessageTime}]",
            true
        )
        throw AssertionError("unreachable")
    }

    @Test
    fun diagInlineStagesAfterRmc() = runBlocking {
        val rmc = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        multiplexer.injectSentence(rmc)
        dump("INLINE after inject:")
    }

    @Test
    fun diagEngineStateTwoSecondsAfterRmc() = runBlocking {
        val rmc = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        multiplexer.injectSentence(rmc)
        Thread.sleep(2000)
        dump("ASYNC 2s after RMC inject:")
    }

    @Test
    fun diagEngineStateAfterJsonMessage() = runBlocking {
        val json = "{\"context\":\"vessels.self\",\"updates\":[{\"timestamp\":\"2026-08-15T12:00:00.000Z\",\"values\":[{\"path\":\"navigation.speedOverGround\",\"value\":6.5}]}]}"
        engine.handleIncomingMessage(json)
        Thread.sleep(2000)
        dump("ASYNC 2s after json message:")
    }
}
