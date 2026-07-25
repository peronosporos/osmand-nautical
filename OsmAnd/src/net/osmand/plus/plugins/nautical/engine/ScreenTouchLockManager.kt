package net.osmand.plus.plugins.nautical.engine

import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil

/**
 * ScreenTouchLockManager prevents ghost touches from water spray during heavy weather or maneuvers.
 * Consumes touch events on the map view except for a long-press unlock gesture,
 * and restricts control interaction to BLE Remote, Physical Buttons, or Voice commands.
 */
class ScreenTouchLockManager {
    private val log = PlatformUtil.getLog(ScreenTouchLockManager::class.java)

    private val _isTouchLockActive = MutableStateFlow(false)
    val isTouchLockActive: StateFlow<Boolean> = _isTouchLockActive.asStateFlow()

    private var downEventTime = 0L
    private val longPressUnlockThreshold = 2000L // 2 seconds long-press to unlock

    fun setTouchLockActive(active: Boolean) {
        if (_isTouchLockActive.value != active) {
            _isTouchLockActive.value = active
            log.info("Screen Touch Lock active state changed: $active")
        }
    }

    /**
     * Intercept touch events on the map view.
     * Returns true if the touch event is consumed (blocked), false if allowed.
     */
    fun interceptTouchEvent(event: MotionEvent): Boolean {
        if (!_isTouchLockActive.value) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downEventTime = event.eventTime
                log.info("Touch intercepted during Screen Touch Lock. Hold for 2s to unlock.")
                return true // Consume touch
            }
            MotionEvent.ACTION_UP -> {
                val duration = event.eventTime - downEventTime
                if (duration >= longPressUnlockThreshold) {
                    setTouchLockActive(false)
                    log.info("Long-press unlock gesture detected. Touch lock disabled.")
                }
                return true // Consume touch
            }
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_CANCEL -> {
                return true // Consume touch
            }
        }
        return true
    }
}
