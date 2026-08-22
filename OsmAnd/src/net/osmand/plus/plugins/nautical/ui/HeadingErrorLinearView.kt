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
    private val DEGREE_LABELS = arrayOf("-40°", "-20°", "0°", "+20°", "+40°")
    private val DEGREE_VALUES = intArrayOf(-40, -20, 0, 20, 40)

    private var dp1 = 0f
    private var dp2 = 0f
    private var dp3 = 0f
    private var dp4 = 0f
    private var dp5 = 0f
    private var dp6 = 0f
    private var dp8 = 0f
    private var dp10 = 0f
    private var dp12 = 0f
    private var dp14 = 0f
    private var dp16 = 0f

    init {
        isClickable = true
        isFocusable = true
        val density = resources.displayMetrics.density
        dp1 = 1f * density
        dp2 = 2f * density
        dp3 = 3f * density
        dp4 = 4f * density
        dp5 = 5f * density
        dp6 = 6f * density
        dp8 = 8f * density
        dp10 = 10f * density
        dp12 = 12f * density
        dp14 = 14f * density
        dp16 = 16f * density
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
        if (w <= 0 || h <= 0) return
        
        val centerY = h * 0.38f
        val padding = dp16
        val scaleWidth = w - (padding * 2)

        // 1. Draw Background Scale Line
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp2
        paint.color = colorSecondary
        paint.alpha = 70
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        // 2. Draw Ticks
        for (i in (-45..45) step 5) {
            val ratio = (i + 45) / 90f
            val x = padding + (ratio * scaleWidth)
            
            val isMajor = i % 10 == 0
            val tickLen = if (isMajor) dp8 else dp4
            
            paint.alpha = if (isMajor) 220 else 90
            paint.strokeWidth = if (isMajor) dp2 else dp1
            paint.color = if (i == 0) colorOrange else colorPrimary
            
            canvas.drawLine(x, centerY - tickLen, x, centerY + tickLen, paint)
        }

        // 3. Draw Degree Offset Labels with High Legibility
        textPaint.textSize = dp12
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val textY = centerY + dp16
        for (idx in DEGREE_VALUES.indices) {
            val degVal = DEGREE_VALUES[idx]
            val ratio = (degVal + 45f) / 90f
            val x = padding + (ratio * scaleWidth)
            textPaint.color = if (degVal == 0) colorOrange else colorSecondary
            textPaint.alpha = if (degVal == 0) 255 else 200
            canvas.drawText(DEGREE_LABELS[idx], x, textY, textPaint)
        }

        // 4. Draw Dynamic Error Magnitude Highlight Bar
        val zeroX = padding + (0.5f * scaleWidth)
        val errorRatio = (headingError + 45f) / 90f
        val indicatorX = padding + (errorRatio * scaleWidth)

        val errorColor = when {
            abs(headingError) < 5f -> colorGreen
            abs(headingError) < 15f -> colorYellow
            else -> colorRed
        }

        if (abs(headingError) > 0.5f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp4
            paint.color = errorColor
            paint.alpha = 190
            canvas.drawLine(zeroX, centerY, indicatorX, centerY, paint)
        }

        // 5. Draw Error Indicator (Triangle pointing up)
        paint.style = Paint.Style.FILL
        paint.color = errorColor
        paint.alpha = 255
        
        indicatorPath.reset()
        indicatorPath.moveTo(indicatorX, centerY - dp2)
        indicatorPath.lineTo(indicatorX - dp6, centerY - dp12)
        indicatorPath.lineTo(indicatorX + dp6, centerY - dp12)
        indicatorPath.close()
        canvas.drawPath(indicatorPath, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingErrorLinearView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_hdg_err) + ": " + headingError.toString() + "°"
    }
}
