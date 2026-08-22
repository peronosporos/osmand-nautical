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

import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller

class HeadingArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onCenterClicked: (() -> Unit)? = null
    var onHeadingChanged: ((Int) -> Unit)? = null
    var onHeadingPreview: ((Int?) -> Unit)? = null
    var onHeadingCommitted: ((Int) -> Unit)? = null
    var onWindAngleChanged: ((Int) -> Unit)? = null
    
    private var animatedTargetHeading: Float = 0f
    private var targetAnimator: ValueAnimator? = null

    var targetHeading: Int = 0
        set(value) {
            if (isDragging || !scroller.isFinished) return
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
            duration = 200
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

    // DP cache
    private var dp1 = 0f
    private var dp2 = 0f
    private var dp3 = 0f
    private var dp5 = 0f
    private var dp6 = 0f
    private var dp8 = 0f
    private var dp10 = 0f
    private var dp12 = 0f
    private var dp14 = 0f
    private var dp15 = 0f
    private var dp18 = 0f
    private var dp24 = 0f
    private var dp28 = 0f
    private var dp32 = 0f
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
        dp5 = 5f * density
        dp6 = 6f * density
        dp8 = 8f * density
        dp10 = 10f * density
        dp12 = 12f * density
        dp14 = 14f * density
        dp15 = 15f * density
        dp18 = 18f * density
        dp24 = 24f * density
        dp28 = 28f * density
        dp32 = 32f * density
        dp45 = 45f * density
        dp64 = 64f * density
        
        for (i in cardinalRes.indices) {
            cardinalLabels[i] = context.getString(cardinalRes[i])
        }
        offlineLabel = context.getString(R.string.nautical_offline)
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
        val baseRadius = min(w, h) / 2f
        val radius = baseRadius * 0.85f
        if (radius <= 0 || radius.isNaN()) return
        
        // Dynamic Font Scaling (Phase 1)
        val cardinalSize = (radius / 3.8f).coerceIn(dp12, dp24)
        val majorSize = (radius / 5.5f).coerceIn(dp10, dp18)
        val centerValueSize = (radius / 1.7f).coerceIn(dp28, dp45)
        val labelSize = (radius / 7.5f).coerceIn(dp10, dp14)

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
                    val tickLen = if (isCardinal || isMajor) radius * 0.15f else radius * 0.08f

                    val x1 = centerX + radius * cosRad
                    val y1 = centerY + radius * sinRad
                    val x2 = centerX + (radius - tickLen) * cosRad
                    val y2 = centerY + (radius - tickLen) * sinRad
                    drawLine(x1, y1, x2, y2, tickPaint)

                    if (isCardinal) {
                        paint.style = Paint.Style.FILL
                        paint.textSize = cardinalSize
                        paint.textAlign = Paint.Align.CENTER
                        paint.color = accentColor
                        paint.alpha = 255
                        paint.typeface = cardinalTypeface

                        val textOffset = radius * 0.28f
                        val tx = centerX + (radius - textOffset) * cosRad
                        val ty = centerY + (radius - textOffset) * sinRad
                        
                        withRotation(animatedTargetHeading - hDeg + 90f, tx, ty) {
                            drawText(cardinalLabels[cardinalIdx], tx, ty + (paint.textSize / 3f), paint)
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
            textPaint.textSize = cardinalSize
            canvas.drawText(offlineLabel, centerX, centerY + dp6, textPaint)
        } else {
            textPaint.textSize = centerValueSize
            textPaint.color = textColorPrimary
            if (currentMode == "WIND") {
                val tawa = targetWindAngleApparent ?: 0
                val side = if (tawa < 0) "P" else if (tawa > 0) "S" else ""
                val absVal = abs(tawa)
                val displayText = if (side.isNotEmpty()) "$side $absVal°" else "$absVal°"
                canvas.drawText(displayText, centerX, centerY + (centerValueSize * 0.35f), textPaint)
            } else {
                val centralValue = targetHeading
                NauticalFormatter.drawDeg(canvas, centralValue.toFloat(), centerX, centerY + (centerValueSize * 0.35f), textPaint, degreeBuffer)
            }
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = HeadingArcView::class.java.name
        info.contentDescription = context.getString(R.string.nautical_pilot_title) + ": " + targetHeading.toString() + "°"
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var isDragging = false
    private var accumulatedDelta = 0f
    private var velocityTracker: VelocityTracker? = null
    private val scroller by lazy { OverScroller(context) }
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val curr = (scroller.currX % 360 + 360) % 360
            if (currentMode == "WIND") {
                targetWindAngleApparent = curr
                onWindAngleChanged?.invoke(curr)
            } else {
                targetHeading = curr
                onHeadingChanged?.invoke(curr)
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        val relX = event.x - width / 2f
        val relY = event.y - height / 2f
        val dist = sqrt(relX.pow(2) + relY.pow(2))

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                accumulatedDelta = 0f
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - lastX
                lastX = event.x
                val totalDist = sqrt((event.x - downX).pow(2) + (event.y - downY).pow(2))

                if (!isDragging && totalDist > touchSlop) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (isDragging) {
                    val pixelsPerDegree = width / 90f
                    accumulatedDelta -= deltaX / pixelsPerDegree
                    val change = accumulatedDelta.toInt()
                    if (change != 0) {
                        accumulatedDelta -= change
                        if (currentMode == "WIND") {
                            val cur = targetWindAngleApparent ?: 0
                            val newAwa = (cur + change).coerceIn(-180, 180)
                            targetWindAngleApparent = newAwa
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onWindAngleChanged?.invoke(newAwa)
                        } else {
                            val oldH = targetHeading
                            val newH = (targetHeading + change + 360) % 360
                            targetHeading = newH
                            animatedTargetHeading = newH.toFloat()
                            if (newH != oldH) {
                                if (newH % 10 == 0) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                else performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onHeadingChanged?.invoke(newH)
                                onHeadingPreview?.invoke(newH)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging) {
                    velocityTracker?.let { vt ->
                        vt.computeCurrentVelocity(1000)
                        val velocityX = vt.xVelocity
                        if (abs(velocityX) > 500) {
                            val pixelsPerDegree = width / 90f
                            val startVal = if (currentMode == "WIND") (targetWindAngleApparent ?: 0) else targetHeading
                            scroller.fling(startVal, 0, -(velocityX / pixelsPerDegree).toInt(), 0, -10000, 10000, 0, 0)
                            postInvalidateOnAnimation()
                        }
                    }
                    onHeadingPreview?.invoke(null)
                    if (currentMode == "WIND") {
                        targetWindAngleApparent?.let { onWindAngleChanged?.invoke(it) }
                    } else {
                        onHeadingCommitted?.invoke(targetHeading)
                    }
                } else if (dist < (min(width, height) / 3f)) {
                    onCenterClicked?.invoke()
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    performClick()
                }
                isDragging = false
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
