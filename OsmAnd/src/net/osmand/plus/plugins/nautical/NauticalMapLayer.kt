package net.osmand.plus.plugins.nautical

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import net.osmand.data.RotatedTileBox
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyIssue
import net.osmand.plus.plugins.nautical.plugin.SailingIntegrationPlugin
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.Locale
import kotlin.math.*

class NauticalMapLayer(context: Context) : OsmandMapLayer(context) {

    private var lastKnownTileBox: RotatedTileBox? = null
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val waypointIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_action_waypoint)

    private val projectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(30f, 15f), 0f)
        alpha = 180
    }

    private val cogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    private val targetHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 165, 0) // Orange
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(25f, 15f), 0f)
        alpha = 200
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(30f, 20f), 0f)
        alpha = 140
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        textAlign = Paint.Align.CENTER
        textSize = 60f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val trajectoryPath = Path()
    private val trajectoryHistory = mutableListOf<Pair<Double, Double>>()
    private var lastTrajectoryPoint: Pair<Double, Double>? = null
    private var lastDrawTileBox: RotatedTileBox? = null

    private var cachedSafetyIssues: List<SafetyIssue> = emptyList()
    private var lastCheckedRoute: List<Pair<Double, Double>>? = null
    private var lastCheckedVesselPos: Pair<Double, Double>? = null

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        if (app.settings.APPLICATION_MODE.get() != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }
        this.lastKnownTileBox = tileBox

        val engine = NauticalPlugin.engine ?: return
        val osmandSettings = app.settings

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            trailPaint.color = Color.RED
            projectionPaint.color = Color.RED
            projectionPaint.alpha = 180
            cogPaint.color = Color.RED
            currentPaint.color = Color.RED
            targetHeadingPaint.color = Color.RED
            routePaint.color = Color.RED
        } else {
            trailPaint.color = Color.MAGENTA
            projectionPaint.color = Color.WHITE
            projectionPaint.alpha = 180
            cogPaint.color = Color.GREEN
            currentPaint.color = Color.BLUE
            targetHeadingPaint.color = Color.rgb(255, 165, 0)
            routePaint.color = Color.YELLOW
        }

        if (osmandSettings.NAUTICAL_SHOW_TRAJECTORY.get()) {
            engine.copyTrajectoryTo(trajectoryHistory)
            if (trajectoryHistory.size >= 2) {
                val lastPoint = trajectoryHistory.last()
                val lastTb = lastDrawTileBox
                val tileBoxChanged = (lastTb == null) || (lastTb.zoom != tileBox.zoom) || 
                    (abs(lastTb.rotate - tileBox.rotate) > 0.01f) ||
                    (abs(lastTb.center31X - tileBox.center31X) > 1) ||
                    (abs(lastTb.center31Y - tileBox.center31Y) > 1)
                
                if (lastPoint != lastTrajectoryPoint || tileBoxChanged) {
                    trajectoryPath.reset()
                    var first = true
                    for (point in trajectoryHistory) {
                        val x = tileBox.getPixXFromLatLon(point.first, point.second)
                        val y = tileBox.getPixYFromLatLon(point.first, point.second)
                        if (first) {
                            trajectoryPath.moveTo(x, y)
                            first = false
                        } else {
                            trajectoryPath.lineTo(x, y)
                        }
                    }
                    lastTrajectoryPoint = lastPoint
                    lastDrawTileBox = tileBox
                }
                trailPaint.alpha = 200
                canvas.drawPath(trajectoryPath, trailPaint)
            }
        }

        if (engine.isFollowingRoute) {
            drawNavigationPath(canvas, tileBox, engine)
            engine.getNextWaypoint()?.let { nextPoint ->
                val x = tileBox.getPixXFromLatLon(nextPoint.first, nextPoint.second)
                val y = tileBox.getPixYFromLatLon(nextPoint.first, nextPoint.second)

                waypointIcon?.let {
                    val density = context.resources.displayMetrics.density
                    val iconSize = (20 * density).toInt()
                    it.setBounds(x.toInt() - iconSize, y.toInt() - iconSize, x.toInt() + iconSize, y.toInt() + iconSize)
                    if (isNightVision) {
                        it.setTint(Color.RED)
                    } else {
                        it.setTintList(null)
                    }
                    it.draw(canvas)
                } ?: run {
                    // Fallback to circle if icon is missing
                    canvas.drawCircle(x, y, 20f, Paint().apply { color = Color.RED })
                }
            }
        }

        if (NauticalPlugin.getInstance()?.isConnectionLostAlertActive == true) {
            drawConnectionWarning(canvas)
        }

        drawVesselProjections(canvas, tileBox, engine, osmandSettings)
    }

    private fun drawConnectionWarning(canvas: Canvas) {
        val now = System.currentTimeMillis()
        if ((now / 500) % 2 == 0L) return // Blink 1Hz
        
        val text = context.getString(R.string.nautical_autopilot_data_lost)
        val x = canvas.width / 2f
        val y = 200f // Top area
        
        val bgPaint = Paint().apply {
            color = Color.RED
            alpha = 220
        }
        val textWidth = warningPaint.measureText(text)
        canvas.drawRect(x - textWidth / 2 - 40, y - 80, x + textWidth / 2 + 40, y + 30, bgPaint)
        
        val textPaint = Paint(warningPaint).apply {
            color = Color.WHITE
            textSize = 70f
            isFakeBoldText = true
        }
        canvas.drawText(text, x, y, textPaint)
    }

    private fun drawVesselProjections(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine,
        settings: net.osmand.plus.settings.backend.OsmandSettings,
    ) {
        val state = engine.getCurrentState() ?: return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val lookAheadMin = settings.NAUTICAL_LOOK_AHEAD_TIME.get()
        val lookAheadSec = lookAheadMin * 60.0

        val startX = tileBox.getPixXFromLatLon(lat, lon)
        val startY = tileBox.getPixYFromLatLon(lat, lon)

        // 1. Heading Line
        val hdg = state.headingTrue
        if (settings.NAUTICAL_SHOW_HEADING_LINE.get() && hdg != null) {
            val stw = state.speedThroughWater ?: state.speedOverGround ?: 0.0
            val dist = stw * lookAheadSec
            val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(lat, lon, dist, Math.toDegrees(hdg))
            val endX = tileBox.getPixXFromLatLon(endPoint.latitude, endPoint.longitude)
            val endY = tileBox.getPixYFromLatLon(endPoint.latitude, endPoint.longitude)
            canvas.drawLine(startX, startY, endX, endY, projectionPaint)
        }

        // 2. COG Line
        if (settings.NAUTICAL_SHOW_COG_LINE.get()) {
            val cog = state.courseOverGroundTrue
            val sog = state.speedOverGround
            if (cog != null && sog != null) {
                val dist = sog * lookAheadSec
                val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(lat, lon, dist, Math.toDegrees(cog))
                val endX = tileBox.getPixXFromLatLon(endPoint.latitude, endPoint.longitude)
                val endY = tileBox.getPixYFromLatLon(endPoint.latitude, endPoint.longitude)
                canvas.drawLine(startX, startY, endX, endY, cogPaint)
                drawArrowHead(canvas, startX, startY, endX, endY, cogPaint)
            }
        }

        // 3. Current Vector
        if (settings.NAUTICAL_SHOW_CURRENT_VECTOR.get()) {
            val set = state.setTrue
            val drift = state.drift
            if (set != null && drift != null) {
                val dist = drift * lookAheadSec
                val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(lat, lon, dist, Math.toDegrees(set))
                val endX = tileBox.getPixXFromLatLon(endPoint.latitude, endPoint.longitude)
                val endY = tileBox.getPixYFromLatLon(endPoint.latitude, endPoint.longitude)
                canvas.drawLine(startX, startY, endX, endY, currentPaint)
                drawArrowHead(canvas, startX, startY, endX, endY, currentPaint)
            }
        }

        // 4. Target Heading (Autopilot)
        val targetHdg = state.targetHeading
        if (targetHdg != null && state.autopilotState.lowercase(Locale.US) != "standby") {
            val speed = state.speedThroughWater ?: state.speedOverGround ?: 5.0 // Default 5kn length for visibility if stationary
            val dist = speed * lookAheadSec
            val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(lat, lon, dist, Math.toDegrees(targetHdg))
            val endX = tileBox.getPixXFromLatLon(endPoint.latitude, endPoint.longitude)
            val endY = tileBox.getPixYFromLatLon(endPoint.latitude, endPoint.longitude)
            canvas.drawLine(startX, startY, endX, endY, targetHeadingPaint)
            drawArrowHead(canvas, startX, startY, endX, endY, targetHeadingPaint)
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLength = 30f
        val headAngle = PI / 6

        val p1x = x2 - headLength * cos(angle - headAngle).toFloat()
        val p1y = y2 - headLength * sin(angle - headAngle).toFloat()
        val p2x = x2 - headLength * cos(angle + headAngle).toFloat()
        val p2y = y2 - headLength * sin(angle + headAngle).toFloat()

        canvas.drawLine(x2, y2, p1x, p1y, paint)
        canvas.drawLine(x2, y2, p2x, p2y, paint)
    }

    fun getTileBox(): RotatedTileBox? = lastKnownTileBox

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        return false
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean {
        // Handled via standard Map Context Menu
        return false
    }

    private fun drawNavigationPath(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine) {
        val route = engine.getRoutePoints()
        if (route.size < 2) return

        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val currentState = engine.getCurrentState()
        val vesselPos = if (currentState?.latitude != null && currentState.longitude != null) {
            Pair(currentState.latitude, currentState.longitude)
        } else null

        // Caching logic for safety check
        val routeChanged = route != lastCheckedRoute
        val vesselMoved = vesselPos != null && lastCheckedVesselPos != null && 
            net.osmand.shared.util.KMapUtils.getDistance(vesselPos.first, vesselPos.second, lastCheckedVesselPos!!.first, lastCheckedVesselPos!!.second) > 50.0
        val needsRecheck = routeChanged || (vesselPos != null && (lastCheckedVesselPos == null || vesselMoved))

        if (needsRecheck) {
            val sailingPlugin = net.osmand.plus.plugins.PluginsHelper.getPlugin(SailingIntegrationPlugin::class.java)
            val indexManager = sailingPlugin?.s57IndexManager
            
            val checker = indexManager?.let {
                SafetyCorridorChecker(
                    it,
                    app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble(),
                    app.settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
                )
            }
            val corridorWidth = app.settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()

            val waypoints = route.map { Waypoint(it.first, it.second) }
            cachedSafetyIssues = checker?.checkCorridor(waypoints, corridorWidth) ?: emptyList()
            lastCheckedRoute = route.toList()
            lastCheckedVesselPos = vesselPos
        }

        val hazardousSegments = cachedSafetyIssues.map { it.segmentIndex }.toSet()
        val isNightVision = NauticalPlugin.isNightVision(app)

        for (i in 0 until route.size - 1) {
            val p1 = route[i]
            val p2 = route[i + 1]
            val x1 = tileBox.getPixXFromLatLon(p1.first, p1.second)
            val y1 = tileBox.getPixYFromLatLon(p1.first, p1.second)
            val x2 = tileBox.getPixXFromLatLon(p2.first, p2.second)
            val y2 = tileBox.getPixYFromLatLon(p2.first, p2.second)

            val baseColor = if (isNightVision) Color.RED else Color.YELLOW
            routePaint.color = if (hazardousSegments.contains(i)) Color.RED else baseColor
            
            if (hazardousSegments.contains(i) && !isNightVision) {
                routePaint.strokeWidth = 12f // Thicker for warning
            } else {
                routePaint.strokeWidth = 6f
            }
            
            canvas.drawLine(x1, y1, x2, y2, routePaint)
        }
    }
}
