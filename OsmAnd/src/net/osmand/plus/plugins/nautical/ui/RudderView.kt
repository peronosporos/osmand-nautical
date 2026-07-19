package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import net.osmand.plus.R

class RudderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var rudderAngle: Double = 0.0 // Radians
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isNightMode = false

    private var colorPort = "#E71D36".toColorInt()
    private var colorStarboard = "#5BAF3F".toColorInt()

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
        //centerY shifted down to accommodate label on top
        val centerY = h * 0.65f 
        val centerX = w / 2f
        val padding = 45f
        val scaleWidth = w - (padding * 2)

        // Draw minimalist scale line
        paint.strokeWidth = 1.5f
        paint.color = ContextCompat.getColor(context, if (isNightMode) R.color.divider_color_dark else R.color.divider_color_light)
        canvas.drawLine(padding, centerY, w - padding, centerY, paint)

        // Draw Port and Starboard subtle color accents
        paint.strokeWidth = 3f
        paint.color = colorPort
        paint.alpha = 180
        canvas.drawLine(padding, centerY, centerX - 6f, centerY, paint)
        paint.color = colorStarboard
        canvas.drawLine(centerX + 6f, centerY, w - padding, centerY, paint)

        // Draw minimalist ticks
        paint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
        paint.strokeWidth = 1f
        paint.alpha = 120
        for (i in -30..30 step 15) {
            val ratio = (i + 30) / 60f
            val x = padding + (ratio * scaleWidth)
            canvas.drawLine(x, centerY - 6f, x, centerY + 6f, paint)
        }

        // Calculate pointer position
        val maxVisualAngle = Math.toRadians(35.0)
        val ratio = (rudderAngle.coerceIn(-maxVisualAngle, maxVisualAngle) / maxVisualAngle).toFloat()
        val pointerX = centerX + (ratio * (scaleWidth / 2f))

        // Draw pointer (Modern Sleek Line)
        paint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        paint.alpha = 255
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(pointerX, centerY - 14f, pointerX, centerY + 14f, paint)
        
        // Draw angle digital readout (Moved baseline to fit)
        val deg = Math.toDegrees(rudderAngle).toInt()
        val label = if (deg == 0) "MID" else "${kotlin.math.abs(deg)}° ${if (deg < 0) "PORT" else "STBD"}"
        textPaint.textSize = 24f
        textPaint.color = paint.color
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        // Baseline at centerY - 20f ensures text doesn't hit the top (0dp)
        canvas.drawText(label, pointerX, centerY - 22f, textPaint)
        
        // Label the ends
        textPaint.textSize = 18f
        textPaint.alpha = 150
        canvas.drawText("P", padding - 20f, centerY + 6f, textPaint)
        canvas.drawText("S", w - padding + 20f, centerY + 6f, textPaint)
    }
}
