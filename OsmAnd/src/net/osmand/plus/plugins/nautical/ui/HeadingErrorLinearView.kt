package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.utils.NauticalFormatter
import kotlin.math.abs

class HeadingErrorLinearView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var headingError: Float = 0f
        set(value) {
            field = value.coerceIn(-45f, 45f)
            invalidate()
        }

    var label: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private var isNightMode: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var colorOrange = Color.TRANSPARENT
    private var colorPrimary = Color.TRANSPARENT
    private var colorSecondary = Color.TRANSPARENT
    private var colorGreen = Color.TRANSPARENT
    private var colorYellow = Color.TRANSPARENT
    private var colorRed = Color.TRANSPARENT
    private val indicatorPath = Path()

    init {
        isClickable = true
        isFocusable = true
        updateColors()
        paint.strokeCap = Paint.Cap.ROUND
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun updateColors() {
        colorOrange = ContextCompat.getColor(context, R.color.icon_color_osmand_light)
        colorPrimary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        colorSecondary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
        colorGreen = ContextCompat.getColor(context, R.color.nautical_status_green)
        colorYellow = ContextCompat.getColor(context, R.color.nautical_status_yellow)
        colorRed = ContextCompat.getColor(context, R.color.nautical_status_red)
    }

    fun setNightMode(night: Boolean) {
        if (this.isNightMode != night) {
            this.isNightMode = night
            updateColors()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        
        val padding = 40f
        val scaleWidth = w - (padding * 2)

        // 1. Draw Background Scale Line
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = colorSecondary
        paint.alpha = 60
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        // 2. Draw Ticks
        for (i in (-45..45) step 5) {
            val ratio = (i + 45) / 90f
            val x = padding + (ratio * scaleWidth)
            
            val isMajor = i % 15 == 0
            val tickLen = if (isMajor) 12f else 6f
            
            paint.alpha = if (isMajor) 200 else 80
            paint.strokeWidth = if (isMajor) 2.5f else 1.5f
            paint.color = if (i == 0) colorOrange else colorPrimary
            
            canvas.drawLine(x, centerY - tickLen, x, centerY + tickLen, paint)
        }

        // 3. Draw Error Indicator (Triangle pointing up)
        val errorRatio = (headingError + 45) / 90f
        val indicatorX = padding + (errorRatio * scaleWidth)
        
        paint.style = Paint.Style.FILL
        paint.color = when {
            abs(headingError) < 5 -> colorGreen
            abs(headingError) < 15 -> colorYellow
            else -> colorRed
        }
        paint.alpha = 255
        
        indicatorPath.reset()
        indicatorPath.moveTo(indicatorX, centerY - 2f)
        indicatorPath.lineTo(indicatorX - 8f, centerY - 14f)
        indicatorPath.lineTo(indicatorX + 8f, centerY - 14f)
        indicatorPath.close()
        canvas.drawPath(indicatorPath, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingErrorLinearView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_hdg_err) + ": " + headingError.toString() + "°"
    }
}
