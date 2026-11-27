package net.osmand.plus.plugins.nautical.hazard.engine

import org.junit.Assert.*
import org.junit.Test

class NavtexSentenceParserTest {

    @Test
    fun testParseCRRXO() {
        // Mock sentence with coordinates in body
        val sentence = "\$CRRXO,518,OA00,0,ZCZC 37-55N 023-40E NAVIGATIONAL WARNING NNNN*01"
        val message = NavtexSentenceParser.parseNmeaSentence(sentence)
        
        assertNotNull("Message should not be null", message)
        assertEquals("OA00", message?.id)
        assertEquals('O', message?.stationLetter)
        assertEquals(NavtexSubject.NAVIGATIONAL_WARNING, message?.subject)
        assertEquals(0, message?.sequenceNumber)
        assertTrue("Message should be urgent", message?.isUrgent ?: false)
        
        val points = message?.points ?: emptyList()
        assertFalse("Points should be extracted from body", points.isEmpty())
        assertEquals(37.9166, points[0].latitude, 0.001)
        assertEquals(23.6666, points[0].longitude, 0.001)
    }

    @Test
    fun testParseCZCX() {
        // Mock CZCX sentence
        val sentence = "\$CZCX,518,PA01,0,BOUNDED BY 38N 020E, 38N 022E, 36N 022E*4A"
        val message = NavtexSentenceParser.parseNmeaSentence(sentence)
        
        assertNotNull("Message should not be null", message)
        assertEquals("PA01", message?.id)
        assertTrue(message!!.isPolygon)
        assertEquals(3, message.points.size)
        assertEquals(38.0, message.points[0].latitude, 0.1)
        assertEquals(20.0, message.points[0].longitude, 0.1)
    }

    @Test
    fun testParseGPNVT() {
        val sentence = "\$GPNVT,123456,5045.123,N,00112.456,W,OA01*7B" // Valid NMEA checksum
        val message = NavtexSentenceParser.parseNmeaSentence(sentence)
        
        assertNotNull("Message should not be null", message)
        assertEquals("OA01", message?.id)
        
        val points = message?.points ?: emptyList()
        assertFalse("Points should be parsed from GPNVT fields", points.isEmpty())
        assertEquals(50.752, points[0].latitude, 0.001)
        assertEquals(-1.207, points[0].longitude, 0.001)
    }

    @Test
    fun testCoordinateExtractionPatterns() {
        // Test Pattern 1: 37-55N 023-40E
        val points1 = NavtexSentenceParser.extractCoordinates("REPORTED AT 37-55N 023-40E")
        assertEquals(1, points1.size)
        assertEquals(37.9166, points1[0].latitude, 0.001)
        assertEquals(23.6666, points1[0].longitude, 0.001)

        // Test Pattern 2: NMEA concatenation ddmm.m (documented LAT_NMEA/LON_NMEA form)
        val points2 = NavtexSentenceParser.extractCoordinates("AREA 3712.5N 02340.0E")
        assertEquals(1, points2.size)
        assertEquals(37.2083, points2[0].latitude, 0.001)
        assertEquals(23.6667, points2[0].longitude, 0.001)

        // Test mixed delimiters: 37°55'N / 023°40'E
        val points3 = NavtexSentenceParser.extractCoordinates("LOC: 37°55'N / 023°40'E")
        assertEquals(1, points3.size)
        assertEquals(37.9166, points3[0].latitude, 0.001)
        assertEquals(23.6666, points3[0].longitude, 0.001)

        // Test multi-point (Polygon)
        val points4 = NavtexSentenceParser.extractCoordinates("BOUNDED BY 38N 020E, 38N 022E, 36N 022E")
        assertEquals(3, points4.size)
        assertEquals(38.0, points4[0].latitude, 0.001)
        assertEquals(20.0, points4[0].longitude, 0.001)
    }

    @Test
    fun testMalformedSentences() {
        assertNull(NavtexSentenceParser.parseNmeaSentence("NOT A SENTENCE"))
        assertNull(NavtexSentenceParser.parseNmeaSentence("\$INVALID,1,2*FF"))
        assertNull(NavtexSentenceParser.parseNmeaSentence("\$CRRXO,518,INVALID,0,TEXT*00"))
    }

    @Test
    fun testMandatoryChecksum() {
        // No checksum should now return null
        val sentence = "\$CRRXO,518,OA00,0,ZCZC 37-55N 023-40E NAVIGATIONAL WARNING NNNN"
        assertNull("Message should be null when checksum is missing", NavtexSentenceParser.parseNmeaSentence(sentence))
    }

    @Test
    fun testLowDegreeCoordinates() {
        // Test single digit degree: 5-55N 001-40E
        val points = NavtexSentenceParser.extractCoordinates("LOC: 5-55N 001-40E")
        assertEquals(1, points.size)
        assertEquals(5.9166, points[0].latitude, 0.001)
        assertEquals(1.6666, points[0].longitude, 0.001)
    }

    @Test
    fun testMeteorologicalUrgency() {
        // Meteorological Warning ('B') should be urgent
        val sentence = "\$CRRXO,518,OB01,0,ZCZC GALE WARNING NNNN*31" // Valid NMEA checksum
        val message = NavtexSentenceParser.parseNmeaSentence(sentence)
        assertNotNull(message)
        assertTrue("Meteorological warning should be urgent", message!!.isUrgent)
    }
}
