package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.settings.enums.NauticalDisplayMode
import net.osmand.plus.settings.enums.ThemeUsageContext

enum class NauticalSemanticColor {
    PRIMARY,
    SECONDARY,
    ACCENT,
    STATUS_OK,
    STATUS_WARNING,
    STATUS_ERROR,
    GRID,
    MARKER
}

object NauticalColorResolver {

    fun getColor(context: Context, semanticColor: NauticalSemanticColor): Int {
        val app = context.applicationContext as? OsmandApplication
        val displayMode = app?.settings?.NAUTICAL_DISPLAY_MODE?.get() ?: NauticalDisplayMode.NORMAL
        
        if (displayMode == NauticalDisplayMode.DARK) {
            return when (semanticColor) {
                NauticalSemanticColor.PRIMARY -> 0xFFFF0000.toInt() // Pure Red
                NauticalSemanticColor.SECONDARY -> 0xFF880000.toInt() // Dim Red
                NauticalSemanticColor.ACCENT -> 0xFFFF0000.toInt()
                NauticalSemanticColor.STATUS_OK -> 0xFFCC0000.toInt() // Strict Red for scotopic safety
                NauticalSemanticColor.STATUS_WARNING -> 0xFFFF4400.toInt() // Orange-Red
                NauticalSemanticColor.STATUS_ERROR -> 0xFFFF0000.toInt()
                NauticalSemanticColor.GRID -> 0xFF330000.toInt() // Very Dark Red
                NauticalSemanticColor.MARKER -> 0xFFFF0000.toInt()
            }
        }

        val isSunlight = displayMode == NauticalDisplayMode.SUNLIGHT
        val isNightMode = app?.daynightHelper?.isNightMode(app.settings.APPLICATION_MODE.get(), ThemeUsageContext.OVER_MAP) ?: false

        if (isSunlight && !isNightMode) {
            // Enforce absolute contrast in direct sunlight (Day mode only)
            return when (semanticColor) {
                NauticalSemanticColor.PRIMARY -> Color.BLACK
                NauticalSemanticColor.SECONDARY -> Color.DKGRAY
                NauticalSemanticColor.ACCENT -> 0xFF0000FF.toInt() // High-contrast Blue
                NauticalSemanticColor.STATUS_OK -> 0xFF006600.toInt() // Deep Green
                NauticalSemanticColor.STATUS_WARNING -> 0xFFCC6600.toInt() // Deep Orange
                NauticalSemanticColor.STATUS_ERROR -> Color.RED
                NauticalSemanticColor.GRID -> Color.BLACK // Solid black grid for visibility
                NauticalSemanticColor.MARKER -> Color.BLACK
            }
        }

        return when (semanticColor) {
            NauticalSemanticColor.PRIMARY -> ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
            NauticalSemanticColor.SECONDARY -> ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
            NauticalSemanticColor.ACCENT -> ContextCompat.getColor(context, R.color.icon_color_osmand_light)
            NauticalSemanticColor.STATUS_OK -> ContextCompat.getColor(context, R.color.nautical_status_green)
            NauticalSemanticColor.STATUS_WARNING -> ContextCompat.getColor(context, R.color.nautical_status_yellow)
            NauticalSemanticColor.STATUS_ERROR -> ContextCompat.getColor(context, R.color.nautical_status_red)
            NauticalSemanticColor.GRID -> if (isNightMode) 0xFF444444.toInt() else 0xFFCCCCCC.toInt()
            NauticalSemanticColor.MARKER -> 0xFFFFA500.toInt() // Orange
        }
    }
}
