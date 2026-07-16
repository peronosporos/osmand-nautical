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

    var projectionHeading: Double? = null
        set(value) {
            field = value
            (context.applicationContext as? net.osmand.plus.OsmandApplication)?.osmandMap?.refreshMap()
        }

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

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        this.lastKnownTileBox = tileBox

        val engine = NauticalPlugin.engine ?: return
        val osmandSettings = (context.applicationContext as net.osmand.plus.OsmandApplication).settings
        val plugin = NauticalPlugin.getInstance()

        if (plugin?.isNightVisionEnabled == true) {
            canvas.drawColor(0x33FF0000, android.graphics.PorterDuff.Mode.MULTIPLY)
        }

        if (osmandSettings.NAUTICAL_SHOW_TRAJECTORY.get()) {
            val history = engine.getTrajectory()
            if (history.size >= 2) {
                val path = Path()
                var first = true
                for (point in history) {
                    val x = tileBox.getPixXFromLatLon(point.first, point.second)
                    val y = tileBox.getPixYFromLatLon(point.first, point.second)
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                trailPaint.alpha = 200
                canvas.drawPath(path, trailPaint)
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

        projectionHeading?.let { heading ->
            drawProjectionLine(canvas, tileBox, engine.getCurrentState(), heading)
        }
    }

    private fun drawProjectionLine(canvas: Canvas, tileBox: RotatedTileBox, state: net.osmand.plus.plugins.nautical.engine.MarineState?, headingDeg: Double) {
        val lat = state?.latitude ?: return
        val lon = state.longitude ?: return
        
        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)
        
        val angleRad = Math.toRadians(headingDeg)
        val lineLength = 3000f // Long line to represent projected path
        
        val px = centerX + (lineLength * sin(angleRad).toFloat())
        val py = centerY - (lineLength * cos(angleRad).toFloat())
        
        canvas.drawLine(centerX, centerY, px, py, projectionPaint)
        
        // Optionally draw an arrow or dot at the end or some markers
        canvas.drawCircle(centerX, centerY, 15f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; alpha = 200 })
    }

    private fun drawWindShifts(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine) {
        val state = engine.getCurrentState() ?: return
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val history = engine.getWindDirectionHistory()
        if (history.isEmpty()) return

        val minWind = history.minOrNull() ?: return
        val maxWind = history.maxOrNull() ?: return

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)
        val radius = 300f

        val rect = android.graphics.RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        // SignalK wind direction is 0 at North, clockwise.
        // Android drawArc: 0 is East, clockwise.
        val startAngle = Math.toDegrees(minWind) - 90.0
        val sweepAngle = Math.toDegrees(maxWind - minWind)
        
        canvas.drawArc(rect, startAngle.toFloat(), sweepAngle.toFloat(), true, windShiftPaint)
    }

    private fun drawLaylines(canvas: Canvas, tileBox: RotatedTileBox, state: net.osmand.plus.plugins.nautical.engine.MarineState?) {
        val lat = state?.latitude ?: return
        val lon = state.longitude ?: return
        val windDir = state.windDirectionTrue ?: return

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)

        // Typically 45 degrees upwind
        val tackAngle = Math.toRadians(45.0)
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
