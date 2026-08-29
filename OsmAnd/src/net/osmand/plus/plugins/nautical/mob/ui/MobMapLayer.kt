package net.osmand.plus.plugins.nautical.mob.ui

import android.content.Context
import android.graphics.*
import kotlin.math.abs
import net.osmand.data.LatLon
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.mob.engine.MobState
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.shared.util.KMapUtils

/**
 * Map layer for Man Overboard (MOB) visualization.
 * Draws the drop location, drifting casualty datum with CEP uncertainty circle,
 * and IAMSAR search pattern tracks with zero per-frame runtime allocations.
 */
class MobMapLayer(context: Context) : OsmandMapLayer(context) {

    private var mobUiState: MobUiState? = null

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        alpha = 90
        style = Paint.Style.FILL
    }

    private val driftingDatumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt() // Amber
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val driftingDatumFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        alpha = 90
        style = Paint.Style.FILL
    }

    private val driftLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val searchUncertaintyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        alpha = 30
        style = Paint.Style.FILL
    }

    private val searchUncertaintyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        alpha = 140
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val activeLegPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val sarWaypointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val sarWaypointBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD212121.toInt()
        style = Paint.Style.FILL
    }

    private val sarWaypointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val sarWaypointTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val pulsingCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.RED
    }

    private val patternPath = Path()
    private val activeLegPath = Path()
    private val returnLinePath = Path()
    private val waypointRect = RectF()
    private val searchEllipseRect = RectF()

    fun updateState(state: MobUiState) {
        this.mobUiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    private fun setupPaints(isNight: Boolean) {
        if (isNight) {
            val brightRed = 0xFFFF1744.toInt()
            val vibrantRed = 0xFFFF5252.toInt()
            val lightRed = 0xFFFF8A80.toInt()

            markerPaint.color = brightRed
            fillPaint.color = brightRed
            fillPaint.alpha = 60

            driftingDatumPaint.color = vibrantRed
            driftingDatumFillPaint.color = vibrantRed
            driftingDatumFillPaint.alpha = 60

            driftLinePaint.color = vibrantRed

            searchUncertaintyFillPaint.color = 0x25B71C1C.toInt()
            searchUncertaintyStrokePaint.color = 0x80FF1744.toInt()

            patternPaint.color = brightRed
            activeLegPaint.color = brightRed
            sarWaypointPaint.color = lightRed
            sarWaypointBgPaint.color = 0xEE120000.toInt()
            sarWaypointStrokePaint.color = brightRed
            sarWaypointTextPaint.color = lightRed

            linePaint.color = brightRed
            pulsingCirclePaint.color = brightRed
        } else {
            markerPaint.color = Color.RED
            fillPaint.color = Color.RED
            fillPaint.alpha = 90

            driftingDatumPaint.color = 0xFFFF9800.toInt()
            driftingDatumFillPaint.color = 0xFFFF9800.toInt()
            driftingDatumFillPaint.alpha = 90

            driftLinePaint.color = 0xFFFF9800.toInt()

            searchUncertaintyFillPaint.color = 0xFFFF9800.toInt()
            searchUncertaintyFillPaint.alpha = 30
            searchUncertaintyStrokePaint.color = 0xFFFF9800.toInt()
            searchUncertaintyStrokePaint.alpha = 140

            patternPaint.color = Color.YELLOW
            activeLegPaint.color = 0xFF00E5FF.toInt()
            sarWaypointPaint.color = Color.YELLOW
            sarWaypointBgPaint.color = 0xDD212121.toInt()
            sarWaypointStrokePaint.color = Color.YELLOW
            sarWaypointTextPaint.color = Color.WHITE

            linePaint.color = Color.RED
            pulsingCirclePaint.color = Color.RED
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val state = mobUiState ?: return
        if (state.state == MobState.INACTIVE) return

        val mobLocation = state.mobLocation ?: return
        val density = context.resources.displayMetrics.density

        val app = context.applicationContext as? OsmandApplication ?: return
        val isNight = net.osmand.plus.plugins.nautical.NauticalPlugin.isNightVision(app)
        setupPaints(isNight)

        // 1. Draw original MOB drop location marker (Red circle + crosshair + pulsing circle)
        val dropX = tileBox.getPixXFromLatLon(mobLocation.latitude, mobLocation.longitude)
        val dropY = tileBox.getPixYFromLatLon(mobLocation.latitude, mobLocation.longitude)

        val radius = 24f * density
        val crosshairSize = 36f * density

        val pulsePhase = (System.currentTimeMillis() % 1200L) / 1200f
        val pulseRadius = radius + (pulsePhase * 20f * density)
        val pulseAlpha = ((1f - pulsePhase) * 220).toInt().coerceIn(0, 255)
        pulsingCirclePaint.alpha = pulseAlpha

        canvas.drawCircle(dropX, dropY, radius, fillPaint)
        canvas.drawCircle(dropX, dropY, radius, markerPaint)
        canvas.drawCircle(dropX, dropY, pulseRadius, pulsingCirclePaint)
        canvas.drawLine(dropX - crosshairSize, dropY, dropX + crosshairSize, dropY, markerPaint)
        canvas.drawLine(dropX, dropY - crosshairSize, dropX, dropY + crosshairSize, markerPaint)

        // 2. Draw Estimated Drifting Datum & Uncertainty Circle
        val datumLocation = state.estimatedCasualtyLocation ?: mobLocation
        val datumX = tileBox.getPixXFromLatLon(datumLocation.latitude, datumLocation.longitude)
        val datumY = tileBox.getPixYFromLatLon(datumLocation.latitude, datumLocation.longitude)

        if (datumLocation != mobLocation) {
            // Drift trajectory line from drop to estimated datum
            canvas.drawLine(dropX, dropY, datumX, datumY, driftLinePaint)

            // Drifting datum marker
            canvas.drawCircle(datumX, datumY, radius * 0.8f, driftingDatumFillPaint)
            canvas.drawCircle(datumX, datumY, radius * 0.8f, driftingDatumPaint)
            canvas.drawLine(datumX - crosshairSize * 0.8f, datumY, datumX + crosshairSize * 0.8f, datumY, driftingDatumPaint)
            canvas.drawLine(datumX, datumY - crosshairSize * 0.8f, datumX, datumY + crosshairSize * 0.8f, driftingDatumPaint)
        }

        // 2b. IAMSAR Expanding Search Probability Ellipse around estimated datum
        val majorMeters = if (state.ellipseMajorRadiusMeters > 0.0) state.ellipseMajorRadiusMeters else state.uncertaintyRadiusMeters
        val minorMeters = if (state.ellipseMinorRadiusMeters > 0.0) state.ellipseMinorRadiusMeters else majorMeters * 0.6
        if (majorMeters > 0.0) {
            val northP = KMapUtils.rhumbDestinationPoint(datumLocation.latitude, datumLocation.longitude, majorMeters, 0.0)
            val northY = tileBox.getPixYFromLatLon(northP.latitude, northP.longitude)
            val majorPix = abs(datumY - northY).coerceAtLeast(8f)

            val eastP = KMapUtils.rhumbDestinationPoint(datumLocation.latitude, datumLocation.longitude, minorMeters, 90.0)
            val eastX = tileBox.getPixXFromLatLon(eastP.latitude, eastP.longitude)
            val minorPix = abs(datumX - eastX).coerceAtLeast(6f)

            searchEllipseRect.set(datumX - majorPix, datumY - minorPix, datumX + majorPix, datumY + minorPix)

            canvas.save()
            canvas.rotate((state.ellipseBearingDeg - tileBox.rotate).toFloat(), datumX, datumY)
            canvas.drawOval(searchEllipseRect, searchUncertaintyFillPaint)
            canvas.drawOval(searchEllipseRect, searchUncertaintyStrokePaint)
            canvas.restore()
        }

        val boatLocation = app.locationProvider.lastKnownLocation
        val engine = net.osmand.plus.plugins.nautical.NauticalPlugin.engine

        // 3. Draw SAR Pattern if active
        val sarWaypoints = if (state.sarPatternWaypoints.isNotEmpty()) {
            state.sarPatternWaypoints
        } else if (engine != null && engine.isFollowingRoute) {
            engine.getRoutePoints().map { LatLon(it.first, it.second) }
        } else {
            emptyList()
        }

        if (sarWaypoints.isNotEmpty()) {
            patternPath.reset()
            val first = sarWaypoints.first()
            patternPath.moveTo(
                tileBox.getPixXFromLatLon(first.latitude, first.longitude),
                tileBox.getPixYFromLatLon(first.latitude, first.longitude)
            )
            for (i in 1 until sarWaypoints.size) {
                val pt = sarWaypoints[i]
                patternPath.lineTo(
                    tileBox.getPixXFromLatLon(pt.latitude, pt.longitude),
                    tileBox.getPixYFromLatLon(pt.latitude, pt.longitude)
                )
            }
            canvas.drawPath(patternPath, patternPaint)

            // Draw highlighted active leg
            val activeIdx = state.activeSarWaypointIndex.coerceIn(0, sarWaypoints.size - 1)
            activeLegPath.reset()
            if (activeIdx == 0) {
                val startX = if (boatLocation != null) tileBox.getPixXFromLatLon(boatLocation.latitude, boatLocation.longitude) else datumX
                val startY = if (boatLocation != null) tileBox.getPixYFromLatLon(boatLocation.latitude, boatLocation.longitude) else datumY
                val endX = tileBox.getPixXFromLatLon(sarWaypoints[0].latitude, sarWaypoints[0].longitude)
                val endY = tileBox.getPixYFromLatLon(sarWaypoints[0].latitude, sarWaypoints[0].longitude)
                activeLegPath.moveTo(startX, startY)
                activeLegPath.lineTo(endX, endY)
            } else {
                val prevPt = sarWaypoints[activeIdx - 1]
                val targetPt = sarWaypoints[activeIdx]
                activeLegPath.moveTo(
                    tileBox.getPixXFromLatLon(prevPt.latitude, prevPt.longitude),
                    tileBox.getPixYFromLatLon(prevPt.latitude, prevPt.longitude)
                )
                activeLegPath.lineTo(
                    tileBox.getPixXFromLatLon(targetPt.latitude, targetPt.longitude),
                    tileBox.getPixYFromLatLon(targetPt.latitude, targetPt.longitude)
                )
            }
            canvas.drawPath(activeLegPath, activeLegPaint)

            // Draw numbered search waypoints (S_1, S_2, ... S_n)
            for (i in sarWaypoints.indices) {
                val pt = sarWaypoints[i]
                val px = tileBox.getPixXFromLatLon(pt.latitude, pt.longitude)
                val py = tileBox.getPixYFromLatLon(pt.latitude, pt.longitude)

                val isActiveWp = (i == activeIdx)
                if (isActiveWp) {
                    canvas.drawCircle(px, py, 9f * density, pulsingCirclePaint)
                }

                canvas.drawCircle(px, py, (if (isActiveWp) 6f else 4f) * density, sarWaypointPaint)

                val label = "S${i + 1}"
                val textW = sarWaypointTextPaint.measureText(label)
                val textH = sarWaypointTextPaint.textSize
                val badgeW = textW + 12f
                val badgeH = textH + 6f
                val badgeX = px + 12f + (badgeW / 2f)
                val badgeY = py - 8f

                waypointRect.set(
                    badgeX - badgeW / 2f,
                    badgeY - badgeH / 2f,
                    badgeX + badgeW / 2f,
                    badgeY + badgeH / 2f
                )
                canvas.drawRoundRect(waypointRect, 6f, 6f, sarWaypointBgPaint)
                canvas.drawRoundRect(waypointRect, 6f, 6f, if (isActiveWp) activeLegPaint else sarWaypointStrokePaint)
                canvas.drawText(label, badgeX, badgeY + (textH * 0.35f), sarWaypointTextPaint)
            }
        }

        // 4. Draw return vector line if boat location is available and NOT following a pattern
        if (boatLocation != null && sarWaypoints.isEmpty()) {
            val boatX = tileBox.getPixXFromLatLon(boatLocation.latitude, boatLocation.longitude)
            val boatY = tileBox.getPixYFromLatLon(boatLocation.latitude, boatLocation.longitude)
            
            returnLinePath.reset()
            returnLinePath.moveTo(boatX, boatY)
            returnLinePath.lineTo(datumX, datumY)
            canvas.drawPath(returnLinePath, linePaint)
        }
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
