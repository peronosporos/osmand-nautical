package net.osmand.plus.plugins.nautical.ui

/**
 * Interface for Nautical HUD headers to support layout arbitration and compact styling.
 */
interface INauticalHudHeader {
    /**
     * Toggles between standard and compact visual presentation.
     * Used when multiple headers are stacked vertically to save screen space.
     */
    fun setCompactMode(enabled: Boolean)

    /**
     * Returns true if the header contains a critical/emergency warning that requires priority arbitration.
     */
    fun isEmergency(): Boolean = false

    /**
     * Applies monochromatic red night vision theming.
     */
    fun applyNightVision(enabled: Boolean) {}
}
