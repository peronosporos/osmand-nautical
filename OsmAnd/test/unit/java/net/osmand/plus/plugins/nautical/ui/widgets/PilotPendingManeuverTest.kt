package net.osmand.plus.plugins.nautical.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PilotPendingManeuverTest {

    @Test
    fun testPrimaryRoundTripWithTarget() {
        val original = PilotPendingManeuver(isPrimary = true, kind = null, isPort = true, targetHeading = 135)
        val decoded = PilotPendingManeuver.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun testPrimaryRoundTripNullTarget() {
        val original = PilotPendingManeuver(isPrimary = true, kind = null, isPort = false, targetHeading = null)
        val decoded = PilotPendingManeuver.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun testSecondaryRoundTripWithTarget() {
        val original = PilotPendingManeuver(
            isPrimary = false,
            kind = PilotManeuverKind.GYBE_STBD,
            isPort = false,
            targetHeading = 271
        )
        val decoded = PilotPendingManeuver.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun testSecondaryRoundTripNullTarget() {
        val original = PilotPendingManeuver(
            isPrimary = false,
            kind = PilotManeuverKind.HEAVE_TO,
            isPort = false,
            targetHeading = null
        )
        val decoded = PilotPendingManeuver.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun testDecodeRejectsUnknownSecondaryKind() {
        assertNull(PilotPendingManeuver.decode("secondary|NOT_A_KIND|port|90"))
    }

    @Test
    fun testDecodeRejectsPrimaryCarryingKind() {
        assertNull(PilotPendingManeuver.decode("primary|GYBE_PORT|port|90"))
    }

    @Test
    fun testDecodeRejectsMalformedInput() {
        assertNull(PilotPendingManeuver.decode(null))
        assertNull(PilotPendingManeuver.decode(""))
        assertNull(PilotPendingManeuver.decode("primary|port|90"))
        assertNull(PilotPendingManeuver.decode("primary||port|90|extra"))
        assertNull(PilotPendingManeuver.decode("unknown||port|90"))
        assertNull(PilotPendingManeuver.decode("primary||portside|90"))
        assertNull(PilotPendingManeuver.decode("primary||port|not-a-number"))
    }

    @Test
    fun testDecodeRejectsOutOfRangeTarget() {
        assertNull(PilotPendingManeuver.decode("primary||port|360"))
        assertNull(PilotPendingManeuver.decode("primary||port|-5"))
    }

    @Test
    fun testConstructorRequiresKindForSecondary() {
        assertThrows(IllegalArgumentException::class.java) {
            PilotPendingManeuver(isPrimary = false, kind = null, isPort = true, targetHeading = 10)
        }
    }
}
