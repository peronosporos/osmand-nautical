package net.osmand.plus.plugins.nautical.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
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
        
        // Use a more robust way to trigger the view's own touch handling
        // by resetting the state and letting the next MOVE/UP events pass through
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

            // For Sliders and other complex views, the simulated injection is fragile.
            // If the view is NOT currently locked by a global/emergency state, 
            // we should allow immediate interaction for better usability,
            // relying on the explicit "Safety Lock" toggle in the UI for protection.
            if (lockSwitch == null && isLockedCheck == null) {
                return@setOnTouchListener false 
            }

            lastX = event.x
            lastY = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isUnlocked) return@setOnTouchListener false
                    isUnlocked = false
                    isInteracting = false
                    handler.postDelayed(unlockRunnable, 500) 
                    true 
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isUnlocked) return@setOnTouchListener false
                    if (abs(event.x - lastX) > 30 || abs(event.y - lastY) > 30) {
                        handler.removeCallbacks(unlockRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(unlockRunnable)
                    if (isUnlocked) {
                        isUnlocked = false
                        return@setOnTouchListener false
                    }
                    true
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
