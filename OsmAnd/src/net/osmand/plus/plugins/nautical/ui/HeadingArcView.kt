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

    var actualHeading: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var windAngleApparent: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var targetWindAngleApparent: Int? = null
        set(value) {
            if (field != value) {
                field = value
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windPath = Path()

    init {
        paint.strokeWidth = 14f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        textPaint.color = ContextCompat.getColor(context, R.color.text_color_primary_light)
        textPaint.textSize = 84f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif-condensed-light", Typeface.BOLD)

        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val radius = min(w, h * 2) * 0.42f
        val centerY = h * 0.9f

        arcRect.left = centerX - radius
        arcRect.top = centerY - radius
        arcRect.right = centerX + radius
        arcRect.bottom = centerY + radius

        val startAngle = 210f
        val sweepAngle = 120f

        // Draw background track
        paint.color = Color.GRAY
        paint.alpha = 40
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, paint)

        // Draw detailed ticks
        tickPaint.color = if (isNightMode) Color.LTGRAY else Color.DKGRAY
        for (i in -60..60 step 5) {
            val angle = i.toFloat()
            val rad = Math.toRadians((angle - 90).toDouble())
            val innerR = if (i % 10 == 0) radius - 30f else radius - 15f
            val outerR = radius + 5f
            
            val startX = centerX + (innerR * cos(rad)).toFloat()
            val startY = centerY + (innerR * sin(rad)).toFloat()
            val endX = centerX + (outerR * cos(rad)).toFloat()
            val endY = centerY + (outerR * sin(rad)).toFloat()
            
            tickPaint.alpha = if (i % 10 == 0) 200 else 100
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }

        // Target Heading Shadow (Actual)
        actualHeading?.let { actual ->
            val diff = ((actual - targetHeading + 540) % 360) - 180
            if (abs(diff) < 60) {
                paint.color = Color.RED
                paint.alpha = 150
                paint.strokeWidth = 20f
                canvas.drawArc(arcRect, 270f + diff - 1f, 2f, false, paint)
                paint.strokeWidth = 14f
            }
        }

        // Draw center indicator (Target)
        paint.color = ContextCompat.getColor(context, R.color.active_color_primary_light)
        paint.alpha = 255
        paint.strokeWidth = 18f
        canvas.drawArc(arcRect, 269f, 2f, false, paint)
        paint.strokeWidth = 14f

        // Draw Wind Indicator
        actualHeading?.let { actual ->
            windAngleApparent?.let { wind ->
                val diff = ((actual - targetHeading + 540) % 360) - 180
                val windAngleRel = diff.toFloat() + wind.toFloat()
                if (abs(windAngleRel) < 70f) {
                    val rad = Math.toRadians((windAngleRel - 90f).toDouble())
                    val arrowR = radius + 20f
                    val arrowX = centerX + (arrowR * cos(rad)).toFloat()
                    val arrowY = centerY + (arrowR * sin(rad)).toFloat()
                    
                    paint.style = Paint.Style.FILL
                    paint.color = Color.CYAN
                    paint.alpha = 200
                    
                    // Draw a small wind arrow
                    windPath.reset()
                    windPath.moveTo(arrowX, arrowY)
                    windPath.lineTo(arrowX - 15f, arrowY + 25f)
                    windPath.lineTo(arrowX + 15f, arrowY + 25f)
                    windPath.close()
                    
                    canvas.save()
                    canvas.rotate(windAngleRel, arrowX, arrowY)
                    canvas.drawPath(windPath, paint)
                    canvas.restore()
                    
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 14f
                }
            }
        }

        // Maneuver Visualization
        if (isTacking) {
            paint.color = Color.YELLOW
            paint.alpha = 80
            paint.strokeWidth = 30f
            // Highlight a 45 degree sector indicating the turn
            canvas.drawArc(arcRect, 270f - 22.5f, 45f, false, paint)
            paint.strokeWidth = 14f
        }

        // Draw heading text
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        canvas.drawText("${targetHeading}°", centerX, centerY - radius * 0.35f, textPaint)
    }

    private var isNightMode: Boolean = false

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
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
