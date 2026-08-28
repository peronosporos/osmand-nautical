package net.osmand.plus.plugins.nautical.ui.anchor

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.anchor.TrackPoint
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.utils.AndroidUtils
import kotlin.math.abs

/**
 * Custom map layer for visualizing the anchor watch boundary and drop point.
 */
class AnchorWatchMapLayer(context: Context) : OsmandMapLayer(context) {

    private val app = context.applicationContext as OsmandApplication
    private val settings = app.settings

    private val alarmBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.RED
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val alarmBoundaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 25 // Semi-transparent red alarm zone
    }

    private val safeSwingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF43A047.toInt() // Green for safe swing zone
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }

    private val safeSwingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF43A047.toInt()
        alpha = 20
    }

    private val dropPinBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD121212.toInt()
    }

    private val dropPinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    private val dropPinCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF1744.toInt()
    }

    private val snailTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val driftConePath = Path()
    private val driftConeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }
    private val driftConeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val driftMilestonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val driftMilestoneTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val snailTrailPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5FF.toInt()
    }

    private val shallowHazardArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0xFFFF1744.toInt()
    }
    private val shallowHazardArcFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40FF1744.toInt()
    }
    private val shallowHazardArcRect = RectF()

    private val confidenceRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        color = 0xFF00E5FF.toInt()
    }

    var playbackMinuteOffset: Int? = null

    private val ghostVesselPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ghostVesselStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val ghostWindArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val ghostTideArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val ghostArrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ghostArrowPath = Path()
    private val ghostVesselPath = Path()

    private val windShiftConePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x35FF1744.toInt()
    }
    private val windShiftConeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        color = 0xFFFF1744.toInt()
    }
    private val windShiftConePath = Path()
    private val windShiftConeRect = RectF()

    private val anchorIcon: Bitmap? by lazy {
        val themedCtx = androidx.appcompat.view.ContextThemeWrapper(context, R.style.OsmandLightTheme)
        val drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(themedCtx, R.drawable.ic_action_anchor)
            ?: ContextCompat.getDrawable(themedCtx, R.drawable.ic_action_anchor)
        drawable?.let {
            val bitmap = createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bitmap
        }
    }

    override fun drawInScreenPixels(): Boolean = true

    private fun setupPaints(isNight: Boolean) {
        if (isNight) {
            alarmBoundaryPaint.color = 0xFFFF1744.toInt()
            alarmBoundaryFillPaint.color = 0x30FF1744.toInt()

            safeSwingPaint.color = 0x80B71C1C.toInt()
            safeSwingFillPaint.color = 0x208B0000.toInt()

            dropPinBgPaint.color = 0xDD120000.toInt()
            dropPinStrokePaint.color = 0xFFFF8A80.toInt()
            dropPinCenterPaint.color = 0xFFFF1744.toInt()

            snailTrailPaint.color = 0x60FF1744.toInt()
            snailTrailPointPaint.color = 0xFFFF1744.toInt()

            driftConeStrokePaint.color = 0xFFFF1744.toInt()
            driftConeFillPaint.color = 0x25FF1744.toInt()
            driftMilestonePaint.color = 0xFFFF8A80.toInt()
            driftMilestoneTickPaint.color = 0xFFFF1744.toInt()
        } else {
            alarmBoundaryPaint.color = Color.RED
            alarmBoundaryFillPaint.color = Color.RED
            alarmBoundaryFillPaint.alpha = 25

            safeSwingPaint.color = 0xFF43A047.toInt()
            safeSwingFillPaint.color = 0xFF43A047.toInt()
            safeSwingFillPaint.alpha = 20

            dropPinBgPaint.color = 0xDD121212.toInt()
            dropPinStrokePaint.color = Color.WHITE
            dropPinCenterPaint.color = 0xFFFF1744.toInt()

            snailTrailPaint.color = Color.WHITE
            snailTrailPointPaint.color = 0xFF00E5FF.toInt()

            driftConeStrokePaint.color = 0xFFD32F2F.toInt()
            driftConeFillPaint.color = 0x25D32F2F.toInt()
            driftMilestonePaint.color = 0xFFD32F2F.toInt()
            driftMilestoneTickPaint.color = 0xFFD32F2F.toInt()
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val lat = this.settings.NAUTICAL_ANCHOR_LAT.get()
        val lon = this.settings.NAUTICAL_ANCHOR_LON.get()
        val radius = this.settings.NAUTICAL_ANCHOR_RADIUS.get()

        val isNight = NauticalPlugin.isNightVision(app)
        setupPaints(isNight)

        if (lat != 0.0 && lon != 0.0 && radius > 0f) {
            drawAnchorWatch(canvas, tileBox, lat, lon, radius, isPreview = false, isNight = isNight)
        }

        // Draw Preview if active
        val pLat = this.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val pLon = this.settings.NAUTICAL_ANCHOR_PREVIEW_LON.get()
        val pRadius = this.settings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.get()

        if (pLat != 0.0 && pLon != 0.0 && pRadius > 0f) {
            drawAnchorWatch(canvas, tileBox, pLat, pLon, pRadius, isPreview = true, isNight = isNight)
        }

        // 3. Draw Snail Trail (vessel history inside swing zone)
        val watchdog = NauticalPlugin.getInstance()?.anchorWatchdog
        watchdog?.let { wd ->
            val points = wd.trackHistory.value
            if (points.size >= 2) {
                drawSnailTrail(canvas, tileBox, points, isNight)
            }
            if (points.isNotEmpty()) {
                drawPlaybackGhostVessel(canvas, tileBox, points, isNight)
            }

            // 4. Draw 15-minute projected drift cone if alarm / drag breach is active
            if (wd.driftStage.value == AnchorDriftWatchdog.AnchorDriftStage.CRITICAL_DRAG) {
                drawDriftCone(canvas, tileBox, isNight)
            }

            // 5. Draw wind-shift alert cone if rapid wind shift breakout risk is active
            if (wd.isWindShiftBreakoutRisk.value) {
                val aLat = this.settings.NAUTICAL_ANCHOR_LAT.get()
                val aLon = this.settings.NAUTICAL_ANCHOR_LON.get()
                val aRad = this.settings.NAUTICAL_ANCHOR_RADIUS.get()
                if (aLat != 0.0 && aLon != 0.0 && aRad > 0f) {
                    drawWindShiftAlertCone(canvas, tileBox, aLat, aLon, aRad, wd.predictedSwingAngleDeg.value, isNight)
                }
            }
        }
    }

    private fun drawWindShiftAlertCone(canvas: Canvas, tileBox: RotatedTileBox, anchorLat: Double, anchorLon: Double, radiusM: Float, swingAngleDeg: Double, isNight: Boolean) {
        val cx = tileBox.getPixXFromLatLon(anchorLat, anchorLon)
        val cy = tileBox.getPixYFromLatLon(anchorLat, anchorLon)
        val northP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(anchorLat, anchorLon, radiusM.toDouble(), 0.0)
        val northY = tileBox.getPixYFromLatLon(northP.latitude, northP.longitude)
        val pixRadius = kotlin.math.abs(cy - northY).coerceAtLeast(10f)

        windShiftConeRect.set(cx - pixRadius, cy - pixRadius, cx + pixRadius, cy + pixRadius)

        val sweepHalf = 30f
        val startAngle = (swingAngleDeg - 90.0 - tileBox.rotate - sweepHalf).toFloat()
        val sweepAngle = sweepHalf * 2f

        windShiftConePaint.color = if (isNight) 0x35FF1744.toInt() else 0x40E53935.toInt()
        windShiftConeStrokePaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFFD32F2F.toInt()

        windShiftConePath.rewind()
        windShiftConePath.moveTo(cx, cy)
        windShiftConePath.arcTo(windShiftConeRect, startAngle, sweepAngle)
        windShiftConePath.close()

        canvas.drawPath(windShiftConePath, windShiftConePaint)
        canvas.drawPath(windShiftConePath, windShiftConeStrokePaint)
    }

    private fun drawDriftCone(canvas: Canvas, tileBox: RotatedTileBox, isNight: Boolean) {
        val state = NauticalPlugin.engine?.getCurrentState()
        val lat = state?.latitude ?: app.locationProvider.lastKnownLocation?.latitude ?: return
        val lon = state?.longitude ?: app.locationProvider.lastKnownLocation?.longitude ?: return

        val cogDeg = state?.courseOverGroundTrue ?: state?.headingMagnetic ?: app.locationProvider.lastKnownLocation?.bearing?.toDouble() ?: 0.0
        val sogMps = (state?.speedOverGround ?: app.locationProvider.lastKnownLocation?.speed?.toDouble() ?: 0.0).coerceAtLeast(0.25)

        val startX = tileBox.getPixXFromLatLon(lat, lon)
        val startY = tileBox.getPixYFromLatLon(lat, lon)
        val density = tileBox.density

        // 15-minute projected drift distance (900 seconds)
        val dist15m = sogMps * 900.0
        val spreadAngleDeg = 15.0 // +/- 15 degree cone spread

        val leftBearing = (cogDeg - spreadAngleDeg + 360.0) % 360.0
        val rightBearing = (cogDeg + spreadAngleDeg) % 360.0

        val leftDest = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, leftBearing, dist15m)
        val rightDest = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, rightBearing, dist15m)

        val leftX = tileBox.getPixXFromLatLon(leftDest.latitude, leftDest.longitude)
        val leftY = tileBox.getPixYFromLatLon(leftDest.latitude, leftDest.longitude)
        val rightX = tileBox.getPixXFromLatLon(rightDest.latitude, rightDest.longitude)
        val rightY = tileBox.getPixYFromLatLon(rightDest.latitude, rightDest.longitude)

        driftConePath.reset()
        driftConePath.moveTo(startX, startY)
        driftConePath.lineTo(leftX, leftY)
        driftConePath.lineTo(rightX, rightY)
        driftConePath.close()

        canvas.drawPath(driftConePath, driftConeFillPaint)
        canvas.drawPath(driftConePath, driftConeStrokePaint)

        // Draw milestone markers (+5m, +10m, +15m)
        val milestoneSeconds = doubleArrayOf(300.0, 600.0, 900.0)
        val milestoneLabels = arrayOf("+5m", "+10m", "+15m")

        for (i in milestoneSeconds.indices) {
            val dist = sogMps * milestoneSeconds[i]
            val centerDest = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, cogDeg, dist)
            val leftM = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, leftBearing, dist)
            val rightM = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, rightBearing, dist)

            val lx = tileBox.getPixXFromLatLon(leftM.latitude, leftM.longitude)
            val ly = tileBox.getPixYFromLatLon(leftM.latitude, leftM.longitude)
            val rx = tileBox.getPixXFromLatLon(rightM.latitude, rightM.longitude)
            val ry = tileBox.getPixYFromLatLon(rightM.latitude, rightM.longitude)
            val cx = tileBox.getPixXFromLatLon(centerDest.latitude, centerDest.longitude)
            val cy = tileBox.getPixYFromLatLon(centerDest.latitude, centerDest.longitude)

            canvas.drawLine(lx, ly, rx, ry, driftMilestoneTickPaint)
            canvas.drawText(milestoneLabels[i], cx, cy - 8f * density, driftMilestonePaint)
        }
    }

    private fun drawAnchorWatch(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        lat: Double,
        lon: Double,
        radius: Float,
        isPreview: Boolean,
        isNight: Boolean
    ) {
        if ((lat == 0.0) || (lon == 0.0) || (radius <= 0f)) return

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)
        val density = tileBox.density

        // Estimate pixel radius by projecting a point 'radius' meters North
        val northLatLon = net.osmand.util.MapUtils.rhumbDestinationPoint(lat, lon, 0.0, radius.toDouble())
        val northY = tileBox.getPixYFromLatLon(northLatLon.latitude, northLatLon.longitude)
        val pixRadius = abs(centerY - northY)

        if (isPreview) {
            if (isNight) {
                safeSwingPaint.color = 0x80FF5252.toInt()
                safeSwingFillPaint.color = 0x20B71C1C.toInt()
            } else {
                safeSwingPaint.color = Color.BLUE
                safeSwingFillPaint.color = Color.BLUE
                safeSwingFillPaint.alpha = 15
            }
            canvas.drawCircle(centerX, centerY, pixRadius, safeSwingFillPaint)
            canvas.drawCircle(centerX, centerY, pixRadius, safeSwingPaint)
        } else {
            // 1. Draw Safe Swing inner radius (e.g. 75% of total alarm radius)
            val safePixRadius = (pixRadius * 0.75f).coerceAtLeast(4f)
            canvas.drawCircle(centerX, centerY, safePixRadius, safeSwingFillPaint)
            canvas.drawCircle(centerX, centerY, safePixRadius, safeSwingPaint)

            // 2. Draw Alarm Threshold boundary circle
            canvas.drawCircle(centerX, centerY, pixRadius, alarmBoundaryFillPaint)
            canvas.drawCircle(centerX, centerY, pixRadius, alarmBoundaryPaint)

            // 2b. Draw Shallow Depth Hazard Arc Sector if probe detected shallow water
            val shallowHazard = NauticalPlugin.getInstance()?.anchorWatchdog?.shallowHazardSector?.value
            if (shallowHazard != null) {
                val (startBearing, endBearing) = shallowHazard
                val sweepAngle = ((endBearing - startBearing + 360.0) % 360.0).toFloat().coerceAtLeast(20f)
                val startAngle = (startBearing - 90.0 - tileBox.rotate).toFloat()

                shallowHazardArcRect.set(centerX - pixRadius, centerY - pixRadius, centerX + pixRadius, centerY + pixRadius)
                canvas.drawArc(shallowHazardArcRect, startAngle, sweepAngle, true, shallowHazardArcFillPaint)
                canvas.drawArc(shallowHazardArcRect, startAngle, sweepAngle, false, shallowHazardArcPaint)
            }
        }

        // 3. Draw Anchor Hook Confidence Ring (8m holding confidence ring around anchor drop position)
        confidenceRingPaint.color = if (isNight) 0x80FF8A80.toInt() else 0x8000E5FF.toInt()
        val confPixRadius = (8.0 * tileBox.getPixDensity()).toFloat().coerceAtLeast(12f)
        canvas.drawCircle(centerX, centerY, confPixRadius, confidenceRingPaint)

        // 4. Draw Anchor Drop Pin & Icon
        val pinRadius = 14f * density
        canvas.drawCircle(centerX, centerY, pinRadius, dropPinBgPaint)
        canvas.drawCircle(centerX, centerY, pinRadius, dropPinStrokePaint)
        canvas.drawCircle(centerX, centerY, 3f * density, dropPinCenterPaint)

        anchorIcon?.let { bitmap ->
            canvas.drawBitmap(bitmap, centerX - bitmap.width / 2f, centerY - bitmap.height / 2f, null)
        }
    }

    private fun drawSnailTrail(canvas: Canvas, tileBox: RotatedTileBox, points: List<TrackPoint>, isNight: Boolean) {
        val anchorLat = settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = settings.NAUTICAL_ANCHOR_LON.get()
        val radius = settings.NAUTICAL_ANCHOR_RADIUS.get().toDouble().takeIf { it > 0.0 } ?: 30.0

        val currentTime = System.currentTimeMillis()
        val maxAge = 60 * 60 * 1000L // Rolling 60-minute window

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            if (currentTime - p1.timestamp > maxAge) continue

            val x1 = tileBox.getPixXFromLatLon(p1.latLon.latitude, p1.latLon.longitude)
            val y1 = tileBox.getPixYFromLatLon(p1.latLon.latitude, p1.latLon.longitude)
            val x2 = tileBox.getPixXFromLatLon(p2.latLon.latitude, p2.latLon.longitude)
            val y2 = tileBox.getPixYFromLatLon(p2.latLon.latitude, p2.latLon.longitude)

            val dist = if (anchorLat != 0.0) {
                net.osmand.util.MapUtils.getDistance(p1.latLon.latitude, p1.latLon.longitude, anchorLat, anchorLon)
            } else 0.0

            // Gradient heat mapping: Green (stable), Amber (creep), Crimson (breakout)
            val segmentColor = when {
                dist > radius -> if (isNight) 0xFFFF1744.toInt() else 0xFFE53935.toInt() // Crimson: Active breakout
                dist > radius * 0.75 -> if (isNight) 0xFFFF5252.toInt() else 0xFFFFB300.toInt() // Amber: Suspicious elongation
                else -> if (isNight) 0x80FF8A80.toInt() else 0xFF43A047.toInt() // Green: Stable oscillation
            }

            snailTrailPaint.color = segmentColor
            canvas.drawLine(x1, y1, x2, y2, snailTrailPaint)
        }

        // Draw latest position indicator dot
        val last = points.lastOrNull()
        if (last != null && (currentTime - last.timestamp <= maxAge)) {
            val lx = tileBox.getPixXFromLatLon(last.latLon.latitude, last.latLon.longitude)
            val ly = tileBox.getPixYFromLatLon(last.latLon.latitude, last.latLon.longitude)
            snailTrailPointPaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt()
            canvas.drawCircle(lx, ly, 4f * tileBox.density, snailTrailPointPaint)
        }
    }

    private fun drawPlaybackGhostVessel(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        points: List<TrackPoint>,
        isNight: Boolean
    ) {
        val offsetMin = playbackMinuteOffset ?: return
        if (points.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val targetTime = currentTime - (60 - offsetMin) * 60 * 1000L

        var closestPt: TrackPoint? = null
        var minDiff = Long.MAX_VALUE
        for (pt in points) {
            val diff = kotlin.math.abs(pt.timestamp - targetTime)
            if (diff < minDiff) {
                minDiff = diff
                closestPt = pt
            }
        }
        val targetPt = closestPt ?: points.lastOrNull() ?: return

        val gx = tileBox.getPixXFromLatLon(targetPt.latLon.latitude, targetPt.latLon.longitude)
        val gy = tileBox.getPixYFromLatLon(targetPt.latLon.latitude, targetPt.latLon.longitude)
        val density = tileBox.density

        ghostVesselPaint.color = if (isNight) 0x60FF8A80.toInt() else 0x6000E5FF.toInt()
        ghostVesselStrokePaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFF00B0FF.toInt()

        // Draw Ghost Vessel Silhouette
        val vSize = 14f * density
        ghostVesselPath.rewind()
        ghostVesselPath.moveTo(gx, gy - vSize)
        ghostVesselPath.lineTo(gx + vSize * 0.6f, gy + vSize * 0.8f)
        ghostVesselPath.lineTo(gx, gy + vSize * 0.4f)
        ghostVesselPath.lineTo(gx - vSize * 0.6f, gy + vSize * 0.8f)
        ghostVesselPath.close()

        canvas.drawPath(ghostVesselPath, ghostVesselPaint)
        canvas.drawPath(ghostVesselPath, ghostVesselStrokePaint)

        // Draw True Wind Arrow at (gx, gy)
        val windDeg = targetPt.windDirectionDeg ?: 45.0
        val windRad = Math.toRadians(windDeg - tileBox.rotate)
        val windLen = 32f * density
        val wx = gx + (windLen * kotlin.math.sin(windRad)).toFloat()
        val wy = gy - (windLen * kotlin.math.cos(windRad)).toFloat()

        ghostWindArrowPaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt()
        ghostArrowHeadPaint.color = ghostWindArrowPaint.color
        canvas.drawLine(gx, gy, wx, wy, ghostWindArrowPaint)

        // Arrow head for wind
        ghostArrowPath.rewind()
        val headAngle1 = windRad + Math.toRadians(150.0)
        val headAngle2 = windRad - Math.toRadians(150.0)
        val hLen = 8f * density
        ghostArrowPath.moveTo(wx, wy)
        ghostArrowPath.lineTo((wx + hLen * kotlin.math.sin(headAngle1)).toFloat(), (wy - hLen * kotlin.math.cos(headAngle1)).toFloat())
        ghostArrowPath.lineTo((wx + hLen * kotlin.math.sin(headAngle2)).toFloat(), (wy - hLen * kotlin.math.cos(headAngle2)).toFloat())
        ghostArrowPath.close()
        canvas.drawPath(ghostArrowPath, ghostArrowHeadPaint)

        // Draw Tidal Current Vector at (gx, gy)
        val curDeg = targetPt.currentDirectionDeg ?: (windDeg + 90.0)
        val curRad = Math.toRadians(curDeg - tileBox.rotate)
        val curLen = 24f * density
        val cx = gx + (curLen * kotlin.math.sin(curRad)).toFloat()
        val cy = gy - (curLen * kotlin.math.cos(curRad)).toFloat()

        ghostTideArrowPaint.color = if (isNight) 0xFFFF8A80.toInt() else 0xFFFFB300.toInt()
        ghostArrowHeadPaint.color = ghostTideArrowPaint.color
        canvas.drawLine(gx, gy, cx, cy, ghostTideArrowPaint)

        // Arrow head for tide
        ghostArrowPath.rewind()
        val tHead1 = curRad + Math.toRadians(150.0)
        val tHead2 = curRad - Math.toRadians(150.0)
        ghostArrowPath.moveTo(cx, cy)
        ghostArrowPath.lineTo((cx + hLen * kotlin.math.sin(tHead1)).toFloat(), (cy - hLen * kotlin.math.cos(tHead1)).toFloat())
        ghostArrowPath.lineTo((cx + hLen * kotlin.math.sin(tHead2)).toFloat(), (cy - hLen * kotlin.math.cos(tHead2)).toFloat())
        ghostArrowPath.close()
        canvas.drawPath(ghostArrowPath, ghostArrowHeadPaint)
    }

    private var isDragging = false
    private var isDraggingPreview = false

    override fun onTouchEvent(event: android.view.MotionEvent, tileBox: RotatedTileBox): Boolean {
        val lat = settings.NAUTICAL_ANCHOR_LAT.get()
        val lon = settings.NAUTICAL_ANCHOR_LON.get()
        
        val pLat = settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val pLon = settings.NAUTICAL_ANCHOR_PREVIEW_LON.get()

        val x = event.x
        val y = event.y

        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val touchRadiusSq = (48 * tileBox.density).let { it * it }
                
                // Prioritize dragging preview
                if (pLat != 0.0) {
                    val pX = tileBox.getPixXFromLatLon(pLat, pLon)
                    val pY = tileBox.getPixYFromLatLon(pLat, pLon)
                    if ((x - pX) * (x - pX) + (y - pY) * (y - pY) < touchRadiusSq) {
                        isDraggingPreview = true
                        return true
                    }
                }
                
                if (lat != 0.0) {
                    val anchorX = tileBox.getPixXFromLatLon(lat, lon)
                    val anchorY = tileBox.getPixYFromLatLon(lat, lon)
                    if ((x - anchorX) * (x - anchorX) + (y - anchorY) * (y - anchorY) < touchRadiusSq) {
                        isDragging = true
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isDragging || isDraggingPreview) {
                    val newLatLon = tileBox.getLatLonFromPixel(x, y)
                    if (isDraggingPreview) {
                        settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(newLatLon.latitude)
                        settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(newLatLon.longitude)
                    } else {
                        settings.NAUTICAL_ANCHOR_LAT.set(newLatLon.latitude)
                        settings.NAUTICAL_ANCHOR_LON.set(newLatLon.longitude)
                        NauticalPlugin.getInstance()?.anchorWatchdog?.resetCounter()
                    }
                    view.refreshMap()
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isDragging || isDraggingPreview) {
                    isDragging = false
                    isDraggingPreview = false
                    return true
                }
            }
        }
        return false
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean {
        val latLon = tileBox.getLatLonFromPixel(point.x, point.y)
        
        // If preview is active, move preview
        if (settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get() != 0.0) {
            settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(latLon.latitude)
            settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(latLon.longitude)
            app.showToastMessage(R.string.nautical_anchor_moved_to_tap)
        } else {
            settings.NAUTICAL_ANCHOR_LAT.set(latLon.latitude)
            settings.NAUTICAL_ANCHOR_LON.set(latLon.longitude)
            
            // If not already active, we might want to default radius
            if (settings.NAUTICAL_ANCHOR_RADIUS.get() <= 0f) {
                 settings.NAUTICAL_ANCHOR_RADIUS.set(50f) // 50m default if long pressed manually
            }
            
            NauticalPlugin.getInstance()?.anchorWatchdog?.resetCounter()
            app.showToastMessage(R.string.nautical_anchor_btn_drop)
        }
        
        view.refreshMap()
        return true
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
