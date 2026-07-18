package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class RudderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var rudderAngle: Double = 0.0 // Radians
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerPath = Path()
    private var isNightMode = false

    init {
        paint.strokeWidth = 3f
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setRudderAngle(angle: Double) {
        this.rudderAngle = angle
        invalidate()
    }

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h * 0.4f
        val centerX = w / 2f
        val padding = 40f
        val scaleWidth = w - (padding * 2)

        textPaint.color = if (isNightMode) Color.LTGRAY else Color.DKGRAY

        // Draw horizontal scale track
        paint.color = if (isNightMode) Color.DKGRAY else Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        // Draw scale ticks
        for (i in -30..30 step 10) {
            val ratio = (i + 30) / 60f
            val x = padding + (ratio * scaleWidth)
            val tickH = if (i % 30 == 0) 20f else 10f
            canvas.drawLine(x, centerY - tickH, x, centerY + tickH, paint)
            
            if (i % 30 == 0) {
                val label = when {
                    i < 0 -> "P"
                    i > 0 -> "S"
                    else -> "0"
                }
                canvas.drawText(label, x, centerY + 45f, textPaint)
            }
        }

        // Calculate pointer position
        val maxVisualAngle = Math.toRadians(30.0)
        val ratio = (rudderAngle.coerceIn(-maxVisualAngle, maxVisualAngle) / maxVisualAngle).toFloat()
        val pointerX = centerX + (ratio * (scaleWidth / 2f))

        // Draw pointer (Modern triangle)
        paint.color = Color.parseColor("#E71D36") // Professional Red
        paint.style = Paint.Style.FILL
        pointerPath.reset()
        pointerPath.moveTo(pointerX, centerY - 15f)
        pointerPath.lineTo(pointerX - 10f, centerY + 5f)
        pointerPath.lineTo(pointerX + 10f, centerY + 5f)
        pointerPath.close()
        canvas.drawPath(pointerPath, paint)
        
        // Draw angle digital readout
        val deg = Math.toDegrees(rudderAngle).toInt()
        val label = if (deg == 0) "MID" else "${kotlin.math.abs(deg)}° ${if (deg < 0) "P" else "S"}"
        textPaint.textSize = 32f
        textPaint.color = if (isNightMode) Color.WHITE else Color.BLACK
        canvas.drawText(label, pointerX, centerY - 25f, textPaint)
    }
}
