package net.osmand.plus.plugins.nautical.dr.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.dr.engine.FixSource
import net.osmand.plus.plugins.nautical.dr.viewmodel.DrUiState
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import net.osmand.plus.utils.AndroidUtils

/**
 * Warning banner for Dead Reckoning mode.
 * Visible only when GPS signal is lost.
 */
class DrWarningHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val durationView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.dr_warning_banner, this, true)
        durationView = findViewById(R.id.dr_warning_duration)
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 8f else 12f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun updateState(state: DrUiState) {
        val isDrActive = state.source == FixSource.DEAD_RECKONING
        isVisible = isDrActive
        
        if (isDrActive) {
            durationView.text = context.getString(R.string.dr_banner_time, state.drDurationSeconds)
        }
    }
}
