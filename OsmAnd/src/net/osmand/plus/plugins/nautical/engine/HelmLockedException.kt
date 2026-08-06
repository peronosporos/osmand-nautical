package net.osmand.plus.plugins.nautical.engine

import java.io.IOException

/**
 * Thrown when a helm command is rejected due to a higher-priority maneuver
 * active in the [NauticalHelmArbitrator].
 */
class HelmLockedException(val activePriority: Int, message: String) : IOException("[Priority $activePriority] $message")
