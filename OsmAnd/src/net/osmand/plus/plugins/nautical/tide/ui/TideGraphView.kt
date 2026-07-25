package net.osmand.plus.plugins.nautical.tide.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import java.text.SimpleDateFormat
import java.util.*

class TideGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var predictions: List<TidePrediction> = emptyList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    fun setPredictions(newPredictions: List<TidePrediction>) {
        this.predictions = newPredictions
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (predictions.isEmpty()) return

        val app = context.applicationContext as? net.osmand.plus.OsmandApplication
        if (NauticalPlugin.isNightVision(app)) {
            linePaint.color = Color.RED
            textPaint.color = Color.RED
            markerPaint.color = Color.RED
        } else {
            linePaint.color = Color.CYAN
            textPaint.color = Color.BLACK
            markerPaint.color = Color.rgb(255, 165, 0) // Orange
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 50f
        val graphW = w - 2 * padding
        val graphH = h - 2 * padding

        val minHeight = predictions.minOf { it.heightMeters }
        val maxHeight = predictions.maxOf { it.heightMeters }
        val range = (maxHeight - minHeight).coerceAtLeast(0.1)
        
        val startTime = predictions.first().timestamp
        val endTime = predictions.last().timestamp
        val timeRange = (endTime - startTime).coerceAtLeast(1)

        val path = Path()
        predictions.forEachIndexed { i, p ->
            val x = padding + ((p.timestamp - startTime).toFloat() / timeRange * graphW)
            val y = h - padding - (((p.heightMeters - minHeight).toFloat() / range.toFloat()) * graphH)
            
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            
            // Draw High/Low Markers
            if (p.isHighTide != null) {
                canvas.drawCircle(x, y, 10f, markerPaint)
                val label = String.format(Locale.US, "%.1fm", p.heightMeters)
                val timeLabel = timeFormat.format(Date(p.timestamp))
                canvas.drawText(label, x - 20, if (p.isHighTide) y - 20 else y + 40, textPaint)
                canvas.drawText(timeLabel, x - 20, if (p.isHighTide) y - 50 else y + 70, textPaint)
            }
        }
        canvas.drawPath(path, linePaint)
        
        // Draw Y-axis labels
        canvas.drawText(String.format(Locale.US, "%.1fm", maxHeight), 5f, padding, textPaint)
        canvas.drawText(String.format(Locale.US, "%.1fm", minHeight), 5f, h - padding, textPaint)
    }
}
