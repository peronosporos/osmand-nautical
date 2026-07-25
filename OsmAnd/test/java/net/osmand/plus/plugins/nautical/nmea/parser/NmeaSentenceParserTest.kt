package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import org.junit.Assert.*
import org.junit.Test

class NmeaSentenceParserTest {

    private val parser = NmeaSentenceParser()

    @Test
    fun testParseRMC() {
        val sentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
        val delta = parser.parse(sentence)
        
        assertNotNull(delta)
        val updates = delta?.updates
        assertNotNull(updates)
        assertEquals(1, updates?.size)
        
        val values = updates!![0].values!!
        assertNotNull(values)
        
        // SOG: 22.4 knots * 0.514444 = 11.5235456 m/s
        val sogValue = values.find { it.path == LivePerformanceData.PATH_SOG }
        assertNotNull(sogValue)
        assertEquals(11.5235456, sogValue?.value as Double, 0.0001)
        
        // COG: 84.4 degrees = 1.473 radians
        val cogValue = values.find { it.path == LivePerformanceData.PATH_COG }
        assertNotNull(cogValue)
        assertEquals(Math.toRadians(84.4), cogValue?.value as Double, 0.0001)
    }

    @Test
    fun testParseMWV() {
        val sentence = "\$IIMWV,214.8,T,05.1,N,A*29"
        val delta = parser.parse(sentence)
        
        assertNotNull(delta)
        val values = delta?.updates!![0].values!!
        
        // TWS: 5.1 knots * 0.514444 = 2.6236644 m/s
        val twsValue = values.find { it.path == LivePerformanceData.PATH_TWS }
        assertNotNull(twsValue)
        assertEquals(2.6236644, twsValue?.value as Double, 0.0001)
        
        // TWA: 214.8 degrees = 3.7489 radians
        val twaValue = values.find { it.path == LivePerformanceData.PATH_TWA }
        assertNotNull(twaValue)
        assertEquals(Math.toRadians(214.8), twaValue?.value as Double, 0.0001)
    }

    @Test
    fun testParseDBT() {
        val sentence = "\$IIDBT,033.1,f,010.1,M,005.4,F*25"
        val delta = parser.parse(sentence)
        
        assertNotNull(delta)
        val values = delta?.updates!![0].values!!
        
        val depthValue = values.find { it.path == LivePerformanceData.PATH_DEPTH }
        assertNotNull(depthValue)
        assertEquals(10.1, depthValue?.value as Double, 0.0001)
    }

    @Test
    fun testParseInvalid() {
        assertNull(parser.parse("INVALID"))
        assertNull(parser.parse("\$GPXXX,1,2,3*00"))
    }

    @Test
    fun testParseCorruptedChecksum() {
        // Correct is 6A. Change to 6B.
        val sentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6B"
        assertNull(parser.parse(sentence))
    }

    @Test
    fun testParseAIS() {
        // !AIVDM,...
        val sentence = "!AIVDM,1,1,,A,13u9P80000OrqunE9S`000000000,0*11"
        // Our parser doesn't parse AIVDM payload yet, but it should validate it
        // and return empty values (or null if no recognized fragments)
        // Wait, parseRMC, parseMWV, parseDepth only handle RMC, MWV, DBT, DBS.
        // So AIVDM should return null (emptyList values).
        assertNull(parser.parse(sentence))
    }
}
