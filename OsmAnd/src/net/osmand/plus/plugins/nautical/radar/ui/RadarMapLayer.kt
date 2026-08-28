package net.osmand.plus.plugins.nautical.radar.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.OsmandMapLayer
import net.osmand.plus.views.OsmandMapTileView

class RadarMapLayer(
    private val context: Context
) : OsmandMapLayer(), OsmandMapLayer.DrawSettings {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x8000E5FF.toInt()
    }

    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x4000E5FF.toInt()
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD212121.toInt()
    }

    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF00E5FF.toInt()
    }

    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val chipRect = RectF()
    private val sweepSweepShaderMatrix = Matrix()

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication ?: return
        val isNight = NauticalPlugin.isNightVision(app)
        val opacity = app.settings.NAUTICAL_RADAR_OPACITY.get().coerceIn(20, 100) / 100f
        val gain = app.settings.NAUTICAL_RADAR_GAIN.get()
        val seaClutter = app.settings.NAUTICAL_RADAR_SEA_CLUTTER.get()

        val loc = app.locationProvider.lastKnownLocation ?: return
        val cx = tileBox.getPixXFromLatLon(loc.latitude, loc.longitude)
        val cy = tileBox.getPixYFromLatLon(loc.latitude, loc.longitude)

        // Range rings (0.5 NM, 1.0 NM, 2.0 NM in pixels)
        val nmToMeters = 1852.0
        val ringsNm = floatArrayOf(0.5f, 1.0f, 2.0f)

        ringPaint.color = if (isNight) 0x60FF1744.toInt() else 0x6000E5FF.toInt()
        spokePaint.color = if (isNight) 0x30FF1744.toInt() else 0x3000E5FF.toInt()
        ringPaint.alpha = (ringPaint.alpha * opacity).toInt()
        spokePaint.alpha = (spokePaint.alpha * opacity).toInt()

        for (nm in ringsNm) {
            val radiusPx = (tileBox.getPixDensity() * (nm * nmToMeters)).toFloat().coerceAtLeast(10f)
            canvas.drawCircle(cx, cy, radiusPx, ringPaint)
        }

        // Draw crosshair spokes
        val maxRadius = (tileBox.getPixDensity() * (2.0 * nmToMeters)).toFloat().coerceAtLeast(20f)
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, spokePaint)
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, spokePaint)

        // Draw HUD Control Chip (top-left or clickable badge)
        val density = context.resources.displayMetrics.density
        val chipW = 200f * density
        val chipH = 36f * density
        val chipX = 16f * density
        val chipY = 80f * density

        chipRect.set(chipX, chipY, chipX + chipW, chipY + chipH)

        chipBgPaint.color = if (isNight) 0xEE120000.toInt() else 0xDD212121.toInt()
        chipStrokePaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt()
        chipTextPaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.WHITE
        chipTextPaint.textSize = 12f * density

        canvas.drawRoundRect(chipRect, 8f * density, 8f * density, chipBgPaint)
        canvas.drawRoundRect(chipRect, 8f * density, 8f * density, chipStrokePaint)

        val autoGain = app.settings.NAUTICAL_RADAR_AUTO_GAIN.get()
        val label = "RADAR: ${if (autoGain) "AUTO" else "$gain%"} | SEA: $seaClutter%"
        canvas.drawText(label, chipRect.centerX(), chipRect.centerY() + (chipTextPaint.textSize * 0.35f), chipTextPaint)
    }

    override fun onSingleTap(e: MotionEvent, tileBox: RotatedTileBox): Boolean {
        if (chipRect.contains(e.x, e.y)) {
            val mapActivity = context as? MapActivity
            if (mapActivity != null && !mapActivity.isFinishing && !mapActivity.isDestroyed) {
                RadarControlBottomSheet.show(mapActivity.supportFragmentManager)
                return true
            }
        }
        return false
    }
}
