package net.osmand.plus.plugins.nautical.engine

import android.view.MotionEvent
import kotlinx.coroutines.*
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

    private val _isTouchLockActive = MutableStateFlow(value = false)
    val isTouchLockActive: StateFlow<Boolean> = _isTouchLockActive.asStateFlow()

    private val _unlockProgress = MutableStateFlow(0f)
    val unlockProgress: StateFlow<Float> = _unlockProgress.asStateFlow()

    private var downEventTime = 0L
    private val longPressUnlockThreshold = 2000L // 2 seconds long-press to unlock
    private var unlockJob: Job? = null

    fun setTouchLockActive(active: Boolean) {
        if (_isTouchLockActive.value != active) {
            _isTouchLockActive.value = active
            _unlockProgress.value = 0f
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
                _unlockProgress.value = 0f
                startUnlockTimer()
                log.info("Touch intercepted during Screen Touch Lock. Hold for 2s to unlock.")
                return true // Consume touch
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelUnlockTimer()
                val duration = event.eventTime - downEventTime
                if (duration >= longPressUnlockThreshold) {
                    setTouchLockActive(active = false)
                    log.info("Long-press unlock gesture detected. Touch lock disabled.")
                }
                _unlockProgress.value = 0f
                return true // Consume touch
            }
            MotionEvent.ACTION_MOVE -> {
                return true // Consume touch
            }
        }
        return true
    }

    private fun startUnlockTimer() {
        cancelUnlockTimer()
        unlockJob = CoroutineScope(Dispatchers.Default).launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / longPressUnlockThreshold).coerceIn(0f, 1f)
                _unlockProgress.value = progress
                if (progress >= 1f) break
                delay(50)
            }
        }
    }

    private fun cancelUnlockTimer() {
        unlockJob?.cancel()
        unlockJob = null
    }
}
