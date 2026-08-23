package net.osmand.plus.plugins.nautical.dr.ui

import android.content.Context
import android.graphics.*
import kotlin.math.abs
import net.osmand.data.RotatedTileBox
import net.osmand.plus.plugins.nautical.dr.engine.FixSource
import net.osmand.plus.plugins.nautical.dr.viewmodel.DrUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils

/**
 * Map layer for visualizing Dead Reckoning projections.
 * Draws an amber boat marker, dashed trail from last GPS fix,
 * multi-interval forward projections (+15m, +30m, +45m, +60m),
 * and growing uncertainty radius circles.
 */
class DeadReckoningMapLayer(context: Context) : OsmandMapLayer(context) {

    private var drUiState: DrUiState? = null

    private val colorAmber = 0xFFFF9800.toInt()

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dashedAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val projectionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        alpha = 100
        style = Paint.Style.FILL
    }

    private val uncertaintyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        alpha = 35 // Semi-transparent CEP
        style = Paint.Style.FILL
    }

    private val uncertaintyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        alpha = 140
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val milestonePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        style = Paint.Style.FILL
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt() // Dark blue-gray badge
        alpha = 220
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAmber
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val projectionPath = Path()
    private val badgeRect = RectF()

    fun updateState(state: DrUiState) {
        this.drUiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val state = drUiState ?: return
        if (state.source != FixSource.DEAD_RECKONING) return

        val lat = state.latitude ?: return
        val lon = state.longitude ?: return

        val drX = tileBox.getPixXFromLatLon(lat, lon)
        val drY = tileBox.getPixYFromLatLon(lat, lon)

        // 1. Draw dashed line from last valid GPS fix
        val lastLat = state.lastValidGpsLat
        val lastLon = state.lastValidGpsLon
        if ((lastLat != null) && (lastLon != null)) {
            val startX = tileBox.getPixXFromLatLon(lastLat, lastLon)
            val startY = tileBox.getPixYFromLatLon(lastLat, lastLon)
            canvas.drawLine(startX, startY, drX, drY, dashedAmberPaint)
        }

        // 2. Draw Multi-Interval Projections (+15m, +30m, +45m, +60m)
        val projections = state.projectionPoints
        if (projections.isNotEmpty()) {
            projectionPath.reset()
            projectionPath.moveTo(drX, drY)

            for (point in projections) {
                val px = tileBox.getPixXFromLatLon(point.lat, point.lon)
                val py = tileBox.getPixYFromLatLon(point.lat, point.lon)
                projectionPath.lineTo(px, py)
            }
            canvas.drawPath(projectionPath, projectionLinePaint)

            // Draw uncertainty circles and milestone badges
            for (point in projections) {
                val px = tileBox.getPixXFromLatLon(point.lat, point.lon)
                val py = tileBox.getPixYFromLatLon(point.lat, point.lon)

                // Uncertainty circle
                if (point.uncertaintyMeters > 0.0) {
                    val northPoint = MapUtils.rhumbDestinationPoint(point.lat, point.lon, 0.0, point.uncertaintyMeters)
                    val northY = tileBox.getPixYFromLatLon(northPoint.latitude, northPoint.longitude)
                    val pixRadius = abs(py - northY).coerceAtLeast(4f)

                    canvas.drawCircle(px, py, pixRadius, uncertaintyFillPaint)
                    canvas.drawCircle(px, py, pixRadius, uncertaintyStrokePaint)
                }

                // Milestone dot
                canvas.drawCircle(px, py, 6f, milestonePointPaint)

                // Milestone Label pill (+15m, +30m, etc.)
                val label = "+${point.minuteOffset}m"
                val textWidth = badgeTextPaint.measureText(label)
                val textHeight = badgeTextPaint.textSize
                val badgeWidth = textWidth + 16f
                val badgeHeight = textHeight + 8f

                val badgeCenterX = px + 18f + (badgeWidth / 2f)
                val badgeCenterY = py - 10f

                badgeRect.set(
                    badgeCenterX - (badgeWidth / 2f),
                    badgeCenterY - (badgeHeight / 2f),
                    badgeCenterX + (badgeWidth / 2f),
                    badgeCenterY + (badgeHeight / 2f)
                )

                canvas.drawRoundRect(badgeRect, 8f, 8f, badgeBgPaint)
                canvas.drawRoundRect(badgeRect, 8f, 8f, badgeStrokePaint)
                canvas.drawText(label, badgeCenterX, badgeCenterY + (textHeight * 0.35f), badgeTextPaint)
            }
        }

        // 3. Draw estimated boat position marker (Amber circle with crosshair)
        canvas.drawCircle(drX, drY, 25f, fillPaint)
        canvas.drawCircle(drX, drY, 25f, amberPaint)
        
        canvas.drawLine(drX - 40f, drY, drX + 40f, drY, amberPaint)
        canvas.drawLine(drX, drY - 40f, drX, drY + 40f, amberPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
