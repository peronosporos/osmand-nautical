package net.osmand.plus.plugins.nautical.hazard.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer

class DynamicHazardLayer(activity: MapActivity) : OsmandMapLayer(activity) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 60
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 3f
    }

    private val polygonPath = Path()

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val safetyManager = plugin.safetyManager ?: return
        
        // 1. Draw SignalK Regions (Restricted Areas / Waterway Closures)
        val regions = safetyManager.getSignalKRegions()
        for (region in regions) {
            val geometry = region.feature.geometry
            if (geometry["type"] == "Polygon") {
                val coords = (geometry["coordinates"] as? List<*>)?.get(0) as? List<*>
                if (coords != null) {
                    drawSkPolygon(canvas, tileBox, coords)
                }
            }
        }

        // 2. Draw Forward Watch Hazards
        val forwardHazards = safetyManager.getForwardHazards()
        for (hazard in forwardHazards) {
            val pos = hazard.position ?: continue
            val x = tileBox.getPixXFromLatLon(pos.first, pos.second)
            val y = tileBox.getPixYFromLatLon(pos.first, pos.second)
            
            fillPaint.alpha = if (settings.isNightMode) 100 else 150
            canvas.drawCircle(x, y, 20f, fillPaint)
            canvas.drawCircle(x, y, 20f, strokePaint)
        }
    }

    private fun drawSkPolygon(canvas: Canvas, tileBox: RotatedTileBox, coords: List<*>) {
        polygonPath.reset()
        coords.forEachIndexed { index, coord ->
            val lonLat = coord as? List<*> ?: return@forEachIndexed
            val lon = (lonLat[0] as? Number)?.toDouble() ?: 0.0
            val lat = (lonLat[1] as? Number)?.toDouble() ?: 0.0
            
            val x = tileBox.getPixXFromLatLon(lat, lon)
            val y = tileBox.getPixYFromLatLon(lat, lon)
            
            if (index == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, fillPaint)
        canvas.drawPath(polygonPath, strokePaint)
    }
}
