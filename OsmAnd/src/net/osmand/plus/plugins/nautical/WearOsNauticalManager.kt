package net.osmand.plus.plugins.nautical

import android.content.Context
import android.content.pm.PackageManager
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
     * hardware features, smallest screen width, or manual override.
     */
    fun isWatchMode(): Boolean {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        if (app.settings.NAUTICAL_FORCE_WATCH_LAYOUT.get()) {
            return true
        }

        val config = context.resources.configuration
        val uiMode = config.uiMode
        val isHardwareWatch = (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH
        
        val hasWatchFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        
        // Heuristic: Smallest screen width < 380dp often indicates a smartwatch form factor in modern high-res devices
        val isExceptionallySmall = config.smallestScreenWidthDp in 1..379
        
        return isHardwareWatch || hasWatchFeature || isExceptionallySmall
    }

    /**
     * Returns true if the screen is round (Wear OS specific).
     */
    fun isScreenRound(): Boolean {
        return context.resources.configuration.isScreenRound
    }

    fun setAmbientMode(enabled: Boolean) {
        _isAmbientMode.value = enabled
    }
}
