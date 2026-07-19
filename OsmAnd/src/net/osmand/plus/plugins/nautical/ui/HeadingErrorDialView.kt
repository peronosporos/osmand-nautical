package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import net.osmand.plus.R
import java.util.Locale
import kotlin.math.*

class HeadingErrorDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onHeadingChanged: ((Int) -> Unit)? = null
    var targetHeading: Int = 0
    var headingError: Float = 0f
        set(value) {
            field = value.coerceIn(-45f, 45f)
            invalidate()
        }

    private var isNightMode: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialRect = RectF()
    private var osmandOrange = Color.parseColor("#FF8800")

    init {
        isClickable = true
        isFocusable = true
        osmandOrange = ContextCompat.getColor(context, R.color.icon_color_osmand_light)
        paint.strokeCap = Paint.Cap.ROUND
        textPaint.textAlign = Paint.Align.CENTER
    }

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) / 2f * 0.8f
        val centerX = w / 2f
        val centerY = h / 2f

        dialRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        val textColorPrimary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        val textColorSecondary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)

        // 1. Background Arc (Modern thin line)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = textColorPrimary
        paint.alpha = 100
        canvas.drawArc(dialRect, 140f, 260f, false, paint)

        // 2. Modern Ticks
        paint.strokeWidth = 2f
        for (i in -45..45 step 15) {
            val angle = 270f + i
            val rad = Math.toRadians(angle.toDouble())
            val startX = centerX + (radius - 8f) * cos(rad).toFloat()
            val startY = centerY + (radius - 8f) * sin(rad).toFloat()
            val endX = centerX + (radius + 8f) * cos(rad).toFloat()
            val endY = centerY + (radius + 8f) * sin(rad).toFloat()
            
            paint.alpha = if (i == 0) 255 else 120
            paint.color = textColorPrimary
            canvas.drawLine(startX, startY, endX, endY, paint)
            
            if (i % 30 == 0) {
                textPaint.textSize = 22f
                textPaint.color = textColorPrimary
                textPaint.alpha = 180
                textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                val labelRadius = radius + 30f
                val labelX = centerX + labelRadius * cos(rad).toFloat()
                val labelY = centerY + labelRadius * sin(rad).toFloat() + 8f
                canvas.drawText(abs(i).toString(), labelX, labelY, textPaint)
            }
        }

        // 3. Center Lubber Line
        paint.color = osmandOrange
        paint.strokeWidth = 4f
        paint.alpha = 255
        canvas.drawLine(centerX, centerY - radius + 10f, centerX, centerY - radius - 10f, paint)

        // 4. Modern Needle (Diamond indicator)
        val needleAngle = 270f + headingError
        val needleRad = Math.toRadians(needleAngle.toDouble())
        val needleX = centerX + radius * cos(needleRad).toFloat()
        val needleY = centerY + radius * sin(needleRad).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = when {
            abs(headingError) < 5 -> ContextCompat.getColor(context, R.color.nautical_status_green)
            abs(headingError) < 15 -> ContextCompat.getColor(context, R.color.nautical_status_yellow)
            else -> ContextCompat.getColor(context, R.color.nautical_status_red)
        }
        canvas.drawCircle(needleX, needleY, 10f, paint)
        
        // Digital Readout
        textPaint.textSize = 64f
        textPaint.color = textColorPrimary
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val errorText = String.format(Locale.US, "%.1f°", headingError)
        canvas.drawText(errorText, centerX, centerY + 15f, textPaint)
        
        textPaint.textSize = 18f
        textPaint.color = textColorSecondary
        textPaint.alpha = 150
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText(context.getString(R.string.nautical_hdg_err_label), centerX, centerY + 45f, textPaint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingErrorDialView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_hdg_err) + ": " + String.format(Locale.US, "%.1f°", headingError)
    }

    private var lastAngle = 0.0
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x - width / 2f
        val y = event.y - height / 2f
        val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastAngle = angle
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var delta = (angle - lastAngle)
                while (delta > 180) delta -= 360
                while (delta < -180) delta += 360

                if (abs(delta) >= 1.0) {
                    val oldHeading = targetHeading
                    targetHeading = (targetHeading + delta.toInt() + 360) % 360
                    if (targetHeading != oldHeading) {
                        if (targetHeading % 10 == 0) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        onHeadingChanged?.invoke(targetHeading)
                    }
                    lastAngle = angle
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
