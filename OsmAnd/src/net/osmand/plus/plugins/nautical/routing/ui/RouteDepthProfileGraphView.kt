package net.osmand.plus.plugins.nautical.routing.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Route Depth & Safety Clearance Profile Graph.
 *
 * Plots the REAL minimum chart depth per leg (S-57 DEPARE areas sampled by
 * the routing engine) as a step profile with the draft safety limit, keel
 * clearance badge and waypoint ticks. When any leg lacks chart depth
 * coverage the view renders an honest "data unavailable" message instead of
 * an invented seabed curve (the previous implementation drew a sine wave
 * from the leg index and computed fake safe/shallow verdicts from it).
 *
 * Strictly zero allocations in onDraw cycles.
 */
class RouteDepthProfileGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var legs: List<PassagePlanLeg> = emptyList()
    private var minKeelClearanceMeters: Float = 0.0f
    private var safetyThresholdMeters: Float = 2.5f // Draft + Safety Margin
    private var maxDepthScaleMeters: Float = 20.0f
    private var totalRouteNm: Float = 0.0f
    private var hasChartDepths: Boolean = false
    private var isRouteDepthSafe: Boolean = false

    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNightVisionTheme(value)
                invalidate()
            }
        }

    // Preallocated Paint objects for zero-allocation rendering in onDraw
    private val waterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x220288D1.toInt() // Translucent ocean blue
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val waterlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFF00E5FF.toInt() // Cyan waterline
    }

    private val safetyLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFF9800.toInt() // Orange safety clearance limit
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }

    private val seabedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF8D6E63.toInt() // Sand/brown seabed contour
    }

    private val seabedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD3E2723.toInt() // Dark brown seabed polygon fill
    }

    private val unsafeShadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44D32F2F.toInt() // Red warning shading for shallow legs
    }

    private val unsafeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFF1744.toInt() // Vibrant red seabed warning
    }

    private val waypointMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF0288D1.toInt()
    }

    private val waypointMarkerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCFD8DC.toInt()
        textSize = 18f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val clearanceBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEE121820.toInt()
    }

    private val clearanceBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF43A047.toInt()
    }

    private val clearanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF69F0AE.toInt()
        textSize = 19f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val noDataTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF607D8B.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Preallocated geometry & paths
    private val seabedPath = Path()
    private val seabedFillPath = Path()
    private val unsafeZonePath = Path()
    private val badgeRect = RectF()

    fun setRouteData(
        legs: List<PassagePlanLeg>,
        safetyDraftMeters: Float = 2.0f,
        safetyMarginMeters: Float = 0.5f,
    ) {
        this.legs = legs
        this.safetyThresholdMeters = safetyDraftMeters + safetyMarginMeters
        this.totalRouteNm = legs.sumOf { it.distanceNm }.toFloat()

        // Honest data gate: the profile is drawn only when every leg has real
        // chart depth coverage (engine-sampled S-57 DEPARE minima).
        hasChartDepths = legs.isNotEmpty() && legs.all { it.minDepthMeters != null }
        if (hasChartDepths) {
            val minDepth = legs.minOf { it.minDepthMeters!! }.toFloat()
            minKeelClearanceMeters = (minDepth - safetyDraftMeters).coerceAtLeast(0f)
            // Safe means enough water beyond draft: clearance >= safety margin.
            isRouteDepthSafe = minKeelClearanceMeters >= safetyMarginMeters
            if (isNightVision) {
                clearanceBadgeStrokePaint.color = if (isRouteDepthSafe) 0xFFD50000.toInt() else 0xFFFF1744.toInt()
                clearanceTextPaint.color = if (isRouteDepthSafe) 0xFFFF8A80.toInt() else 0xFFFF1744.toInt()
            } else {
                clearanceBadgeStrokePaint.color = if (isRouteDepthSafe) 0xFF43A047.toInt() else 0xFFFF1744.toInt()
                clearanceTextPaint.color = if (isRouteDepthSafe) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt()
            }
        }
        invalidate()
    }

    private fun applyNightVisionTheme(enabled: Boolean) {
        if (enabled) {
            waterBgPaint.color = 0x224A0007.toInt() // Dark red translucent water
            gridLinePaint.color = 0x33FF1744.toInt()
            waterlinePaint.color = 0xFFFF1744.toInt()
            safetyLinePaint.color = 0xFFFF5252.toInt()
            seabedLinePaint.color = 0xFFB71C1C.toInt()
            seabedFillPaint.color = 0xEE120000.toInt()
            unsafeShadingPaint.color = 0x66D50000.toInt()
            unsafeStrokePaint.color = 0xFFFF1744.toInt()
            waypointMarkerPaint.color = 0xFFD50000.toInt()
            labelTextPaint.color = 0xFFFF8A80.toInt()
            clearanceBadgeBgPaint.color = 0xEE120000.toInt()
            noDataTextPaint.color = 0xFFFF8A80.toInt()
        } else {
            waterBgPaint.color = 0x220288D1.toInt()
            gridLinePaint.color = 0x33FFFFFF.toInt()
            waterlinePaint.color = 0xFF00E5FF.toInt()
            safetyLinePaint.color = 0xFFFF9800.toInt()
            seabedLinePaint.color = 0xFF8D6E63.toInt()
            seabedFillPaint.color = 0xDD3E2723.toInt()
            unsafeShadingPaint.color = 0x44D32F2F.toInt()
            unsafeStrokePaint.color = 0xFFFF1744.toInt()
            waypointMarkerPaint.color = 0xFF0288D1.toInt()
            labelTextPaint.color = 0xFFCFD8DC.toInt()
            clearanceBadgeBgPaint.color = 0xEE121820.toInt()
            noDataTextPaint.color = 0xFF607D8B.toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = context.resources.displayMetrics.density
        val padL = 36f * density
        val padR = 16f * density
        val padT = 32f * density
        val padB = 28f * density

        val graphW = w - padL - padR
        val graphH = h - padT - padB
        if (graphW <= 10f || graphH <= 10f) return

        // 1. Water Background
        canvas.drawRect(padL, padT, padL + graphW, padT + graphH, waterBgPaint)

        if (!hasChartDepths) {
            // Honesty state: no real bathymetry for this route; nothing else
            // may be drawn (previously a simulated sine seabed was shown here).
            val msg = context.getString(R.string.nautical_pp_no_bathymetry)
            noDataTextPaint.textSize = 12f * density
            canvas.drawText(msg, w / 2f, padT + graphH / 2f, noDataTextPaint)
            canvas.drawText(context.getString(R.string.nautical_pp_depth_profile_title),
                16f * density, (20f * density), labelTextPaint)
            return
        }

        // 2. Depth Grid Lines (0m, 5m, 10m, 15m, 20m)
        val depthSteps = floatArrayOf(0f, 5f, 10f, 15f, 20f)
        for (d in depthSteps) {
            if (d > maxDepthScaleMeters) break
            val y = padT + (d / maxDepthScaleMeters) * graphH
            canvas.drawLine(padL, y, padL + graphW, y, gridLinePaint)
            labelTextPaint.textSize = 10f * density
            canvas.drawText("${d.toInt()}m", padL - (28f * density), y + (3f * density), labelTextPaint)
        }

        // 3. Waterline at Top (0m)
        canvas.drawLine(padL, padT, padL + graphW, padT, waterlinePaint)

        // 4. Safety Clearance Threshold Line (Draft + Margin)
        val safetyY = padT + (min(safetyThresholdMeters, maxDepthScaleMeters) / maxDepthScaleMeters) * graphH
        canvas.drawLine(padL, safetyY, padL + graphW, safetyY, safetyLinePaint)

        labelTextPaint.textSize = 9.5f * density
        labelTextPaint.color = 0xFFFF9800.toInt()
        val safetyLabel = context.getString(
            R.string.nautical_pp_safety_limit_fmt,
            String.format(Locale.US, "%.1f", safetyThresholdMeters)
        )
        canvas.drawText(safetyLabel, padL + (6f * density), safetyY - (3f * density), labelTextPaint)

        // 5. Seabed Profile Polygon & Waypoint Ticks (real per-leg minima)
        drawSeabedProfile(canvas, padL, padT, graphW, graphH, density)

        // 6. Header Clearance Badge Readout
        drawClearanceBadge(canvas, w, density)
    }

    private fun drawSeabedProfile(
        canvas: Canvas,
        padL: Float,
        padT: Float,
        graphW: Float,
        graphH: Float,
        density: Float
    ) {
        val nLegs = legs.size
        val nPoints = nLegs + 1

        seabedPath.reset()
        seabedFillPath.reset()
        unsafeZonePath.reset()

        val totalDist = max(0.1f, totalRouteNm)
        fun xAt(distNm: Float): Float = padL + (distNm / totalDist).coerceIn(0f, 1f) * graphW
        fun yAt(depthM: Float): Float = padT + (min(depthM, maxDepthScaleMeters) / maxDepthScaleMeters).coerceIn(0f, 1f) * graphH

        // Step profile from real per-leg minima: each leg's depth applies
        // from its start boundary to its end boundary.
        var distNm = 0f
        var currentDepth = legs[0].minDepthMeters!!.toFloat()
        seabedPath.moveTo(xAt(0f), yAt(currentDepth))
        seabedFillPath.moveTo(xAt(0f), yAt(currentDepth))

        for (i in 0 until nLegs) {
            val legDepth = legs[i].minDepthMeters!!.toFloat()
            val xStart = xAt(distNm)
            val legDist = legs[i].distanceNm.toFloat()
            val xEnd = xAt(distNm + legDist)

            // Vertical step transition at the leg boundary, then flat.
            if (legDepth != currentDepth && i > 0) {
                seabedPath.lineTo(xStart, yAt(currentDepth))
                seabedFillPath.lineTo(xStart, yAt(currentDepth))
                currentDepth = legDepth
                seabedPath.lineTo(xStart, yAt(currentDepth))
                seabedFillPath.lineTo(xStart, yAt(currentDepth))
            } else {
                currentDepth = legDepth
            }
            seabedPath.lineTo(xEnd, yAt(currentDepth))
            seabedFillPath.lineTo(xEnd, yAt(currentDepth))

            // Highlight unsafe shallow sections (real data verdicts only)
            if (currentDepth < safetyThresholdMeters) {
                unsafeZonePath.reset()
                unsafeZonePath.moveTo(xStart, yAt(currentDepth))
                unsafeZonePath.lineTo(xEnd, yAt(currentDepth))
                unsafeZonePath.lineTo(xEnd, padT + graphH)
                unsafeZonePath.lineTo(xStart, padT + graphH)
                unsafeZonePath.close()
                canvas.drawPath(unsafeZonePath, unsafeShadingPaint)
                canvas.drawLine(xStart, yAt(currentDepth), xEnd, yAt(currentDepth), unsafeStrokePaint)
            }

            // Draw Waypoint Marker & Label at the leg start boundary
            canvas.drawCircle(xStart, padT, 4f * density, waypointMarkerPaint)
            canvas.drawCircle(xStart, padT, 4f * density, waypointMarkerStrokePaint)
            labelTextPaint.color = Color.WHITE
            labelTextPaint.textSize = 9f * density
            canvas.drawText("WP${i + 1}", xStart - (8f * density), padT + graphH + (14f * density), labelTextPaint)

            distNm += legDist
        }

        // Final waypoint marker
        val lastX = padL + graphW
        canvas.drawCircle(lastX, padT, 4f * density, waypointMarkerPaint)
        canvas.drawCircle(lastX, padT, 4f * density, waypointMarkerStrokePaint)
        labelTextPaint.color = Color.WHITE
        labelTextPaint.textSize = 9f * density
        canvas.drawText("WP$nPoints", lastX - (8f * density), padT + graphH + (14f * density), labelTextPaint)

        // Close seabed polygon down to bottom
        seabedFillPath.lineTo(lastX, padT + graphH)
        seabedFillPath.lineTo(padL, padT + graphH)
        seabedFillPath.close()

        canvas.drawPath(seabedFillPath, seabedFillPaint)
        canvas.drawPath(seabedPath, seabedLinePaint)
    }

    private fun drawClearanceBadge(canvas: Canvas, width: Float, density: Float) {
        val verdictRes = if (isRouteDepthSafe) R.string.nautical_depth_safe else R.string.nautical_depth_shallow_warning
        val label = context.getString(
            R.string.nautical_pp_badge_clearance_fmt,
            String.format(Locale.US, "%.1f", minKeelClearanceMeters),
            context.getString(verdictRes)
        )

        val textWidth = clearanceTextPaint.measureText(label)
        val badgeW = textWidth + (16f * density)
        val badgeH = 20f * density
        val badgeRight = width - (12f * density)
        val badgeLeft = badgeRight - badgeW
        val badgeTop = 6f * density

        badgeRect.set(badgeLeft, badgeTop, badgeRight, badgeTop + badgeH)
        canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, clearanceBadgeBgPaint)
        canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, clearanceBadgeStrokePaint)
        canvas.drawText(label, badgeRight - (8f * density), badgeTop + (14f * density), clearanceTextPaint)

        // Route Profile Title
        labelTextPaint.color = 0xFF00E5FF.toInt()
        labelTextPaint.textSize = 11f * density
        canvas.drawText(context.getString(R.string.nautical_pp_depth_profile_title), 16f * density, badgeTop + (14f * density), labelTextPaint)
    }
}
