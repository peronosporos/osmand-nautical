package net.osmand.plus.plugins.nautical.grib

import net.osmand.plus.plugins.nautical.grib.parser.Grib1Parser
import net.osmand.plus.plugins.nautical.grib.parser.GribGridData
import net.osmand.plus.plugins.nautical.grib.parser.GribHeader
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.GribParser
import net.osmand.plus.plugins.nautical.grib.parser.TimeStepGrid
import net.osmand.plus.plugins.nautical.grib.parser.UnsupportedGribException
import net.osmand.plus.plugins.nautical.grib.parser.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GribDecoderTest {

    @Test
    fun testEmptyAndTruncatedByteArraysHandling() {
        val parser2 = GribParser()
        val parser1 = Grib1Parser()

        // 1. Empty byte array
        assertNull(parser2.parse(ByteArray(0)))
        assertNull(parser1.parse(ByteArray(0)))

        // 2. Very small arrays (less than minimum header size)
        assertNull(parser2.parse(byteArrayOf(0x01, 0x02, 0x03)))
        assertNull(parser1.parse(byteArrayOf(0x01, 0x02, 0x03)))

        // 3. Array of 15 bytes (below 16 bytes for GRIB2)
        assertNull(parser2.parse(ByteArray(15)))

        // 4. Corrupted/random bytes containing no "GRIB" magic
        val randomBytes = "GARBAGE_HEADER_DATA_1234567890".toByteArray()
        assertNull(parser2.parse(randomBytes))
        assertNull(parser1.parse(randomBytes))
    }

    @Test
    fun testGribEditionDetection() {
        val parser2 = GribParser()
        val parser1 = Grib1Parser()

        // Construct fake GRIB1 header (starts with "GRIB", 3-byte length, edition = 1)
        val grib1Bytes = ByteArray(32)
        grib1Bytes[0] = 'G'.code.toByte()
        grib1Bytes[1] = 'R'.code.toByte()
        grib1Bytes[2] = 'I'.code.toByte()
        grib1Bytes[3] = 'B'.code.toByte()
        grib1Bytes[4] = 0x00 // length MSB
        grib1Bytes[5] = 0x00
        grib1Bytes[6] = 0x20 // length 32 bytes
        grib1Bytes[7] = 0x01 // Edition 1

        // GRIB2 parser encountering Edition 1 should throw UnsupportedGribException
        try {
            parser2.parse(grib1Bytes)
            fail("Expected UnsupportedGribException for GRIB Edition 1 in GribParser")
        } catch (e: UnsupportedGribException) {
            assertTrue(e.message?.contains("Edition 1 is not supported") == true)
        }

        // Construct fake GRIB2 header (starts with "GRIB", discipline, edition = 2)
        val grib2Bytes = ByteArray(32)
        grib2Bytes[0] = 'G'.code.toByte()
        grib2Bytes[1] = 'R'.code.toByte()
        grib2Bytes[2] = 'I'.code.toByte()
        grib2Bytes[3] = 'B'.code.toByte()
        grib2Bytes[6] = 0x00 // discipline
        grib2Bytes[7] = 0x02 // Edition 2

        // GRIB1 parser encountering Edition 2 should skip and return null
        assertNull(parser1.parse(grib2Bytes))
    }

    @Test
    fun testWindVectorMath() {
        // Pure Northward wind (u=0, v=10) -> Coming from South (180°)
        val windFromSouth = WindVector(u = 0.0, v = 10.0)
        assertEquals(10.0, windFromSouth.speed, 1e-6)
        assertEquals(180.0, windFromSouth.direction, 1e-6)

        // Pure Eastward wind (u=10, v=0) -> Coming from West (270°)
        val windFromWest = WindVector(u = 10.0, v = 0.0)
        assertEquals(10.0, windFromWest.speed, 1e-6)
        assertEquals(270.0, windFromWest.direction, 1e-6)

        // Southward wind (u=0, v=-10) -> Coming from North (0°/360°)
        val windFromNorth = WindVector(u = 0.0, v = -10.0)
        assertEquals(10.0, windFromNorth.speed, 1e-6)
        assertEquals(0.0, windFromNorth.direction, 1e-6)
    }

    @Test
    fun testShortestArcAngleInterpolation() {
        val dummyHeader = GribHeader(latMin = 0.0, latMax = 1.0, lonMin = 0.0, lonMax = 1.0, latSteps = 2, lonSteps = 2)
        val dummyData = GribGridData(dummyHeader, emptyList())
        val engine = GribInterpolationEngine(dummyData)

        // Crossing 0° / 360° boundary: 350° to 10° at 50% ratio should be 0° (or 360°)
        val angleAcrossZero = engine.interpolateShortestArc(350.0, 10.0, 0.5)
        assertEquals(0.0, angleAcrossZero, 1e-6)

        // Reverse: 10° to 350° at 50% ratio should be 0°
        val angleReverse = engine.interpolateShortestArc(10.0, 350.0, 0.5)
        assertEquals(0.0, angleReverse, 1e-6)

        // Normal interpolation: 90° to 180° at 50% ratio should be 135°
        val angleNormal = engine.interpolateShortestArc(90.0, 180.0, 0.5)
        assertEquals(135.0, angleNormal, 1e-6)
    }

    @Test
    fun testBilinearSpatialAndTemporalInterpolation() {
        // Create 2x2 grid from lat [10.0, 20.0], lon [-30.0, -20.0]
        val header = GribHeader(
            latMin = 10.0,
            latMax = 20.0,
            lonMin = -30.0,
            lonMax = -20.0,
            latSteps = 2,
            lonSteps = 2
        )

        // Step 1: T = 1000L
        // Indices: (0,0)=lat10,lon-30; (0,1)=lat10,lon-20; (1,0)=lat20,lon-30; (1,1)=lat20,lon-20
        val u1 = floatArrayOf(10f, 20f, 10f, 20f)
        val v1 = floatArrayOf(0f, 0f, 10f, 10f)
        val p1 = floatArrayOf(1010f, 1012f, 1014f, 1016f)
        val wh1 = floatArrayOf(1.0f, 2.0f, 1.0f, 2.0f)
        val wd1 = floatArrayOf(90f, 90f, 90f, 90f)

        // Step 2: T = 2000L (values doubled)
        val u2 = floatArrayOf(20f, 40f, 20f, 40f)
        val v2 = floatArrayOf(0f, 0f, 20f, 20f)
        val p2 = floatArrayOf(1020f, 1022f, 1024f, 1026f)
        val wh2 = floatArrayOf(2.0f, 4.0f, 2.0f, 4.0f)
        val wd2 = floatArrayOf(180f, 180f, 180f, 180f)

        val step1 = TimeStepGrid(timestamp = 1000L, uGrid = u1, vGrid = v1, pressureGrid = p1, waveHeightGrid = wh1, waveDirectionGrid = wd1)
        val step2 = TimeStepGrid(timestamp = 2000L, uGrid = u2, vGrid = v2, pressureGrid = p2, waveHeightGrid = wh2, waveDirectionGrid = wd2)

        val gridData = GribGridData(header = header, timeSteps = listOf(step1, step2))
        val engine = GribInterpolationEngine(gridData)

        // 1. Spatial interpolation at T = 1000L at grid center (lat=15.0, lon=-25.0)
        // u = (10 + 20 + 10 + 20) / 4 = 15.0
        // v = (0 + 0 + 10 + 10) / 4 = 5.0
        val windT1Center = engine.getWindVector(lat = 15.0, lon = -25.0, timestamp = 1000L)
        assertNotNull(windT1Center)
        assertEquals(15.0, windT1Center!!.u, 1e-3)
        assertEquals(5.0, windT1Center.v, 1e-3)

        // 2. Temporal interpolation at midpoint T = 1500L at grid center (lat=15.0, lon=-25.0)
        // At T1: u=15.0, v=5.0. At T2: u=30.0, v=10.0.
        // Midpoint at T=1500L should give u=22.5, v=7.5
        val windMid = engine.getWindVector(lat = 15.0, lon = -25.0, timestamp = 1500L)
        assertNotNull(windMid)
        assertEquals(22.5, windMid!!.u, 1e-3)
        assertEquals(7.5, windMid.v, 1e-3)

        // 3. Pressure interpolation at center at T = 1000L
        // p = (1010 + 1012 + 1014 + 1016) / 4 = 1013.0 hPa
        val pressure = engine.getPressure(lat = 15.0, lon = -25.0, timestamp = 1000L)
        assertNotNull(pressure)
        assertEquals(1013.0, pressure!!, 1e-3)

        // 4. Wave data interpolation at T = 1000L (wave height=1.5m, wave direction=90°)
        val waveData = engine.getWaveData(lat = 15.0, lon = -25.0, timestamp = 1000L)
        assertNotNull(waveData)
        assertEquals(1.5, waveData!!.height, 1e-3)
        assertEquals(90.0, waveData.direction, 1.0)

        // 5. Out-of-bounds queries should return null
        assertNull(engine.getWindVector(lat = 25.0, lon = -25.0, timestamp = 1000L)) // lat above max
        assertNull(engine.getWindVector(lat = 5.0, lon = -25.0, timestamp = 1000L))  // lat below min
        assertNull(engine.getWindVector(lat = 15.0, lon = -40.0, timestamp = 1000L)) // lon outside
    }
}
