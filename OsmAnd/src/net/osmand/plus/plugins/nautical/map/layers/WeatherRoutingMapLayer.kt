package net.osmand.plus.plugins.nautical.map.layers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import net.osmand.data.RotatedTileBox
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.plugin.SailingIntegrationPlugin
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.views.layers.base.OsmandMapLayer

class WeatherRoutingMapLayer(context: Context) : OsmandMapLayer(context) {

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val isochronePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000BCD4")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    var optimalRouteResult: OptimalRouteResult? = null
        set(value) {
            field = value
            needsRecheck = true
        }

    private var safetyCorridorChecker: SafetyCorridorChecker? = null
    private var hazardousSegments: Set<Int> = emptySet()
    private var needsRecheck = true

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val result = optimalRouteResult ?: return
        val pathPoints = result.path
        if (pathPoints.size < 2) return

        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val sailingPlugin = net.osmand.plus.plugins.PluginsHelper.getPlugin(SailingIntegrationPlugin::class.java)
        val indexManager = sailingPlugin?.s57SpatialIndex

        if (needsRecheck && indexManager != null) {
            if (safetyCorridorChecker == null) {
                safetyCorridorChecker = SafetyCorridorChecker(
                    indexManager,
                    app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble(),
                    app.settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
                )
            }
            val corridorWidth = app.settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
            val issues = safetyCorridorChecker?.checkCorridor(pathPoints, corridorWidth) ?: emptyList()
            hazardousSegments = issues.map { it.segmentIndex }.toSet()
            needsRecheck = false
        }

        // Draw isochrone expansion rings (simulated concentric bounds)
        val start = pathPoints.first()
        val startX = tileBox.getPixXFromLatLon(start.latitude, start.longitude)
        val startY = tileBox.getPixYFromLatLon(start.latitude, start.longitude)
        for (radiusPx in listOf(100f, 250f, 400f, 600f)) {
            canvas.drawCircle(startX, startY, radiusPx, isochronePaint)
        }

        // Draw optimal route polyline
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            val x1 = tileBox.getPixXFromLatLon(p1.latitude, p1.longitude)
            val y1 = tileBox.getPixYFromLatLon(p1.latitude, p1.longitude)
            val x2 = tileBox.getPixXFromLatLon(p2.latitude, p2.longitude)
            val y2 = tileBox.getPixYFromLatLon(p2.latitude, p2.longitude)

            if (hazardousSegments.contains(i)) {
                routePaint.color = Color.RED
                routePaint.strokeWidth = 12f
            } else {
                routePaint.color = Color.parseColor("#4CAF50") // Green Reaching
                routePaint.strokeWidth = 8f
            }
            canvas.drawLine(x1, y1, x2, y2, routePaint)
        }
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
