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
            invalidate()
        }

    var smoothedPoints: List<Pair<Double, Double>> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onPointDragged: ((Int, Double, Double) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var draggedPointIndex: Int = -1

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val originX = w / 2f
        val originY = h - 50f
        val maxRadius = min(w * 0.45f, h * 0.85f)

        // Draw half-radial coordinate grid (0 to 180 TWA, speed rings)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.LTGRAY

        for (speed in 2..10 step 2) {
            val r = (speed / 10f) * maxRadius
            canvas.drawArc(
                originX - r, originY - r,
                originX + r, originY + r,
                180f, 180f, false, paint
            )
        }

        // Draw TWA angle radials (0, 30, 60, 90, 120, 150, 180)
        for (angle in 0..180 step 30) {
            val rad = Math.toRadians(angle.toDouble())
            val x = originX + maxRadius * cos(rad + Math.PI).toFloat()
            val y = originY + maxRadius * sin(rad + Math.PI).toFloat()
            canvas.drawLine(originX, originY, x, y, paint)
        }

        // Draw faint raw scatter points
        paint.style = Paint.Style.FILL
        paint.color = "#80BBDEFB".toColorInt()
        for (pt in rawPoints) {
            val rad = Math.toRadians(pt.first)
            val r = (pt.second / 10.0) * maxRadius
            val x = originX + r.toFloat() * cos(rad + Math.PI).toFloat()
            val y = originY + r.toFloat() * sin(rad + Math.PI).toFloat()
            canvas.drawCircle(x, y, 10f, paint)
        }

        // Draw prominent smoothed curve path
        if (smoothedPoints.isNotEmpty()) {
            path.reset()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = ContextCompat.getColor(context, R.color.icon_color_osmand_light)

            for (i in smoothedPoints.indices) {
                val pt = smoothedPoints[i]
                val rad = Math.toRadians(pt.first)
                val r = (pt.second / 10.0) * maxRadius
                val x = originX + r.toFloat() * cos(rad + Math.PI).toFloat()
                val y = originY + r.toFloat() * sin(rad + Math.PI).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)

            // Draw draggable control points on smoothed curve
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.nautical_status_green)
            for (pt in smoothedPoints) {
                val rad = Math.toRadians(pt.first)
                val r = (pt.second / 10.0) * maxRadius
                val x = originX + r.toFloat() * cos(rad + Math.PI).toFloat()
                val y = originY + r.toFloat() * sin(rad + Math.PI).toFloat()
                canvas.drawCircle(x, y, 14f, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val originX = w / 2f
        val originY = h - 50f
        val maxRadius = min(w * 0.45f, h * 0.85f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find closest smoothed point to touch
                for (i in smoothedPoints.indices) {
                    val pt = smoothedPoints[i]
                    val rad = Math.toRadians(pt.first)
                    val r = (pt.second / 10.0) * maxRadius
                    val x = originX + r.toFloat() * cos(rad + Math.PI).toFloat()
                    val y = originY + r.toFloat() * sin(rad + Math.PI).toFloat()

                    val dist = hypot(event.x - x, event.y - y)
                    if (dist < 40f) {
                        draggedPointIndex = i
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedPointIndex != -1) {
                    val dx = event.x - originX
                    val dy = event.y - originY
                    val distance = sqrt(dx * dx + dy * dy)
                    val speed = (distance / maxRadius) * 10.0
                    val angleRad = atan2(dy.toDouble(), dx.toDouble()) - Math.PI
                    var angleDeg = Math.toDegrees(angleRad)
                    if (angleDeg < 0) angleDeg += 360.0
                    if (angleDeg > 180.0) angleDeg = 180.0

                    onPointDragged?.invoke(draggedPointIndex, angleDeg, speed.coerceIn(0.0, 15.0))
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedPointIndex != -1) {
                    performClick()
                }
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
