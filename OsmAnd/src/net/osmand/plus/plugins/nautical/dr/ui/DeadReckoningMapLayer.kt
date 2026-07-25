package net.osmand.plus.plugins.nautical.dr.ui

import android.content.Context
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.plugins.nautical.dr.engine.FixSource
import net.osmand.plus.plugins.nautical.dr.viewmodel.DrUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer

/**
 * Map layer for visualizing Dead Reckoning projections.
 * Draws an amber boat marker and a dashed line from the last known GPS fix.
 */
class DeadReckoningMapLayer(context: Context) : OsmandMapLayer(context) {

    private var drUiState: DrUiState? = null

    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 136, 0) // Amber
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dashedAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 136, 0) // Amber
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 136, 0)
        alpha = 100
        style = Paint.Style.FILL
    }

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

        // 2. Draw estimated boat position marker (Amber circle with crosshair)
        canvas.drawCircle(drX, drY, 25f, fillPaint)
        canvas.drawCircle(drX, drY, 25f, amberPaint)
        
        canvas.drawLine(drX - 40f, drY, drX + 40f, drY, amberPaint)
        canvas.drawLine(drX, drY - 40f, drX, drY + 40f, amberPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
