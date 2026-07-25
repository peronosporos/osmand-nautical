package net.osmand.plus.views.mapwidgets.widgets

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.toColorInt
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.*

class NauticalGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var data: MutableList<Pair<Double, Long>> = mutableListOf()
    private var dataCoeff = 1.0
    private var dataOffset = 0.0
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var unit = ""
    private val graphPath = Path()

    private var defaultLineColor = Color.CYAN
    private var defaultTextColor = Color.BLACK
    private var defaultGridColor = Color.LTGRAY

    init {
        setWillNotDraw(false)
        val density = resources.displayMetrics.density

        if (isInEditMode) {
            val now = System.currentTimeMillis()
            data.add(10.0 to (now - 3600000))
            data.add(15.0 to (now - 2700000))
            data.add(12.0 to (now - 1800000))
            data.add(20.0 to (now - 900000))
            data.add(18.0 to now)
            unit = "kn"
        }

        val typedValue = TypedValue()
        if (context.theme?.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true) == true) {
            defaultTextColor = typedValue.data
        }

        if (context.theme?.resolveAttribute(android.R.attr.colorForeground, typedValue, true) == true) {
            defaultGridColor = typedValue.data
        }

        if (context.theme?.resolveAttribute(R.attr.active_color_primary, typedValue, true) == true) {
            defaultLineColor = typedValue.data
        }

        gridPaint.color = defaultGridColor
        gridPaint.alpha = 160
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = density * 0.5f

        textPaint.color = defaultTextColor
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        linePaint.color = defaultLineColor
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2.5f * density
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND

        dotPaint.color = defaultLineColor
        dotPaint.style = Paint.Style.FILL
    }

    fun setData(newData: List<Pair<Double, Long>>?, unit: String, coeff: Double = 1.0, offset: Double = 0.0) {
        if (newData == null) return
        synchronized(this) {
            data.clear()
            data.addAll(newData)
            this.unit = unit
            this.dataCoeff = coeff
            this.dataOffset = offset
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(this) {
            if (data.isEmpty()) {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(context.getString(R.string.nautical_no_data), width / 2f, height / 2f, textPaint)
                return
            }

            val isNightVision = NauticalPlugin.isNightVision(context.applicationContext as? net.osmand.plus.OsmandApplication)
            if (isNightVision) {
                linePaint.color = Color.RED
                textPaint.color = Color.RED
                gridPaint.color = "#330000".toColorInt()
            } else {
                linePaint.color = defaultLineColor
                textPaint.color = defaultTextColor
                gridPaint.color = defaultGridColor
            }

            val width = width.toFloat()
            val height = height.toFloat()
            val density = resources.displayMetrics.density
            val paddingH = 36f * density
            val paddingV = 16f * density
            val paddingBottom = 24f * density
            val graphW = width - (paddingH * 2)
            val graphH = height - (paddingV + paddingBottom)

            // Optimized min/max calculation and path building in one pass
            var min = Double.MAX_VALUE
            var max = -Double.MAX_VALUE
            
            for (p in data) {
                val v = p.first * dataCoeff + dataOffset
                if (v < min) min = v
                if (v > max) max = v
            }

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
                val v = data[i].first * dataCoeff + dataOffset
                val y = (height - paddingBottom - (((v - min) / range) * graphH)).toFloat()
                if (i == 0) graphPath.moveTo(x, y)
                else graphPath.lineTo(x, y)
            }
            canvas.drawPath(graphPath, linePaint)

            // Draw Labels
            textPaint.textAlign = Paint.Align.RIGHT
            val shadowColor = if (!isNightVision && Color.luminance(textPaint.color) > 0.5f) Color.BLACK else Color.WHITE
            if (!isNightVision) textPaint.setShadowLayer(2f, 1f, 1f, shadowColor)

            canvas.drawText(String.format(Locale.US, "%.1f", max), paddingH - (4 * density), paddingV + (4 * density), textPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", min), paddingH - (4 * density), height - paddingBottom, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(unit, (width - paddingH) + (4 * density), paddingV + (4 * density), textPaint)
            
            val now = System.currentTimeMillis()
            val totalSpanMs = now - data.first().second
            if (totalSpanMs > 1000) {
                textPaint.textAlign = Paint.Align.CENTER
                val spanMinutes = totalSpanMs / 60000
                if (spanMinutes > 0) {
                    canvas.drawText("-${spanMinutes}m", paddingH, height - (4 * density), textPaint)
                    canvas.drawText("-${spanMinutes / 2}m", paddingH + (graphW / 2), height - (4 * density), textPaint)
                } else {
                    canvas.drawText("-${totalSpanMs / 1000}s", paddingH, height - (4 * density), textPaint)
                }
            }
            textPaint.clearShadowLayer()
        }
    }
}
