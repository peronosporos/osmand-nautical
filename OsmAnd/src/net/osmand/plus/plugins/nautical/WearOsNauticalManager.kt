package net.osmand.plus.plugins.nautical

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages WearOS / Watch specific hardware detection and layout arbitration.
 */
class WearOsNauticalManager(private val context: Context) {

    private val _isAmbientMode = MutableStateFlow(value = false)
    val isAmbientMode: StateFlow<Boolean> = _isAmbientMode.asStateFlow()

    /**
     * Detects if the device is a WearOS / Watch hardware based on UI mode,
     * smallest screen width (heuristic for full-Android watches), or manual override.
     */
    fun isWatchMode(): Boolean {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        if (app.settings.NAUTICAL_FORCE_WATCH_LAYOUT.get()) {
            return true
        }

        val config = context.resources.configuration
        val uiMode = config.uiMode
        val isHardwareWatch = (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH
        
        // Heuristic: Smallest screen width < 300dp often indicates a smartwatch form factor
        val isExceptionallySmall = config.smallestScreenWidthDp in 1..299
        
        return isHardwareWatch || isExceptionallySmall
    }
}
