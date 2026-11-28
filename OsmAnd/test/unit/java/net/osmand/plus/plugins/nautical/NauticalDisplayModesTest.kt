package net.osmand.plus.plugins.nautical

import net.osmand.plus.settings.enums.DayNightMode
import net.osmand.plus.settings.enums.NauticalDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure display mode mapping helpers extracted from
 * NauticalPlugin and NauticalUiOverlayManager during the U34 red mode
 * round. These mappings previously lived inline at several call sites and
 * were not covered by any test.
 */
class NauticalDisplayModesTest {

    @Test
    fun nightVisionToggleCyclesBetweenNormalAndDark() {
        assertEquals(NauticalDisplayMode.DARK, NauticalDisplayModes.nextForNightVisionToggle(NauticalDisplayMode.NORMAL))
        assertEquals(NauticalDisplayMode.NORMAL, NauticalDisplayModes.nextForNightVisionToggle(NauticalDisplayMode.DARK))
    }

    @Test
    fun nightVisionToggleFromSunlightGoesDark() {
        assertEquals(NauticalDisplayMode.DARK, NauticalDisplayModes.nextForNightVisionToggle(NauticalDisplayMode.SUNLIGHT))
    }

    @Test
    fun renderPropertyMatchesDisplayMode() {
        assertEquals("day", NauticalDisplayModes.renderPropertyValue(NauticalDisplayMode.NORMAL))
        assertEquals("night", NauticalDisplayModes.renderPropertyValue(NauticalDisplayMode.DARK))
        assertEquals("sunlight", NauticalDisplayModes.renderPropertyValue(NauticalDisplayMode.SUNLIGHT))
    }

    @Test
    fun serverSunlightHintIsCaseInsensitive() {
        assertEquals(NauticalDisplayMode.SUNLIGHT, NauticalDisplayModes.fromServerSunlightHint("Bright"))
        assertEquals(NauticalDisplayMode.SUNLIGHT, NauticalDisplayModes.fromServerSunlightHint("HIGH"))
        assertEquals(NauticalDisplayMode.DARK, NauticalDisplayModes.fromServerSunlightHint("Night"))
        assertEquals(NauticalDisplayMode.DARK, NauticalDisplayModes.fromServerSunlightHint("dark"))
    }

    @Test
    fun serverSunlightHintUnknownFallsBackToNormal() {
        assertEquals(NauticalDisplayMode.NORMAL, NauticalDisplayModes.fromServerSunlightHint("dusk"))
        assertEquals(NauticalDisplayMode.NORMAL, NauticalDisplayModes.fromServerSunlightHint(""))
    }

    @Test
    fun dayNightThemeFollowsDisplayMode() {
        assertEquals(DayNightMode.NIGHT, NauticalDisplayModes.dayNightTheme(NauticalDisplayMode.DARK))
        assertEquals(DayNightMode.DAY, NauticalDisplayModes.dayNightTheme(NauticalDisplayMode.SUNLIGHT))
        assertNull(NauticalDisplayModes.dayNightTheme(NauticalDisplayMode.NORMAL))
    }

    @Test
    fun isNightAndIsSunlightPredicates() {
        assertTrue(NauticalDisplayModes.isNight(NauticalDisplayMode.DARK))
        assertFalse(NauticalDisplayModes.isNight(NauticalDisplayMode.NORMAL))
        assertFalse(NauticalDisplayModes.isNight(NauticalDisplayMode.SUNLIGHT))
        assertTrue(NauticalDisplayModes.isSunlight(NauticalDisplayMode.SUNLIGHT))
        assertFalse(NauticalDisplayModes.isSunlight(NauticalDisplayMode.DARK))
        assertFalse(NauticalDisplayModes.isSunlight(NauticalDisplayMode.NORMAL))
    }
}
