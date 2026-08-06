package net.osmand.plus.settings.enums

import net.osmand.plus.R

enum class OsmandTheme(private val titleId: Int) : EnumWithTitleId {
    DARK(R.string.dark_theme),
    LIGHT(R.string.light_theme),
    SYSTEM_DEFAULT(R.string.system_default_theme);

    override fun getTitleId(): Int = titleId
}
