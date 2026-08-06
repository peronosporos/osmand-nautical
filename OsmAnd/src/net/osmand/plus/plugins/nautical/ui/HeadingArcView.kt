package net.osmand.plus.plugins.nautical.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withRotation
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.StateChangedListener
import net.osmand.plus.plugins.nautical.utils.NauticalFormatter
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
    
    private var animatedTargetHeading: Float = 0f
    private var targetAnimator: ValueAnimator? = null

    var targetHeading: Int = 0
        set(value) {
            val v = (value + 360) % 360
            if (field != v) {
                field = v
                animateTarget(v.toFloat())
            }
        }

    private fun animateTarget(to: Float) {
        targetAnimator?.cancel()
        
        var start = animatedTargetHeading
        var end = to
        
        // Find shortest path
        if (abs(end - start) > 180) {
            if (end > start) start += 360 else end += 360
        }

        targetAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedTargetHeading = ((it.animatedValue as Float) + 360) % 360
                invalidate()
            }
            start()
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
                    isRecovering = (newErr < (oldErr - 0.5f)) || (newErr < 2.0f)
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
    private var isAmbientMode: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerPath = Path()
    
    private val cardinalTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val normalTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private var isDragging = false

    private val cardinalLabels = Array(8) { "" }
    private val cardinalIndices = intArrayOf(0, 45, 90, 135, 180, 225, 270, 315)
    private val cardinalRes = intArrayOf(
        R.string.nautical_cardinal_n,
        R.string.nautical_cardinal_ne,
        R.string.nautical_cardinal_e,
        R.string.nautical_cardinal_se,
        R.string.nautical_cardinal_s,
        R.string.nautical_cardinal_sw,
        R.string.nautical_cardinal_w,
        R.string.nautical_cardinal_nw,
    )
    
    private val degreeBuffer = CharArray(16)
    private var offlineLabel: String
    private var awaLabel: String
    private var setHeadingLabel: String

    // DP cache
    private var dp1 = 0f
    private var dp2 = 0f
    private var dp3 = 0f
    private var dp6 = 0f
    private var dp8 = 0f
    private var dp10 = 0f
    private var dp12 = 0f
    private var dp14 = 0f
    private var dp15 = 0f
    private var dp18 = 0f
    private var dp24 = 0f
    private var dp28 = 0f
    private var dp45 = 0f
    private var dp64 = 0f

    init {
        isClickable = true
        isFocusable = true
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = cardinalTypeface

        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeWidth = 2f
        
        val density = resources.displayMetrics.density
        dp1 = 1f * density
        dp2 = 2f * density
        dp3 = 3f * density
        dp6 = 6f * density
        dp8 = 8f * density
        dp10 = 10f * density
        dp12 = 12f * density
        dp14 = 14f * density
        dp15 = 15f * density
        dp18 = 18f * density
        dp24 = 24f * density
        dp28 = 28f * density
        dp45 = 45f * density
        dp64 = 64f * density
        
        for (i in cardinalRes.indices) {
            cardinalLabels[i] = context.getString(cardinalRes[i])
        }
        offlineLabel = context.getString(R.string.nautical_offline)
        awaLabel = context.getString(R.string.nautical_awa)
        setHeadingLabel = context.getString(R.string.nautical_set_heading_label)
    }

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    /**
     * Toggles ambient mode for watch displays.
     * Preserves battery by reducing draw frequency and color depth.
     */
    @Suppress("unused")
    fun setAmbientMode(ambient: Boolean) {
        this.isAmbientMode = ambient
        invalidate()
    }

    private val nightVisionListener = StateChangedListener<Boolean> {
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (context.applicationContext as? OsmandApplication)?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.addListener(nightVisionListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (context.applicationContext as? OsmandApplication)?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.removeListener(nightVisionListener)
    }

    fun calculateError(actual: Int, target: Int): Float {
        var diff = (actual - target).toFloat()
        if (diff.isNaN()) return 0f
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val centerX = w / 2f
        val centerY = h / 2f
        val radius = min(w, h) / 2f * 0.85f
        if (radius <= 0 || radius.isNaN()) return
        
        val textColorPrimary = NauticalColorResolver.getColor(context, NauticalSemanticColor.PRIMARY)
        val textColorSecondary = NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY)
        val accentColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.ACCENT)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp2
        paint.color = textColorSecondary
        paint.alpha = 40
        canvas.drawCircle(centerX, centerY, radius, paint)

        canvas.withRotation(-animatedTargetHeading, centerX, centerY) {
            for (hDeg in 0 until 360 step 5) {
                val normH = hDeg % 360
                val angleOnCircle = hDeg.toFloat() - 90f
                val rad = Math.toRadians(angleOnCircle.toDouble())

                val cosRad = cos(rad).toFloat()
                val sinRad = sin(rad).toFloat()

                var cardinalIdx = -1
                for (i in cardinalIndices.indices) {
                    if (cardinalIndices[i] == normH) {
                        cardinalIdx = i
                        break
                    }
                }
                val isCardinal = cardinalIdx != -1
                val isMajor = normH % 10 == 0
                val isMinor = normH % 5 == 0

                if (isCardinal || isMajor || isMinor) {
                    tickPaint.color = textColorPrimary
                    tickPaint.alpha = if (isCardinal || isMajor) 200 else 80
                    tickPaint.strokeWidth = if (isCardinal || isMajor) dp3 else dp1 * 1.5f
                    val tickLen = if (isCardinal || isMajor) dp24 else dp12

                    val x1 = centerX + radius * cosRad
                    val y1 = centerY + radius * sinRad
                    val x2 = centerX + (radius - tickLen) * cosRad
                    val y2 = centerY + (radius - tickLen) * sinRad
                    drawLine(x1, y1, x2, y2, tickPaint)

                    if (isCardinal || isMajor) {
                        paint.style = Paint.Style.FILL
                        paint.textSize = if (isCardinal) dp28 else dp18
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = if (isCardinal) accentColor else textColorSecondary
                        paint.alpha = 255
                        paint.typeface = if (isCardinal) cardinalTypeface else normalTypeface

                        val tx = centerX + (radius - dp45) * cosRad
                        val ty = centerY + (radius - dp45) * sinRad
                        
                        withRotation(animatedTargetHeading - hDeg + 90f, tx, ty) {
                            if (isCardinal) {
                                drawText(cardinalLabels[cardinalIdx], tx, ty + dp8, paint)
                            } else {
                                val count = NauticalFormatter.formatInt(normH, degreeBuffer)
                                drawText(degreeBuffer, 0, count, tx, ty + dp8, paint)
                            }
                        }
                    }
                }
            }

            actualHeading?.let { actual ->
                var diff = (actual - targetHeading).toFloat()
                while (diff > 180) diff -= 360
                while (diff < -180) diff += 360

                val angleOnCircle = diff - 90f
                val rad = Math.toRadians(angleOnCircle.toDouble())
                val cosRad = cos(rad).toFloat()
                val sinRad = sin(rad).toFloat()

                val x = centerX + radius * cosRad
                val y = centerY + radius * sinRad

                val color = if (isAmbientMode) {
                    Color.WHITE
                } else if (isRecovering) {
                    NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
                } else {
                    NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_WARNING)
                }
                paint.color = color
                paint.style = Paint.Style.FILL
                paint.alpha = if (isAmbientMode) 255 else 240

                drawCircle(x, y, dp8, paint)
                paint.strokeWidth = dp3
                paint.style = Paint.Style.STROKE

                val x2 = centerX + (radius + dp15) * cosRad
                val y2 = centerY + (radius + dp15) * sinRad
                drawLine(x, y, x2, y2, paint)
            }

            windAngleApparent?.let { awa ->
                val angleOnCircle = awa.toFloat() - 90f
                val rad = Math.toRadians(angleOnCircle.toDouble())
                val x = centerX + (radius + dp10) * cos(rad).toFloat()
                val y = centerY + (radius + dp10) * sin(rad).toFloat()
                paint.style = Paint.Style.FILL
                paint.color = if (isAmbientMode) Color.WHITE else textColorPrimary
                paint.alpha = if (isAmbientMode) 150 else 200
                drawCircle(x, y, dp6, paint)
            }

            targetWindAngleApparent?.let { tawa ->
                val angleOnCircle = tawa.toFloat() - 90f
                val rad = Math.toRadians(angleOnCircle.toDouble())
                val x = centerX + (radius + dp10) * cos(rad).toFloat()
                val y = centerY + (radius + dp10) * sin(rad).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp2
                paint.color = textColorPrimary
                paint.alpha = 255
                drawCircle(x, y, dp8, paint)
            }
        }

        paint.color = accentColor
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        pointerPath.reset()
        pointerPath.moveTo(centerX, centerY - radius + dp10)
        pointerPath.lineTo(centerX - dp10, centerY - radius - dp15)
        pointerPath.lineTo(centerX + dp10, centerY - radius - dp15)
        pointerPath.close()
        canvas.drawPath(pointerPath, paint)

        val isOffline = actualHeading == null
        if (isOffline) {
            textPaint.color = NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_ERROR)
            textPaint.textSize = dp28
            canvas.drawText(offlineLabel, centerX, centerY + dp10, textPaint)
        } else {
            textPaint.color = textColorPrimary
            textPaint.textSize = dp64
            val centralValue = if (currentMode == "WIND") targetWindAngleApparent ?: 0 else targetHeading
            NauticalFormatter.drawDeg(canvas, centralValue.toFloat(), centerX, centerY + dp10, textPaint, degreeBuffer)
        }
        
        paint.textSize = dp14
        paint.color = textColorSecondary
        paint.alpha = 150
        paint.typeface = mediumTypeface
        val label = if (currentMode == "WIND") awaLabel else setHeadingLabel
        canvas.drawText(label, centerX, centerY + dp45 * 0.8f, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingArcView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_pilot_title) + ": " + targetHeading.toString() + "°"
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
                if (dist < (min(width, height) / 4f)) {
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
