package net.osmand.plus.plugins.nautical.hazard.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import net.osmand.data.RotatedTileBox
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer

class DynamicHazardLayer(private val activity: MapActivity) : OsmandMapLayer(activity) {

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

    private val dashedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFF1744.toInt()
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }

    private val pulsingCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFF1744.toInt()
    }

    private val polygonPath = Path()

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val safetyManager = plugin.safetyManager ?: return
        val isNight = NauticalPlugin.isNightVision(activity.app)
        
        // 1. Draw SignalK Regions (Restricted Areas / Waterway Closures)
        val regions = safetyManager.getSignalKRegions()
        if (isNight) {
            fillPaint.color = 0x20B71C1C.toInt()
            fillPaint.alpha = 32
        } else {
            fillPaint.color = Color.RED
            fillPaint.alpha = 60
            strokePaint.color = Color.RED
            strokePaint.strokeWidth = 3f
        }

        for (region in regions) {
            val geometry = region.feature.geometry
            if (geometry["type"] == "Polygon") {
                val coords = (geometry["coordinates"] as? List<*>)?.get(0) as? List<*>
                if (coords != null) {
                    drawSkPolygon(canvas, tileBox, coords, isNight)
                }
            }
        }

        // 2. Draw Forward Watch Hazards
        val forwardHazards = safetyManager.getForwardHazards()
        val pulsePhase = (System.currentTimeMillis() % 1200L) / 1200f
        val pulseRadius = 20f + (pulsePhase * 16f)
        val pulseAlpha = ((1f - pulsePhase) * 200).toInt().coerceIn(0, 255)

        for (hazard in forwardHazards) {
            val pos = hazard.position ?: continue
            val x = tileBox.getPixXFromLatLon(pos.first, pos.second)
            val y = tileBox.getPixYFromLatLon(pos.first, pos.second)
            
            if (isNight) {
                fillPaint.color = 0xFFFF1744.toInt()
                fillPaint.alpha = 100
                strokePaint.color = 0xFFFF1744.toInt()
                strokePaint.strokeWidth = 3f

                pulsingCirclePaint.color = 0xFFFF1744.toInt()
                pulsingCirclePaint.alpha = pulseAlpha

                canvas.drawCircle(x, y, 20f, fillPaint)
                canvas.drawCircle(x, y, 20f, strokePaint)
                canvas.drawCircle(x, y, pulseRadius, pulsingCirclePaint)
            } else {
                fillPaint.color = Color.RED
                fillPaint.alpha = if (settings.isNightMode) 100 else 150
                strokePaint.color = Color.RED
                strokePaint.strokeWidth = 3f

                canvas.drawCircle(x, y, 20f, fillPaint)
                canvas.drawCircle(x, y, 20f, strokePaint)
            }
        }
    }

    private fun drawSkPolygon(canvas: Canvas, tileBox: RotatedTileBox, coords: List<*>, isNight: Boolean) {
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
        if (isNight) {
            canvas.drawPath(polygonPath, dashedStrokePaint)
        } else {
            canvas.drawPath(polygonPath, strokePaint)
        }
    }
}
