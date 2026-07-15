package net.osmand.plus.views.mapwidgets.widgets

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.*

class NauticalGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var data: List<Double> = ArrayList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var unit = ""
    private val graphPath = Path()

    init {
        setWillNotDraw(false)
        val density = resources.displayMetrics.density

        if (isInEditMode) {
            data = listOf(10.0, 15.0, 12.0, 20.0, 18.0)
            unit = "kn"
        }

        val typedValue = TypedValue()
        var textColor = Color.BLACK
        if (context.theme?.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true) == true) {
            textColor = typedValue.data
        }

        var gridColor = Color.LTGRAY
        if (context.theme?.resolveAttribute(android.R.attr.colorForeground, typedValue, true) == true) {
            gridColor = typedValue.data
        }

        var activeColor = Color.CYAN
        if (context.theme?.resolveAttribute(R.attr.active_color_primary, typedValue, true) == true) {
            activeColor = typedValue.data
        }

        gridPaint.color = gridColor
        gridPaint.alpha = 160 // Increased from 80 for better visibility in high contrast
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = density * 0.5f // Thinner grid lines

        textPaint.color = textColor
        textPaint.textSize = 12f * density // Slightly larger text
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // Bold for better readability

        linePaint.color = activeColor
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2.5f * density
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND

        dotPaint.color = activeColor
        dotPaint.style = Paint.Style.FILL
    }

    fun setData(newData: List<Double>?, unit: String) {
        if (newData == null) return
        synchronized(this) {
            this.data = ArrayList(newData)
            this.unit = unit
        }
        postInvalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(this) {
            if (data.isEmpty()) {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(context.getString(R.string.nautical_no_data), width / 2f, height / 2f, textPaint)
                return
            }

            val isNightVision = NauticalPlugin.getInstance()?.isNightVisionEnabled == true
            if (isNightVision) {
                linePaint.color = Color.RED
                textPaint.color = Color.RED
                gridPaint.color = Color.DKGRAY
            }

            val width = width.toFloat()
            val height = height.toFloat()
            val density = resources.displayMetrics.density
            val paddingH = 36f * density
            val paddingV = 16f * density
            val paddingBottom = 24f * density
            val graphW = width - (paddingH * 2)
            val graphH = height - (paddingV + paddingBottom)

            var min = data.minOrNull() ?: 0.0
            var max = data.maxOrNull() ?: 1.0
            if (min == max) {
                min -= 1.0
                max += 1.0
            }

            val range = max - min
            val stepX = if (data.size > 1) graphW / (data.size - 1) else 0f

            // Draw Grid
            canvas.drawLine(paddingH, paddingV, width - paddingH, paddingV, gridPaint)
            canvas.drawLine(paddingH, height - paddingBottom, width - paddingH, height - paddingBottom, gridPaint)

            // Draw Path
            graphPath.reset()
            for (i in data.indices) {
                val x = paddingH + (i * stepX)
                val y = (height - paddingBottom - (((data[i] - min) / range) * graphH)).toFloat()
                if (i == 0) graphPath.moveTo(x, y)
                else graphPath.lineTo(x, y)
            }
            canvas.drawPath(graphPath, linePaint)

            // Draw Labels
            textPaint.textAlign = Paint.Align.RIGHT
            val luminance = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Color.luminance(textPaint.color)
            } else {
                val r = Color.red(textPaint.color) / 255.0
                val g = Color.green(textPaint.color) / 255.0
                val b = Color.blue(textPaint.color) / 255.0
                ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)).toFloat()
            }
            val shadowColor = if (luminance > 0.5f) Color.BLACK else Color.WHITE
            if (!isNightVision) textPaint.setShadowLayer(2f, 1f, 1f, shadowColor) // Add shadow for high contrast

            canvas.drawText(String.format(Locale.US, "%.1f", max), paddingH - (4 * density), paddingV + (4 * density), textPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", min), paddingH - (4 * density), height - paddingBottom, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(unit, (width - paddingH) + (4 * density), paddingV + (4 * density), textPaint)
            
            // X-Axis Timespan Labels
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("-6m", paddingH, height - (4 * density), textPaint)
            canvas.drawText("-3m", paddingH + (graphW / 2), height - (4 * density), textPaint)
            canvas.drawText("Now", width - paddingH, height - (4 * density), textPaint)

            textPaint.clearShadowLayer()
        }
    }
}
