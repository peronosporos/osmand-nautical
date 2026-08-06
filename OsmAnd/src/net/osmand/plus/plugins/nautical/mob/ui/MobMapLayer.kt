package net.osmand.plus.plugins.nautical.mob.ui

import android.content.Context
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.mob.engine.MobState
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer

/**
 * Map layer for Man Overboard (MOB) visualization.
 * Draws the drop location and a return vector line.
 */
class MobMapLayer(context: Context) : OsmandMapLayer(context) {

    private var mobUiState: MobUiState? = null

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        alpha = 100
        style = Paint.Style.FILL
    }

    fun updateState(state: MobUiState) {
        this.mobUiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val state = mobUiState ?: return
        if (state.state == MobState.INACTIVE) return

        val mobLocation = state.mobLocation ?: return
        
        // Draw MOB target marker
        val mobX = tileBox.getPixXFromLatLon(mobLocation.latitude, mobLocation.longitude)
        val mobY = tileBox.getPixYFromLatLon(mobLocation.latitude, mobLocation.longitude)

        canvas.drawCircle(mobX, mobY, 30f, fillPaint)
        canvas.drawCircle(mobX, mobY, 30f, markerPaint)
        
        // Crosshair
        canvas.drawLine(mobX - 45f, mobY, mobX + 45f, mobY, markerPaint)
        canvas.drawLine(mobX, mobY - 45f, mobX, mobY + 45f, markerPaint)

        // Draw return vector line if boat location is available
        val app = context.applicationContext as? OsmandApplication
        val boatLocation = app?.locationProvider?.lastKnownLocation
        
        if (boatLocation != null) {
            val boatX = tileBox.getPixXFromLatLon(boatLocation.latitude, boatLocation.longitude)
            val boatY = tileBox.getPixYFromLatLon(boatLocation.latitude, boatLocation.longitude)
            
            canvas.drawLine(boatX, boatY, mobX, mobY, linePaint)
        }
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
