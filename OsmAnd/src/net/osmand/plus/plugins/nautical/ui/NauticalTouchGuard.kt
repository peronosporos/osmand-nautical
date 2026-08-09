package net.osmand.plus.plugins.nautical.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import net.osmand.plus.R
import kotlin.math.abs

class NauticalTouchGuard(
    private val view: View,
    private val lockSwitch: View? = null,
    private val isLockedCheck: (() -> Boolean)? = null,
    private val onUnlock: (() -> Unit)? = null
) {
    private var isUnlocked = false
    private var isInteracting = false
    private var lastX = 0f
    private var lastY = 0f
    private val handler = Handler(Looper.getMainLooper())
    
    private val unlockRunnable = Runnable {
        isUnlocked = true
        isInteracting = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onUnlock?.invoke()
        
        val context = view.context
        Toast.makeText(context, context.getString(R.string.nautical_control_unlocked), Toast.LENGTH_SHORT).show()
        
        // Inject a simulated DOWN event at the current position to start Slider/View tracking
        val now = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, lastX, lastY, 0)
        view.onTouchEvent(downEvent)
        downEvent.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun apply() {
        view.setOnTouchListener { _, event ->
            // If a global safety lock switch is ON, block everything regardless
            if (lockSwitch?.isEnabled == true && (lockSwitch as? android.widget.CompoundButton)?.isChecked == true) {
                return@setOnTouchListener true
            }
            if (isLockedCheck?.invoke() == true) {
                return@setOnTouchListener true
            }

            lastX = event.x
            lastY = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isInteracting) return@setOnTouchListener false
                    isUnlocked = false
                    handler.postDelayed(unlockRunnable, 600) // Reduced delay to 600ms
                    true // Consume DOWN to prevent immediate interaction (e.g. Slider jump)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isInteracting) return@setOnTouchListener false
                    if (!isUnlocked) {
                        // If user moves significantly before unlock, cancel the unlock to avoid accidental drag
                        if (abs(event.x - lastX) > 20 || abs(event.y - lastY) > 20) {
                            handler.removeCallbacks(unlockRunnable)
                        }
                        true // Block the move
                    } else {
                        false // Allow the move (shouldn't really hit this due to isInteracting logic)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(unlockRunnable)
                    if (isInteracting) {
                        isInteracting = false
                        return@setOnTouchListener false
                    }
                    isUnlocked = false
                    true // Prevent final jump on UP if it was never unlocked
                }
                else -> false
            }
        }
    }

    companion object {
        @JvmStatic
        fun apply(view: View, lockSwitch: View? = null, isLockedCheck: (() -> Boolean)? = null, onUnlock: (() -> Unit)? = null) {
            NauticalTouchGuard(view, lockSwitch, isLockedCheck, onUnlock).apply()
        }
    }
}
