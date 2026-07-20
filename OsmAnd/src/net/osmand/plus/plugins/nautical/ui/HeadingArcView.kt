package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.HapticFeedbackConstants
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
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
    private var isNightMode: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPath = Path()
    private var osmandOrange = "#FF8800".toColorInt()
    
    private val scroller = Scroller(context, DecelerateInterpolator())
    private var velocityTracker: VelocityTracker? = null
    
    private val cardinalPoints = mapOf(
        0 to R.string.nautical_cardinal_n,
        45 to R.string.nautical_cardinal_ne,
        90 to R.string.nautical_cardinal_e,
        135 to R.string.nautical_cardinal_se,
        180 to R.string.nautical_cardinal_s,
        225 to R.string.nautical_cardinal_sw,
        270 to R.string.nautical_cardinal_w,
        315 to R.string.nautical_cardinal_nw
    )

    init {
        isClickable = true
        isFocusable = true
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

    fun calculateError(actual: Int, target: Int): Float {
        var diff = (actual - target).toFloat()
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h * 0.45f 
        val paddingView = 20f

        val pixelsPerDegree = when(currentMode) {
            "TRACK" -> w / 25f 
            else -> w / 80f    
        }
        
        val textColorPrimary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        val textColorSecondary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
        val dividerColor = ContextCompat.getColor(context, if (isNightMode) R.color.divider_color_dark else R.color.divider_color_light)

        tickPaint.color = textColorPrimary
        tickPaint.alpha = 100
        
        // 1. Draw Absolute Compass Tape
        val visibleSpan = (w / pixelsPerDegree).toInt() + 10
        val startH = targetHeading - visibleSpan / 2
        val endH = targetHeading + visibleSpan / 2
        
        for (hDeg in startH..endH) {
            val normH = (hDeg + 360) % 360
            val x = centerX + (hDeg - targetHeading) * pixelsPerDegree
            
            if (x < paddingView || x > (w - paddingView)) continue

            val isCardinal = cardinalPoints.containsKey(normH)
            val isMajor = normH % 10 == 0
            val isMinor = normH % 5 == 0
            
            if (isCardinal || isMajor || isMinor) {
                tickPaint.alpha = if (isCardinal || isMajor) 200 else 80
                tickPaint.strokeWidth = if (isCardinal || isMajor) 3f else 1.5f
                val tickH = if (isCardinal || isMajor) 30f else 15f
                canvas.drawLine(x, centerY + 20f, x, (centerY + 20f) - tickH, tickPaint)
                
                if (isCardinal) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = 28f
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = osmandOrange
                    paint.alpha = 255
                    paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    canvas.drawText(context.getString(cardinalPoints[normH]!!), x, centerY + 58f, paint)
                } else if (isMajor) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = 22f
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = textColorSecondary
                    paint.alpha = 200
                    paint.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    canvas.drawText(normH.toString(), x, centerY + 58f, paint)
                }
            }
        }

        // 2. Error Marker (Actual Heading Pointer)
        actualHeading?.let { actual ->
            val error = calculateError(actual, targetHeading)
            val x = centerX + error * pixelsPerDegree
            if (x in paddingView..(w - paddingView)) {
                val color = if (isRecovering) ContextCompat.getColor(context, R.color.nautical_status_green) 
                            else ContextCompat.getColor(context, R.color.nautical_status_yellow)
                paint.color = color
                paint.style = Paint.Style.FILL
                paint.alpha = 240
                
                canvas.drawCircle(x, centerY + 20f, 6f, paint)
                paint.strokeWidth = 3f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(x, centerY + 20f, x, centerY + 45f, paint)
                
                paint.textSize = 22f
                paint.style = Paint.Style.FILL
                paint.alpha = 255
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                paint.color = color
                val sign = if (error > 0) "+" else ""
                canvas.drawText(String.format(Locale.US, "%s%.1f°", sign, error), x, centerY + 72f, paint)
            }
        }

        // 3. Wind Indicators
        windAngleApparent?.let { awa ->
            val actual = actualHeading ?: targetHeading
            val absWind = (actual + awa + 360) % 360
            val windErr = calculateError(absWind, targetHeading)
            val x = centerX + windErr * pixelsPerDegree
            if (x in paddingView..(w - paddingView)) {
                paint.color = Color.CYAN
                paint.style = Paint.Style.FILL
                paint.alpha = 180
                canvas.drawCircle(x, centerY + 20f, 8f, paint)
                
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawLine(x, centerY + 20f, x, centerY + 5f, paint)

                // AWA Label
                paint.style = Paint.Style.FILL
                paint.textSize = 18f
                paint.alpha = 255
                canvas.drawText(context.getString(R.string.nautical_awa_label), x, centerY + 2f, paint)
            }
        }

        targetWindAngleApparent?.let { targetAwa ->
            val actual = actualHeading ?: targetHeading
            val absTargetWind = (actual + targetAwa + 360) % 360
            val targetWindErr = calculateError(absTargetWind, targetHeading)
            val x = centerX + targetWindErr * pixelsPerDegree
            if (x in paddingView..(w - paddingView)) {
                paint.color = Color.CYAN
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.alpha = 220
                canvas.drawCircle(x, centerY + 20f, 10f, paint)
                
                paint.style = Paint.Style.FILL
                paint.textSize = 14f
                paint.alpha = 255
                canvas.drawText("T", x, centerY + 25f, paint)
            }
        }

        // Lubber Line
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
        textPaint.color = textColorPrimary
        textPaint.textSize = 90f
        canvas.drawText("${targetHeading}°", centerX, centerY - 40f, textPaint)
        
        paint.textSize = 20f
        paint.color = textColorSecondary
        paint.alpha = 200
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText(context.getString(R.string.nautical_set_heading_label), centerX, centerY - 135f, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingArcView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_pilot_title) + ": " + targetHeading + "°"
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            targetHeading = scroller.currX % 360
            onHeadingChanged?.invoke(targetHeading)
            postInvalidateOnAnimation()
        }
    }

    private var lastX = 0f
    private var isDragging = false
    private var accumulatedDelta = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastX = event.x
                accumulatedDelta = 0f
                isDragging = true
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - lastX
                lastX = event.x
                
                val pixelsPerDegree = when(currentMode) {
                    "TRACK" -> width / 25f 
                    else -> width / 80f    
                }
                
                accumulatedDelta -= deltaX / pixelsPerDegree
                val change = accumulatedDelta.toInt()
                if (change != 0) {
                    accumulatedDelta -= change
                    val oldH = targetHeading
                    targetHeading = (targetHeading + change + 360) % 360
                    if (targetHeading != oldH) {
                        if (targetHeading % 10 == 0) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        else performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onHeadingChanged?.invoke(targetHeading)
                        onHeadingPreview?.invoke(targetHeading)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker?.let { vt ->
                    vt.computeCurrentVelocity(1000)
                    val velocityX = vt.xVelocity
                    if (abs(velocityX) > 500) {
                        val pixelsPerDegree = when(currentMode) {
                            "TRACK" -> width / 25f 
                            else -> width / 80f    
                        }
                        scroller.fling(targetHeading, 0, -(velocityX / pixelsPerDegree).toInt(), 0, -10000, 10000, 0, 0)
                        postInvalidateOnAnimation()
                    }
                }
                onHeadingPreview?.invoke(null)
                onHeadingCommitted?.invoke(targetHeading)
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
