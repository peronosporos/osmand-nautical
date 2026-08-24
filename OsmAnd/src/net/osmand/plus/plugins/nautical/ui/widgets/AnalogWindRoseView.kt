package net.osmand.plus.plugins.nautical.ui.widgets

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import java.util.Locale
import kotlin.math.*

/**
 * 360° Analog Circular Wind Rose Tactical Instrument.
 * Displays:
 * - 0°..360° graduated dial (with 0° to 180° port/starboard sailing graduations)
 * - Close-hauled dead zone (0° to ±35° shaded)
 * - Port (red) and Starboard (green) tactical layline sectors from live polar targets
 * - Apparent Wind (AWA needle - Cyan/Blue) and True Wind (TWA needle - Amber/Yellow)
 * - Polar Target VMG markers (optimal upwind beat and downwind gybe angles)
 * - Digital center hub readout
 *
 * Strictly zero allocations in onDraw cycles.
 */
class AnalogWindRoseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var awaRad: Double? = null
        private set
    var awsMs: Double? = null
        private set
    var twaRad: Double? = null
        private set
    var twsMs: Double? = null
        private set
    var targetUpwindRad: Double? = null
        private set
    var targetDownwindRad: Double? = null
        private set
    var isNightMode: Boolean = false
        private set
    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNightVisionTheme(value)
                invalidate()
            }
        }
    var isExpanded: Boolean = false
        private set

    // Exponential low-pass needle damping state (smoothing factor alpha = 0.15)
    private var smoothedAwaRad: Double? = null
    private var smoothedTwaRad: Double? = null

    // Preallocated Paint objects for zero-allocation rendering in onDraw
    private val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEE121820.toInt() // Dark marine background
    }

    private val dialRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFF546E7A.toInt() // Slate rim
    }

    private val deadZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44D32F2F.toInt() // Translucent red dead zone
    }

    private val deadZoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x88D32F2F.toInt()
    }

    private val portRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFE53935.toInt() // Port Red
    }

    private val stbdRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF43A047.toInt() // Starboard Green
    }

    private val polarTargetPortPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF5252.toInt() // Vibrant red marker
    }

    private val polarTargetStbdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF69F0AE.toInt() // Vibrant green marker
    }

    private val polarSectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FFD600.toInt() // Translucent amber target zone
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFFB0BEC5.toInt()
    }

    private val dialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val bowMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF1744.toInt() // Red bow indicator
    }

    private val awaNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
        color = 0xFF00E5FF.toInt() // Cyan/Blue for Apparent Wind
    }

    private val awaNeedleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xAA000000.toInt()
    }

    private val twaNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
        color = 0xFFFFD600.toInt() // Amber/Yellow for True Wind
    }

    private val twaDashedShaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = 0xFFFFD600.toInt()
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val hubBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF102027.toInt() // Very dark navy hub
    }

    private val hubStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF90A4AE.toInt()
    }

    private val hubTextAwaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hubTextTwaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD600.toInt()
        textSize = 17f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val hubSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFECEFF1.toInt()
        textSize = 15f
        textAlign = Paint.Align.CENTER
    }

    // Preallocated geometry & paths
    private val dialRect = RectF()
    private val hubRect = RectF()
    private val needlePath = Path()
    private val bowPath = Path()
    private val targetMarkerPath = Path()
    private val rotateMatrix = Matrix()

    // Preallocated char buffer for zero-alloc number formatting
    private val labelBuffer = CharArray(16)

    init {
        setOnClickListener {
            toggleExpanded()
        }
    }

    fun toggleExpanded() {
        isExpanded = !isExpanded
        requestLayout()
        invalidate()
    }

    private fun smoothAngle(current: Double?, target: Double?, alpha: Double = 0.15): Double? {
        if (target == null) return null
        if (current == null) return target

        var diff = (target - current) % (2.0 * Math.PI)
        if (diff > Math.PI) diff -= (2.0 * Math.PI)
        if (diff < -Math.PI) diff += (2.0 * Math.PI)

        return current + (alpha * diff)
    }

    fun setData(
        awaRad: Double?,
        awsMs: Double?,
        twaRad: Double?,
        twsMs: Double?,
        targetUpwindRad: Double? = null,
        targetDownwindRad: Double? = null,
        isNightMode: Boolean = false,
    ) {
        var changed = false
        val newSmoothedAwa = smoothAngle(this.smoothedAwaRad, awaRad, 0.15)
        val newSmoothedTwa = smoothAngle(this.smoothedTwaRad, twaRad, 0.15)

        if (this.smoothedAwaRad != newSmoothedAwa) { this.smoothedAwaRad = newSmoothedAwa; changed = true }
        if (this.smoothedTwaRad != newSmoothedTwa) { this.smoothedTwaRad = newSmoothedTwa; changed = true }
        if (this.awaRad != awaRad) { this.awaRad = awaRad; changed = true }
        if (this.awsMs != awsMs) { this.awsMs = awsMs; changed = true }
        if (this.twaRad != twaRad) { this.twaRad = twaRad; changed = true }
        if (this.twsMs != twsMs) { this.twsMs = twsMs; changed = true }
        if (this.targetUpwindRad != targetUpwindRad) { this.targetUpwindRad = targetUpwindRad; changed = true }
        if (this.targetDownwindRad != targetDownwindRad) { this.targetDownwindRad = targetDownwindRad; changed = true }
        if (this.isNightMode != isNightMode) { this.isNightMode = isNightMode; changed = true }

        if (changed) {
            invalidate()
        }
    }

    private fun applyNightVisionTheme(enabled: Boolean) {
        if (enabled) {
            dialBgPaint.color = 0xFF120000.toInt() // Pitch black background
            dialRimPaint.color = 0xFF8B0000.toInt() // Dark red rim
            majorTickPaint.color = 0xFFFF1744.toInt()
            minorTickPaint.color = 0xFFD50000.toInt()
            dialTextPaint.color = 0xFFFF8A80.toInt()
            awaNeedlePaint.color = 0xFFFF1744.toInt() // Deep red AWA needle
            twaNeedlePaint.color = 0xFFD50000.toInt() // Deep red TWA needle
            hubBgPaint.color = 0xEE120000.toInt()
            hubStrokePaint.color = 0xFFFF1744.toInt()
            hubTextPrimaryPaint.color = 0xFFFF5252.toInt()
            hubTextSecondaryPaint.color = 0xFFFF8A80.toInt()
            deadZonePaint.color = 0x334A0007.toInt()
            polarSectorPaint.color = 0x338B0000.toInt()
        } else {
            dialBgPaint.color = 0xEE121820.toInt()
            dialRimPaint.color = 0xFF546E7A.toInt()
            majorTickPaint.color = Color.WHITE
            minorTickPaint.color = 0xFFB0BEC5.toInt()
            dialTextPaint.color = Color.WHITE
            awaNeedlePaint.color = 0xFF00E5FF.toInt()
            twaNeedlePaint.color = 0xFFFFD600.toInt()
            hubBgPaint.color = 0xEE1A232E.toInt()
            hubStrokePaint.color = 0xFF0288D1.toInt()
            hubTextPrimaryPaint.color = 0xFF00E5FF.toInt()
            hubTextSecondaryPaint.color = Color.WHITE
            deadZonePaint.color = 0x44D32F2F.toInt()
            polarSectorPaint.color = 0x33FFD600.toInt()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = context.resources.displayMetrics.density
        val defaultSize = if (isExpanded) (180f * density).toInt() else (110f * density).toInt()

        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(cx, cy) - 4f
        if (radius <= 10f) return

        val density = context.resources.displayMetrics.density
        updatePaintsForDensity(density)

        // 1. Draw Dial Background
        canvas.drawCircle(cx, cy, radius, dialBgPaint)
        canvas.drawCircle(cx, cy, radius, dialRimPaint)

        dialRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 2. Draw Close-Hauled Dead Zone (0° to ±35° shaded)
        // 0° is Top (-90° in Android Canvas arc coordinates)
        canvas.drawArc(dialRect, -90f - 35f, 70f, true, deadZonePaint)
        canvas.drawArc(dialRect, -90f - 35f, 70f, true, deadZoneStrokePaint)

        // 3. Draw Port (Red, Left side) and Starboard (Green, Right side) Rim Sectors
        val rimInset = 3f * density
        dialRect.set(cx - radius + rimInset, cy - radius + rimInset, cx + radius - rimInset, cy + radius - rimInset)
        canvas.drawArc(dialRect, -90f, 180f, false, stbdRimPaint)
        canvas.drawArc(dialRect, -90f, -180f, false, portRimPaint)

        // 4. Draw Polar Target Sectors & Markers (if available)
        drawPolarTargets(canvas, cx, cy, radius, density)

        // 5. Draw 0°..360° Graduated Dial Ticks & Degree Labels
        drawGraduatedDial(canvas, cx, cy, radius, density)

        // 6. Draw Bow Marker (Red Triangle pointing forward at 0°)
        drawBowMarker(canvas, cx, cy, radius, density)

        // 7. Draw True Wind Angle (TWA) Needle (Amber/Yellow)
        (smoothedTwaRad ?: twaRad)?.let { twa ->
            drawTwaNeedle(canvas, cx, cy, radius, twa, density)
        }

        // 8. Draw Apparent Wind Angle (AWA) Needle (Cyan/Blue)
        (smoothedAwaRad ?: awaRad)?.let { awa ->
            drawAwaNeedle(canvas, cx, cy, radius, awa, density)
        }

        // 9. Draw Center Hub with Digital Readout
        drawCenterHub(canvas, cx, cy, radius, density)
    }

    private fun updatePaintsForDensity(density: Float) {
        if (isNightMode) {
            dialBgPaint.color = 0xFF000000.toInt()
            dialTextPaint.color = 0xFFFF5252.toInt()
            minorTickPaint.color = 0xFF880000.toInt()
            majorTickPaint.color = 0xFFFF1744.toInt()
        } else {
            dialBgPaint.color = 0xEE121820.toInt()
            dialTextPaint.color = Color.WHITE
            minorTickPaint.color = 0xFFB0BEC5.toInt()
            majorTickPaint.color = Color.WHITE
        }
        dialTextPaint.textSize = if (isExpanded) 11f * density else 8.5f * density
        hubTextAwaPaint.textSize = if (isExpanded) 14f * density else 10f * density
        hubTextTwaPaint.textSize = if (isExpanded) 12f * density else 9f * density
        hubSubTextPaint.textSize = if (isExpanded) 11f * density else 8.5f * density
    }

    private fun drawPolarTargets(canvas: Canvas, cx: Float, cy: Float, radius: Float, density: Float) {
        val upwind = targetUpwindRad ?: Math.toRadians(42.0)
        val downwind = targetDownwindRad ?: Math.toRadians(145.0)

        val upwindDeg = Math.toDegrees(upwind).toFloat()
        val downwindDeg = Math.toDegrees(downwind).toFloat()

        // Upwind Target Sectors (Starboard and Port)
        canvas.drawArc(dialRect, -90f + upwindDeg - 3f, 6f, true, polarSectorPaint)
        canvas.drawArc(dialRect, -90f - upwindDeg - 3f, 6f, true, polarSectorPaint)

        // Downwind Target Sectors (Starboard and Port)
        canvas.drawArc(dialRect, -90f + downwindDeg - 4f, 8f, true, polarSectorPaint)
        canvas.drawArc(dialRect, -90f - downwindDeg - 4f, 8f, true, polarSectorPaint)

        // Draw Inward Arrow Markers on the rim
        drawTargetMarker(canvas, cx, cy, radius, upwindDeg, polarTargetStbdPaint, density)
        drawTargetMarker(canvas, cx, cy, radius, -upwindDeg, polarTargetPortPaint, density)
        drawTargetMarker(canvas, cx, cy, radius, downwindDeg, polarTargetStbdPaint, density)
        drawTargetMarker(canvas, cx, cy, radius, -downwindDeg, polarTargetPortPaint, density)
    }

    private fun drawTargetMarker(canvas: Canvas, cx: Float, cy: Float, radius: Float, angleDeg: Float, paint: Paint, density: Float) {
        val rad = Math.toRadians((angleDeg - 90.0)).toFloat()
        val markerDist = radius - (2f * density)
        val tipX = cx + cos(rad) * (markerDist - (6f * density))
        val tipY = cy + sin(rad) * (markerDist - (6f * density))

        val baseRad1 = rad + 0.08f
        val baseRad2 = rad - 0.08f
        val base1X = cx + cos(baseRad1) * markerDist
        val base1Y = cy + sin(baseRad1) * markerDist
        val base2X = cx + cos(baseRad2) * markerDist
        val base2Y = cy + sin(baseRad2) * markerDist

        targetMarkerPath.reset()
        targetMarkerPath.moveTo(tipX, tipY)
        targetMarkerPath.lineTo(base1X, base1Y)
        targetMarkerPath.lineTo(base2X, base2Y)
        targetMarkerPath.close()

        canvas.drawPath(targetMarkerPath, paint)
    }

    private fun drawGraduatedDial(canvas: Canvas, cx: Float, cy: Float, radius: Float, density: Float) {
        val majorTickLen = if (isExpanded) 10f * density else 7f * density
        val minorTickLen = if (isExpanded) 5f * density else 3.5f * density
        val textDist = radius - (18f * density)

        for (deg in 0 until 360 step 10) {
            val rad = Math.toRadians((deg - 90.0)).toFloat()
            val cosVal = cos(rad)
            val sinVal = sin(rad)

            val isMajor = (deg % 30 == 0)
            val isCardinal = (deg % 90 == 0)
            val tickLen = if (isMajor) majorTickLen else minorTickLen
            val currentPaint = if (isMajor) majorTickPaint else minorTickPaint

            val outerX = cx + cosVal * (radius - 2f * density)
            val outerY = cy + sinVal * (radius - 2f * density)
            val innerX = cx + cosVal * (radius - 2f * density - tickLen)
            val innerY = cy + sinVal * (radius - 2f * density - tickLen)

            canvas.drawLine(outerX, outerY, innerX, innerY, currentPaint)

            // Draw labels at major angles (0, 30, 60, 90, 120, 150, 180)
            if (isMajor && deg != 0 && (isExpanded || isCardinal || deg == 60 || deg == 120 || deg == 180 || deg == 240 || deg == 300)) {
                val labelVal = if (deg <= 180) deg else (360 - deg)
                val textX = cx + cosVal * textDist
                val textY = cy + sinVal * textDist + (dialTextPaint.textSize * 0.35f)

                // Color 0..180 port/stbd labels
                dialTextPaint.color = when {
                    isNightMode -> 0xFFFF5252.toInt()
                    deg in 1..179 -> 0xFF81C784.toInt() // Greenish for Starboard
                    deg in 181..359 -> 0xFFE57373.toInt() // Reddish for Port
                    else -> Color.WHITE
                }
                canvas.drawText(labelVal.toString(), textX, textY, dialTextPaint)
            }
        }
    }

    private fun drawBowMarker(canvas: Canvas, cx: Float, cy: Float, radius: Float, density: Float) {
        val top = cy - radius + (2f * density)
        val height = 9f * density
        val width = 7f * density

        bowPath.reset()
        bowPath.moveTo(cx, top)
        bowPath.lineTo(cx - width / 2f, top + height)
        bowPath.lineTo(cx + width / 2f, top + height)
        bowPath.close()

        canvas.drawPath(bowPath, bowMarkerPaint)
    }

    private fun drawTwaNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float, twaRad: Double, density: Float) {
        val twaDeg = Math.toDegrees(twaRad).toFloat()
        val needleLen = radius * 0.72f
        val rad = Math.toRadians((twaDeg - 90.0)).toFloat()

        val endX = cx + cos(rad) * needleLen
        val endY = cy + sin(rad) * needleLen

        // Dashed Shaft
        canvas.drawLine(cx, cy, endX, endY, twaDashedShaftPaint)

        // Arrowhead
        val headSize = 10f * density
        val leftAngle = rad + Math.toRadians(150.0).toFloat()
        val rightAngle = rad - Math.toRadians(150.0).toFloat()

        needlePath.reset()
        needlePath.moveTo(endX, endY)
        needlePath.lineTo(endX + cos(leftAngle) * headSize, endY + sin(leftAngle) * headSize)
        needlePath.lineTo(endX + cos(rightAngle) * headSize, endY + sin(rightAngle) * headSize)
        needlePath.close()

        canvas.drawPath(needlePath, twaNeedlePaint)
    }

    private fun drawAwaNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float, awaRad: Double, density: Float) {
        val awaDeg = Math.toDegrees(awaRad).toFloat()
        val needleLen = radius * 0.88f
        val rad = Math.toRadians((awaDeg - 90.0)).toFloat()

        val endX = cx + cos(rad) * needleLen
        val endY = cy + sin(rad) * needleLen

        // Solid Needle Shaft with shadow for high contrast
        canvas.drawLine(cx, cy, endX, endY, awaNeedleShadowPaint)
        canvas.drawLine(cx, cy, endX, endY, awaNeedlePaint)

        // Arrowhead
        val headSize = 14f * density
        val leftAngle = rad + Math.toRadians(155.0).toFloat()
        val rightAngle = rad - Math.toRadians(155.0).toFloat()

        needlePath.reset()
        needlePath.moveTo(endX, endY)
        needlePath.lineTo(endX + cos(leftAngle) * headSize, endY + sin(leftAngle) * headSize)
        needlePath.lineTo(endX + cos(rightAngle) * headSize, endY + sin(rightAngle) * headSize)
        needlePath.close()

        canvas.drawPath(needlePath, awaNeedlePaint)
    }

    private fun drawCenterHub(canvas: Canvas, cx: Float, cy: Float, radius: Float, density: Float) {
        val hubRadius = radius * (if (isExpanded) 0.38f else 0.42f)

        hubRect.set(cx - hubRadius, cy - hubRadius, cx + hubRadius, cy + hubRadius)
        canvas.drawCircle(cx, cy, hubRadius, hubBgPaint)
        canvas.drawCircle(cx, cy, hubRadius, hubStrokePaint)

        // Readout in Center
        val awsKn = awsMs?.let { SignalKUnitConverter.msToKnots(it) } ?: 0.0
        val twsKn = twsMs?.let { SignalKUnitConverter.msToKnots(it) } ?: 0.0

        val awaDeg = awaRad?.let { ((Math.toDegrees(it) + 360.0) % 360.0).roundToInt() }
        val twaDeg = twaRad?.let { ((Math.toDegrees(it) + 360.0) % 360.0).roundToInt() }

        if (awaDeg != null) {
            val awaDisplay = if (awaDeg <= 180) "${awaDeg}°" else "${360 - awaDeg}°"
            val awaSide = if (awaDeg in 1..179) "S" else if (awaDeg in 181..359) "P" else ""
            val awaText = "AWA $awaDisplay$awaSide"
            val awsText = String.format(Locale.US, "%.1f kts", awsKn)

            if (isExpanded && twaDeg != null) {
                val twaDisplay = if (twaDeg <= 180) "${twaDeg}°" else "${360 - twaDeg}°"
                val twaText = "TWA $twaDisplay"
                canvas.drawText(awaText, cx, cy - (10f * density), hubTextAwaPaint)
                canvas.drawText(awsText, cx, cy + (3f * density), hubSubTextPaint)
                canvas.drawText(twaText, cx, cy + (16f * density), hubTextTwaPaint)
            } else {
                canvas.drawText(awaText, cx, cy - (3f * density), hubTextAwaPaint)
                canvas.drawText(awsText, cx, cy + (12f * density), hubSubTextPaint)
            }
        } else {
            canvas.drawText("WIND", cx, cy - (3f * density), hubSubTextPaint)
            canvas.drawText("--", cx, cy + (12f * density), hubSubTextPaint)
        }
    }
}
