package net.osmand.plus.plugins.nautical

import net.osmand.plus.settings.enums.DayNightMode
import net.osmand.plus.settings.enums.NauticalDisplayMode
import java.util.Locale

/**
 * Pure mapping helpers for the nautical display mode (NORMAL, DARK night
 * vision, SUNLIGHT). Extracted so that mode mapping, night vision
 * toggling, render property values, and the SignalK server hint mapping
 * live in one testable place instead of being re-implemented at every
 * call site.
 */
object NauticalDisplayModes {

    fun isNight(mode: NauticalDisplayMode?): Boolean = mode == NauticalDisplayMode.DARK

    fun isSunlight(mode: NauticalDisplayMode?): Boolean = mode == NauticalDisplayMode.SUNLIGHT

    /**
     * Mode applied when the night vision quick action, tool center, or map
     * menu toggle is used: on from any other mode, off from dark.
     */
    fun nextForNightVisionToggle(current: NauticalDisplayMode?): NauticalDisplayMode =
        if (current == NauticalDisplayMode.DARK) NauticalDisplayMode.NORMAL else NauticalDisplayMode.DARK

    /**
     * Value written to the nautical_display_mode render property.
     */
    fun renderPropertyValue(mode: NauticalDisplayMode?): String = when (mode) {
        NauticalDisplayMode.DARK -> "night"
        NauticalDisplayMode.SUNLIGHT -> "sunlight"
        else -> "day"
    }

    /**
     * Display mode requested by the SignalK sunlight mode path.
     * Unknown values fall back to NORMAL, matching the original inline
     * mapping in the marine state listener.
     */
    fun fromServerSunlightHint(hint: String): NauticalDisplayMode = when (hint.lowercase(Locale.US)) {
        "high", "bright", "sunlight" -> NauticalDisplayMode.SUNLIGHT
        "night", "dark" -> NauticalDisplayMode.DARK
        else -> NauticalDisplayMode.NORMAL
    }

    /**
     * OsmAnd day/night map theme implied by the display mode.
     */
    fun dayNightTheme(mode: NauticalDisplayMode?): DayNightMode? = when (mode) {
        NauticalDisplayMode.DARK -> DayNightMode.NIGHT
        NauticalDisplayMode.SUNLIGHT -> DayNightMode.DAY
        else -> null
    }
}
