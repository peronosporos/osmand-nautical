package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.utils.AndroidUtils

/**
 * Critical warning header for device thermal issues.
 */
class ThermalWarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val textView: TextView = TextView(context).apply {
        text = context.getString(R.string.nautical_thermal_warning)
        setTextColor(Color.WHITE)
        textSize = 16f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    private val iconView: ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_action_alert)
        setColorFilter(Color.WHITE)
        val size = AndroidUtils.dpToPx(context, 24f)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = AndroidUtils.dpToPx(context, 12f)
        }
    }
    private val container: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.text_color_negative))
        val p = AndroidUtils.dpToPx(context, 12f)
        setPadding(p, p, p, p)
    }

    init {

        container.addView(iconView)
        container.addView(textView)
        addView(container)

        visibility = GONE
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 4f else 12f
        val px = AndroidUtils.dpToPx(context, p)
        container.setPadding(px, px, px, px)
        textView.textSize = if (enabled) 14f else 16f
    }

    /**
     * Updates the warning text dynamically.
     */
    @Suppress("unused")
    fun setWarningText(resId: Int) {
        textView.setText(resId)
    }
}
