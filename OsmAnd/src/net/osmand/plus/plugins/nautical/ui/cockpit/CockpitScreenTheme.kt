package net.osmand.plus.plugins.nautical.ui.cockpit

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.enums.ThemeUsageContext

/**
 * Dark-surface decision for the Cockpit shell and its pages.
 *
 * Dark palette is used when either
 * - the nautical night-vision mode is on (red-on-black instrument mode), or
 * - the OsmAnd app theme is a dark theme (follows the global theme),
 * so the cockpit never blinds the helmsman with a light surface in a
 * dark-themed app. Both states intentionally share the same palette.
 */
object CockpitScreenTheme {

    fun isDark(app: OsmandApplication): Boolean =
        NauticalPlugin.isNightVision(app) ||
            app.daynightHelper.isNightMode(ThemeUsageContext.APP)
}
