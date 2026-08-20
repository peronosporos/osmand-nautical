package net.osmand.plus.plugins.nautical.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt

class SlideToConfirmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var isDragging = false
    private var downX = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onConfirm: (() -> Unit)? = null
    var label: String = "SLIDE TO CONFIRM"
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#33FFFFFF".toColorInt()
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.icon_color_osmand_light)
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(
            context.resources.displayMetrics.density * 4f,
            0f,
            context.resources.displayMetrics.density * 2f,
            "#40000000".toColorInt(),
        )
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.resources.displayMetrics.density * 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferredHeight = (56 * context.resources.displayMetrics.density).toInt()
        val minRequiredHeight = (48 * context.resources.displayMetrics.density).toInt()

        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        var finalHeight = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> max(minRequiredHeight, min(hSize, preferredHeight))
            else -> preferredHeight
        }

        if (finalHeight < minRequiredHeight) {
            finalHeight = minRequiredHeight
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), finalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        val density = context.resources.displayMetrics.density
        val margin = 4 * density

        // Draw track
        canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)

        // Draw fill
        val thumbSize = h - (margin * 2)
        val maxTravel = w - thumbSize - (margin * 2)
        val currentX = margin + progress * maxTravel
        
        canvas.drawRoundRect(0f, 0f, currentX + thumbSize / 2, h, r, r, fillPaint)

        // Draw label text
        val centerY = h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, w / 2, centerY, textPaint)

        // Draw thumb
        canvas.drawRoundRect(currentX, margin, currentX + thumbSize, h - margin, r, r, thumbPaint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SlideToConfirmView::class.java.name
        info.contentDescription = label
        info.isClickable = true
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        
        val progressPercent = (progress * 100).toInt()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            info.stateDescription = "$progressPercent percent confirmed"
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
            progress = 1f
            invalidate()
            onConfirm?.invoke()
            reset()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < height * 1.5f) { 
                    parent?.requestDisallowInterceptTouchEvent(true)
                    downX = event.x
                    downY = event.y
                    isDragging = false // Don't start dragging yet
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isDragging) {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    val thumbSize = h - 8
                    val maxTravel = w - thumbSize - 8
                    
                    val moveX = event.x - 4f - thumbSize / 2
                    progress = min(1f, max(0f, moveX / maxTravel))
                    invalidate()
                    
                    if (progress >= 0.98f) {
                        isDragging = false
                        progress = 1f
                        invalidate()
                        onConfirm?.invoke()
                        reset()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging && progress < 0.98f) {
                    animateReset()
                }
                isDragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateReset() {
        val animator = android.animation.ValueAnimator.ofFloat(progress, 0f)
        animator.duration = 200
        animator.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun reset() {
        progress = 0f
        invalidate()
    }
}
