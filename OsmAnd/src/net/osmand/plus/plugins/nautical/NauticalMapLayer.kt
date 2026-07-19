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
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlin.math.*

class NauticalMapLayer(context: Context) : OsmandMapLayer(context) {

    private var lastKnownTileBox: RotatedTileBox? = null
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val lastPressPoint = PointF()
    private val waypointIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_action_waypoint)

    private val projectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(30f, 15f), 0f)
        alpha = 180
    }

    private val laylinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    private val windShiftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        alpha = 60
        style = Paint.Style.FILL
    }

    private val cogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val trajectoryPath = Path()
    private var lastTrajectorySize = 0
    private var lastDrawTileBox: RotatedTileBox? = null

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: OsmandMapLayer.DrawSettings) {
        this.lastKnownTileBox = tileBox

        val engine = NauticalPlugin.engine ?: return
        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val osmandSettings = app.settings

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            trailPaint.color = Color.RED
            projectionPaint.color = Color.RED
            laylinePaint.color = Color.RED
            windShiftPaint.color = Color.RED
            windShiftPaint.alpha = 60
            cogPaint.color = Color.RED
            currentPaint.color = Color.RED
        } else {
            trailPaint.color = Color.MAGENTA
            projectionPaint.color = Color.WHITE
            laylinePaint.color = Color.YELLOW
            windShiftPaint.color = Color.CYAN
            windShiftPaint.alpha = 60
            cogPaint.color = Color.GREEN
            currentPaint.color = Color.BLUE
        }

        if (osmandSettings.NAUTICAL_SHOW_TRAJECTORY.get()) {
            val history = engine.getTrajectory()
            if (history.size >= 2) {
                val tb = tileBox
                val lastTb = lastDrawTileBox
                val tileBoxChanged = lastTb == null || 
                    lastTb.getLatFromPixel(0f, 0f) != tb.getLatFromPixel(0f, 0f) ||
                    lastTb.getLonFromPixel(0f, 0f) != tb.getLonFromPixel(0f, 0f)
                
                if (history.size != lastTrajectorySize || tileBoxChanged) {
                    trajectoryPath.reset()
                    var first = true
                    for (point in history) {
                        val x = tileBox.getPixXFromLatLon(point.first, point.second)
                        val y = tileBox.getPixYFromLatLon(point.first, point.second)
                        if (first) {
                            trajectoryPath.moveTo(x, y)
                            first = false
                        } else {
                            trajectoryPath.lineTo(x, y)
                        }
                    }
                    lastTrajectorySize = history.size
                    lastDrawTileBox = tileBox
                }
                trailPaint.alpha = 200
                canvas.drawPath(trajectoryPath, trailPaint)
            }
        }

        if (engine.isFollowingRoute) {
            engine.getNextWaypoint()?.let { nextPoint ->
                val x = tileBox.getPixXFromLatLon(nextPoint.first, nextPoint.second)
                val y = tileBox.getPixYFromLatLon(nextPoint.first, nextPoint.second)

                waypointIcon?.let {
                    val iconSize = 40
                    it.setBounds((x - iconSize).toInt(), (y - iconSize).toInt(), (x + iconSize).toInt(), (y + iconSize).toInt())
                    it.draw(canvas)
                } ?: run {
                    // Fallback to circle if icon is missing
                    canvas.drawCircle(x, y, 20f, Paint().apply { color = Color.RED })
                }
            }
        }

        if (osmandSettings.NAUTICAL_SHOW_LAYLINES.get()) {
            drawLaylines(canvas, tileBox, engine.getCurrentState())
        }
        if (osmandSettings.NAUTICAL_SHOW_WIND_SHIFTS.get()) {
            drawWindShifts(canvas, tileBox, engine)
        }

        drawVesselProjections(canvas, tileBox, engine, osmandSettings)
    }

    private fun drawVesselProjections(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine,
        settings: net.osmand.plus.settings.backend.OsmandSettings
    ) {
        val state = engine.getCurrentState() ?: return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val lookAheadMin = settings.NAUTICAL_LOOK_AHEAD_TIME.get()
        val lookAheadSec = lookAheadMin * 60.0

        val startX = tileBox.getPixXFromLatLon(lat, lon)
        val startY = tileBox.getPixYFromLatLon(lat, lon)

        // 1. Heading Line
        if (settings.NAUTICAL_SHOW_HEADING_LINE.get()) {
            val hdg = state.headingTrue
            val stw = state.speedThroughWater
            if (hdg != null && stw != null) {
                val dist = stw * lookAheadSec
                val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(lat, lon, dist, Math.toDegrees(hdg))
                val endX = tileBox.getPixXFromLatLon(endPoint.latitude, endPoint.longitude)
                val endY = tileBox.getPixYFromLatLon(endPoint.latitude, endPoint.longitude)
                canvas.drawLine(startX, startY, endX, endY, projectionPaint)
            }
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

    private fun drawWindShifts(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine) {
        val state = engine.getCurrentState() ?: return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val history = engine.getWindDirectionHistory()
        if (history.isEmpty()) return

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)
        val radius = 300f

        val rect = android.graphics.RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        // Find smallest arc covering all points
        val sortedAngles = history.map { 
            val deg = Math.toDegrees(it)
            (deg % 360.0 + 360.0) % 360.0 
        }.sorted()
        
        var maxGap = 0.0
        var startOfMaxGap = sortedAngles.last()
        
        for (i in 0 until sortedAngles.size) {
            val a1 = sortedAngles[i]
            val a2 = if (i + 1 < sortedAngles.size) sortedAngles[i + 1] else sortedAngles[0] + 360.0
            val gap = a2 - a1
            if (gap > maxGap) {
                maxGap = gap
                startOfMaxGap = a1
            }
        }

        val sweepAngle = (360.0 - maxGap).coerceAtLeast(1.0)
        val startAngle = (startOfMaxGap + maxGap) - 90.0
        
        canvas.drawArc(rect, startAngle.toFloat(), sweepAngle.toFloat(), true, windShiftPaint)
    }

    private fun drawLaylines(canvas: Canvas, tileBox: RotatedTileBox, state: net.osmand.plus.plugins.nautical.engine.MarineState?) {
        val lat = state?.latitude ?: return
        val lon = state.longitude ?: return
        val windDir = state.windDirectionTrue ?: return

        val app = context.applicationContext as net.osmand.plus.OsmandApplication
        val tackAngleDeg = app.settings.NAUTICAL_LAYLINES_TACK_ANGLE.get().toDouble()
        val tackAngle = Math.toRadians(tackAngleDeg)

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)

        val lineLength = 2000f // Screen pixels

        // Port Layline
        val portAngle = windDir + (PI + tackAngle)
        val px = centerX + (lineLength * sin(portAngle).toFloat())
        val py = centerY - (lineLength * cos(portAngle).toFloat())
        canvas.drawLine(centerX, centerY, px, py, laylinePaint)

        // Starboard Layline
        val stbdAngle = windDir + (PI - tackAngle)
        val sx = centerX + (lineLength * sin(stbdAngle).toFloat())
        val sy = centerY - (lineLength * cos(stbdAngle).toFloat())
        canvas.drawLine(centerX, centerY, sx, sy, laylinePaint)
    }

    fun getTileBox(): RotatedTileBox? = lastKnownTileBox

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        this.lastPressPoint.set(point)
        return false
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean {
        val distance = hypot((point.x - lastPressPoint.x).toDouble(), (point.y - lastPressPoint.y).toDouble()).toFloat()
        if (distance > 20f) return false

        NauticalPlugin.engine?.clearRoute()

        val lat = tileBox.getLatFromPixel(point.x, point.y)
        val lon = tileBox.getLonFromPixel(point.x, point.y)
        NauticalPlugin.sendWaypoint(lat, lon)
        return true
    }
}
