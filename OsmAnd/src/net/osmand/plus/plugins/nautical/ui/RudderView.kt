package net.osmand.plus.plugins.nautical.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import net.osmand.plus.R
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.StateChangedListener
import net.osmand.plus.plugins.nautical.utils.NauticalFormatter
import kotlin.math.abs

class RudderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var rudderAngle: Double = 0.0 // Radians
    private var animatedAngle: Double = 0.0
    private var animator: ValueAnimator? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isNightMode = false

    private var colorPort = Color.TRANSPARENT
    private var colorStarboard = Color.TRANSPARENT
    private var colorPrimary = Color.TRANSPARENT
    private var colorSecondary = Color.TRANSPARENT
    
    private val degreeBuffer = CharArray(16)
    private var offlineLabel: String
    private var midLabel: String = "MID"
    private var portLabel: String = "PORT"
    private var stbdLabel: String = "STBD"
    private var degLabel: String = "°"

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var lastAccessibilityAnnouncement = 0L
    private val announcementThrottleMs = 3000L

    // DP cache
    private var dp1 = 0f
    private var dp15 = 0f
    private var dp3 = 0f
    private var dp4 = 0f
    private var dp6 = 0f
    private var dp14 = 0f
    private var dp18 = 0f
    private var dp20 = 0f
    private var dp22 = 0f
    private var dp24 = 0f
    private var dp45 = 0f

    init {
        isClickable = true
        isFocusable = true
        paint.strokeWidth = 2f
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        
        val density = resources.displayMetrics.density
        dp1 = 1f * density
        dp15 = 1.5f * density
        dp3 = 3f * density
        dp4 = 4f * density
        dp6 = 6f * density
        dp14 = 14f * density
        dp18 = 18f * density
        dp20 = 20f * density
        dp22 = 22f * density
        dp24 = 24f * density
        dp45 = 45f * density
        
        offlineLabel = context.getString(R.string.nautical_offline)
        portLabel = context.getString(R.string.nautical_port_indicator)
        stbdLabel = context.getString(R.string.nautical_starboard_indicator)

        setupAccessibility()
    }

    private fun setupAccessibility() {
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val deg = Math.toDegrees(rudderAngle).toInt()
                    val label = if (deg == 0) midLabel 
                                else "${abs(deg)}° ${if (deg < 0) portLabel else stbdLabel}"
                    info.contentDescription = context.getString(R.string.nautical_rudder_angle) + ": " + label
                }
            },
        )
    }

    private fun updateColors() {
        colorPort = NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_ERROR)
        colorStarboard = NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
        colorPrimary = NauticalColorResolver.getColor(context, NauticalSemanticColor.PRIMARY)
        colorSecondary = NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY)
    }

    fun setRudderAngle(angle: Double) {
        if (angle.isNaN() || angle.isInfinite()) return
        if (abs(angle - this.rudderAngle) < 0.001) return
        this.rudderAngle = angle
        
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animatedAngle.toFloat(), angle.toFloat()).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedAngle = (it.animatedValue as Float).toDouble()
                invalidate()
            }
            start()
        }

        announceTelemetryIfNeeded()
    }

    private fun announceTelemetryIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastAccessibilityAnnouncement > announcementThrottleMs) {
            val deg = Math.toDegrees(rudderAngle).toInt()
            val label = if (deg == 0) midLabel 
                        else "${abs(deg)} $degLabel ${if (deg < 0) portLabel else stbdLabel}"
            announceForAccessibility(context.getString(R.string.nautical_rudder_angle) + ": " + label)
            lastAccessibilityAnnouncement = now
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setNightMode(night: Boolean) {
        if (this.isNightMode != night) {
            this.isNightMode = night
            invalidate()
        }
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = abs(event.x - initialTouchX)
                    val dy = abs(event.y - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                return isDragging
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // It was a tap, check if it was deliberate
                    val dx = abs(event.x - initialTouchX)
                    val dy = abs(event.y - initialTouchY)
                    if (dx < touchSlop && dy < touchSlop) {
                        performClick()
                    }
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = RudderView::class.java.name
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateColors()

        val state = NauticalPlugin.engine?.getCurrentState()
        val effectiveAngle = state?.rudderAngle ?: state?.simulatedRudderAngle ?: Double.NaN
        setRudderAngle(effectiveAngle)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        
        val centerY = h * 0.62f 
        val centerX = w / 2f
        
        if (centerY.isNaN() || centerX.isNaN()) return
        
        val padding = dp45
        val scaleWidth = w - (padding * 2)
        
        paint.strokeWidth = dp15
        paint.color = colorPrimary
        paint.alpha = 100
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        paint.strokeWidth = dp3
        paint.color = colorPort
        paint.alpha = 180
        canvas.drawLine(padding, centerY, centerX - dp6, centerY, paint)
        paint.color = colorStarboard
        canvas.drawLine(centerX + dp6, centerY, w - padding, centerY, paint)

        val settings = (context.applicationContext as? OsmandApplication)?.settings
        val limitDeg = settings?.NAUTICAL_RUDDER_LIMIT?.get() ?: 35f
        val limitDegInt = limitDeg.toInt()

        paint.color = colorPrimary
        paint.strokeWidth = dp1
        paint.alpha = 120
        val step = if (limitDegInt > 45) 20 else 15
        for (i in (-limitDegInt..limitDegInt) step step) {
            val r = (i + limitDeg) / (limitDeg * 2f)
            val x = padding + (r * scaleWidth)
            canvas.drawLine(x, centerY - dp6, x, centerY + dp6, paint)
        }

        val maxVisualAngle = Math.toRadians(limitDeg.toDouble())
        val isOffline = animatedAngle.isNaN()
        val ratio = if (isOffline) 0f else (animatedAngle.coerceIn(-maxVisualAngle, maxVisualAngle) / maxVisualAngle).toFloat()
        val pointerX = centerX + (ratio * (scaleWidth / 2f))

        if (isOffline) {
            paint.color = colorSecondary
            paint.alpha = 80
        } else {
            paint.color = colorPrimary
            paint.alpha = 255
        }
        paint.strokeWidth = dp4
        paint.strokeCap = Paint.Cap.ROUND
        if (!isOffline) {
            canvas.drawLine(pointerX, centerY - dp12, pointerX, centerY + dp12, paint)
        }
        
        val deg = Math.toDegrees(animatedAngle).toInt()
        val textY = centerY - dp14
        
        textPaint.textSize = dp14
        textPaint.color = if (isOffline) colorSecondary else colorPrimary
        if (isOffline) textPaint.alpha = 120
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        
        if (isOffline) {
            canvas.drawText(offlineLabel, centerX, textY, textPaint)
        } else {
            val absDeg = abs(deg)
            val side = if (deg < -0.5) "P" else if (deg > 0.5) "S" else ""
            val labelX = pointerX.coerceIn(padding + dp14, w - padding - dp14)
            val count = NauticalFormatter.formatInt(absDeg, degreeBuffer)
            degreeBuffer[count] = '°'
            if (side.isNotEmpty()) {
                degreeBuffer[count + 1] = ' '
                degreeBuffer[count + 2] = side[0]
                canvas.drawText(degreeBuffer, 0, count + 3, labelX, textY, textPaint)
            } else {
                canvas.drawText(degreeBuffer, 0, count + 1, labelX, textY, textPaint)
            }
        }
    }
}
