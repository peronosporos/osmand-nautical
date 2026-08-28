package net.osmand.plus.plugins.nautical.ui.helpers

import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ScreenTouchLockManager

/**
 * WetScreenTouchGuardHelper manages wet-screen and rough sea touch suppression.
 * Prevents phantom water droplet touches from triggering map pan/zoom while keeping
 * live telemetry visible, and maps physical volume rocker keys to zoom & micro-steering.
 */
object WetScreenTouchGuardHelper {

    private val touchLockManager = ScreenTouchLockManager()

    val isLockActive: StateFlow<Boolean> = touchLockManager.isTouchLockActive
    val unlockProgress: StateFlow<Float> = touchLockManager.unlockProgress

    fun toggleLock(mapActivity: MapActivity?): Boolean {
        val newState = !touchLockManager.isTouchLockActive.value
        touchLockManager.setTouchLockActive(newState)
        if (mapActivity != null) {
            val msg = if (newState) "WET-SCREEN TOUCH LOCK ACTIVE (Hold 2s to unlock)" else "TOUCH LOCK RELEASED"
            NauticalPlugin.hudManager?.get()?.showBanner(msg, 5000L, isWarning = newState, priority = 2)
        }
        return newState
    }

    fun setLock(active: Boolean, mapActivity: MapActivity? = null) {
        touchLockManager.setTouchLockActive(active)
        if (mapActivity != null) {
            val msg = if (active) "WET-SCREEN TOUCH LOCK ACTIVE (Hold 2s to unlock)" else "TOUCH LOCK RELEASED"
            NauticalPlugin.hudManager?.get()?.showBanner(msg, 5000L, isWarning = active, priority = 2)
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return touchLockManager.interceptTouchEvent(event)
    }

    /**
     * Maps physical hardware Volume Up / Down keys to map zoom and autopilot micro-steering
     * when touch lock is active.
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent, mapActivity: MapActivity): Boolean {
        if (!touchLockManager.isTouchLockActive.value) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    // Zoom In / Autopilot +1° Steer
                    val autopilot = NauticalPlugin.autopilot
                    val state = NauticalPlugin.engine?.getCurrentState()
                    if (autopilot != null && state?.autopilotState != "standby") {
                        autopilot.stepTargetHeading(+1.0, "Volume Up Nudge")
                    } else {
                        mapActivity.mapView?.let { mv ->
                            mv.setZoomWithFloatPart(mv.zoom + 1, 0f)
                            mv.refreshMap()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // Zoom Out / Autopilot -1° Steer
                    val autopilot = NauticalPlugin.autopilot
                    val state = NauticalPlugin.engine?.getCurrentState()
                    if (autopilot != null && state?.autopilotState != "standby") {
                        autopilot.stepTargetHeading(-1.0, "Volume Down Nudge")
                    } else {
                        mapActivity.mapView?.let { mv ->
                            mv.setZoomWithFloatPart(mv.zoom - 1, 0f)
                            mv.refreshMap()
                        }
                    }
                    return true
                }
            }
        }
        return false
    }
}
