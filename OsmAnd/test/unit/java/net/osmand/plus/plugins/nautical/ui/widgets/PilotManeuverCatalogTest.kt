package net.osmand.plus.plugins.nautical.ui.widgets

import net.osmand.plus.R
import net.osmand.plus.settings.enums.VesselType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the data-driven pilot maneuver catalog introduced in the U35
 * pilot sheet round. The catalog reproduces the button order and labels of
 * the previous hand-wired layout; these tests pin that contract so future
 * maneuver changes stay intentional.
 */
class PilotManeuverCatalogTest {

    @Test
    fun conventionalVesselHasFourteenSecondaryManeuversInLegacyOrder() {
        val maneuvers = PilotManeuverCatalog.secondaryManeuvers(VesselType.CONVENTIONAL)
        assertEquals(14, maneuvers.size)
        val kinds = maneuvers.map { it.kind }
        assertEquals(
            listOf(
                PilotManeuverKind.GYBE_PORT,
                PilotManeuverKind.GYBE_STBD,
                PilotManeuverKind.HEAVE_TO,
                PilotManeuverKind.CENTER_RUDDER,
                PilotManeuverKind.ANCHORING,
                PilotManeuverKind.WEIGH_ANCHOR,
                PilotManeuverKind.DOCKING,
                PilotManeuverKind.MED_MOORING,
                PilotManeuverKind.MOORING,
                PilotManeuverKind.SLIP_EXIT,
                PilotManeuverKind.DODGE_PORT,
                PilotManeuverKind.DODGE_STBD,
                PilotManeuverKind.HOLDING_PATTERN,
                PilotManeuverKind.EMERGENCY_STOP
            ),
            kinds
        )
    }

    @Test
    fun proaVesselHasTenSecondaryManeuversWithoutGybeOrHeaveTo() {
        val maneuvers = PilotManeuverCatalog.secondaryManeuvers(VesselType.PROA)
        assertEquals(10, maneuvers.size)
        val kinds = maneuvers.map { it.kind }
        assertFalse(PilotManeuverKind.GYBE_PORT in kinds)
        assertFalse(PilotManeuverKind.GYBE_STBD in kinds)
        assertFalse(PilotManeuverKind.HEAVE_TO in kinds)
        assertEquals(
            listOf(
                PilotManeuverKind.ANCHORING,
                PilotManeuverKind.WEIGH_ANCHOR,
                PilotManeuverKind.DOCKING,
                PilotManeuverKind.MED_MOORING,
                PilotManeuverKind.MOORING,
                PilotManeuverKind.SLIP_EXIT,
                PilotManeuverKind.DODGE_PORT,
                PilotManeuverKind.DODGE_STBD,
                PilotManeuverKind.HOLDING_PATTERN,
                PilotManeuverKind.EMERGENCY_STOP
            ),
            kinds
        )
    }

    @Test
    fun conventionalGybeButtonsKeepShortLabelWithLongAccessibilityText() {
        val maneuvers = PilotManeuverCatalog.secondaryManeuvers(VesselType.CONVENTIONAL)
        val gybePort = maneuvers.first { it.kind == PilotManeuverKind.GYBE_PORT }
        assertEquals(R.string.nautical_gybe_port_short, gybePort.labelRes)
        assertEquals(R.string.nautical_gybe_port_label, gybePort.descriptionRes)
        val gybeStbd = maneuvers.first { it.kind == PilotManeuverKind.GYBE_STBD }
        assertEquals(R.string.nautical_gybe_stbd_short, gybeStbd.labelRes)
        assertEquals(R.string.nautical_gybe_stbd_label, gybeStbd.descriptionRes)
    }

    @Test
    fun primaryLabelsSwitchForProa() {
        assertEquals(R.string.nautical_tack_port_short, PilotManeuverCatalog.primaryPortLabelRes(VesselType.CONVENTIONAL))
        assertEquals(R.string.nautical_tack_port, PilotManeuverCatalog.primaryPortDescriptionRes(VesselType.CONVENTIONAL))
        assertEquals(R.string.nautical_tack_stbd_short, PilotManeuverCatalog.primaryStarboardLabelRes(VesselType.CONVENTIONAL))
        assertEquals(R.string.nautical_tack_stbd, PilotManeuverCatalog.primaryStarboardDescriptionRes(VesselType.CONVENTIONAL))

        assertEquals(R.string.nautical_shunt, PilotManeuverCatalog.primaryPortLabelRes(VesselType.PROA))
        assertEquals(R.string.nautical_shunt, PilotManeuverCatalog.primaryPortDescriptionRes(VesselType.PROA))
        assertEquals(R.string.nautical_center_rudder, PilotManeuverCatalog.primaryStarboardLabelRes(VesselType.PROA))
        assertEquals(R.string.nautical_center_rudder, PilotManeuverCatalog.primaryStarboardDescriptionRes(VesselType.PROA))
    }

    @Test
    fun isProaOnlyForProaVesselType() {
        assertTrue(PilotManeuverCatalog.isProa(VesselType.PROA))
        assertFalse(PilotManeuverCatalog.isProa(VesselType.CONVENTIONAL))
    }

    @Test
    fun rowVisibilityContractMatchesLegacyLayout() {
        // Proa: 10 maneuvers -> rows 3..5 visible (buttons 5..10), rows 6..7 hidden.
        val proaSize = PilotManeuverCatalog.secondaryManeuvers(VesselType.PROA).size
        assertEquals(10, proaSize)
        for (rowNumber in 3..7) {
            val visible = proaSize >= 2 * rowNumber - 1
            if (rowNumber <= 5) assertTrue("row $rowNumber must be visible for proa", visible)
            else assertFalse("row $rowNumber must be hidden for proa", visible)
        }
        // Conventional: 14 maneuvers -> all rows 3..7 visible.
        val convSize = PilotManeuverCatalog.secondaryManeuvers(VesselType.CONVENTIONAL).size
        for (rowNumber in 3..7) {
            assertTrue("row $rowNumber must be visible for conventional", convSize >= 2 * rowNumber - 1)
        }
    }
}
