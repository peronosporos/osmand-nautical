package net.osmand.plus.plugins.nautical.hazard.ui

import android.graphics.*
import androidx.core.graphics.toColorInt
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils

class NavtexMapLayer(private val activity: MapActivity) : OsmandMapLayer(activity) {

    private var uiState: NavtexUiState = NavtexUiState()
    
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val markerPath = Path()
    private val touchRect = RectF()

    fun updateState(state: NavtexUiState) {
        this.uiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        uiState.messages.forEach { msg ->
            if (msg.points.isEmpty()) return@forEach
            
            if (msg.isPolygon) {
                drawPolygon(canvas, tileBox, msg, settings.isNightMode)
            } else {
                val coords = msg.points[0]
                val x = tileBox.getPixXFromLatLon(coords.latitude, coords.longitude)
                val y = tileBox.getPixYFromLatLon(coords.latitude, coords.longitude)
                
                if (x < 0 || x > canvas.width || y < 0 || y > canvas.height) return@forEach
                
                drawWarningMarker(canvas, x, y, msg.isUrgent, settings.isNightMode)
            }
        }
    }

    private fun drawPolygon(canvas: Canvas, tileBox: RotatedTileBox, msg: NavtexMessage, isNight: Boolean) {
        val path = Path()
        msg.points.forEachIndexed { index, latLon ->
            val x = tileBox.getPixXFromLatLon(latLon.latitude, latLon.longitude)
            val y = tileBox.getPixYFromLatLon(latLon.latitude, latLon.longitude)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        
        val baseColor = if (msg.isUrgent) {
            if (isNight) Color.parseColor("#B71C1C") else Color.RED
        } else {
            if (isNight) Color.parseColor("#E65100") else Color.parseColor("#FF8F00")
        }

        markerPaint.style = Paint.Style.FILL
        markerPaint.color = baseColor
        markerPaint.alpha = 40
        canvas.drawPath(path, markerPaint)
        
        strokePaint.color = baseColor
        strokePaint.alpha = 200
        strokePaint.strokeWidth = 4f
        canvas.drawPath(path, strokePaint)
        
        markerPaint.alpha = 255 // Reset
        strokePaint.alpha = 255
        strokePaint.strokeWidth = 2f
    }

    private fun drawWarningMarker(canvas: Canvas, x: Float, y: Float, isUrgent: Boolean, isNight: Boolean) {
        markerPaint.style = Paint.Style.FILL
        markerPaint.color = if (isUrgent) {
            if (isNight) Color.parseColor("#B71C1C") else Color.RED
        } else {
            if (isNight) Color.parseColor("#E65100") else Color.parseColor("#FF8F00")
        }
        
        strokePaint.color = if (isNight) Color.LTGRAY else Color.WHITE
        
        val size = 30f
        markerPath.reset()
        markerPath.moveTo(x, y - size)
        markerPath.lineTo(x - size, y + size)
        markerPath.lineTo(x + size, y + size)
        markerPath.close()
        
        canvas.drawPath(markerPath, markerPaint)
        canvas.drawPath(markerPath, strokePaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        uiState.messages.forEach { msg ->
            if (msg.points.isEmpty()) return@forEach
            
            if (msg.isPolygon) {
                // Simple bounding box check for polygon tap
                val bounds = RectF()
                val path = Path()
                msg.points.forEachIndexed { index, latLon ->
                    val x = tileBox.getPixXFromLatLon(latLon.latitude, latLon.longitude)
                    val y = tileBox.getPixYFromLatLon(latLon.latitude, latLon.longitude)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.computeBounds(bounds, true)
                if (bounds.contains(point.x, point.y)) {
                    showDetails(msg)
                    return true
                }
            } else {
                val coords = msg.points[0]
                val x = tileBox.getPixXFromLatLon(coords.latitude, coords.longitude)
                val y = tileBox.getPixYFromLatLon(coords.latitude, coords.longitude)
                
                val size = 40f
                touchRect.set(x - size, y - size, x + size, y + size)
                
                if (touchRect.contains(point.x, point.y)) {
                    showDetails(msg)
                    return true
                }
            }
        }
        return false
    }

    private fun showDetails(message: NavtexMessage) {
        NavtexDetailsBottomSheet.newInstance(message).show(activity.supportFragmentManager, "navtex_details")
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
