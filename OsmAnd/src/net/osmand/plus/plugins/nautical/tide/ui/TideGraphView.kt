package net.osmand.plus.plugins.nautical.tide.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.StateChangedListener
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import java.text.SimpleDateFormat
import java.util.*

class TideGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var predictions: List<TidePrediction> = emptyList()
    private var vesselTide: net.osmand.plus.plugins.nautical.engine.TideState? = null

    fun setVesselTide(state: net.osmand.plus.plugins.nautical.engine.TideState?) {
        this.vesselTide = state
        invalidate()
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            12f,
            context.resources.displayMetrics,
        )
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    
    private val tidePath = Path()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    // Pre-allocated cache for rendering
    private class PredictionDrawData(
        val x: Float,
        val y: Float,
        val heightLabel: String?,
        val timeLabel: String?,
    )
    private var drawData: List<PredictionDrawData> = emptyList()
    private var yAxisMaxLabel: String = ""
    private var yAxisMinLabel: String = ""

    private var viewWidth = 0f
    private var viewHeight = 0f

    fun setPredictions(newPredictions: List<TidePrediction>) {
        this.predictions = newPredictions
        updateDrawData()
        invalidate()
    }

    private val nightVisionListener = StateChangedListener<Boolean> {
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (context.applicationContext as? OsmandApplication)?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.addListener(nightVisionListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (context.applicationContext as? OsmandApplication)?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.removeListener(nightVisionListener)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        updateDrawData()
    }

    private fun updateDrawData() {
        if (predictions.isEmpty() || (viewWidth <= 0)) return
        
        val w = viewWidth
        val h = viewHeight
        val padding = 50f
        val graphW = w - (2 * padding)
        val graphH = h - (2 * padding)

        val minHeight = predictions.minOf { it.heightMeters }
        val maxHeight = predictions.maxOf { it.heightMeters }
        val range = (maxHeight - minHeight).coerceAtLeast(0.1)
        
        val startTime = predictions.first().timestamp
        val endTime = predictions.last().timestamp
        val timeRange = (endTime - startTime).coerceAtLeast(1).toFloat()

        yAxisMaxLabel = if (predictions.any { it.velocity != null }) String.format(Locale.US, "%.1fkn", maxHeight * 1.94384) else String.format(Locale.US, "%.1fm", maxHeight)
        yAxisMinLabel = if (predictions.any { it.velocity != null }) String.format(Locale.US, "%.1fkn", minHeight * 1.94384) else String.format(Locale.US, "%.1fm", minHeight)

        tidePath.rewind()
        drawData = predictions.mapIndexed { i, p ->
            val valToDraw = p.velocity ?: p.heightMeters
            val x = padding + (((p.timestamp - startTime).toFloat() / timeRange) * graphW)
            val y = h - padding - (((valToDraw - minHeight).toFloat() / range.toFloat()) * graphH)
            
            if (i == 0) tidePath.moveTo(x, y) else tidePath.lineTo(x, y)
            
            val labelValue = if (p.velocity != null) p.velocity * 1.94384 else p.heightMeters
            val unit = if (p.velocity != null) "kn" else "m"
            
            val hLabel = if ((p.isHighTide != null) || (p.velocity != null)) String.format(Locale.US, "%.1f%s", labelValue, unit) else null
            val tLabel = if ((p.isHighTide != null) || (p.velocity != null)) timeFormat.format(Date(p.timestamp)) else null
            
            PredictionDrawData(x, y, hLabel, tLabel)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawData.isEmpty()) return

        val textColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.PRIMARY)
        linePaint.color = textColor
        textPaint.color = textColor
        markerPaint.color = NauticalColorResolver.getColor(context, NauticalSemanticColor.MARKER)

        drawData.forEachIndexed { i, data ->
            // Draw High/Low Markers
            if ((data.heightLabel != null) && (data.timeLabel != null)) {
                canvas.drawCircle(data.x, data.y, 10f, markerPaint)
                val isHigh = predictions[i].isHighTide == true
                canvas.drawText(data.heightLabel, data.x - 20, if (isHigh) data.y - 20 else data.y + 40, textPaint)
                canvas.drawText(data.timeLabel, data.x - 20, if (isHigh) data.y - 50 else data.y + 70, textPaint)
            }
        }
        canvas.drawPath(tidePath, linePaint)
        
        // Draw Y-axis labels
        canvas.drawText(yAxisMaxLabel, 5f, 50f, textPaint)
        canvas.drawText(yAxisMinLabel, 5f, viewHeight - 50f, textPaint)
    }
}
