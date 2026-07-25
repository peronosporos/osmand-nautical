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
import net.osmand.plus.R
import java.util.Locale
import kotlin.math.*

class HeadingArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onCenterClicked: (() -> Unit)? = null
    var onHeadingChanged: ((Int) -> Unit)? = null
    var onWindAngleChanged: ((Int) -> Unit)? = null
    
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
                val oldTarget = targetHeading
                val oldActual = field
                if (oldActual != null && value != null) {
                    val oldErr = abs(calculateError(oldActual, oldTarget))
                    val newErr = abs(calculateError(value, oldTarget))
                    // isRecovering if error is decreasing and still significant
                    isRecovering = (newErr < oldErr - 0.5f) || (newErr < 2.0f)
                }
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
    private val pointerPath = Path()
    private var osmandOrange = Color.TRANSPARENT
    
    private var isDragging = false

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
        val centerY = h / 2f
        val radius = min(w, h) / 2f * 0.85f
        
        val textColorPrimary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        val textColorSecondary = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)

        // 1. Draw Compass Ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = textColorSecondary
        paint.alpha = 40
        canvas.drawCircle(centerX, centerY, radius, paint)

        // 2. Draw Compass Ticks (Rotating the canvas to make drawing easier)
        canvas.save()
        canvas.rotate(-targetHeading.toFloat(), centerX, centerY)
        
        for (hDeg in 0 until 360 step 5) {
            val normH = hDeg % 360
            val angleOnCircle = hDeg.toFloat() - 90f // 0 is top
            val rad = Math.toRadians(angleOnCircle.toDouble())
            
            val cosRad = cos(rad).toFloat()
            val sinRad = sin(rad).toFloat()
            
            val isCardinal = cardinalPoints.containsKey(normH)
            val isMajor = normH % 10 == 0
            val isMinor = normH % 5 == 0
            
            if (isCardinal || isMajor || isMinor) {
                tickPaint.color = textColorPrimary
                tickPaint.alpha = if (isCardinal || isMajor) 200 else 80
                tickPaint.strokeWidth = if (isCardinal || isMajor) 3f else 1.5f
                val tickLen = if (isCardinal || isMajor) 24f else 12f
                
                val x1 = centerX + radius * cosRad
                val y1 = centerY + radius * sinRad
                val x2 = centerX + (radius - tickLen) * cosRad
                val y2 = centerY + (radius - tickLen) * sinRad
                canvas.drawLine(x1, y1, x2, y2, tickPaint)
                
                if (isCardinal || isMajor) {
                    paint.style = Paint.Style.FILL
                    paint.textSize = if (isCardinal) 28f else 18f
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = if (isCardinal) osmandOrange else textColorSecondary
                    paint.alpha = 255
                    paint.typeface = if (isCardinal) Typeface.create("sans-serif-condensed", Typeface.BOLD) else Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    
                    val tx = centerX + (radius - 45f) * cosRad
                    val ty = centerY + (radius - 45f) * sinRad
                    val label = if (isCardinal) context.getString(cardinalPoints[normH]!!) else normH.toString()
                    
                    // Rotate text back so it's upright
                    canvas.save()
                    canvas.rotate(targetHeading.toFloat() - hDeg + 90f, tx, ty)
                    canvas.drawText(label, tx, ty + 8f, paint)
                    canvas.restore()
                }
            }
        }

        // 3. Error Marker (Actual Heading)
        actualHeading?.let { actual ->
            val error = calculateError(actual, targetHeading)
            val angleOnCircle = error - 90f
            val rad = Math.toRadians(angleOnCircle.toDouble())
            val cosRad = cos(rad).toFloat()
            val sinRad = sin(rad).toFloat()
            
            val x = centerX + radius * cosRad
            val y = centerY + radius * sinRad
            
            val color = if (isRecovering) ContextCompat.getColor(context, R.color.nautical_status_green) 
                        else ContextCompat.getColor(context, R.color.nautical_status_yellow)
            paint.color = color
            paint.style = Paint.Style.FILL
            paint.alpha = 240
            
            canvas.drawCircle(x, y, 8f, paint)
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            
            val x2 = centerX + (radius + 15f) * cosRad
            val y2 = centerY + (radius + 15f) * sinRad
            canvas.drawLine(x, y, x2, y2, paint)
        }

        // 3b. Wind Markers
        windAngleApparent?.let { awa ->
            val angleOnCircle = awa.toFloat() - 90f
            val rad = Math.toRadians(angleOnCircle.toDouble())
            val x = centerX + (radius + 10f) * cos(rad).toFloat()
            val y = centerY + (radius + 10f) * sin(rad).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.nautical_status_blue)
            paint.alpha = 200
            canvas.drawCircle(x, y, 6f, paint)
        }

        targetWindAngleApparent?.let { tawa ->
            val angleOnCircle = tawa.toFloat() - 90f
            val rad = Math.toRadians(angleOnCircle.toDouble())
            val x = centerX + (radius + 10f) * cos(rad).toFloat()
            val y = centerY + (radius + 10f) * sin(rad).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = ContextCompat.getColor(context, R.color.nautical_status_blue)
            paint.alpha = 255
            canvas.drawCircle(x, y, 8f, paint)
        }

        canvas.restore()

        // 4. Fixed Lubber Line (Always at top)
        paint.color = osmandOrange
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        pointerPath.reset()
        pointerPath.moveTo(centerX, centerY - radius + 10f)
        pointerPath.lineTo(centerX - 10f, centerY - radius - 15f)
        pointerPath.lineTo(centerX + 10f, centerY - radius - 15f)
        pointerPath.close()
        canvas.drawPath(pointerPath, paint)

        // 5. Central Digital Readout
        textPaint.color = textColorPrimary
        textPaint.textSize = 64f
        val centralValue = if (currentMode == "WIND") targetWindAngleApparent ?: 0 else targetHeading
        canvas.drawText(context.getString(R.string.nautical_format_deg, centralValue.toString()), centerX, centerY + 10f, textPaint)
        
        paint.textSize = 14f
        paint.color = textColorSecondary
        paint.alpha = 150
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val label = if (currentMode == "WIND") context.getString(R.string.nautical_awa) else context.getString(R.string.nautical_set_heading_label)
        canvas.drawText(label, centerX, centerY + 36f, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingArcView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_pilot_title) + ": " + context.getString(R.string.nautical_format_deg, targetHeading.toString())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x - width / 2f
        val y = event.y - height / 2f
        val dist = sqrt(x.pow(2) + y.pow(2))
        val radius = min(width, height) / 2f * 0.85f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (dist > radius * 0.5f && dist < radius * 1.2f) {
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val angleRad = atan2(y.toDouble(), x.toDouble())
                    var angleDeg = Math.toDegrees(angleRad).toInt() + 90
                    angleDeg = (angleDeg + 360) % 360
                    
                    if (currentMode == "WIND") {
                        // In WIND mode, we calculate relative angle
                        targetWindAngleApparent = (angleDeg + 360) % 360
                    } else {
                        targetHeading = angleDeg
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    if (currentMode == "WIND") {
                        targetWindAngleApparent?.let { onWindAngleChanged?.invoke(it) }
                    } else {
                        onHeadingChanged?.invoke(targetHeading)
                    }
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    return true
                }
                if (dist < (min(width, height) / 4f)) { // Tapped in center 25% area
                    onCenterClicked?.invoke()
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    performClick()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
