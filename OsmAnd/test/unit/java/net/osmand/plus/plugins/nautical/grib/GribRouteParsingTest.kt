package net.osmand.plus.plugins.nautical.grib

import net.osmand.plus.plugins.nautical.grib.parser.Grib1Parser
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.GribParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Route-level tests for the GRIB decoders with synthetic but spec-accurate
 * messages. Section layouts follow the WMO FM92 / NCEP GRIB2 documentation
 * (Indicator octets 1-16, Section 3 templates 3.0 and 3.10, Section 4
 * template 4.0, Section 5 template 5.0, Section 7 payload from octet 6) and
 * the GRIB1 ON388 layout (PDS octets, GDS type 0, BDS simple packing).
 *
 * Only non-logging paths are exercised because android.util.Log is not
 * available in plain JVM unit tests.
 */
class GribRouteParsingTest {

    private val refTime: Long =
        LocalDateTime.of(2026, 6, 15, 12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    // ------------------------------------------------------------------
    // GRIB2 message builder
    // ------------------------------------------------------------------

    private fun u32(v: Long) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    private fun ieeeFloat(v: Float): ByteArray {
        val bits = java.lang.Float.floatToIntBits(v)
        return u32(bits.toLong() and 0xFFFFFFFFL)
    }

    /** Grid Definition Section, template 3.0 (equidistant cylindrical). */
    private fun gridSection0(ni: Int, nj: Int, la1: Int, lo1: Int, la2: Int, lo2: Int, scanMode: Int): ByteArray {
        val s = ByteArray(81)
        u32(81).copyInto(s, 0)
        s[4] = 3 // section number
        u16(0).copyInto(s, 12) // grid definition template number 3.0
        u32(ni.toLong()).copyInto(s, 30) // Ni (octets 31-34)
        u32(nj.toLong()).copyInto(s, 34) // Nj (octets 35-38)
        u32(la1.toLong()).copyInto(s, 46) // La1 (octets 47-50)
        u32(lo1.toLong()).copyInto(s, 50) // Lo1 (octets 51-54)
        s[54] = 0x80 // resolution and component flags
        u32(la2.toLong()).copyInto(s, 55) // La2 (octets 56-59)
        u32(lo2.toLong()).copyInto(s, 59) // Lo2 (octets 60-63)
        s[71] = scanMode.toByte() // scanning mode (octet 72)
        return s
    }

    /** Grid Definition Section, template 3.10 (Mercator). */
    private fun mercatorGridSection(
        ni: Int, nj: Int, la1: Int, lo1: Int, laD: Int, la2: Int, lo2: Int,
        scanMode: Int, dj: Int, di: Int
    ): ByteArray {
        val s = ByteArray(72)
        u32(72).copyInto(s, 0)
        s[4] = 3 // section number
        u16(10).copyInto(s, 12) // grid definition template number 3.10
        u32(ni.toLong()).copyInto(s, 30) // Ni (octets 31-34)
        u32(nj.toLong()).copyInto(s, 34) // Nj (octets 35-38)
        u32(la1.toLong()).copyInto(s, 38) // La1 (octets 39-42)
        u32(lo1.toLong()).copyInto(s, 42) // Lo1 (octets 43-46)
        s[46] = 0x80 // resolution and component flags (octet 47)
        u32(laD.toLong()).copyInto(s, 47) // LaD (octets 48-51)
        u32(la2.toLong()).copyInto(s, 51) // La2 (octets 52-55)
        u32(lo2.toLong()).copyInto(s, 55) // Lo2 (octets 56-59)
        s[59] = scanMode.toByte() // scanning mode (octet 60)
        u32(dj.toLong()).copyInto(s, 60) // orientation of the grid (octets 61-64)
        u32(di.toLong()).copyInto(s, 64) // longitudinal grid length (octets 65-68)
        return s
    }

    private fun grib2Message(
        discipline: Int,
        gridSection: ByteArray,
        paramCategory: Int,
        paramNumber: Int,
        packed: ByteArray,
        timeUnit: Int = 1,
        forecastTime: Int = 0
    ): ByteArray {
        // Section 1: Identification (21 octets)
        val s1 = ByteArray(21)
        u32(21).copyInto(s1, 0)
        s1[4] = 1
        u16(7).copyInto(s1, 5) // center
        s1[12] = 1 // significance of reference time
        u16(2026).copyInto(s1, 13) // year (octets 13-14)
        s1[15] = 6 // month
        s1[16] = 15 // day
        s1[17] = 12 // hour
        s1[18] = 0 // minute
        s1[19] = 0 // second

        // Section 4: Product Definition, template 4.0 (34 octets)
        val s4 = ByteArray(34)
        u32(34).copyInto(s4, 0)
        s4[4] = 4
        u16(0).copyInto(s4, 7) // product definition template number
        s4[9] = paramCategory.toByte() // parameter category (octet 10)
        s4[10] = paramNumber.toByte() // parameter number (octet 11)
        s4[17] = timeUnit.toByte() // indicator of unit of time range (octet 18)
        u32(forecastTime.toLong()).copyInto(s4, 18) // forecast time (octets 19-22)

        // Section 5: Data Representation, template 5.0 simple packing (21 octets)
        val s5 = ByteArray(21)
        u32(21).copyInto(s5, 0)
        s5[4] = 5
        u32(packed.size.toLong()).copyInto(s5, 5) // number of data points (octets 6-9)
        u16(0).copyInto(s5, 9) // data representation template number (octets 10-11)
        ieeeFloat(0.0f).copyInto(s5, 11) // reference value R (octets 12-15)
        u16(0).copyInto(s5, 15) // binary scale factor E = 0 (octets 16-17)
        u16(0).copyInto(s5, 17) // decimal scale factor D = 0 (octets 18-19)
        s5[19] = 8 // 8 bits per value (octet 20)

        // Section 6: Bit-map, indicator 255 = no bitmap (6 octets)
        val s6 = ByteArray(6)
        u32(6).copyInto(s6, 0)
        s6[4] = 6
        s6[5] = 255.toByte()

        // Section 7: Data (octets 6+ are the packed values)
        val s7 = ByteArray(5 + packed.size)
        u32((5 + packed.size).toLong()).copyInto(s7, 0)
        s7[4] = 7
        packed.copyInto(s7, 5)

        val total = 16 + s1.size + gridSection.size + s4.size + s5.size + s6.size + s7.size + 4
        val msg = ByteArray(total)
        "GRIB".toByteArray().copyInto(msg, 0)
        msg[6] = discipline.toByte()
        msg[7] = 2 // edition 2
        u32(total.toLong()).copyInto(msg, 8)
        var off = 16
        s1.copyInto(msg, off); off += s1.size
        gridSection.copyInto(msg, off); off += gridSection.size
        s4.copyInto(msg, off); off += s4.size
        s5.copyInto(msg, off); off += s5.size
        s6.copyInto(msg, off); off += s6.size
        s7.copyInto(msg, off); off += s7.size
        "7777".toByteArray().copyInto(msg, off)
        return msg
    }

    /** Simple-packing values with R = 0, E = 0, D = 0, 8 bits per value. */
    private fun packed(vararg values: Int) = ByteArray(values.size) { i -> values[i].toByte() }

    // ------------------------------------------------------------------
    // GRIB1 message builder
    // ------------------------------------------------------------------

    private fun grib1Message(
        parameter: Int,
        ni: Int, nj: Int,
        la1: Int, lo1: Int, la2: Int, lo2: Int,
        packed: ByteArray
    ): ByteArray {
        // PDS: 28 octets, GDS present (octet 8 = 0x80), unit hour, analysis.
        val pds = ByteArray(28)
        pds[0] = 28.toByte()
        pds[3] = 3 // parameter table version (octet 4)
        pds[4] = 7 // center (octet 5)
        pds[6] = 255.toByte() // grid identification (octet 7)
        pds[7] = 0x80.toByte() // GDS/BMS flags (octet 8): GDS present
        pds[8] = parameter.toByte() // parameter (octet 9)
        pds[9] = 1 // type of level (octet 10): ground or water surface
        pds[12] = 26.toByte() // year of century (octet 13)
        pds[13] = 6 // month (octet 14)
        pds[14] = 15 // day (octet 15)
        pds[15] = 12 // hour (octet 16)
        pds[16] = 0 // minute (octet 17)
        pds[17] = 1 // unit of time range (octet 18): hour
        pds[18] = 0 // P1 (octet 19)
        pds[19] = 0 // P2 (octet 20)
        pds[20] = 0 // time range indicator (octet 21): forecast at P1
        pds[24] = 21 // century of reference time (octet 25)
        // decimal scale factor (octets 27-28) stays 0

        // GDS: 32 octets, lat/lon grid, millidegree angles in 3 octets.
        val gds = ByteArray(32)
        gds[0] = 32.toByte()
        gds[3] = 0 // NV
        gds[4] = 255.toByte() // PV/PL
        gds[5] = 0 // data representation type: lat/lon
        u16(ni).copyInto(gds, 6) // Ni (octets 7-8)
        u16(nj).copyInto(gds, 8) // Nj (octets 9-10)
        grib1Angle(la1).copyInto(gds, 10) // La1 (octets 11-13)
        grib1Angle(lo1).copyInto(gds, 13) // Lo1 (octets 14-16)
        gds[16] = 0x80 // resolution flags (octet 17)
        grib1Angle(la2).copyInto(gds, 17) // La2 (octets 18-20)
        grib1Angle(lo2).copyInto(gds, 20) // Lo2 (octets 21-23)
        u16(500).copyInto(gds, 23) // Di (octets 24-25)
        u16(500).copyInto(gds, 25) // Dj (octets 26-27)
        gds[27] = 0 // scanning mode (octet 28): +i, north first

        // BDS: simple packing, R = 0, E = 0, 8 bits per value.
        val bds = ByteArray(11 + packed.size)
        u16(11 + packed.size).copyInto(bds, 0) // length octets 1-2 (high byte 0)
        bds[0] = 0
        bds[1] = ((11 + packed.size) ushr 8).toByte()
        bds[2] = ((11 + packed.size) and 0xFF).toByte()
        u16(0).copyInto(bds, 4) // binary scale (octets 5-6)
        ieeeFloat(0.0f).copyInto(bds, 6) // reference value (octets 7-10)
        bds[10] = 8 // bits per value (octet 11)
        packed.copyInto(bds, 11)

        val total = 8 + pds.size + gds.size + bds.size + 4
        val msg = ByteArray(total)
        "GRIB".toByteArray().copyInto(msg, 0)
        msg[4] = (total ushr 16).toByte()
        msg[5] = ((total ushr 8) and 0xFF).toByte()
        msg[6] = (total and 0xFF).toByte()
        msg[7] = 1 // edition 1
        var off = 8
        pds.copyInto(msg, off); off += pds.size
        gds.copyInto(msg, off); off += gds.size
        bds.copyInto(msg, off); off += bds.size
        "7777".toByteArray().copyInto(msg, off)
        return msg
    }

    /** GRIB1 signed millidegree angle packed in 3 octets. */
    private fun grib1Angle(milliDegrees: Int): ByteArray {
        val negative = milliDegrees < 0
        val magnitude = if (negative) -milliDegrees else milliDegrees
        val first = ((magnitude shr 16) and 0x7F) or (if (negative) 0x80 else 0)
        return byteArrayOf(first.toByte(), ((magnitude shr 8) and 0xFF).toByte(), (magnitude and 0xFF).toByte())
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun testNonAdvancingGrib2MessageDoesNotHang() {
        // Magic + edition 2 + zero total length: without the guard this
        // re-scans the same offset forever and hangs the parse coroutine
        // (which cannot be cancelled while busy-looping).
        val bytes = ByteArray(40)
        "GRIB".toByteArray().copyInto(bytes, 0)
        bytes[7] = 2
        assertNull(GribParser().parse(bytes))
    }

    @Test
    fun testNonAdvancingGrib1MessageDoesNotHang() {
        val bytes = ByteArray(40)
        "GRIB".toByteArray().copyInto(bytes, 0)
        bytes[7] = 1 // edition 1, total length 0
        assertNull(Grib1Parser().parse(bytes))
    }

    @Test
    fun testMercatorScanningModeIsReadFromOctet60() {
        // Scan mode 0x40 (bit 2 set: points scan in +j, i.e. southernmost
        // row first) with a nonzero longitudinal grid length whose first octet
        // sits where the old code wrongly read the scan mode. Data order (u):
        // south row first.
        val grid = mercatorGridSection(
            ni = 2, nj = 2, la1 = 40000000, lo1 = 10000000, laD = 0,
            la2 = 45000000, lo2 = 15000000, scanMode = 0x40,
            dj = 0, di = 0x11271000 // first octet nonzero: old offset read 0x01
        )
        val u = grib2Message(0, grid, 2, 2, packed(20, 20, 10, 10))
        val v = grib2Message(0, grid, 2, 3, packed(5, 5, 0, 0))
        val data = GribParser().parse(u + v)
        assertNotNull(data)

        val engine = GribInterpolationEngine(data!!)
        val south = engine.getWindVector(40.0, 12.5, refTime)
        assertNotNull(south)
        assertEquals(20.0, south!!.u, 1e-3)
        assertEquals(5.0, south.v, 1e-3)

        val north = engine.getWindVector(45.0, 12.5, refTime)
        assertNotNull(north)
        assertEquals(10.0, north!!.u, 1e-3)
        assertEquals(0.0, north.v, 1e-3)
    }

    @Test
    fun testMixedGridFileKeepsPerGridGeometry() {
        // Wind on a 2x2 grid (lat 40-45, lon 10-15), waves on a 3x3 grid
        // (lat 41-44, lon 11-14), same timestamp: mixed-grid files are common
        // (e.g. wind 0.25 deg with waves 0.5 deg). Wave data must only be
        // served inside the wave grid domain, and wind must keep its own grid.
        val gridA = gridSection0(ni = 2, nj = 2, la1 = 40000000, lo1 = 10000000,
            la2 = 45000000, lo2 = 15000000, scanMode = 0x00)
        val gridB = gridSection0(ni = 3, nj = 3, la1 = 41000000, lo1 = 11000000,
            la2 = 44000000, lo2 = 14000000, scanMode = 0x00)

        val windU = grib2Message(0, gridA, 2, 2, packed(10, 10, 20, 20)) // north row 10, south row 20
        val windV = grib2Message(0, gridA, 2, 3, packed(0, 0, 0, 0))
        // Wave height 3.0 m and direction 90 deg on every point of grid B.
        val waveHeight = grib2Message(10, gridB, 0, 3, packed(3, 3, 3, 3, 3, 3, 3, 3, 3))
        val waveDir = grib2Message(10, gridB, 0, 4, packed(90, 90, 90, 90, 90, 90, 90, 90, 90))

        val data = GribParser().parse(windU + windV + waveHeight + waveDir)
        assertNotNull(data)

        val engine = GribInterpolationEngine(data!!)

        // Wind keeps grid A geometry: south 20 kn u, north 10.
        val wind = engine.getWindVector(42.5, 12.5, refTime)
        assertNotNull(wind)
        assertEquals(15.0, wind!!.u, 1e-3) // bilinear midpoint between 20 and 10
        assertEquals(0.0, wind.v, 1e-3)

        // Waves serve their own domain.
        val waveInside = engine.getWaveData(42.5, 12.5, refTime)
        assertNotNull(waveInside)
        assertEquals(3.0, waveInside!!.height, 1e-3)
        assertEquals(90.0, waveInside.direction, 1.0)

        // Inside the wind grid but outside the wave grid: no wave data. With
        // the single file-level header this wrongly returned 3.0 (or threw).
        assertNull(engine.getWaveData(40.5, 10.5, refTime))

        // Pressure was never in the file.
        assertNull(engine.getPressure(42.5, 12.5, refTime))
    }

    @Test
    fun testGrib1WindMessagesParse() {
        // Standard GRIB1 route (previously untested end to end): u-wind is
        // parameter 33, v-wind 34 (ON388 table 2), scan mode 0 = north first.
        val u = grib1Message(33, 2, 2, 40000, 10000, 45000, 15000, packed(10, 10, 20, 20))
        val v = grib1Message(34, 2, 2, 40000, 10000, 45000, 15000, packed(0, 0, 5, 5))
        val data = Grib1Parser().parse(u + v)
        assertNotNull("GRIB1 wind messages must parse", data)

        val engine = GribInterpolationEngine(data!!)
        val south = engine.getWindVector(40.0, 12.5, refTime)
        assertNotNull(south)
        assertEquals(20.0, south!!.u, 1e-3)
        assertEquals(5.0, south.v, 1e-3)

        val north = engine.getWindVector(45.0, 12.5, refTime)
        assertNotNull(north)
        assertEquals(10.0, north!!.u, 1e-3)
        assertEquals(0.0, north.v, 1e-3)
    }

    @Test
    fun testGrib2TimeUnits() {
        // Unit 10 = 3 hours per the NOAA/NCEP edition of Code Table 4.4 and
        // the ECMWF reference table. A value of 2 must mean 6 hours, not 2.
        val grid = gridSection0(ni = 2, nj = 2, la1 = 40000000, lo1 = 10000000,
            la2 = 45000000, lo2 = 15000000, scanMode = 0x00)
        val u = grib2Message(0, grid, 2, 2, packed(10, 10, 10, 10), timeUnit = 1, forecastTime = 0)
        val v = grib2Message(0, grid, 2, 3, packed(0, 0, 0, 0), timeUnit = 10, forecastTime = 2)

        val data = GribParser().parse(u + v)
        assertNotNull(data)
        val timestamps = data!!.timeSteps.map { it.timestamp }.distinct().sorted()
        assertEquals("steps at refTime and refTime + 6h expected", 2, timestamps.size)
        assertEquals(refTime, timestamps.first())
        assertEquals(refTime + 6 * 3600000L, timestamps.last())
    }
}
