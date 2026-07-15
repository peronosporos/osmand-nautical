package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI

class RudderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var rudderAngle: Double = 0.0 // Radians
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.strokeWidth = 4f
        textPaint.color = Color.BLACK
        textPaint.textSize = 30f
        textPaint.textAlign = Paint.Align.CENTER
    }

    fun setRudderAngle(angle: Double) {
        this.rudderAngle = angle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        val centerX = w / 2f

        // Draw horizontal line
        paint.color = Color.GRAY
        paint.style = Paint.Style.STROKE
        canvas.drawLine(20f, centerY, w - 20f, centerY, paint)

        // Draw markers
        canvas.drawLine(centerX, centerY - 15f, centerX, centerY + 15f, paint) // Center
        canvas.drawLine(20f, centerY - 10f, 20f, centerY + 10f, paint) // Left max
        canvas.drawLine(w - 20f, centerY - 10f, w - 20f, centerY + 10f, paint) // Right max

        // Draw labels
        canvas.drawText("0", centerX, centerY + 45f, textPaint)
        canvas.drawText("P", 30f, centerY + 45f, textPaint)
        canvas.drawText("S", w - 30f, centerY + 45f, textPaint)

        // Calculate pointer position
        // Map +/- PI/4 (45 deg) to screen width
        val maxAngle = PI / 4.0
        val ratio = (rudderAngle.coerceIn(-maxAngle, maxAngle) / maxAngle).toFloat()
        val pointerX = centerX + (ratio * (centerX - 20f))

        // Draw pointer
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pointerX, centerY, 12f, paint)
        
        // Draw angle text
        val deg = Math.toDegrees(rudderAngle).toInt()
        val label = if (deg == 0) "MID" else "${kotlin.math.abs(deg)}° ${if (deg < 0) "P" else "S"}"
        canvas.drawText(label, pointerX, centerY - 25f, textPaint)
    }
}
