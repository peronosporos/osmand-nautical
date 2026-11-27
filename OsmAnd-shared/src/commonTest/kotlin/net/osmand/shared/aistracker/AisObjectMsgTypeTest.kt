package net.osmand.shared.aistracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies AisObject.set() merge semantics for AIS message types delivered in
 * separate updates (the local NMEA route ships ais.msgType as its own value):
 * msgTypes accumulate, objectClass is re-derived, and existing position and
 * static data are preserved when the incoming update carries no position.
 */
class AisObjectMsgTypeTest {

    private val invalidLat = AisObjectConstants.INVALID_LAT
    private val invalidLon = AisObjectConstants.INVALID_LON
    private val invalidDim = AisObjectConstants.INVALID_DIMENSION

    @Test
    fun testClassBMessageTypeMergesAndKeepsVesselClass() {
        val target = AisObject(247123456, 1, 37.9, 23.5)
        assertEquals(AisObjType.AIS_VESSEL, target.objectClass)

        target.set(AisObject(247123456, 18, invalidLat, invalidLon))

        assertTrue(target.msgTypes.contains(1), "original msgType 1 must be kept")
        assertTrue(target.msgTypes.contains(18), "Class B msgType 18 must be merged")
        assertEquals(AisObjType.AIS_VESSEL, target.objectClass)
        assertNotNull(target.position, "position must survive a position-less update")
        assertEquals(37.9, target.position!!.latitude, 0.0001)
        assertEquals(23.5, target.position!!.longitude, 0.0001)
    }

    @Test
    fun testAtoNMessageTypeReclassifiesTarget() {
        val target = AisObject(992660001, 1, 37.55, 23.65)

        target.set(AisObject(992660001, 21, invalidLat, invalidLon))

        assertTrue(target.msgTypes.contains(21))
        assertEquals(AisObjType.AIS_ATON, target.objectClass)
        assertNotNull(target.position)
    }

    @Test
    fun testVirtualAtoNAidType() {
        val target = AisObject(992660001, 1, 37.55, 23.65)

        target.set(AisObject(992660001, 21, invalidLat, invalidLon, 30, invalidDim, invalidDim, invalidDim, invalidDim))

        assertEquals(AisObjType.AIS_ATON_VIRTUAL, target.objectClass)
        assertEquals(30, target.aidType)
        assertTrue(target.msgTypes.contains(21))
    }

    @Test
    fun testSarAircraftMessageType() {
        val target = AisObject(111123456, 1, 37.9, 23.5)

        target.set(AisObject(111123456, 9, invalidLat, invalidLon))

        assertEquals(AisObjType.AIS_AIRPLANE, target.objectClass)
        assertTrue(target.msgTypes.contains(9))
    }

    @Test
    fun testStaticDataMergedThroughFullConstructor() {
        val target = AisObject(247123456, 1, 37.9, 23.5)
        target.set(
            AisObject(
                247123456, 5,
                1234567, "TEST123", "TEST VESSEL",
                37, 10, 10, 3, 3,
                1.5, "PIRAEUS", 6, 15, 8, 30
            )
        )

        assertEquals(1234567, target.imo)
        assertEquals("TEST123", target.callSign)
        assertEquals("TEST VESSEL", target.shipName)
        assertEquals(37, target.shipType)
        assertEquals(1.5, target.draught, 0.0001)
        assertEquals("PIRAEUS", target.destination)
        assertEquals(6, target.etaMon)
        assertEquals(15, target.etaDay)
        assertEquals(8, target.etaHour)
        assertEquals(30, target.etaMin)
        assertNotNull(target.position, "position must survive a static data update")
    }
}
