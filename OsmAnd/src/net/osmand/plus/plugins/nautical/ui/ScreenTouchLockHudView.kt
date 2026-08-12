package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.osmand.plus.R

class ScreenTouchLockHudView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val lockIcon: ImageView
    private val progressIndicator: CircularProgressIndicator
    private val lockText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_touch_lock_hud, this, true)
        lockIcon = findViewById(R.id.img_touch_lock)
        progressIndicator = findViewById(R.id.unlock_progress)
        lockText = findViewById(R.id.txt_touch_lock)
        isVisible = false
    }

    fun setLocked(locked: Boolean) {
        isVisible = locked
    }

    fun setUnlockProgress(progress: Float) {
        progressIndicator.progress = (progress * 100).toInt()
        if (progress > 0) {
            lockText.text = context.getString(R.string.nautical_unlocking_hint)
        } else {
            lockText.setText(R.string.nautical_touch_lock_active)
        }
    }

    override fun isEmergency(): Boolean = false
    override fun setCompactMode(enabled: Boolean) {
        // Not used for this small icon
    }
}
