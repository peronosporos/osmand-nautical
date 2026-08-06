package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class NauticalSparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private var data: List<Double> = emptyList()
    private var lineColor: Int = Color.BLUE
    private var areaColor: Int = Color.argb(40, 0, 0, 255)

    fun setData(newData: List<Double>, color: Int) {
        this.data = newData
        this.lineColor = color
        this.areaColor = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val min = data.minOrNull() ?: 0.0
        val max = data.maxOrNull() ?: 1.0
        val range = (max - min).coerceAtLeast(0.0001)

        path.reset()
        val stepX = w / (data.size - 1)

        for (i in data.indices) {
            val x = i * stepX
            val y = h - ((data[i] - min) / range * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

        // Draw area
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        areaPaint.color = areaColor
        canvas.drawPath(path, areaPaint)
    }
}
