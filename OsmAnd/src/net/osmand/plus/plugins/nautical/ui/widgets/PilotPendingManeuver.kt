package net.osmand.plus.plugins.nautical.ui.widgets

/**
 * Saveable identity of a pilot maneuver confirmation that was in progress
 * when the sheet was destroyed (e.g. by a device rotation).
 *
 * The confirmation's execute action is a lambda and cannot be stored in a
 * Bundle; the sheet re-derives it after restore by routing this spec back
 * through [AutopilotBottomSheetHelper]. The user-adjusted target heading
 * is part of the spec so the restored confirmation shows, and executes,
 * exactly the heading that was on screen before the rotation.
 *
 * Encoding is a plain string so the encode/decode round trip is fully
 * covered by JVM unit tests without an Android Bundle.
 */
data class PilotPendingManeuver(
    val isPrimary: Boolean,
    val kind: PilotManeuverKind?,
    val isPort: Boolean,
    val targetHeading: Int?
) {
    init {
        require(isPrimary || kind != null) { "secondary maneuvers require a kind" }
        require(!isPrimary || kind == null) { "primary maneuvers do not carry a kind" }
    }

    fun encode(): String {
        val scope = if (isPrimary) SCOPE_PRIMARY else SCOPE_SECONDARY
        val kindName = kind?.name ?: ""
        val side = if (isPort) SIDE_PORT else SIDE_STARBOARD
        val target = targetHeading?.toString() ?: NO_TARGET
        return "$scope|$kindName|$side|$target"
    }

    companion object {
        private const val SCOPE_PRIMARY = "primary"
        private const val SCOPE_SECONDARY = "secondary"
        private const val SIDE_PORT = "port"
        private const val SIDE_STARBOARD = "stbd"
        private const val NO_TARGET = "-"

        fun decode(raw: String?): PilotPendingManeuver? {
            if (raw == null) return null
            val parts = raw.split("|")
            if (parts.size != 4) return null
            val isPrimary = when (parts[0]) {
                SCOPE_PRIMARY -> true
                SCOPE_SECONDARY -> false
                else -> return null
            }
            val kindName = parts[1]
            val kind = if (kindName.isEmpty()) null else PilotManeuverKind.entries.firstOrNull { it.name == kindName }
            val isPort = when (parts[2]) {
                SIDE_PORT -> true
                SIDE_STARBOARD -> false
                else -> return null
            }
            val targetRaw = parts[3]
            val target = if (targetRaw == NO_TARGET) {
                null
            } else {
                val parsed = targetRaw.toIntOrNull() ?: return null
                if (parsed !in 0..359) return null
                parsed
            }
            if (!isPrimary && kind == null) return null
            if (isPrimary && kind != null) return null
            return PilotPendingManeuver(isPrimary = isPrimary, kind = kind, isPort = isPort, targetHeading = target)
        }
    }
}
