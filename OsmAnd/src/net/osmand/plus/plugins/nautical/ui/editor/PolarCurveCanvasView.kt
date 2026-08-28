package net.osmand.plus.plugins.nautical.ui.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import kotlin.math.*

class PolarCurveCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var rawPoints: List<Pair<Double, Double>> = emptyList()
        set(value) {
            field = value
            updateScale()
            invalidate()
        }

    var smoothedPoints: List<Pair<Double, Double>> = emptyList()
        set(value) {
            field = value
            updateScale()
            invalidate()
        }

    var recordedPoints: List<Triple<Double, Double, Double>> = emptyList()
        set(value) {
            field = value
            updateScale()
            invalidate()
        }

    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateNightVisionColors(value)
                invalidate()
            }
        }

    var onPointDragged: ((Int, Double, Double) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var draggedPointIndex: Int = -1
    private var maxSpeedScale = 10.0

    // Cached semantic colors
    private var colorGrid = 0x40888888
    private var colorLabel = 0xBB888888.toInt()
    private var colorRawPoint = 0x5029B6F6
    private var colorCurve = ContextCompat.getColor(context, R.color.active_color_primary_light)
    private var colorControlPoint = ContextCompat.getColor(context, R.color.nautical_status_green)
    private var colorPerfRed = 0xFFD32F2F.toInt() // <80% target
    private var colorPerfAmber = 0xFFFFB300.toInt() // 80-95% target
    private var colorPerfGreen = 0xFF43A047.toInt() // >95% target

    init {
        isClickable = true
        textPaint.textSize = 10f * resources.displayMetrics.density
        textPaint.color = colorLabel
    }

    private fun updateNightVisionColors(enabled: Boolean) {
        if (enabled) {
            colorGrid = 0x40FF1744.toInt()
            colorLabel = 0xBBFF8A80.toInt()
            colorRawPoint = 0x50FF5252.toInt()
            colorCurve = 0xFFFF1744.toInt()
            colorControlPoint = 0xFFFF5252.toInt()
            colorPerfRed = 0xFF8B0000.toInt()
            colorPerfAmber = 0xFFD50000.toInt()
            colorPerfGreen = 0xFFFF1744.toInt()
            textPaint.color = colorLabel
        } else {
            colorGrid = 0x40888888
            colorLabel = 0xBB888888.toInt()
            colorRawPoint = 0x5029B6F6
            colorCurve = ContextCompat.getColor(context, R.color.active_color_primary_light)
            colorControlPoint = ContextCompat.getColor(context, R.color.nautical_status_green)
            colorPerfRed = 0xFFD32F2F.toInt()
            colorPerfAmber = 0xFFFFB300.toInt()
            colorPerfGreen = 0xFF43A047.toInt()
            textPaint.color = colorLabel
        }
    }

    private fun updateScale() {
        val maxRawSpeed = (rawPoints + smoothedPoints).maxByOrNull { it.second }?.second ?: 0.0
        val maxRecSpeed = recordedPoints.maxOfOrNull { max(it.second, it.third) } ?: 0.0
        val maxPointSpeed = max(maxRawSpeed, maxRecSpeed)
        maxSpeedScale = when {
            maxPointSpeed > 20.0 -> 30.0
            maxPointSpeed > 15.0 -> 25.0
            maxPointSpeed > 10.0 -> 20.0
            maxPointSpeed > 5.0 -> 15.0
            else -> 10.0
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density

        // Bow-Up Vertical Semi-Circle (0° top to 180° bottom on right side)
        val originX = 40f * density
        val originY = h / 2f
        val maxRadius = min(w - 70f * density, h * 0.46f)

        if (maxRadius <= 0f) return

        // Draw radial concentric speed arcs
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = colorGrid

        val step = if (maxSpeedScale > 20) 5 else 2
        for (speed in step..maxSpeedScale.toInt() step step) {
            val r = (speed / maxSpeedScale) * maxRadius
            canvas.drawArc(
                (originX - r).toFloat(), (originY - r).toFloat(),
                (originX + r).toFloat(), (originY + r).toFloat(),
                -90f, 180f, false, paint
            )
            // Speed labels
            canvas.drawText("${speed}kn", (originX + r).toFloat() + 2f * density, originY + 12f * density, textPaint)
        }

        // Draw TWA angle radial lines (0°, 30°, 60°, 90°, 120°, 150°, 180°)
        paint.style = Paint.Style.STROKE
        paint.color = colorGrid
        for (angle in 0..180 step 30) {
            val rad = Math.toRadians(angle.toDouble())
            val x = originX + maxRadius * sin(rad).toFloat()
            val y = originY - maxRadius * cos(rad).toFloat()
            canvas.drawLine(originX, originY, x, y, paint)

            // Angle text
            val labelX = x + 4f * density
            val labelY = y + 4f * density
            canvas.drawText("$angle°", labelX, labelY, textPaint)
        }

        // Draw raw scatter points (Zero Allocations)
        paint.style = Paint.Style.FILL
        paint.color = colorRawPoint
        val rawRadius = 5f * density
        for (i in rawPoints.indices) {
            val pt = rawPoints[i]
            val rad = Math.toRadians(pt.first)
            val r = (pt.second / maxSpeedScale) * maxRadius
            val x = originX + r.toFloat() * sin(rad).toFloat()
            val y = originY - r.toFloat() * cos(rad).toFloat()
            canvas.drawCircle(x, y, rawRadius, paint)
        }

        // Draw recorded live performance scatter points (Zero Allocations)
        val recRadius = 4.5f * density
        for (i in recordedPoints.indices) {
            val pt = recordedPoints[i]
            val twa = pt.first
            val stw = pt.second
            val targetStw = pt.third
            val ratio = if (targetStw > 0.0) stw / targetStw else 1.0

            paint.color = when {
                ratio < 0.80 -> colorPerfRed
                ratio <= 0.95 -> colorPerfAmber
                else -> colorPerfGreen
            }

            val rad = Math.toRadians(twa)
            val r = (stw / maxSpeedScale) * maxRadius
            val x = originX + r.toFloat() * sin(rad).toFloat()
            val y = originY - r.toFloat() * cos(rad).toFloat()
            canvas.drawCircle(x, y, recRadius, paint)
        }

        // Draw smoothed curve path and draggable control points (Zero Allocations)
        if (smoothedPoints.isNotEmpty()) {
            path.reset()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = colorCurve

            for (i in smoothedPoints.indices) {
                val pt = smoothedPoints[i]
                val rad = Math.toRadians(pt.first)
                val r = (pt.second / maxSpeedScale) * maxRadius
                val x = originX + r.toFloat() * sin(rad).toFloat()
                val y = originY - r.toFloat() * cos(rad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)

            // Draw draggable control points
            paint.style = Paint.Style.FILL
            paint.color = colorControlPoint
            val ctrlRadius = 7f * density
            for (i in smoothedPoints.indices) {
                val pt = smoothedPoints[i]
                val rad = Math.toRadians(pt.first)
                val r = (pt.second / maxSpeedScale) * maxRadius
                val x = originX + r.toFloat() * sin(rad).toFloat()
                val y = originY - r.toFloat() * cos(rad).toFloat()
                canvas.drawCircle(x, y, ctrlRadius, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val originX = 40f * density
        val originY = h / 2f
        val maxRadius = min(w - 70f * density, h * 0.46f)

        if (maxRadius <= 0f) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in smoothedPoints.indices) {
                    val pt = smoothedPoints[i]
                    val rad = Math.toRadians(pt.first)
                    val r = (pt.second / maxSpeedScale) * maxRadius
                    val px = originX + r.toFloat() * sin(rad).toFloat()
                    val py = originY - r.toFloat() * cos(rad).toFloat()

                    val dist = hypot(event.x - px, event.y - py)
                    if (dist < 32f * density) {
                        draggedPointIndex = i
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPointIndex != -1) {
                    val dx = event.x - originX
                    val dy = originY - event.y // Invert Y
                    val distance = sqrt(dx * dx + dy * dy)
                    val speed = (distance / maxRadius) * maxSpeedScale

                    val angleRad = atan2(dx.toDouble(), dy.toDouble())
                    var angleDeg = Math.toDegrees(angleRad)
                    if (angleDeg < 0.0) angleDeg = 0.0
                    if (angleDeg > 180.0) angleDeg = 180.0

                    onPointDragged?.invoke(draggedPointIndex, angleDeg, speed.coerceIn(0.0, maxSpeedScale * 1.2))
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedPointIndex != -1) performClick()
                draggedPointIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
