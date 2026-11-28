package net.osmand.plus.plugins.nautical.nmea.multiplexer

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.plugins.nautical.nmea.connection.NmeaTransport
import net.osmand.plus.plugins.nautical.nmea.parser.NmeaSentenceParser
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.settings.backend.TestOsmandSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Route test for the NAVTEX paths of the multiplexer worker:
 *
 * 1. raw-text ASCII blocks (ZCZC ... NNNN) arriving over a real transport
 *    must be buffered, decoded and upserted into the repository;
 * 2. a short broadcast carrying the header and the terminator in a single
 *    sentence must be flushed immediately, not buffered forever;
 * 3. interleaved $/! NMEA sentences must never leak into an open raw-text
 *    block (they are routed to the NMEA/AIS pipelines instead);
 * 4. proprietary NAVTEX sentences ($CRRXO/$CZCX/$GPNVT) must be parsed and
 *    upserted, with a rejected checksum producing no write.
 *
 * injectSentence invokes processSentence synchronously, so each step is
 * deterministic and needs no polling.
 */
class DirectNmeaMultiplexerNavtexRouteTest {

    private lateinit var scope: CoroutineScope
    private lateinit var app: OsmandApplication
    private lateinit var transport: NmeaTransport
    private lateinit var navtexRepo: NavtexRepository
    private lateinit var multiplexer: DirectNmeaMultiplexer
    private val recorded = mutableListOf<NavtexMessage>()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        app = mockk<OsmandApplication>(relaxed = true)
        // Some sentence parsers read preferences through the settings
        // object, whose final Java fields a mock cannot intercept.
        val settings = TestOsmandSettings.create(app)
        every { app.settings } returns settings
        transport = mockk<NmeaTransport>(relaxed = true)
        navtexRepo = mockk<NavtexRepository>()
        coEvery { navtexRepo.upsertMessage(any()) } coAnswers {
            recorded.add(firstArg())
            Unit
        }
        multiplexer = DirectNmeaMultiplexer(
            app,
            SailingDataAggregator(),
            scope,
            NmeaSentenceParser(app),
            navtexRepo
        )
    }

    @After
    fun tearDown() {
        // Cancels the multiplexer worker launched in its init block.
        scope.cancel()
    }

    @Test
    fun testMultilineNavtexBlockIsDecodedAndUpserted() = runBlocking {
        multiplexer.injectSentence("ZCZC GA01", transport)
        multiplexer.injectSentence("GALE WARNING. SW WINDS 40 KT.", transport)
        multiplexer.injectSentence("NNNN", transport)

        assertEquals(1, recorded.size)
        val msg = recorded[0]
        assertEquals("GA01", msg.id)
        assertEquals('G', msg.stationLetter)
        assertEquals(NavtexSubject.NAVIGATIONAL_WARNING, msg.subject)
        assertTrue(msg.isUrgent)
        assertEquals(1, msg.sequenceNumber)
        assertEquals("GALE WARNING. SW WINDS 40 KT.", msg.body)
    }

    @Test
    fun testSingleSentenceBlockIsFlushedImmediately() = runBlocking {
        // A short broadcast carrying header and terminator in ONE sentence.
        // The buffer must flush on the ZCZC line itself, otherwise the
        // message is never decoded and silently lost.
        multiplexer.injectSentence("ZCZC GA01 SHORT BROADCAST. NNNN", transport)

        assertEquals(1, recorded.size)
        assertEquals("GA01", recorded[0].id)
        assertEquals(NavtexSubject.NAVIGATIONAL_WARNING, recorded[0].subject)
        assertTrue(recorded[0].isUrgent)
    }

    @Test
    fun testInterleavedNmeaSentencesDoNotPolluteBlockBody() = runBlocking {
        multiplexer.injectSentence("ZCZC GA01", transport)
        // A GPS fix arriving mid-broadcast on the same transport.
        multiplexer.injectSentence("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47", transport)
        multiplexer.injectSentence("GALE WARNING. SW WINDS 40 KT.", transport)
        multiplexer.injectSentence("NNNN", transport)

        assertEquals(1, recorded.size)
        val msg = recorded[0]
        assertEquals("GA01", msg.id)
        assertFalse("NMEA sentence leaked into the NAVTEX body", msg.body.contains("GPGGA"))
        assertEquals("GALE WARNING. SW WINDS 40 KT.", msg.body)
    }

    @Test
    fun testNewHeaderDiscardsIncompletePreviousBlock() = runBlocking {
        multiplexer.injectSentence("ZCZC GA01", transport)
        multiplexer.injectSentence("UNFINISHED BODY WITHOUT TERMINATOR", transport)
        // A second header resets the buffer; the incomplete first block
        // must never be decoded or upserted.
        multiplexer.injectSentence("ZCZC GB02", transport)
        multiplexer.injectSentence("NAVIGATIONAL WARNING. MINES.", transport)
        multiplexer.injectSentence("NNNN", transport)

        assertEquals(1, recorded.size)
        assertEquals("GB02", recorded[0].id)
    }

    @Test
    fun testProprietaryCrrxoSentenceIsUpserted() = runBlocking {
        // Fixture proven in NavtexSentenceParserTest: valid checksum *01.
        multiplexer.injectSentence(
            "\$CRRXO,518,OA00,0,ZCZC 37-55N 023-40E NAVIGATIONAL WARNING NNNN*01",
            transport
        )

        assertEquals(1, recorded.size)
        val msg = recorded[0]
        assertEquals("OA00", msg.id)
        assertEquals(NavtexSubject.NAVIGATIONAL_WARNING, msg.subject)
        assertTrue(msg.isUrgent)
        assertEquals(1, msg.points.size)
        assertEquals(37.9166, msg.points[0].latitude, 0.01)
        assertEquals(23.6666, msg.points[0].longitude, 0.01)
    }

    @Test
    fun testBadChecksumCrrxoSentenceIsRejected() = runBlocking {
        multiplexer.injectSentence(
            "\$CRRXO,518,OA00,0,ZCZC TEST BODY NNNN*7F",
            transport
        )

        assertTrue(recorded.isEmpty())
    }
}
