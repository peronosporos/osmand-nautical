package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import net.osmand.plus.R

class ScreenTouchLockHudView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val lockIcon: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_touch_lock_hud, this, true)
        lockIcon = findViewById(R.id.img_touch_lock)
        isVisible = false
    }

    fun setLocked(locked: Boolean) {
        isVisible = locked
    }

    override fun isEmergency(): Boolean = false
    override fun setCompactMode(compact: Boolean) {
        // Not used for this small icon
    }
}
