package net.osmand.plus.plugins.nautical.map.layers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.toColorInt
import net.osmand.data.RotatedTileBox
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlinx.coroutines.*

class WeatherRoutingMapLayer(context: Context) : OsmandMapLayer(context) {

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val isochronePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4000BCD4".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private data class RenderCache(
        val result: OptimalRouteResult? = null,
        val hazardousSegments: Set<Int> = emptySet(),
        val isochroneRadii: List<Float> = listOf(100f, 250f, 400f, 600f)
    )

    @Volatile
    private var renderCache = RenderCache()
    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    private val routePath = Path()
    private val hazardousPath = Path()

    var optimalRouteResult: OptimalRouteResult? = null
        set(value) {
            field = value
            needsUpdate = true
        }

    private var safetyCorridorChecker: SafetyCorridorChecker? = null
    private var needsUpdate = true

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val cache = renderCache
        val result = cache.result ?: optimalRouteResult ?: return
        
        if (needsUpdate || cache.result != optimalRouteResult) {
            triggerCacheUpdate(result)
        }

        val pathPoints = result.path
        if (pathPoints.isEmpty()) return

        // 1. Draw Isochrone Rings (Project center LatLon on every frame)
        val start = pathPoints.first()
        val startX = tileBox.getPixXFromLatLon(start.latitude, start.longitude)
        val startY = tileBox.getPixYFromLatLon(start.latitude, start.longitude)
        for (radius in cache.isochroneRadii) {
            canvas.drawCircle(startX, startY, radius, isochronePaint)
        }

        // 2. Build and draw Paths dynamically to avoid pixel cache thrashing
        routePath.rewind()
        hazardousPath.rewind()
        
        for (i in 0 until (pathPoints.size - 1)) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]
            val x1 = tileBox.getPixXFromLatLon(p1.latitude, p1.longitude)
            val y1 = tileBox.getPixYFromLatLon(p1.latitude, p1.longitude)
            val x2 = tileBox.getPixXFromLatLon(p2.latitude, p2.longitude)
            val y2 = tileBox.getPixYFromLatLon(p2.latitude, p2.longitude)

            val targetPath = if (cache.hazardousSegments.contains(i)) hazardousPath else routePath
            if (targetPath.isEmpty) targetPath.moveTo(x1, y1) else targetPath.lineTo(x1, y1)
            targetPath.lineTo(x2, y2)
        }

        // Draw optimal route
        routePaint.color = "#4CAF50".toColorInt()
        routePaint.strokeWidth = 8f
        canvas.drawPath(routePath, routePaint)

        // Draw hazardous segments
        if (!hazardousPath.isEmpty) {
            routePaint.color = Color.RED
            routePaint.strokeWidth = 12f
            canvas.drawPath(hazardousPath, routePaint)
        }
    }

    private fun triggerCacheUpdate(result: OptimalRouteResult) {
        needsUpdate = false
        updateJob?.cancel()
        updateJob = layerScope.launch {
            val app = context.applicationContext as net.osmand.plus.OsmandApplication
            val sailingPlugin = net.osmand.plus.plugins.PluginsHelper.getPlugin(NauticalPlugin::class.java)
            val indexManager = sailingPlugin?.s57SpatialIndex

            val newCache = withContext(Dispatchers.Default) {
                if (safetyCorridorChecker == null && indexManager != null && sailingPlugin.safetyManager != null) {
                    safetyCorridorChecker = SafetyCorridorChecker(
                        indexManager,
                        sailingPlugin.safetyManager!!
                    )
                }

                val hazardousSegments = safetyCorridorChecker?.checkCorridor(result.path)?.map { it.segmentIndex }?.toSet() ?: emptySet()
                RenderCache(result, hazardousSegments)
            }
            renderCache = newCache
            app.osmandMap?.refreshMap()
        }
    }

    override fun destroyLayer() {
        super.destroyLayer()
        layerScope.cancel()
    }
}
