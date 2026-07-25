package net.osmand.plus.plugins.nautical.s57.style

/**
 * Standardized S-52 color tokens and their mappings for Day and Night modes.
 */
enum class NauticalColor(val dayHex: Int, val nightHex: Int) {
    DEEP_WATER(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), // White / Black
    SAFETY_ZONE(0xFFBFDFFF.toInt(), 0xFF002244.toInt()), // Medium Blue
    SHALLOW_WATER(0xFFA6E1FF.toInt(), 0xFF001133.toInt()), // Light Blue / Dark Navy
    SAFETY_CONTOUR(0xFF000000.toInt(), 0xFFFF0000.toInt()), // Black / Red
    LAND_AREA(0xFFFFE3A3.toInt(), 0xFF332200.toInt()), // Tan / Dark Brown
    BUOY_PORT(0xFFFF0000.toInt(), 0xFFFF0000.toInt()), // Red
    BUOY_STARBOARD(0xFF00FF00.toInt(), 0xFFFF0000.toInt()), // Green / Red (Night vision)
    DANGER(0xFFFF00FF.toInt(), 0xFFFF0000.toInt()); // Magenta / Red

    fun getColor(isNight: Boolean): Int = if (isNight) nightHex else dayHex
}

/**
 * Defines how a feature should be rendered on the map.
 */
data class S57StyleRule(
    val strokeWidth: Float = 1f,
    val strokeColor: NauticalColor? = null,
    val fillColor: NauticalColor? = null,
    val dashEffect: FloatArray? = null,
    val symbolId: SymbolId? = null,
    val priority: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is S57StyleRule) return false
        return strokeWidth == other.strokeWidth &&
                strokeColor == other.strokeColor &&
                fillColor == other.fillColor &&
                dashEffect.contentEquals(other.dashEffect) &&
                symbolId == other.symbolId &&
                priority == other.priority
    }

    override fun hashCode(): Int {
        var result = strokeWidth.hashCode()
        result = 31 * result + (strokeColor?.hashCode() ?: 0)
        result = 31 * result + (fillColor?.hashCode() ?: 0)
        result = 31 * result + (dashEffect?.contentHashCode() ?: 0)
        result = 31 * result + (symbolId?.hashCode() ?: 0)
        result = 31 * result + priority
        return result
    }
}

/**
 * Identifiers for nautical symbols (Simplified).
 */
enum class SymbolId {
    LATERAL_PORT,
    LATERAL_STARBOARD,
    ISOLATED_DANGER,
    SAFE_WATER,
    SPECIAL_PURPOSE,
    LIGHT_MAJOR,
    LIGHT_MINOR,
    ROCK_AWASH,
    WRECK,
    OBSTRUCTION
}
