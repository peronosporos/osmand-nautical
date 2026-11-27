package net.osmand.plus.plugins.nautical.nmea.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for AisDecoder using bit-accurate AIVDM fixtures generated from
 * the ITU-R M.1371-5 field layout that the vendored marine-api parsers expect
 * (type 5: exactly 424 bits, type 18: 168 bits, type 21: 272 bits with 4 fill
 * bits). Only non-logging paths are exercised because android.util.Log is not
 * available in plain JVM unit tests.
 */
class AisDecoderTest {

    // Type 5: mmsi 247123456, imo 1234567, callsign TEST123, name TEST VESSEL,
    // ship type 37, draught 1.5 m, ETA 06-15 08:30, destination PIRAEUS.
    private val type5 = "!AIVDM,1,1,,B,53cc>000Bm`M@E=C7;=@E=B1HE=<Dh000000000U1@:335W`N3l2DPAEDh0000000000000,2*2E"

    // Same type 5 message delivered as two fragments (message id 7).
    private val type5Fragment1 = "!AIVDM,2,1,7,B,53cc>000Bm`M@E=C7;=@E=B1HE=<Dh000000000U1@:335W`,0*37"
    private val type5Fragment2 = "!AIVDM,2,2,7,B,N3l2DPAEDh0000000000000,2*1E"

    // Type 18 Class B: mmsi 367123456, sog 12.0 kn, cog 45.0 deg, lat 37.9, lon 23.5.
    private val type18 = "!AIVDM,1,1,,B,B5N7L000N72QcTi0Ect87h000000,0*37"

    // Type 21 AtoN: mmsi 992660001, aid type 5, name TEST BUOY, lat 37.55, lon 23.65.
    private val type21 = "!AIVDM,1,1,,B,E>jc:8Bb2ab@1:WdP0000000000@n8Fh:gSr050`HHP000,4*38"

    // Type 18 with coastal station MMSI 003669100 (7 digits, not an AIS target).
    private val coastalStation = "!AIVDM,1,1,,B,B03OtK00N72QcTi0Ect800000000,0*2B"

    // Type 18 with handheld Class B MMSI 836612345 (ITU-R M.585 category 8).
    private val handheldClassB = "!AIVDM,1,1,,B,B<Mnhv@0N72QcTi0Ect800000000,0*29"

    private fun values(sentence: String) =
        AisDecoder.decode(sentence)?.updates?.firstOrNull()?.values

    @Test
    fun testAtoNType21AcceptedWithNineDigitMmsi() {
        val delta = AisDecoder.decode(type21)
        assertNotNull("AtoN MMSI 99XXXXXX must pass the target gate", delta)
        assertEquals("vessels.urn:mrn:imo:mmsi:992660001", delta!!.context)

        val values = delta.updates!!.first().values!!
        assertEquals(21, values.first { it.path == AisDecoder.PATH_MSG_TYPE }.value)
        assertEquals(5, values.first { it.path == AisDecoder.PATH_AID_TYPE }.value)
        assertEquals("TEST BUOY", values.first { it.path == "name" }.value)
        assertNull(
            "AtoN aid type must not be shipped as a ship/cargo type",
            values.find { it.path == "design.type" }
        )

        val position = values.first { it.path == "navigation.position" }.value as Map<*, *>
        assertEquals(37.55, position["latitude"] as Double, 0.0001)
        assertEquals(23.65, position["longitude"] as Double, 0.0001)

        val dimensions = values.first { it.path == "design.dimensions" }.value as Map<*, *>
        assertEquals(5, dimensions["bow"])
        assertEquals(5, dimensions["stern"])
        assertEquals(3, dimensions["port"])
        assertEquals(3, dimensions["starboard"])
    }

    @Test
    fun testClassBType18CarriesMessageTypeAndNavigation() {
        val values = values(type18)
        assertNotNull(values)
        assertEquals(18, values!!.first { it.path == AisDecoder.PATH_MSG_TYPE }.value)

        val position = values.first { it.path == "navigation.position" }.value as Map<*, *>
        assertEquals(37.9, position["latitude"] as Double, 0.0001)
        assertEquals(23.5, position["longitude"] as Double, 0.0001)

        assertEquals(12.0 * 0.514444, values.first { it.path == "navigation.speedOverGround" }.value as Double, 0.0001)
        assertEquals(Math.toRadians(45.0), values.first { it.path == "navigation.courseOverGroundTrue" }.value as Double, 0.0001)
    }

    @Test
    fun testType5StaticDataIsDecoded() {
        val values = values(type5)
        assertNotNull(values)

        assertEquals("TEST VESSEL", values!!.first { it.path == "name" }.value)
        assertEquals("TEST123", values.first { it.path == "navigation.callsign" }.value)
        assertEquals(1234567, values.first { it.path == "imo" }.value)
        assertEquals(1.5, values.first { it.path == "design.draft" }.value as Double, 0.0001)
        assertEquals("PIRAEUS", values.first { it.path == "navigation.destination.commonName" }.value)

        val shipType = values.first { it.path == "design.type" }.value as Map<*, *>
        assertEquals(37, shipType["id"])

        val eta = values.first { it.path == AisDecoder.PATH_ETA }.value as Map<*, *>
        assertEquals(6, eta["month"])
        assertEquals(15, eta["day"])
        assertEquals(8, eta["hour"])
        assertEquals(30, eta["minute"])
    }

    @Test
    fun testFragmentedType5IsAssembled() {
        assertNull("first fragment alone must not decode", AisDecoder.decode(type5Fragment1))
        val values = values(type5Fragment2)
        assertNotNull("second fragment must complete the message", values)

        assertEquals(5, values!!.first { it.path == AisDecoder.PATH_MSG_TYPE }.value)
        assertEquals("TEST VESSEL", values.first { it.path == "name" }.value)
        assertEquals(1234567, values.first { it.path == "imo" }.value)
        assertEquals(1.5, values.first { it.path == "design.draft" }.value as Double, 0.0001)
        assertEquals("PIRAEUS", values.first { it.path == "navigation.destination.commonName" }.value)
    }

    @Test
    fun testCoastalStationMmsiRejected() {
        assertNull("coastal station 00-prefixed MMSI is not a target", AisDecoder.decode(coastalStation))
    }

    @Test
    fun testHandheldClassBMmsiAccepted() {
        val delta = AisDecoder.decode(handheldClassB)
        assertNotNull("handheld Class B 8XXXXXX MMSI must pass the target gate", delta)
        assertEquals("vessels.urn:mrn:imo:mmsi:836612345", delta!!.context)
    }
}
