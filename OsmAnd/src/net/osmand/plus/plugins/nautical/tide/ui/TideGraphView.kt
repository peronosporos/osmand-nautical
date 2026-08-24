package net.osmand.plus.plugins.nautical.tide.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import net.osmand.plus.OsmandApplication
import net.osmand.StateChangedListener
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class TideGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var predictions: List<TidePrediction> = emptyList()
    private var vesselTide: net.osmand.plus.plugins.nautical.engine.TideState? = null

    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNightVisionTheme(value)
                invalidate()
            }
        }

    fun setVesselTide(state: net.osmand.plus.plugins.nautical.engine.TideState?) {
        this.vesselTide = state
        invalidate()
    }

    // Preallocated Paint objects for zero-allocation rendering in onDraw
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Cyan tide curve
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * context.resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hwMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt() // Vibrant Red/Amber for High Water
        style = Paint.Style.FILL
    }

    private val lwMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0288D1.toInt() // Blue for Low Water
        style = Paint.Style.FILL
    }

    private val liveMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E676.toInt() // Green for Current Live Water Level
        style = Paint.Style.FILL
    }

    private val liveLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8800E676.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val scrubberMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
        style = Paint.Style.FILL
    }

    private val scrubberBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEE1A232E.toInt()
        style = Paint.Style.FILL
    }

    private val scrubberBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val scrubberTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * context.resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val tidePath = Path()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    // Preallocated Draw Data and Geometry Cache
    private class PredictionDrawData(
        val x: Float,
        val y: Float,
        val heightLabel: String?,
        val timeLabel: String?,
        val isHigh: Boolean?,
    )

    private var drawData: List<PredictionDrawData> = emptyList()
    private var yAxisMaxLabel: String = ""
    private var yAxisMinLabel: String = ""
    private var minVal = 0.0
    private var maxVal = 1.0
    private var startTime = 0L
    private var endTime = 1L
    private var isCurrentDataset = false

    private var viewWidth = 0f
    private var viewHeight = 0f
    private val padding = 40f

    // Interactive Scrubber State
    private var isScrubbing = false
    private var scrubberTouchX = 0f
    private var scrubberTouchY = 0f
    private var scrubberLabel = ""
    private val badgeRect = RectF()

    fun setPredictions(newPredictions: List<TidePrediction>) {
        this.predictions = newPredictions
        updateDrawData()
        invalidate()
    }

    private val nightVisionListener = StateChangedListener<Boolean> {
        val app = context.applicationContext as? OsmandApplication
        isNightVision = NauticalPlugin.isNightVision(app)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val app = context.applicationContext as? OsmandApplication
        app?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.addListener(nightVisionListener)
        isNightVision = NauticalPlugin.isNightVision(app)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val app = context.applicationContext as? OsmandApplication
        app?.settings?.NAUTICAL_NIGHT_VISION_ENABLED?.removeListener(nightVisionListener)
    }

    private fun applyNightVisionTheme(enabled: Boolean) {
        if (enabled) {
            linePaint.color = 0xFFFF1744.toInt() // Monochromatic Deep Red
            gridPaint.color = 0x33FF1744.toInt()
            textPaint.color = 0xFFFF8A80.toInt()
            hwMarkerPaint.color = 0xFFFF5252.toInt()
            lwMarkerPaint.color = 0xFFB71C1C.toInt()
            liveMarkerPaint.color = 0xFFFF1744.toInt()
            liveLinePaint.color = 0x88FF1744.toInt()
            scrubberLinePaint.color = 0xFFFF5252.toInt()
            scrubberMarkerPaint.color = 0xFFFF1744.toInt()
            scrubberBadgeBgPaint.color = 0xEE120000.toInt()
            scrubberBadgeStrokePaint.color = 0xFFFF1744.toInt()
            scrubberTextPaint.color = 0xFFFF8A80.toInt()
        } else {
            linePaint.color = 0xFF00E5FF.toInt()
            gridPaint.color = 0x33FFFFFF.toInt()
            textPaint.color = Color.WHITE
            hwMarkerPaint.color = 0xFFFF5252.toInt()
            lwMarkerPaint.color = 0xFF0288D1.toInt()
            liveMarkerPaint.color = 0xFF00E676.toInt()
            liveLinePaint.color = 0x8800E676.toInt()
            scrubberLinePaint.color = 0xFFFFD600.toInt()
            scrubberMarkerPaint.color = 0xFFFFD600.toInt()
            scrubberBadgeBgPaint.color = 0xEE1A232E.toInt()
            scrubberBadgeStrokePaint.color = 0xFFFFD600.toInt()
            scrubberTextPaint.color = Color.WHITE
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        updateDrawData()
    }

    private fun updateDrawData() {
        if (predictions.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return

        val w = viewWidth
        val h = viewHeight
        val density = context.resources.displayMetrics.density
        val pad = padding * density
        val graphW = (w - (2 * pad)).coerceAtLeast(10f)
        val graphH = (h - (2 * pad)).coerceAtLeast(10f)

        isCurrentDataset = predictions.any { it.velocity != null }
        minVal = if (isCurrentDataset) predictions.minOf { it.velocity ?: 0.0 } else predictions.minOf { it.heightMeters }
        maxVal = if (isCurrentDataset) predictions.maxOf { it.velocity ?: 0.0 } else predictions.maxOf { it.heightMeters }
        val range = (maxVal - minVal).coerceAtLeast(0.1)

        startTime = predictions.first().timestamp
        endTime = predictions.last().timestamp
        val timeRange = (endTime - startTime).coerceAtLeast(1).toFloat()

        yAxisMaxLabel = if (isCurrentDataset) String.format(Locale.US, "%.1f kn", maxVal * 1.94384) else String.format(Locale.US, "%.1f m", maxVal)
        yAxisMinLabel = if (isCurrentDataset) String.format(Locale.US, "%.1f kn", minVal * 1.94384) else String.format(Locale.US, "%.1f m", minVal)

        tidePath.rewind()
        drawData = predictions.mapIndexed { i, p ->
            val valToDraw = p.velocity ?: p.heightMeters
            val x = pad + (((p.timestamp - startTime).toFloat() / timeRange) * graphW)
            val y = h - pad - (((valToDraw - minVal).toFloat() / range.toFloat()) * graphH)

            if (i == 0) tidePath.moveTo(x, y) else tidePath.lineTo(x, y)

            val labelValue = if (p.velocity != null) p.velocity * 1.94384 else p.heightMeters
            val unit = if (p.velocity != null) "kn" else "m"

            val isHwOrLw = p.isHighTide != null || p.velocity != null
            val prefix = if (p.isHighTide == true) "HW " else if (p.isHighTide == false) "LW " else ""
            val hLabel = if (isHwOrLw) String.format(Locale.US, "%s%.1f%s", prefix, labelValue, unit) else null
            val tLabel = if (isHwOrLw) timeFormat.format(Date(p.timestamp)) else null

            PredictionDrawData(x, y, hLabel, tLabel, p.isHighTide)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (predictions.isEmpty() || viewWidth <= 0) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isScrubbing = true
                updateScrubberPosition(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isScrubbing = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateScrubberPosition(touchX: Float) {
        val density = context.resources.displayMetrics.density
        val pad = padding * density
        val graphW = (viewWidth - (2 * pad)).coerceAtLeast(10f)
        val graphH = (viewHeight - (2 * pad)).coerceAtLeast(10f)

        val clampedX = touchX.coerceIn(pad, viewWidth - pad)
        val progress = ((clampedX - pad) / graphW).coerceIn(0f, 1f)
        val targetTime = (startTime + (progress * (endTime - startTime))).toLong()

        // Interpolate value and trend
        var valAtCursor = minVal
        var trend = "▲"
        if (predictions.size >= 2) {
            for (i in 0 until predictions.size - 1) {
                val p1 = predictions[i]
                val p2 = predictions[i + 1]
                if (targetTime in p1.timestamp..p2.timestamp) {
                    val dt = (p2.timestamp - p1.timestamp).coerceAtLeast(1)
                    val factor = (targetTime - p1.timestamp).toDouble() / dt
                    val v1 = p1.velocity ?: p1.heightMeters
                    val v2 = p2.velocity ?: p2.heightMeters
                    valAtCursor = v1 + factor * (v2 - v1)
                    trend = if (v2 >= v1) "▲" else "▼"
                    break
                }
            }
        }

        val range = (maxVal - minVal).coerceAtLeast(0.1)
        scrubberTouchX = clampedX
        scrubberTouchY = viewHeight - pad - (((valAtCursor - minVal).toFloat() / range.toFloat()) * graphH)

        val timeStr = timeFormat.format(Date(targetTime))
        val unit = if (isCurrentDataset) "kn" else "m"
        val displayVal = if (isCurrentDataset) valAtCursor * 1.94384 else valAtCursor
        val trendText = if (trend == "▲") "Rising" else "Falling"
        scrubberLabel = String.format(Locale.US, "%s • %.1f %s %s (%s)", timeStr, displayVal, unit, trend, trendText)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawData.isEmpty()) return

        val density = context.resources.displayMetrics.density
        val pad = padding * density
        val graphW = viewWidth - (2 * pad)
        val graphH = viewHeight - (2 * pad)

        // Draw horizontal grid lines (0%, 50%, 100%)
        canvas.drawLine(pad, pad, viewWidth - pad, pad, gridPaint)
        canvas.drawLine(pad, pad + graphH / 2f, viewWidth - pad, pad + graphH / 2f, gridPaint)
        canvas.drawLine(pad, viewHeight - pad, viewWidth - pad, viewHeight - pad, gridPaint)

        // Draw Sinusoidal Tide Path
        canvas.drawPath(tidePath, linePaint)

        // Draw High Water (HW) and Low Water (LW) markers & labels
        for (i in drawData.indices) {
            val data = drawData[i]
            if (data.heightLabel != null && data.timeLabel != null) {
                val isHigh = data.isHigh == true
                val markerP = if (isHigh) hwMarkerPaint else lwMarkerPaint
                canvas.drawCircle(data.x, data.y, 6f * density, markerP)

                val labelY1 = if (isHigh) data.y - 18f * density else data.y + 22f * density
                val labelY2 = if (isHigh) data.y - 6f * density else data.y + 34f * density
                canvas.drawText(data.heightLabel, data.x - 24f * density, labelY1, textPaint)
                canvas.drawText(data.timeLabel, data.x - 24f * density, labelY2, textPaint)
            }
        }

        // Draw Live Water Level Indicator at current timestamp
        val now = System.currentTimeMillis()
        if (now in startTime..endTime) {
            val timeRange = (endTime - startTime).coerceAtLeast(1).toFloat()
            val liveX = pad + (((now - startTime).toFloat() / timeRange) * graphW)
            canvas.drawLine(liveX, pad, liveX, viewHeight - pad, liveLinePaint)
            canvas.drawCircle(liveX, pad + graphH / 2f, 4f * density, liveMarkerPaint)
        }

        // Draw Y-axis Max/Min labels
        canvas.drawText(yAxisMaxLabel, 6f * density, pad + 12f * density, textPaint)
        canvas.drawText(yAxisMinLabel, 6f * density, viewHeight - pad - 4f * density, textPaint)

        // Draw Interactive Scrubber Cursor & Floating Badge
        if (isScrubbing && scrubberLabel.isNotEmpty()) {
            canvas.drawLine(scrubberTouchX, pad, scrubberTouchX, viewHeight - pad, scrubberLinePaint)
            canvas.drawCircle(scrubberTouchX, scrubberTouchY, 7f * density, scrubberMarkerPaint)

            // Floating Header Badge
            val badgeW = 200f * density
            val badgeH = 26f * density
            val badgeX = (scrubberTouchX - badgeW / 2f).coerceIn(pad, viewWidth - pad - badgeW)
            val badgeY = (scrubberTouchY - 38f * density).coerceIn(4f * density, viewHeight - pad - badgeH)

            badgeRect.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
            canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, scrubberBadgeBgPaint)
            canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, scrubberBadgeStrokePaint)

            canvas.drawText(scrubberLabel, badgeRect.centerX(), badgeRect.centerY() + 4f * density, scrubberTextPaint)
        }
    }
}
