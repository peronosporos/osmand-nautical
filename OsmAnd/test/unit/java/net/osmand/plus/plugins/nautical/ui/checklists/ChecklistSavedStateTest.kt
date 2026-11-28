package net.osmand.plus.plugins.nautical.ui.checklists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistSavedStateTest {

    private fun items(vararg checked: Boolean): List<ChecklistItem> =
        checked.map { ChecklistItem("item", it) }

    @Test
    fun testEncodeApplyRoundTrip() {
        val original = items(true, false, true, true, false)
        val encoded = ChecklistSavedState.encodeChecked(original)
        val restored = items(false, false, false, false, false)
        assertTrue(ChecklistSavedState.applyChecked(restored, encoded))
        assertEquals(original, restored)
    }

    @Test
    fun testApplyCheckedRejectsSizeMismatch() {
        val encoded = ChecklistSavedState.encodeChecked(items(true, true, true))
        val restored = items(false, false)
        assertFalse(ChecklistSavedState.applyChecked(restored, encoded))
        // No partial application: the restored list is untouched.
        assertFalse(restored[0].isChecked)
        assertFalse(restored[1].isChecked)
    }

    @Test
    fun testApplyCheckedRejectsNull() {
        val restored = items(true, false)
        assertFalse(ChecklistSavedState.applyChecked(restored, null))
        assertTrue(restored[0].isChecked)
        assertFalse(restored[1].isChecked)
    }

    @Test
    fun testEmptyListRoundTrip() {
        val encoded = ChecklistSavedState.encodeChecked(emptyList())
        assertEquals(0, encoded.size)
        assertTrue(ChecklistSavedState.applyChecked(emptyList(), encoded))
    }

    @Test
    fun testChecklistTypeFromName() {
        assertEquals(ChecklistType.PRE_DEPARTURE, ChecklistType.fromName("PRE_DEPARTURE"))
        assertEquals(ChecklistType.HEAVY_WEATHER, ChecklistType.fromName("HEAVY_WEATHER"))
        assertEquals(ChecklistType.WATCH_HANDOVER, ChecklistType.fromName("WATCH_HANDOVER"))
        assertNull(ChecklistType.fromName("NOT_A_TYPE"))
        assertNull(ChecklistType.fromName(null))
        assertNull(ChecklistType.fromName(""))
    }
}
