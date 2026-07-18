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
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val actualPath = Path()
    private val centerPath = Path()
    private var osmandOrange = Color.parseColor("#FF8800")

    init {
        osmandOrange = ContextCompat.getColor(context, R.color.icon_color_osmand_light)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        textPaint.textSize = 80f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f

        // Draw Linear Heading Tape
        val pixelsPerDegree = w / 60f // Show 60 degrees window
        
        tickPaint.color = if (isNightMode) Color.LTGRAY else Color.DKGRAY
        tickPaint.alpha = 100
        
        // Draw baseline
        canvas.drawLine(0f, centerY + 20f, w, centerY + 20f, tickPaint)

        // Draw Ticks
        val startH = targetHeading - 30
        val endH = targetHeading + 30
        
        for (i in startH..endH) {
            val hValue = (i + 360) % 360
            val x = centerX + (i - targetHeading) * pixelsPerDegree
            
            if (hValue % 5 == 0) {
                val tickH = if (hValue % 10 == 0) 40f else 20f
                tickPaint.alpha = if (hValue % 10 == 0) 255 else 150
                canvas.drawLine(x, centerY + 20f, x, centerY + 20f - tickH, tickPaint)
                
                if (hValue % 10 == 0) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = 24f
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = tickPaint.color
                    paint.alpha = 200
                    canvas.drawText(hValue.toString(), x, centerY + 50f, paint)
                }
            }
        }

        // Draw Actual Heading Indicator (if different from target)
        actualHeading?.let { actual ->
            val diff = ((actual.toFloat() - targetHeading.toFloat() + 540f) % 360f) - 180f
            if (abs(diff) <= 30) {
                val x = centerX + diff * pixelsPerDegree
                paint.color = Color.RED
                paint.style = Paint.Style.FILL
                paint.alpha = 180
                // Draw a small triangle for actual heading
                actualPath.reset()
                actualPath.moveTo(x, centerY + 20f)
                actualPath.lineTo(x - 10f, centerY + 40f)
                actualPath.lineTo(x + 10f, centerY + 40f)
                actualPath.close()
                canvas.drawPath(actualPath, paint)
            }
        }

        // Draw Target Center Indicator (Static)
        paint.color = osmandOrange
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        centerPath.reset()
        centerPath.moveTo(centerX, centerY + 20f)
        centerPath.lineTo(centerX - 15f, centerY - 10f)
        centerPath.lineTo(centerX + 15f, centerY - 10f)
        centerPath.close()
        canvas.drawPath(centerPath, paint)

        // Draw Central Digital Readout
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        canvas.drawText("${targetHeading}°", centerX, centerY - 40f, textPaint)

        // Draw Wind Indicator on Tape
        windAngleApparent?.let { wind ->
            val actual = actualHeading?.toDouble() ?: targetHeading.toDouble()
            val diffActual = ((actual.toFloat() - targetHeading.toFloat() + 540f) % 360f) - 180f
            val windAngleRel = diffActual + wind.toFloat()
            
            if (abs(windAngleRel) <= 30f) {
                val x = centerX + windAngleRel * pixelsPerDegree
                paint.color = Color.CYAN
                paint.alpha = 150
                canvas.drawCircle(x, centerY + 20f, 8f, paint)
            }
        }
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
                if (abs(deltaX) > 4) {
                    val change = -(deltaX / 8).toInt() // Reverse direction for natural tape scrolling
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
