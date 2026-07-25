package net.osmand.plus.plugins.nautical.laylines.ui

import android.content.Context
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import kotlin.math.*

/**
 * Custom map layer for rendering tactical laylines and wind shifts.
 */
class SailingLaylinesMapLayer(context: Context) : OsmandMapLayer(context) {

    private var uiState: LaylineUiState? = null

    private val portPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val stbdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val windShiftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dashedEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    private val portPath = Path()
    private val stbdPath = Path()
    private val windShiftRect = RectF()

    // Colors
    private val colorFetchable = Color.parseColor("#4CAF50") // Green
    private val colorTackRequired = Color.parseColor("#F44336") // Red
    private val colorWindShift = Color.parseColor("#8000BCD4") // Semi-transparent Cyan

    fun updateState(state: LaylineUiState) {
        this.uiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val state = uiState ?: return
        val target = state.targetWaypoint ?: return
        
        val app = context.applicationContext as? OsmandApplication ?: return
        if (app.settings.NAUTICAL_SHOW_LAYLINES.get() != true) return

        val boatLat = state.boatLat ?: return
        val boatLon = state.boatLon ?: return

        val isNight = NauticalPlugin.isNightVision(app)
        setupPaints(state.isFetchable, isNight)

        // 1. Render Port Tack Layline: Boat -> PortIntersection -> Target
        state.portTackPoint?.let { ptp ->
            drawLaylinePath(canvas, tileBox, boatLat, boatLon, ptp.latitude, ptp.longitude, target.latitude, target.longitude, portPath, portPaint)
        }

        // 2. Render Starboard Tack Layline: Boat -> StbdIntersection -> Target
        state.starboardTackPoint?.let { stp ->
            drawLaylinePath(canvas, tileBox, boatLat, boatLon, stp.latitude, stp.longitude, target.latitude, target.longitude, stbdPath, stbdPaint)
        }

        // 3. Render Wind Shifts
        if (app.settings.NAUTICAL_SHOW_WIND_SHIFTS.get()) {
            drawWindShifts(canvas, tileBox, boatLat, boatLon)
        }
    }

    private fun setupPaints(isFetchable: Boolean, isNight: Boolean) {
        val baseColor = when {
            isNight -> Color.RED
            isFetchable -> colorFetchable
            else -> colorTackRequired
        }
        val pathEffect = if (isFetchable) null else dashedEffect
        
        portPaint.color = baseColor
        portPaint.pathEffect = pathEffect
        stbdPaint.color = baseColor
        stbdPaint.pathEffect = pathEffect

        windShiftPaint.color = if (isNight) Color.RED else colorWindShift
        if (isNight) windShiftPaint.alpha = 60
    }

    private fun drawLaylinePath(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        boatLat: Double, boatLon: Double,
        interLat: Double, interLon: Double,
        targetLat: Double, targetLon: Double,
        path: Path,
        paint: Paint
    ) {
        path.rewind()
        
        val startX = tileBox.getPixXFromLatLon(boatLat, boatLon)
        val startY = tileBox.getPixYFromLatLon(boatLat, boatLon)
        val interX = tileBox.getPixXFromLatLon(interLat, interLon)
        val interY = tileBox.getPixYFromLatLon(interLat, interLon)
        val targetX = tileBox.getPixXFromLatLon(targetLat, targetLon)
        val targetY = tileBox.getPixYFromLatLon(targetLat, targetLon)

        path.moveTo(startX, startY)
        path.lineTo(interX, interY)
        path.lineTo(targetX, targetY)

        canvas.drawPath(path, paint)
    }

    private fun drawWindShifts(canvas: Canvas, tileBox: RotatedTileBox, boatLat: Double, boatLon: Double) {
        val engine = NauticalPlugin.engine ?: return
        val history = engine.getWindDirectionHistory()
        if (history.isEmpty()) return

        val centerX = tileBox.getPixXFromLatLon(boatLat, boatLon)
        val centerY = tileBox.getPixYFromLatLon(boatLat, boatLon)
        val radius = 300f // pixels

        windShiftRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        // Signal K history is in Radians
        val sortedAngles = history.map { 
            val deg = Math.toDegrees(it.first)
            (deg % 360.0 + 360.0) % 360.0 
        }.sorted()
        
        var maxGap = 0.0
        var startOfMaxGap = sortedAngles.last()
        
        for (i in sortedAngles.indices) {
            val a1 = sortedAngles[i]
            val a2 = if (i + 1 < sortedAngles.size) sortedAngles[i + 1] else sortedAngles[0] + 360.0
            val gap = a2 - a1
            if (gap > maxGap) {
                maxGap = gap
                startOfMaxGap = a1
            }
        }

        val sweepAngle = (360.0 - maxGap).coerceAtLeast(1.0)
        // Adjust for Android canvas startAngle (0 at East, clockwise) vs Nautical (0 at North, clockwise)
        // Also subtract map rotation
        val startAngle = (startOfMaxGap + maxGap) - 90.0 - tileBox.rotate
        
        canvas.drawArc(windShiftRect, startAngle.toFloat(), sweepAngle.toFloat(), true, windShiftPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
