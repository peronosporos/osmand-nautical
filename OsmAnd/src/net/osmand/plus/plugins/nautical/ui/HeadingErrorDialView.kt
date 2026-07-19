package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import kotlin.math.*

class HeadingErrorDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var headingError: Float = 0f
        set(value) {
            field = value.coerceIn(-45f, 45f)
            invalidate()
        }

    private var isNightMode: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialRect = RectF()
    private var osmandOrange = Color.parseColor("#FF8800")

    init {
        osmandOrange = ContextCompat.getColor(context, R.color.icon_color_osmand_light)
        paint.strokeCap = Paint.Cap.ROUND
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    fun setNightMode(night: Boolean) {
        this.isNightMode = night
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) / 2f * 0.85f
        val centerX = w / 2f
        val centerY = h / 2f

        dialRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // Draw Background Arc
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = if (isNightMode) Color.DKGRAY else Color.LTGRAY
        paint.alpha = 100
        canvas.drawArc(dialRect, 135f, 270f, false, paint)

        // Draw Ticks
        paint.strokeWidth = 4f
        for (i in -45..45 step 15) {
            val angle = 270f + i
            val startX = centerX + (radius - 10f) * cos(Math.toRadians(angle.toDouble())).toFloat()
            val startY = centerY + (radius - 10f) * sin(Math.toRadians(angle.toDouble())).toFloat()
            val endX = centerX + (radius + 10f) * cos(Math.toRadians(angle.toDouble())).toFloat()
            val endY = centerY + (radius + 10f) * sin(Math.toRadians(angle.toDouble())).toFloat()
            
            paint.alpha = if (i == 0) 255 else 150
            canvas.drawLine(startX, startY, endX, endY, paint)
            
            if (i % 30 == 0) {
                textPaint.textSize = 24f
                textPaint.color = paint.color
                textPaint.alpha = 200
                val labelX = centerX + (radius + 35f) * cos(Math.toRadians(angle.toDouble())).toFloat()
                val labelY = centerY + (radius + 35f) * sin(Math.toRadians(angle.toDouble())).toFloat() + 10f
                canvas.drawText("${abs(i)}", labelX, labelY, textPaint)
            }
        }

        // Draw Center Index
        paint.color = osmandOrange
        paint.strokeWidth = 6f
        paint.alpha = 255
        canvas.drawLine(centerX, centerY - radius + 15f, centerX, centerY - radius - 15f, paint)

        // Draw Needle
        val needleAngle = 270f + headingError
        val needleX = centerX + (radius - 5f) * cos(Math.toRadians(needleAngle.toDouble())).toFloat()
        val needleY = centerY + (radius - 5f) * sin(Math.toRadians(needleAngle.toDouble())).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = if (abs(headingError) < 5) Color.GREEN else if (abs(headingError) < 15) Color.YELLOW else Color.RED
        canvas.drawCircle(needleX, needleY, 12f, paint)
        
        // Needle line from center (optional for pro look)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawLine(centerX, centerY, needleX, needleY, paint)

        // Digital Display
        textPaint.textSize = 56f
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_primary_dark else R.color.text_color_primary_light)
        val errorText = String.format("%.1f\u00B0", headingError)
        canvas.drawText(errorText, centerX, centerY + 20f, textPaint)
        
        textPaint.textSize = 20f
        textPaint.color = ContextCompat.getColor(context, if (isNightMode) R.color.text_color_secondary_dark else R.color.text_color_secondary_light)
        canvas.drawText("HDG ERR", centerX, centerY + 50f, textPaint)
    }
}
