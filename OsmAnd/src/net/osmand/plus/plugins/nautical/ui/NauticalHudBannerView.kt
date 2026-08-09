package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.utils.AndroidUtils

class NauticalHudBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val messageTextView: TextView
    private val contentLayout: LinearLayout
    private var slideToConfirmView: SlideToConfirmView? = null
    private var secondarySlideToConfirmView: SlideToConfirmView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    var onDismiss: (() -> Unit)? = null

    init {
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        
        val padding = AndroidUtils.dpToPx(context, 12f)
        setPadding(padding, padding, padding, padding)

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        messageTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        contentLayout.addView(messageTextView)

        addView(contentLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val closeButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_remove_dark)
            val size = AndroidUtils.dpToPx(context, 24f)
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = AndroidUtils.dpToPx(context, 4f)
                rightMargin = AndroidUtils.dpToPx(context, 4f)
            }
            setColorFilter(Color.WHITE)
            setOnClickListener { dismiss() }
        }
        addView(closeButton)

        isClickable = true
        isFocusable = true
    }

    fun setMessage(text: String, isWarning: Boolean = false) {
        messageTextView.text = text
        if (isWarning) {
            messageTextView.setTextColor(ContextCompat.getColor(context, R.color.text_color_negative))
        } else {
            messageTextView.setTextColor(Color.WHITE)
        }
    }

    fun setConfirmAction(label: String, onConfirm: () -> Unit) {
        if (slideToConfirmView == null) {
            slideToConfirmView = createSlideToConfirm()
            contentLayout.addView(slideToConfirmView)
        }
        slideToConfirmView?.label = label
        slideToConfirmView?.onConfirm = {
            onConfirm()
            dismiss()
        }
    }

    fun setSecondaryConfirmAction(label: String, onConfirm: () -> Unit) {
        if (secondarySlideToConfirmView == null) {
            secondarySlideToConfirmView = createSlideToConfirm()
            contentLayout.addView(secondarySlideToConfirmView)
        }
        secondarySlideToConfirmView?.label = label
        secondarySlideToConfirmView?.onConfirm = {
            onConfirm()
            dismiss()
        }
    }

    private fun createSlideToConfirm(): SlideToConfirmView {
        return SlideToConfirmView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                AndroidUtils.dpToPx(context, 48f)
            ).apply {
                topMargin = AndroidUtils.dpToPx(context, 8f)
            }
        }
    }

    fun show(durationMs: Long) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        if (durationMs > 0) {
            val runnable = Runnable { dismiss() }
            dismissRunnable = runnable
            handler.postDelayed(runnable, durationMs)
        }
    }

    fun dismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        onDismiss?.invoke()
    }

    override fun setCompactMode(enabled: Boolean) {
        val padding = if (enabled) 8f else 12f
        val px = AndroidUtils.dpToPx(context, padding)
        setPadding(px, px, px, px)
        messageTextView.textSize = if (enabled) 14f else 16f
    }
}
