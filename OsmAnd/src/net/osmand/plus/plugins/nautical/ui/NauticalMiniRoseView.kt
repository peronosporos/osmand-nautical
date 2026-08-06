package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import androidx.core.graphics.withRotation

class NauticalMiniRoseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPath = Path()
    private var angleRad: Double? = null
    private var color: Int = Color.BLUE
    private var isRelative: Boolean = true // Relative to bow (like AWA)

    fun setAngle(rad: Double?, color: Int, relative: Boolean = true) {
        this.angleRad = rad
        this.color = color
        this.isRelative = relative
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val angle = angleRad ?: return
        
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f * 0.8f

        // Draw background circle
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.GRAY
        paint.alpha = 80
        canvas.drawCircle(cx, cy, radius, paint)

        if (isRelative) {
            // Draw "Bow" marker
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            canvas.drawCircle(cx, cy - radius, 6f, paint)
        }

        // Draw arrow
        canvas.withRotation(Math.toDegrees(angle).toFloat(), cx, cy) {
            arrowPath.reset()
            arrowPath.moveTo(cx, cy - radius)
            arrowPath.lineTo(cx - 8f, cy - radius + 20f)
            arrowPath.lineTo(cx + 8f, cy - radius + 20f)
            arrowPath.close()

            paint.color = color
            paint.style = Paint.Style.FILL
            drawPath(arrowPath, paint)

        }
    }
}
