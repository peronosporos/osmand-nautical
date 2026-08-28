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

    private val corridorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val corridorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
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
    private val corridorPath = Path()
    private val chevronPath = Path()
    private val badgeRect = android.graphics.RectF()

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

        val app = context.applicationContext as? net.osmand.plus.OsmandApplication
        val isNight = NauticalPlugin.isNightVision(app)

        // 1. Draw Isochrone Rings (Project center LatLon on every frame)
        isochronePaint.color = if (isNight) 0x40FF1744.toInt() else "#4000BCD4".toColorInt()
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

        // 3. Draw Route Leg Safety Corridor (±50m) & Steering Chevrons
        drawSafetyCorridorAndXte(canvas, tileBox, pathPoints, isNight)

        // Draw optimal route
        routePaint.color = if (isNight) 0xFFFF1744.toInt() else "#4CAF50".toColorInt()
        routePaint.strokeWidth = 8f
        canvas.drawPath(routePath, routePaint)

        // Draw hazardous segments
        if (!hazardousPath.isEmpty) {
            routePaint.color = if (isNight) 0xFFFF5252.toInt() else Color.RED
            routePaint.strokeWidth = 12f
            canvas.drawPath(hazardousPath, routePaint)
        }
    }

    private fun drawSafetyCorridorAndXte(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        pathPoints: List<net.osmand.data.LatLon>,
        isNight: Boolean
    ) {
        if (pathPoints.size < 2) return

        val app = context.applicationContext as? net.osmand.plus.OsmandApplication
        val ownLoc = app?.locationProvider?.lastKnownLocation
        val corridorWidthMeters = 50.0

        if (isNight) {
            corridorFillPaint.color = 0x12FF1744.toInt()
            corridorPaint.color = 0x60FF1744.toInt()
            chevronPaint.color = 0xFFFF5252.toInt()
            badgeBgPaint.color = 0xEE120000.toInt()
            badgeStrokePaint.color = 0xFFFF1744.toInt()
            badgeTextPaint.color = 0xFFFF8A80.toInt()
        } else {
            corridorFillPaint.color = 0x144CAF50.toInt()
            corridorPaint.color = 0x604CAF50.toInt()
            chevronPaint.color = 0xFF00E5FF.toInt()
            badgeBgPaint.color = 0xDD212121.toInt()
            badgeStrokePaint.color = 0xFF00E5FF.toInt()
            badgeTextPaint.color = Color.WHITE
        }

        // Draw corridor polygon for the first / active leg
        val p1 = pathPoints[0]
        val p2 = pathPoints[1]
        val bearing = net.osmand.util.MapUtils.calculateAngle(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

        val p1Left = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(p1.latitude, p1.longitude, corridorWidthMeters, (bearing - 90.0 + 360.0) % 360.0)
        val p1Right = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(p1.latitude, p1.longitude, corridorWidthMeters, (bearing + 90.0) % 360.0)
        val p2Right = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(p2.latitude, p2.longitude, corridorWidthMeters, (bearing + 90.0) % 360.0)
        val p2Left = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(p2.latitude, p2.longitude, corridorWidthMeters, (bearing - 90.0 + 360.0) % 360.0)

        corridorPath.rewind()
        corridorPath.moveTo(tileBox.getPixXFromLatLon(p1Left.latitude, p1Left.longitude), tileBox.getPixYFromLatLon(p1Left.latitude, p1Left.longitude))
        corridorPath.lineTo(tileBox.getPixXFromLatLon(p2Left.latitude, p2Left.longitude), tileBox.getPixYFromLatLon(p2Left.latitude, p2Left.longitude))
        corridorPath.lineTo(tileBox.getPixXFromLatLon(p2Right.latitude, p2Right.longitude), tileBox.getPixYFromLatLon(p2Right.latitude, p2Right.longitude))
        corridorPath.lineTo(tileBox.getPixXFromLatLon(p1Right.latitude, p1Right.longitude), tileBox.getPixYFromLatLon(p1Right.latitude, p1Right.longitude))
        corridorPath.close()

        canvas.drawPath(corridorPath, corridorFillPaint)
        canvas.drawPath(corridorPath, corridorPaint)

        // Compute XTE if own vessel position is known
        if (ownLoc != null) {
            val distToP1 = net.osmand.util.MapUtils.getDistance(ownLoc.latitude, ownLoc.longitude, p1.latitude, p1.longitude)
            val bearingToVessel = net.osmand.util.MapUtils.calculateAngle(p1.latitude, p1.longitude, ownLoc.latitude, ownLoc.longitude)
            val angleDiff = Math.toRadians((bearingToVessel - bearing + 360.0) % 360.0)
            val xteMeters = distToP1 * kotlin.math.sin(angleDiff)

            val vx = tileBox.getPixXFromLatLon(ownLoc.latitude, ownLoc.longitude)
            val vy = tileBox.getPixYFromLatLon(ownLoc.latitude, ownLoc.longitude)

            // Nearest point on track
            val alongTrackDist = distToP1 * kotlin.math.cos(angleDiff)
            val targetPt = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(p1.latitude, p1.longitude, alongTrackDist.coerceAtLeast(0.0), bearing)
            val tx = tileBox.getPixXFromLatLon(targetPt.latitude, targetPt.longitude)
            val ty = tileBox.getPixYFromLatLon(targetPt.latitude, targetPt.longitude)

            // If |XTE| > 20m, render animated steering recovery chevrons pointing back to rhumb line
            if (kotlin.math.abs(xteMeters) > 20.0) {
                val dx = tx - vx
                val dy = ty - vy
                val distPx = kotlin.math.hypot(dx, dy)
                if (distPx > 10f) {
                    val nx = dx / distPx
                    val ny = dy / distPx
                    val perpX = -ny
                    val perpY = nx

                    val animOffset = (System.currentTimeMillis() % 1200L) / 1200f
                    val numChevrons = (distPx / 32f).toInt().coerceIn(1, 6)

                    chevronPath.rewind()
                    for (c in 0 until numChevrons) {
                        val fraction = ((c.toFloat() / numChevrons) + animOffset) % 1.0f
                        val cx = vx + (dx * fraction)
                        val cy = vy + (dy * fraction)
                        val armLen = 8f

                        chevronPath.moveTo(cx - (nx * armLen) + (perpX * armLen), cy - (ny * armLen) + (perpY * armLen))
                        chevronPath.lineTo(cx, cy)
                        chevronPath.lineTo(cx - (nx * armLen) - (perpX * armLen), cy - (ny * armLen) - (perpY * armLen))
                    }
                    canvas.drawPath(chevronPath, chevronPaint)
                }

                // Draw XTE readout badge
                val sideStr = if (xteMeters >= 0) "Stbd" else "Port"
                val xteLabel = "XTE: ${kotlin.math.abs(xteMeters).toInt()}m $sideStr"
                val textW = badgeTextPaint.measureText(xteLabel)
                val textH = badgeTextPaint.textSize
                val bW = textW + 16f
                val bH = textH + 8f
                val bX = vx + 16f + (bW / 2f)
                val bY = vy - 12f

                badgeRect.set(bX - bW / 2f, bY - bH / 2f, bX + bW / 2f, bY + bH / 2f)
                canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBgPaint)
                canvas.drawRoundRect(badgeRect, 6f, 6f, badgeStrokePaint)
                canvas.drawText(xteLabel, bX, bY + (textH * 0.35f), badgeTextPaint)
            }
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
