package net.osmand.plus.plugins.nautical.radar.ui

import androidx.core.content.ContextCompat
import net.osmand.plus.R
import android.content.Context
import android.graphics.*
import android.graphics.PointF
import android.view.MotionEvent
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.OsmandMapTileView

class RadarMapLayer(
    private val context: Context
) : OsmandMapLayer(context) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.nautical_race_cyan_dim_80)
    }

    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = ContextCompat.getColor(context, R.color.nautical_race_cyan_dim_40)
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nautical_day_chip_bg)
    }

    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = ContextCompat.getColor(context, R.color.nautical_race_cyan)
    }

    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = ContextCompat.getColor(context, R.color.nautical_map_text)
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

        ringPaint.color = if (isNight) ContextCompat.getColor(context, R.color.nautical_night_vision_red_dim_60) else ContextCompat.getColor(context, R.color.nautical_race_cyan_dim_60)
        spokePaint.color = if (isNight) ContextCompat.getColor(context, R.color.nautical_night_vision_red_dim_30) else ContextCompat.getColor(context, R.color.nautical_race_cyan_dim_30)
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

        chipBgPaint.color = if (isNight) ContextCompat.getColor(context, R.color.nautical_night_vision_bg) else ContextCompat.getColor(context, R.color.nautical_day_chip_bg)
        chipStrokePaint.color = if (isNight) ContextCompat.getColor(context, R.color.nautical_night_vision_primary) else ContextCompat.getColor(context, R.color.nautical_race_cyan)
        chipTextPaint.color = if (isNight) ContextCompat.getColor(context, R.color.nautical_night_vision_data) else ContextCompat.getColor(context, R.color.nautical_map_text)
        chipTextPaint.textSize = 12f * density

        canvas.drawRoundRect(chipRect, 8f * density, 8f * density, chipBgPaint)
        canvas.drawRoundRect(chipRect, 8f * density, 8f * density, chipStrokePaint)

        val autoGain = app.settings.NAUTICAL_RADAR_AUTO_GAIN.get()
        val label = "RADAR: ${if (autoGain) "AUTO" else "$gain%"} | SEA: $seaClutter%"
        canvas.drawText(label, chipRect.centerX(), chipRect.centerY() + (chipTextPaint.textSize * 0.35f), chipTextPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        if (chipRect.contains(point.x, point.y)) {
            val mapActivity = context as? MapActivity
            if (mapActivity != null && !mapActivity.isFinishing && !mapActivity.isDestroyed) {
                RadarControlBottomSheet.show(mapActivity.supportFragmentManager)
                return true
            }
        }
        return false
    }
}
