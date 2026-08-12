package net.osmand.plus.plugins.nautical.ui.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import kotlin.math.*
import androidx.core.graphics.toColorInt

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

    var onPointDragged: ((Int, Double, Double) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var draggedPointIndex: Int = -1
    private var maxSpeedScale = 10.0

    init {
        isClickable = true
    }

    private fun updateScale() {
        val maxPointSpeed = (rawPoints + smoothedPoints).maxByOrNull { it.second }?.second ?: 0.0
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

        // TASK-012: Bow-Up Vertical Orientation (Industry Standard)
        val originX = 50f * density // Anchor to the left to show 0..180 semi-circle
        val originY = h / 2f
        val maxRadius = min(w - 100f * density, h * 0.45f)

        // Draw radial coordinate grid
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = Color.LTGRAY

        val step = if (maxSpeedScale > 20) 5 else 2
        for (speed in step..maxSpeedScale.toInt() step step) {
            val r = (speed / maxSpeedScale) * maxRadius
            canvas.drawArc(
                (originX - r).toFloat(), (originY - r).toFloat(),
                (originX + r).toFloat(), (originY + r).toFloat(),
                -90f, 180f, false, paint
            )
            // Speed labels
            paint.style = Paint.Style.FILL
            paint.textSize = 10f * density
            canvas.drawText(speed.toString(), (originX + r).toFloat(), originY + 15f * density, paint)
            paint.style = Paint.Style.STROKE
        }

        // Draw TWA angle radials (0, 30, 60, 90, 120, 150, 180)
        for (angle in 0..180 step 30) {
            val rad = Math.toRadians(angle.toDouble())
            // Bow at 0 (Top)
            val x = originX + maxRadius * sin(rad).toFloat()
            val y = originY - maxRadius * cos(rad).toFloat()
            canvas.drawLine(originX, originY, x, y, paint)
            
            // Angle labels
            paint.style = Paint.Style.FILL
            canvas.drawText("$angle°", x + 5f * density, y, paint)
            paint.style = Paint.Style.STROKE
        }

        // Draw raw scatter points
        paint.style = Paint.Style.FILL
        paint.color = "#40BBDEFB".toColorInt() // More transparent
        for (pt in rawPoints) {
            val coords = getPointCoords(pt, originX, originY, maxRadius)
            canvas.drawCircle(coords.x, coords.y, 6f * density, paint)
        }

        // Draw prominent smoothed curve path
        if (smoothedPoints.isNotEmpty()) {
            path.reset()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = ContextCompat.getColor(context, R.color.icon_color_osmand_light)

            for (i in smoothedPoints.indices) {
                val coords = getPointCoords(smoothedPoints[i], originX, originY, maxRadius)
                if (i == 0) path.moveTo(coords.x, coords.y) else path.lineTo(coords.x, coords.y)
            }
            canvas.drawPath(path, paint)

            // Draw draggable control points
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.nautical_status_green)
            for (pt in smoothedPoints) {
                val coords = getPointCoords(pt, originX, originY, maxRadius)
                canvas.drawCircle(coords.x, coords.y, 8f * density, paint)
            }
        }
    }

    private fun getPointCoords(pt: Pair<Double, Double>, originX: Float, originY: Float, maxRadius: Float): PointF {
        val rad = Math.toRadians(pt.first)
        val r = (pt.second / maxSpeedScale) * maxRadius
        val x = originX + r.toFloat() * sin(rad).toFloat()
        val y = originY - r.toFloat() * cos(rad).toFloat()
        return PointF(x, y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val originX = 50f * density
        val originY = h / 2f
        val maxRadius = min(w - 100f * density, h * 0.45f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in smoothedPoints.indices) {
                    val coords = getPointCoords(smoothedPoints[i], originX, originY, maxRadius)
                    val dist = hypot(event.x - coords.x, event.y - coords.y)
                    if (dist < 30f * density) {
                        draggedPointIndex = i
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPointIndex != -1) {
                    val dx = event.x - originX
                    val dy = originY - event.y // Reverse Y
                    val distance = sqrt(dx * dx + dy * dy)
                    val speed = (distance / maxRadius) * maxSpeedScale
                    
                    val angleRad = atan2(dx.toDouble(), dy.toDouble())
                    var angleDeg = Math.toDegrees(angleRad)
                    if (angleDeg < 0) angleDeg = 0.0
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
