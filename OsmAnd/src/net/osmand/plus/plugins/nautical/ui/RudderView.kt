package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.plus.R

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

    private var colorPort = Color.parseColor("#E71D36")
    private var colorStarboard = Color.parseColor("#5BAF3F")

    init {
        colorPort = ContextCompat.getColor(context, R.color.text_color_negative)
        colorStarboard = ContextCompat.getColor(context, R.color.text_color_positive)
        paint.strokeWidth = 2f
        textPaint.textSize = 24f
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
        val centerY = h * 0.7f
        val centerX = w / 2f
        val padding = 20f
        val scaleWidth = w - (padding * 2)

        // Draw minimalist scale line
        paint.color = if (isNightMode) Color.DKGRAY else Color.LTGRAY
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        // Draw Port (Red) and Starboard (Green) indicators
        paint.strokeWidth = 4f
        paint.color = colorPort
        canvas.drawLine(padding, centerY, centerX, centerY, paint)
        paint.color = colorStarboard
        canvas.drawLine(centerX, centerY, w - padding, centerY, paint)

        // Draw ticks
        paint.color = if (isNightMode) Color.WHITE else Color.BLACK
        paint.strokeWidth = 2f
        for (i in -30..30 step 15) {
            val ratio = (i + 30) / 60f
            val x = padding + (ratio * scaleWidth)
            canvas.drawLine(x, centerY - 8f, x, centerY + 2f, paint)
        }

        // Calculate pointer position
        val maxVisualAngle = Math.toRadians(35.0)
        val ratio = (rudderAngle.coerceIn(-maxVisualAngle, maxVisualAngle) / maxVisualAngle).toFloat()
        val pointerX = centerX + (ratio * (scaleWidth / 2f))

        // Draw pointer (Triangle)
        paint.color = if (isNightMode) Color.WHITE else Color.BLACK
        paint.style = Paint.Style.FILL
        pointerPath.reset()
        pointerPath.moveTo(pointerX, centerY)
        pointerPath.lineTo(pointerX - 8f, centerY + 15f)
        pointerPath.lineTo(pointerX + 8f, centerY + 15f)
        pointerPath.close()
        canvas.drawPath(pointerPath, paint)
        
        // Draw angle digital readout
        val deg = Math.toDegrees(rudderAngle).toInt()
        val label = if (deg == 0) "MID" else "${kotlin.math.abs(deg)}° ${if (deg < 0) "P" else "S"}"
        textPaint.textSize = 28f
        textPaint.color = if (isNightMode) Color.WHITE else Color.BLACK
        canvas.drawText(label, pointerX, centerY - 15f, textPaint)
    }
}
