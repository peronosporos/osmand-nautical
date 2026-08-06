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
    private var midLabel: String
    private var portLabel: String
    private var stbdLabel: String
    private var offlineLabel: String
    private var degLabel: String
    private var pShortLabel: String
    private var sShortLabel: String

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
        
        midLabel = context.getString(R.string.nautical_rudder_mid)
        portLabel = context.getString(R.string.nautical_rudder_port)
        stbdLabel = context.getString(R.string.nautical_rudder_stbd)
        offlineLabel = context.getString(R.string.nautical_offline)
        degLabel = context.getString(R.string.nautical_unit_deg)
        pShortLabel = context.getString(R.string.nautical_rudder_p_short)
        sShortLabel = context.getString(R.string.nautical_rudder_s_short)

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
        val isVirtual = state?.rudderAngle == null && state?.simulatedRudderAngle != null
        val effectiveAngle = state?.rudderAngle ?: state?.simulatedRudderAngle ?: Double.NaN
        setRudderAngle(effectiveAngle)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        
        if (isVirtual) {
            textPaint.textSize = dp14
            textPaint.color = colorSecondary
            textPaint.alpha = 180
            canvas.drawText(context.getString(R.string.nautical_virtual_rudder_label), w - dp45, h - dp6, textPaint)
        }
        
        val centerY = h * 0.65f 
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

        paint.color = colorPrimary
        paint.strokeWidth = dp1
        paint.alpha = 120
        for (i in (-30..30) step 15) {
            val ratio = (i + 30) / 60f
            val x = padding + (ratio * scaleWidth)
            canvas.drawLine(x, centerY - dp6, x, centerY + dp6, paint)
        }

        val maxVisualAngle = Math.toRadians(35.0)
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
            canvas.drawLine(pointerX, centerY - dp14, pointerX, centerY + dp14, paint)
        }
        
        val deg = Math.toDegrees(animatedAngle).toInt()
        
        textPaint.textSize = dp24
        textPaint.color = if (isOffline) colorSecondary else colorPrimary
        if (isOffline) textPaint.alpha = 120
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        
        if (isOffline) {
            canvas.drawText(offlineLabel, pointerX, centerY - dp22, textPaint)
        } else if (deg == 0) {
            canvas.drawText(midLabel, pointerX, centerY - dp22, textPaint)
        } else {
            val count = NauticalFormatter.formatInt(abs(deg), degreeBuffer)
            val sideLabel = if (deg < 0) portLabel else stbdLabel
            // We need to combine count, degLabel, and sideLabel. 
            // Since we want zero-allocation, we should draw them separately if needed, but here a simple combination might be okay if it's not too frequent.
            // Wait, the instruction said ZERO allocations.
            
            val xOffset = textPaint.measureText(sideLabel) / 2f + 5f
            canvas.drawText(sideLabel, pointerX + xOffset, centerY - dp22, textPaint)
            
            // Draw value and deg symbol
            degreeBuffer[count] = '°'
            canvas.drawText(degreeBuffer, 0, count + 1, pointerX - xOffset, centerY - dp22, textPaint)
        }
        
        textPaint.textSize = dp18
        textPaint.color = colorSecondary
        textPaint.alpha = 150
        canvas.drawText(pShortLabel, padding - dp20, centerY + dp6, textPaint)
        canvas.drawText(sShortLabel, w - padding + dp20, centerY + dp6, textPaint)
    }
}
