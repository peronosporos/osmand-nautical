package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.HapticFeedbackConstants
import androidx.core.content.ContextCompat
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

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f

        // 1. Zoom logic (Professional Variable Sensitivity)
        val pixelsPerDegree = when(currentMode) {
            "TRACK" -> w / 20f // Highly sensitive: +/- 10 degrees window
            else -> w / 60f    // Standard: +/- 30 degrees window
        }
        
        tickPaint.color = if (isNightMode) Color.LTGRAY else Color.DKGRAY
        tickPaint.alpha = 100
        
        // Draw baseline
        canvas.drawLine(0f, centerY + 20f, w, centerY + 20f, tickPaint)

        // 2. Pro-style Ticks (Deviation scale only)
        val scale = listOf(-20, -10, -5, 0, 5, 10, 20)
        for (s in scale) {
            val x = centerX + (s * pixelsPerDegree)
            if (x < 0 || x > w) continue

            val isCenter = s == 0
            tickPaint.alpha = if (isCenter) 255 else 150
            tickPaint.strokeWidth = if (isCenter) 4f else 2f
            val tickH = if (isCenter) 50f else 30f
            
            canvas.drawLine(x, centerY + 20f, x, centerY + 20f - tickH, tickPaint)
            
            // Label deviations
            if (!isCenter) {
                paint.style = Paint.Style.FILL
                paint.textSize = 20f
                paint.textAlign = Paint.Align.CENTER
                paint.color = tickPaint.color
                paint.alpha = 180
                canvas.drawText("${abs(s)}", x, centerY + 50f, paint)
            }
        }

        // 3. Handling the Marker (The "Professional" Touch)
        actualHeading?.let { actual ->
            val error = calculateError(actual, targetHeading)
            val window = if (currentMode == "TRACK") 10f else 30f
            
            if (abs(error) <= window) {
                val x = centerX + error * pixelsPerDegree
                
                // Color-code based on trend
                paint.color = if (isRecovering) Color.GREEN else Color.parseColor("#FFBF00") // Green or Amber
                paint.style = Paint.Style.FILL
                paint.alpha = 200
                
                // Damping visual: thickness changes based on stability
                val baseSize = 15f
                val jitterModifier = (errorDelta * 5f).coerceIn(0f, 15f)
                val markerWidth = baseSize - jitterModifier
                
                actualPath.reset()
                actualPath.moveTo(x, centerY + 20f)
                actualPath.lineTo(x - markerWidth, centerY + 45f)
                actualPath.lineTo(x + markerWidth, centerY + 45f)
                actualPath.close()
                canvas.drawPath(actualPath, paint)
                
                // Add a small digital error readout near the marker
                paint.textSize = 18f
                paint.alpha = 255
                canvas.drawText(String.format(Locale.US, "%.1f", error), x, centerY + 65f, paint)
            }
        }

        // Static Center Target Indicator (The "Lubber Line")
        paint.color = osmandOrange
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        centerPath.reset()
        centerPath.moveTo(centerX, centerY + 20f)
        centerPath.lineTo(centerX - 10f, centerY - 5f)
        centerPath.lineTo(centerX + 10f, centerY - 5f)
        centerPath.close()
        canvas.drawPath(centerPath, paint)

        // Draw Central Digital Readout (Target)
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        canvas.drawText("${targetHeading}°", centerX, centerY - 45f, textPaint)
        
        paint.textSize = 18f
        paint.color = textPaint.color
        paint.alpha = 150
        canvas.drawText("SET", centerX, centerY - 100f, paint)

        // Draw Wind Indicator on Tape
        windAngleApparent?.let { wind ->
            val actual = actualHeading?.toDouble() ?: targetHeading.toDouble()
            val diffActual = calculateError(actual.toInt(), targetHeading)
            val windAngleRel = diffActual + Math.toDegrees(wind.toDouble()).toFloat()
            
            val window = if (currentMode == "TRACK") 10f else 30f
            if (abs(windAngleRel) <= window) {
                val x = centerX + windAngleRel * pixelsPerDegree
                paint.color = Color.CYAN
                paint.alpha = 150
                canvas.drawCircle(x, centerY + 20f, 8f, paint)
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
