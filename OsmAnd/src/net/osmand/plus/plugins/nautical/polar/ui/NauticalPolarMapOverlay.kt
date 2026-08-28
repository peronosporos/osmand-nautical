package net.osmand.plus.plugins.nautical.polar.ui

import android.content.Context
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.OsmandMapLayer
import kotlin.math.*

class NauticalPolarMapOverlay(
    private val context: Context
) : OsmandMapLayer(), OsmandMapLayer.DrawSettings {

    private val polarLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF00E5FF.toInt()
    }

    private val polarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1800E5FF.toInt()
    }

    private val noGoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x20FF5252.toInt()
    }

    private val vmgTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        color = 0xFF00E676.toInt()
    }

    private val stwVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val targetMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFD600.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val polarPath = Path()
    private val noGoPath = Path()

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication ?: return
        val isNight = NauticalPlugin.isNightVision(app)
        val marineState = NauticalPlugin.engine?.marineStateFlow?.value ?: return

        val loc = app.locationProvider.lastKnownLocation ?: return
        val cx = tileBox.getPixXFromLatLon(loc.latitude, loc.longitude)
        val cy = tileBox.getPixYFromLatLon(loc.latitude, loc.longitude)

        val twsMs = marineState.windSpeedTrue ?: marineState.windSpeedApparent ?: return
        if (twsMs < 1.0) return // Skip if calm

        val windDirRad = marineState.windDirectionTrue ?: marineState.windDirectionApparent?.let {
            val hdg = marineState.headingTrue ?: 0.0
            (it + hdg) % (2.0 * Math.PI)
        } ?: 0.0

        val polarDiagram = NauticalPlugin.getInstance()?.polarDiagram ?: return

        // Night vision theme adjustments
        if (isNight) {
            polarLinePaint.color = 0xFFFF1744.toInt()
            polarFillPaint.color = 0x15FF1744.toInt()
            noGoPaint.color = 0x25B71C1C.toInt()
            vmgTargetPaint.color = 0x80FF1744.toInt()
            targetMarkerPaint.color = 0xFFFF8A80.toInt()
            textPaint.color = 0xFFFF8A80.toInt()
        } else {
            polarLinePaint.color = 0xFF00E5FF.toInt()
            polarFillPaint.color = 0x1800E5FF.toInt()
            noGoPaint.color = 0x20FF5252.toInt()
            vmgTargetPaint.color = 0xFF00E676.toInt()
            targetMarkerPaint.color = 0xFFFFD600.toInt()
            textPaint.color = Color.WHITE
        }

        val pxPerMs = 12f * (tileBox.density) // Scale factor for boat speed in m/s

        // 1. Build Polar Curve Path
        polarPath.rewind()
        noGoPath.rewind()

        val optimalUpwind = polarDiagram.getOptimalUpwindVmg(twsMs)
        val optimalDownwind = polarDiagram.getOptimalDownwindVmg(twsMs)
        val upwindTwaDeg = optimalUpwind?.targetTwaDeg ?: 42.0
        val downwindTwaDeg = optimalDownwind?.targetTwaDeg ?: 145.0

        // Build No-Go Zone cone
        val upwindTwaRad = Math.toRadians(upwindTwaDeg)
        val maxNoGoR = (twsMs * 0.6 * pxPerMs).toFloat().coerceAtLeast(40f)

        val portNoGoAngle = windDirRad - upwindTwaRad
        val stbdNoGoAngle = windDirRad + upwindTwaRad

        noGoPath.moveTo(cx, cy)
        noGoPath.lineTo(cx + (maxNoGoR * sin(portNoGoAngle)).toFloat(), cy - (maxNoGoR * cos(portNoGoAngle)).toFloat())
        noGoPath.lineTo(cx + (maxNoGoR * sin(stbdNoGoAngle)).toFloat(), cy - (maxNoGoR * cos(stbdNoGoAngle)).toFloat())
        noGoPath.close()
        canvas.drawPath(noGoPath, noGoPaint)

        // Plot 360-degree polar curve lobes
        var first = true
        for (deg in 0..360 step 5) {
            val twaDeg = if (deg <= 180) deg.toDouble() else (360 - deg).toDouble()
            val speedMs = polarDiagram.getTargetSpeedDeg(twsMs, twaDeg)
            val r = (speedMs * pxPerMs).toFloat()

            val angleRad = windDirRad + Math.toRadians(deg.toDouble())
            val px = cx + (r * sin(angleRad)).toFloat()
            val py = cy - (r * cos(angleRad)).toFloat()

            if (first) {
                polarPath.moveTo(px, py)
                first = false
            } else {
                polarPath.lineTo(px, py)
            }
        }
        polarPath.close()

        canvas.drawPath(polarPath, polarFillPaint)
        canvas.drawPath(polarPath, polarLinePaint)

        // 2. Draw Optimum VMG Rays
        val upwindSpeedMs = optimalUpwind?.targetSpeedMs ?: 3.0
        val upwindR = (upwindSpeedMs * pxPerMs).toFloat()
        val downwindSpeedMs = optimalDownwind?.targetSpeedMs ?: 4.0
        val downwindR = (downwindSpeedMs * pxPerMs).toFloat()

        // Upwind Port & Starboard
        canvas.drawLine(cx, cy, cx + (upwindR * sin(windDirRad - upwindTwaRad)).toFloat(), cy - (upwindR * cos(windDirRad - upwindTwaRad)).toFloat(), vmgTargetPaint)
        canvas.drawLine(cx, cy, cx + (upwindR * sin(windDirRad + upwindTwaRad)).toFloat(), cy - (upwindR * cos(windDirRad + upwindTwaRad)).toFloat(), vmgTargetPaint)

        // Downwind Port & Starboard
        val downwindTwaRad = Math.toRadians(downwindTwaDeg)
        canvas.drawLine(cx, cy, cx + (downwindR * sin(windDirRad - downwindTwaRad)).toFloat(), cy - (downwindR * cos(windDirRad - downwindTwaRad)).toFloat(), vmgTargetPaint)
        canvas.drawLine(cx, cy, cx + (downwindR * sin(windDirRad + downwindTwaRad)).toFloat(), cy - (downwindR * cos(windDirRad + downwindTwaRad)).toFloat(), vmgTargetPaint)

        // 3. Live STW vector vs Target Polar Speed
        val currentStwMs = marineState.speedThroughWater ?: marineState.speedOverGround ?: 0.0
        val currentTwaRad = marineState.trueWindAngle ?: marineState.windDirectionApparent ?: 0.0
        val targetSpeedMs = polarDiagram.getTargetSpeedRad(twsMs, currentTwaRad)

        val boatHdgRad = marineState.headingTrue ?: (windDirRad - currentTwaRad)

        val targetR = (targetSpeedMs * pxPerMs).toFloat()
        val actualR = (currentStwMs * pxPerMs).toFloat()

        val perfRatio = if (targetSpeedMs > 0.1) currentStwMs / targetSpeedMs else 1.0

        stwVectorPaint.color = when {
            isNight -> 0xFFFF1744.toInt()
            perfRatio >= 0.95 -> 0xFF00E676.toInt()
            perfRatio >= 0.80 -> 0xFFFFD600.toInt()
            else -> 0xFFFF1744.toInt()
        }

        // Draw actual boat velocity vector
        val actualX = cx + (actualR * sin(boatHdgRad)).toFloat()
        val actualY = cy - (actualR * cos(boatHdgRad)).toFloat()
        canvas.drawLine(cx, cy, actualX, actualY, stwVectorPaint)

        // Draw target speed ring marker along heading
        val targetX = cx + (targetR * sin(boatHdgRad)).toFloat()
        val targetY = cy - (targetR * cos(boatHdgRad)).toFloat()
        canvas.drawCircle(targetX, targetY, 5f * tileBox.density, targetMarkerPaint)
    }
}
