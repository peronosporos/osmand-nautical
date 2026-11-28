package net.osmand.plus.plugins.nautical.ui.widgets

import net.osmand.plus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhfScriptSelectionTest {

    private val allCallTypes = listOf(
        VhfDistressScriptBottomSheet.CallType.MAYDAY,
        VhfDistressScriptBottomSheet.CallType.PAN_PAN,
        VhfDistressScriptBottomSheet.CallType.SECURITE
    )

    @Test
    fun testSanitizeKeepsValidSelection() {
        val restored = VhfScriptSelection.sanitize(
            VhfDistressScriptBottomSheet.CallType.PAN_PAN,
            "FIRE / EXPLOSION",
            12
        )
        assertEquals(VhfDistressScriptBottomSheet.CallType.PAN_PAN, restored.first)
        assertEquals("FIRE / EXPLOSION", restored.second)
        assertEquals(12, restored.third)
    }

    @Test
    fun testSanitizeDefaultsUnknownNature() {
        val restored = VhfScriptSelection.sanitize(
            VhfDistressScriptBottomSheet.CallType.MAYDAY,
            "INVENTED PHRASE",
            4
        )
        assertEquals(VhfScriptSelection.DEFAULT_NATURE, restored.second)
    }

    @Test
    fun testSanitizeDefaultsNullCallType() {
        val restored = VhfScriptSelection.sanitize(null, "GROUNDING", 4)
        assertEquals(VhfDistressScriptBottomSheet.CallType.MAYDAY, restored.first)
    }

    @Test
    fun testSanitizeClampsPersonsOnBoard() {
        assertEquals(1, VhfScriptSelection.sanitize(null, null, 0).third)
        assertEquals(1, VhfScriptSelection.sanitize(null, null, -7).third)
        assertEquals(99, VhfScriptSelection.sanitize(null, null, 150).third)
        assertEquals(42, VhfScriptSelection.sanitize(null, null, 42).third)
    }

    @Test
    fun testCanonicalNatureSetMatchesChipMapping() {
        VhfScriptSelection.canonicalNatures.forEach { nature ->
            val chipId = VhfScriptSelection.chipIdForNature(nature)
            assertEquals(nature, VhfScriptSelection.natureForChipId(chipId))
        }
    }

    @Test
    fun testNatureForChipIdUnknownIsNull() {
        assertNull(VhfScriptSelection.natureForChipId(0))
        assertNull(VhfScriptSelection.natureForChipId(-1))
    }

    @Test
    fun testChipIdForNatureUnknownFallsBackToSinking() {
        assertEquals(R.id.chip_sinking, VhfScriptSelection.chipIdForNature("SOMETHING ELSE"))
    }

    @Test
    fun testCallTypeButtonIdsAreDistinctAndComplete() {
        val ids = allCallTypes.map { VhfScriptSelection.callTypeButtonId(it) }
        assertEquals(allCallTypes.size, ids.toSet().size)
        assertEquals(R.id.btn_type_mayday, VhfScriptSelection.callTypeButtonId(VhfDistressScriptBottomSheet.CallType.MAYDAY))
        assertEquals(R.id.btn_type_pan_pan, VhfScriptSelection.callTypeButtonId(VhfDistressScriptBottomSheet.CallType.PAN_PAN))
        assertEquals(R.id.btn_type_securite, VhfScriptSelection.callTypeButtonId(VhfDistressScriptBottomSheet.CallType.SECURITE))
    }

    @Test
    fun testAllNaturesSanitizeWithoutLoss() {
        VhfScriptSelection.canonicalNatures.forEach { nature ->
            val restored = VhfScriptSelection.sanitize(
                VhfDistressScriptBottomSheet.CallType.SECURITE,
                nature,
                4
            )
            assertEquals(nature, restored.second)
            assertTrue(nature in VhfScriptSelection.canonicalNatures)
        }
    }
}
