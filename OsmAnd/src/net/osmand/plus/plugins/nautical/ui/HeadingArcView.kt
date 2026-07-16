package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.HapticFeedbackConstants
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import kotlin.math.*

class HeadingArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onHeadingChanged: ((Int) -> Unit)? = null
    var onHeadingPreview: ((Int?) -> Unit)? = null
    var onHeadingCommitted: ((Int) -> Unit)? = null
    var targetHeading: Int = 0
        set(value) {
            val v = (value + 360) % 360
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var isTacking: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var tackProgress: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    init {
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        textPaint.color = ContextCompat.getColor(context, R.color.text_color_primary_light)
        textPaint.textSize = 72f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val radius = min(w, h * 2) * 0.45f
        val centerY = h * 0.85f

        arcRect.left = centerX - radius
        arcRect.top = centerY - radius
        arcRect.right = centerX + radius
        arcRect.bottom = centerY + radius

        // Draw background arc
        paint.color = Color.LTGRAY
        paint.alpha = 100
        canvas.drawArc(arcRect, 210f, 120f, false, paint)

        if (isTacking) {
            paint.color = Color.YELLOW
            paint.alpha = 255
            canvas.drawArc(arcRect, 210f, 120f * tackProgress, false, paint)
        }

        // Draw center tick
        paint.color = Color.GRAY
        paint.alpha = 255
        canvas.drawLine(centerX, (centerY - radius) - 20f, centerX, (centerY - radius) + 20f, paint)

        // Draw heading text
        textPaint.color = ContextCompat.getColor(context, if (isNightMode()) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        canvas.drawText("${targetHeading}°", centerX, centerY - radius * 0.4f, textPaint)
    }

    private fun isNightMode(): Boolean {
        // Simple check, in real OsmAnd we'd use DaynightHelper
        return false 
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private var lastX = 0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - lastX
                if (abs(deltaX) > 8) {
                    val change = (deltaX / 12).toInt()
                    if (change != 0) {
                        val oldHeading = targetHeading
                        targetHeading = (targetHeading + change + 360) % 360
                        if (targetHeading != oldHeading) {
                            if (targetHeading % 10 == 0) {
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else {
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            onHeadingChanged?.invoke(targetHeading)
                            onHeadingPreview?.invoke(targetHeading)
                        }
                        lastX = event.x
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onHeadingPreview?.invoke(null)
                onHeadingCommitted?.invoke(targetHeading)
            }
        }
        return true
    }
}
