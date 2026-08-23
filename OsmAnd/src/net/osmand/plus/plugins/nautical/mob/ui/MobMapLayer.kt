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

    private val patternPath = Path()
    private val returnLinePath = Path()
    private val waypointRect = RectF()

    fun updateState(state: MobUiState) {
        this.mobUiState = state
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val state = mobUiState ?: return
        if (state.state == MobState.INACTIVE) return

        val mobLocation = state.mobLocation ?: return
        val density = context.resources.displayMetrics.density

        // 1. Draw original MOB drop location marker (Red circle + crosshair)
        val dropX = tileBox.getPixXFromLatLon(mobLocation.latitude, mobLocation.longitude)
        val dropY = tileBox.getPixYFromLatLon(mobLocation.latitude, mobLocation.longitude)

        val radius = 24f * density
        val crosshairSize = 36f * density

        canvas.drawCircle(dropX, dropY, radius, fillPaint)
        canvas.drawCircle(dropX, dropY, radius, markerPaint)
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
            canvas.drawLine(datumX, datumY - crosshairSize * 0.8f, datumX, datumY + crosshairSize * 0.8f, datumY, driftingDatumPaint)
        }

        // Uncertainty radius circle around estimated datum
        val uncertaintyMeters = state.uncertaintyRadiusMeters
        if (uncertaintyMeters > 0.0) {
            val northP = KMapUtils.rhumbDestinationPoint(datumLocation.latitude, datumLocation.longitude, uncertaintyMeters, 0.0)
            val northY = tileBox.getPixYFromLatLon(northP.latitude, northP.longitude)
            val pixRadius = abs(datumY - northY).coerceAtLeast(6f)
            canvas.drawCircle(datumX, datumY, pixRadius, searchUncertaintyFillPaint)
            canvas.drawCircle(datumX, datumY, pixRadius, searchUncertaintyStrokePaint)
        }

        val app = context.applicationContext as? OsmandApplication
        val boatLocation = app?.locationProvider?.lastKnownLocation
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

            // Draw numbered search waypoints
            for (i in sarWaypoints.indices) {
                val pt = sarWaypoints[i]
                val px = tileBox.getPixXFromLatLon(pt.latitude, pt.longitude)
                val py = tileBox.getPixYFromLatLon(pt.latitude, pt.longitude)

                canvas.drawCircle(px, py, 5f * density, sarWaypointPaint)

                val label = (i + 1).toString()
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
                canvas.drawRoundRect(waypointRect, 6f, 6f, sarWaypointStrokePaint)
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
