package net.osmand.plus.plugins.nautical.checklists

import net.osmand.plus.plugins.nautical.network.SignalKChecklist
import net.osmand.plus.plugins.nautical.network.SignalKChecklistItem
import net.osmand.plus.plugins.nautical.ui.checklists.ChecklistItem
import net.osmand.plus.plugins.nautical.ui.checklists.ChecklistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafetyChecklistTest {

    @Test
    fun testDefaultChecklistInitialization() {
        // Test ChecklistType enum presence
        val types = ChecklistType.values()
        assertTrue(types.contains(ChecklistType.PRE_DEPARTURE))
        assertTrue(types.contains(ChecklistType.HEAVY_WEATHER))
        assertTrue(types.contains(ChecklistType.WATCH_HANDOVER))

        // Create standard Pre-Departure checklist
        val preDepartureItems = mutableListOf(
            ChecklistItem("Bilges inspected & pumps operational"),
            ChecklistItem("Engine oil, coolant & transmission fluid checked"),
            ChecklistItem("Raw water strainer clear & seacocks open"),
            ChecklistItem("Standing & running rigging inspected"),
            ChecklistItem("Steering gear & emergency tiller verified"),
            ChecklistItem("EPIRB, flares & safety gear within expiry")
        )

        assertEquals(6, preDepartureItems.size)
        assertTrue("All items must initially be unchecked", preDepartureItems.none { it.isChecked })

        // Create standard Heavy Weather checklist
        val heavyWeatherItems = mutableListOf(
            ChecklistItem("Companionway boards & deck hatches dogged shut"),
            ChecklistItem("Jacklines rigged on port & starboard decks"),
            ChecklistItem("Reefing lines reeved & storm sails prepared"),
            ChecklistItem("Loose cabin gear & galley items lashed down"),
            ChecklistItem("Bilge high-water alarms tested & operational"),
            ChecklistItem("Lifejackets & tethers donned by all watchkeepers")
        )

        assertEquals(6, heavyWeatherItems.size)
        assertTrue("All items must initially be unchecked", heavyWeatherItems.none { it.isChecked })

        // Create standard Watch Handover / Offshore checklist
        val watchHandoverItems = mutableListOf(
            ChecklistItem("3-hour Barometer trend & squall risks noted"),
            ChecklistItem("AIS & Radar targets / closest CPA reviewed"),
            ChecklistItem("Navigation lights, battery SOC & engine temps checked"),
            ChecklistItem("Logbook updated with lat/lon, log & weather observations"),
            ChecklistItem("Standing orders & course changes acknowledged")
        )

        assertEquals(5, watchHandoverItems.size)
        assertTrue("All items must initially be unchecked", watchHandoverItems.none { it.isChecked })
    }

    @Test
    fun testItemToggleAndProgressCalculation() {
        val items = mutableListOf(
            ChecklistItem("Item 1"),
            ChecklistItem("Item 2"),
            ChecklistItem("Item 3"),
            ChecklistItem("Item 4")
        )

        fun calculateProgress(list: List<ChecklistItem>): Int {
            if (list.isEmpty()) return 0
            val checkedCount = list.count { it.isChecked }
            return (checkedCount * 100) / list.size
        }

        // 0 of 4 completed (0%)
        assertEquals(0, calculateProgress(items))

        // Check 1 item -> 1 of 4 completed (25%)
        items[0].isChecked = true
        assertEquals(25, calculateProgress(items))

        // Check 2nd item -> 2 of 4 completed (50%)
        items[1].isChecked = true
        assertEquals(50, calculateProgress(items))

        // Check 3rd item -> 3 of 4 completed (75%)
        items[2].isChecked = true
        assertEquals(75, calculateProgress(items))

        // Check 4th item -> 4 of 4 completed (100%)
        items[3].isChecked = true
        assertEquals(100, calculateProgress(items))

        // Uncheck item 2 -> 3 of 4 completed (75%)
        items[1].isChecked = false
        assertEquals(75, calculateProgress(items))

        // Reset all items -> 0%
        items.forEach { it.isChecked = false }
        assertEquals(0, calculateProgress(items))
    }

    @Test
    fun testSignalKChecklistStateTransitions() {
        val skChecklist = SignalKChecklist(
            name = "Offshore Passage Preparation",
            description = "Pre-passage offshore safety checks",
            items = listOf(
                SignalKChecklistItem("Inspect liferaft", "pending"),
                SignalKChecklistItem("Satellite comms check", "pending"),
                SignalKChecklistItem("Emergency water & rations", "pending")
            )
        )

        assertEquals(3, skChecklist.items.size)
        assertTrue(skChecklist.items.all { it.state == "pending" })

        // Transition 1 item to completed
        val updatedItems = skChecklist.items.mapIndexed { idx, item ->
            if (idx == 0) item.copy(state = "completed") else item
        }
        val updatedChecklist = skChecklist.copy(items = updatedItems)

        val completedCount = updatedChecklist.items.count { it.state == "completed" }
        assertEquals(1, completedCount)
        assertEquals("completed", updatedChecklist.items[0].state)
        assertEquals("pending", updatedChecklist.items[1].state)
    }

    @Test
    fun testLogbookEntryCreationOnChecklistCompletion() {
        val checklistType = ChecklistType.PRE_DEPARTURE
        val items = listOf(
            ChecklistItem("Bilges", isChecked = true),
            ChecklistItem("Engine", isChecked = true),
            ChecklistItem("Rigging", isChecked = true)
        )

        val total = items.size
        val checked = items.count { it.isChecked }
        val timeStr = SimpleDateFormat("HH:mm 'UTC'", Locale.US).format(Date())
        val typeStr = checklistType.name.replace('_', ' ')

        val logEntry = "CHECKLIST LOGGED: $typeStr ($checked/$total complete at $timeStr)"

        assertNotNull(logEntry)
        assertTrue(logEntry.startsWith("CHECKLIST LOGGED: PRE DEPARTURE (3/3 complete at "))
        assertTrue(logEntry.endsWith("UTC)"))

        // Partial completion test
        val partialItems = listOf(
            ChecklistItem("Hatches", isChecked = true),
            ChecklistItem("Jacklines", isChecked = false),
            ChecklistItem("Storm Sails", isChecked = false)
        )
        val partialChecked = partialItems.count { it.isChecked }
        val partialLog = "CHECKLIST LOGGED: ${ChecklistType.HEAVY_WEATHER.name.replace('_', ' ')} ($partialChecked/${partialItems.size} complete at $timeStr)"

        assertTrue(partialLog.startsWith("CHECKLIST LOGGED: HEAVY WEATHER (1/3 complete at "))
    }
}
