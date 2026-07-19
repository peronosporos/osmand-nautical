package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.HapticFeedbackConstants
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import net.osmand.plus.R
import java.util.Locale
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
                val oldError = field?.let { abs(calculateError(it, targetHeading)) } ?: 0f
                val newError = value?.let { abs(calculateError(it, targetHeading)) } ?: 0f
                isRecovering = newError < oldError
                errorDelta = abs(newError - oldError)
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

    var currentMode: String = "AUTO"
        set(value) {
            field = value.uppercase(Locale.US)
            invalidate()
        }

    private var isRecovering: Boolean = false
    private var errorDelta: Float = 0f
    private var isNightMode: Boolean = false

    fun calculateError(actual: Int, target: Int): Float {
        var diff = (actual - target).toFloat()
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPath = Path()
    private var osmandOrange = "#FF8800".toColorInt()

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

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        // Shift centerY slightly up to fit error labels at the bottom
        val centerY = h * 0.45f 
        val paddingView = 20f

        // 1. Professional Zoom logic
        val pixelsPerDegree = when(currentMode) {
            "TRACK" -> w / 25f 
            else -> w / 70f    
        }
        
        tickPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.divider_color_dark else R.color.divider_color_light)
        tickPaint.alpha = 180
        
        // Draw baseline
        canvas.drawLine(paddingView, centerY + 20f, w - paddingView, centerY + 20f, tickPaint)

        // 2. Pro-style Ticks
        val scale = listOf(-30, -20, -10, -5, 0, 5, 10, 20, 30)
        for (s in scale) {
            val x = centerX + (s * pixelsPerDegree)
            if (x < paddingView || x > (w - paddingView)) continue

            val isCenter = s == 0
            tickPaint.alpha = if (isCenter) 255 else 120
            tickPaint.strokeWidth = if (isCenter) 3f else 1.5f
            val tickH = if (isCenter) 40f else 20f
            
            canvas.drawLine(x, centerY + 20f, x, (centerY + 20f) - tickH, tickPaint)
            
            // Label deviations
            if (!isCenter && (abs(s) % 10 == 0)) {
                paint.style = Paint.Style.FILL
                paint.textSize = 22f
                paint.textAlign = Paint.Align.CENTER
                paint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
                paint.alpha = 200
                canvas.drawText(abs(s).toString(), x, centerY + 52f, paint)
            }
        }

        // 3. Professional Error Marker (More instrument-like)
        actualHeading?.let { actual ->
            val error = calculateError(actual, targetHeading)
            val window = if (currentMode == "TRACK") 12.5f else 35f
            
            if (abs(error) <= window) {
                val x = centerX + error * pixelsPerDegree
                if (x in paddingView..(w - paddingView)) {
                    // Color-code based on trend
                    val color = if (isRecovering) ContextCompat.getColor(context, R.color.nautical_status_green) 
                                else ContextCompat.getColor(context, R.color.nautical_status_yellow)
                    paint.color = color
                    paint.style = Paint.Style.FILL
                    paint.alpha = 240
                    
                    // Sleek Pointer
                    val markerRadius = 6f
                    canvas.drawCircle(x, centerY + 20f, markerRadius, paint)
                    
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(x, centerY + 20f, x, centerY + 45f, paint)
                    
                    // Error readout
                    paint.textSize = 22f
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    val sign = if (error > 0) "+" else if (error < 0) "" else ""
                    canvas.drawText(String.format(Locale.US, "%s%.1f°", sign, error), x, centerY + 72f, paint)
                }
            }
        }

        // Lubber Line (Modern Glass Cockpit style)
        paint.color = osmandOrange
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.alpha = 255
        canvas.drawLine(centerX, centerY + 20f, centerX, centerY - 20f, paint)
        
        paint.style = Paint.Style.FILL
        centerPath.reset()
        centerPath.moveTo(centerX, centerY + 20f)
        centerPath.lineTo(centerX - 8f, centerY + 32f)
        centerPath.lineTo(centerX + 8f, centerY + 32f)
        centerPath.close()
        canvas.drawPath(centerPath, paint)

        // Central Digital Readout
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        textPaint.textSize = 90f
        canvas.drawText("${targetHeading}°", centerX, centerY - 40f, textPaint)
        
        paint.textSize = 20f
        paint.color = textPaint.color
        paint.alpha = 140
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("SET HEADING", centerX, centerY - 105f, paint)

        // Draw Wind Indicator on Tape
        windAngleApparent?.let { wind ->
            val actual = actualHeading?.toDouble() ?: targetHeading.toDouble()
            val diffActual = calculateError(actual.toInt(), targetHeading)
            val windAngleRel = diffActual + Math.toDegrees(wind.toDouble()).toFloat()

            val window = if (currentMode == "TRACK") 12.5f else 35f
            if (abs(windAngleRel) <= window) {
                val x = centerX + windAngleRel * pixelsPerDegree
                if (x in paddingView..(w - paddingView)) {
                    paint.color = Color.CYAN
                    paint.alpha = 150
                    canvas.drawCircle(x, centerY + 20f, 8f, paint)
                }
            }
        }
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
