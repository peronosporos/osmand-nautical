package net.osmand.plus.plugins.nautical.depth.ui

import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.graphics.PointF
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyEvaluator
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.Locale

class DepthProfileMapLayer(
    private val context: Context
) : OsmandMapLayer(context) {

    private var isExpanded = true

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nautical_depth_panel_bg)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.nautical_race_cyan)
    }

    private val seabedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nautical_day_slate_dim_80)
    }

    private val seabedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ContextCompat.getColor(context, R.color.nautical_day_cyan_solid)
    }

    private val dangerCeilingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        color = ContextCompat.getColor(context, R.color.nautical_night_vision_primary)
    }

    private val hazardPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nautical_night_vision_primary)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = ContextCompat.getColor(context, R.color.nautical_map_text)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        color = ContextCompat.getColor(context, R.color.nautical_race_dim)
    }

    private val panelRect = RectF()
    private val seabedPath = Path()

    private var lastProfile: NauticalSafetyEvaluator.ForwardRouteProfile? = null
    private var lastSampleTime = 0L

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as? OsmandApplication ?: return
        val isNight = NauticalPlugin.isNightVision(app)
        val safetyEvaluator = NauticalPlugin.getInstance()?.safetyEvaluator ?: return

        val loc = app.locationProvider.lastKnownLocation ?: return
        val marineState = NauticalPlugin.engine?.marineStateFlow?.value
        // "Ahead" needs a real course: COG from instruments (radians) or a
        // GPS bearing (degrees); normalized to degrees here. Without either,
        // no profile is sampled (previously a due-north profile was fabricated).
        val cogDeg = marineState?.courseOverGroundTrue?.let { Math.toDegrees(it) }
            ?: (if (loc.hasBearing()) loc.bearing.toDouble() else null)
        if (cogDeg == null) {
            lastProfile = null
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSampleTime > 2000L || lastProfile == null) {
            lastSampleTime = now
            lastProfile = safetyEvaluator.sampleForwardRouteDepthProfile(loc.latitude, loc.longitude, cogDeg)
        }

        val profile = lastProfile ?: return

        val density = context.resources.displayMetrics.density
        val screenW = tileBox.pixWidth.toFloat()
        val screenH = tileBox.pixHeight.toFloat()

        val panelW = (320f * density).coerceAtMost(screenW - 32f * density)
        val panelH = if (isExpanded) 95f * density else 32f * density
        val panelX = (screenW - panelW) / 2f
        val panelY = screenH - panelH - (16f * density)

        panelRect.set(panelX, panelY, panelX + panelW, panelY + panelH)

        // Night vision theme styling
        if (isNight) {
            bgPaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_bg)
            borderPaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_primary)
            seabedPaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_crimson_dim_40)
            seabedLinePaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_data)
            textPaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_data)
            subTextPaint.color = ContextCompat.getColor(context, R.color.nautical_night_vision_pale)
        } else {
            bgPaint.color = ContextCompat.getColor(context, R.color.nautical_depth_panel_bg)
            borderPaint.color = ContextCompat.getColor(context, R.color.nautical_race_cyan)
            seabedPaint.color = ContextCompat.getColor(context, R.color.nautical_day_slate_dim_80)
            seabedLinePaint.color = ContextCompat.getColor(context, R.color.nautical_day_cyan_solid)
            textPaint.color = ContextCompat.getColor(context, R.color.nautical_map_text)
            subTextPaint.color = ContextCompat.getColor(context, R.color.nautical_race_dim)
        }

        canvas.drawRoundRect(panelRect, 8f * density, 8f * density, bgPaint)
        canvas.drawRoundRect(panelRect, 8f * density, 8f * density, borderPaint)

        if (!isExpanded) {
            textPaint.textSize = 12f * density
            val minClr = profile.minClearanceMeters?.let { String.format(Locale.US, "%.1fm", it) }
                ?: context.getString(R.string.nautical_not_available)
            val title = context.getString(R.string.nautical_depth_profile_summary_fmt, minClr)
            canvas.drawText(title, panelRect.left + 12f * density, panelRect.centerY() + (textPaint.textSize * 0.35f), textPaint)
            return
        }

        // Title and Min Clearance summary
        textPaint.textSize = 11f * density
        subTextPaint.textSize = 10f * density
        val minClr = profile.minClearanceMeters?.let { String.format(Locale.US, "%.1fm", it) }
            ?: context.getString(R.string.nautical_not_available)
        val title = context.getString(R.string.nautical_depth_profile_title)
        canvas.drawText(title, panelRect.left + 12f * density, panelRect.top + 16f * density, textPaint)

        val clrText = context.getString(R.string.nautical_depth_profile_min_clearance_fmt, minClr)
        val clrX = panelRect.right - subTextPaint.measureText(clrText) - (12f * density)
        canvas.drawText(clrText, clrX, panelRect.top + 16f * density, subTextPaint)

        // Draw cross-section graph
        val graphL = panelRect.left + 12f * density
        val graphR = panelRect.right - 12f * density
        val graphT = panelRect.top + 26f * density
        val graphB = panelRect.bottom - 10f * density
        val graphW = graphR - graphL
        val graphH = graphB - graphT

        val maxDepthAxis = 20.0 // 0 to 20m depth axis
        val samples = profile.samples
        if (samples.isEmpty()) return

        // 1. Danger Ceiling Line (Draft + Safety Margin - Tide)
        val dangerDepth = (profile.vesselDraft + profile.safetyMargin - profile.tideHeight).coerceAtLeast(0.5)
        val dangerY = graphT + ((dangerDepth / maxDepthAxis).toFloat().coerceIn(0f, 1f) * graphH)
        canvas.drawLine(graphL, dangerY, graphR, dangerY, dangerCeilingPaint)

        // 2. Seabed Terrain Polygon
        seabedPath.rewind()
        seabedPath.moveTo(graphL, graphB)

        for (i in samples.indices) {
            val s = samples[i]
            val px = graphL + ((i.toFloat() / (samples.size - 1)) * graphW)
            // Unsurveyed samples (null depth) leave a gap in the terrain:
            // no invented seabed between soundings.
            val sampleDepth = s.depthMeters ?: continue
            val effectiveD = sampleDepth + profile.tideHeight
            val py = graphT + ((effectiveD / maxDepthAxis).toFloat().coerceIn(0f, 1f) * graphH)

            seabedPath.lineTo(px, py)

            // Hazard marker
            if (s.hazardName != null) {
                canvas.drawCircle(px, py, 4f * density, hazardPinPaint)
            }
        }
        seabedPath.lineTo(graphR, graphB)
        seabedPath.close()

        canvas.drawPath(seabedPath, seabedPaint)
        canvas.drawPath(seabedPath, seabedLinePaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        if (panelRect.contains(point.x, point.y)) {
            isExpanded = !isExpanded
            return true
        }
        return false
    }
}
